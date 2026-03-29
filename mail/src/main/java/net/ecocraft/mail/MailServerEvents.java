package net.ecocraft.mail;

import com.mojang.logging.LogUtils;
import net.ecocraft.core.EcoServerEvents;
import net.ecocraft.mail.command.MailCommand;
import net.ecocraft.mail.config.MailConfig;
import net.ecocraft.mail.data.MailItemAttachment;
import net.ecocraft.mail.permission.MailPermissions;
import net.ecocraft.mail.service.MailService;
import net.ecocraft.mail.storage.MailStorageProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * Handles server lifecycle events for the mail module.
 * Initializes storage, creates the MailService, registers commands,
 * and runs periodic expiration ticks.
 */
@EventBusSubscriber(modid = MailMod.MOD_ID)
public class MailServerEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static MailStorageProvider storageProvider;
    private static MailService mailService;
    private static MinecraftServer serverInstance;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        var server = event.getServer();
        serverInstance = server;
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        Path dbPath = worldDir.resolve("ecocraft_mail.db");

        storageProvider = new MailStorageProvider(dbPath);
        storageProvider.initialize();

        mailService = new MailService(
                storageProvider,
                EcoServerEvents.getEconomy(),
                EcoServerEvents.getCurrencyRegistry()
        );

        // Wire item deliverer so collected mails deliver items to player inventory
        mailService.setItemDeliverer((playerUuid, items) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) {
                LOGGER.warn("Cannot deliver mail items: player {} is offline", playerUuid);
                return;
            }
            for (MailItemAttachment item : items) {
                try {
                    ItemStack stack;
                    if (item.itemNbt() != null && !item.itemNbt().isEmpty()) {
                        CompoundTag tag = TagParser.parseTag(item.itemNbt());
                        stack = ItemStack.OPTIONAL_CODEC.parse(
                                player.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag
                        ).getOrThrow();
                        if (stack.isEmpty()) {
                            var itemRL = net.minecraft.resources.ResourceLocation.parse(item.itemId());
                            var itemObj = BuiltInRegistries.ITEM.get(itemRL);
                            stack = new ItemStack(itemObj, item.quantity());
                        }
                    } else {
                        var itemRL = net.minecraft.resources.ResourceLocation.parse(item.itemId());
                        var itemObj = BuiltInRegistries.ITEM.get(itemRL);
                        stack = new ItemStack(itemObj, item.quantity());
                    }
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to deliver mail item {} to player {}", item.itemId(), playerUuid, e);
                }
            }
        });

        // Apply config to service
        applyConfig();

        LOGGER.info("EcoCraft Mail initialized with database at {}", dbPath);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (storageProvider != null) {
            storageProvider.shutdown();
            storageProvider = null;
        }
        mailService = null;
        serverInstance = null;
        tickCounter = 0;
        LOGGER.info("EcoCraft Mail shutdown complete.");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MailCommand.register(event.getDispatcher(), MailServerEvents::getService);
        LOGGER.info("EcoCraft Mail commands registered.");
    }

    @SubscribeEvent
    public static void onPermissionGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(
            MailPermissions.READ,
            MailPermissions.COMMAND,
            MailPermissions.SEND,
            MailPermissions.ATTACH_ITEMS,
            MailPermissions.ATTACH_CURRENCY,
            MailPermissions.COD,
            MailPermissions.ADMIN,
            MailPermissions.MAX_ATTACHMENTS
        );
        LOGGER.info("EcoCraft Mail permission nodes registered.");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter >= 1200) { // 60 seconds
            tickCounter = 0;
            if (mailService != null) {
                try {
                    mailService.expireMails();
                } catch (Exception e) {
                    LOGGER.error("Error during mail expiration tick", e);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (mailService == null) return;
        if (storageProvider == null) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        try {
            int mailCount = storageProvider.countAvailableMails(player.getUUID());
            if (mailCount > 0) {
                player.sendSystemMessage(Component.translatable(
                        "ecocraft_mail.login.unread_mails", mailCount
                ));
            }
        } catch (Exception e) {
            LOGGER.error("Error checking mail count for player {}", player.getName().getString(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Config
    // -------------------------------------------------------------------------

    private static void applyConfig() {
        if (mailService == null) return;
        var config = MailConfig.CONFIG;
        mailService.setExpiryMs(config.mailExpiryDays.get() * 24L * 60 * 60 * 1000);
        mailService.setCodFeePercent(config.codFeePercent.get());
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public static MailService getService() {
        return mailService;
    }

    public static MailStorageProvider getStorage() {
        return storageProvider;
    }
}

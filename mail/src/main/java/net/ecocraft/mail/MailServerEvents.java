package net.ecocraft.mail;

import com.mojang.logging.LogUtils;
import net.ecocraft.core.EcoServerEvents;
import net.ecocraft.mail.command.MailCommand;
import net.ecocraft.mail.config.MailConfig;
import net.ecocraft.mail.permission.MailPermissions;
import net.ecocraft.mail.service.MailService;
import net.ecocraft.mail.storage.MailStorageProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        var server = event.getServer();
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        Path dbPath = worldDir.resolve("ecocraft_mail.db");

        storageProvider = new MailStorageProvider(dbPath);
        storageProvider.initialize();

        mailService = new MailService(
                storageProvider,
                EcoServerEvents.getEconomy(),
                EcoServerEvents.getCurrencyRegistry()
        );

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
                player.sendSystemMessage(Component.literal(
                        "\u00a7e\u2709 Vous avez " + mailCount + " mail(s) dans votre bo\u00eete aux lettres."
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

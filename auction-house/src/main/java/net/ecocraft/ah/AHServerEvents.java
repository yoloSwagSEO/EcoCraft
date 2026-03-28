package net.ecocraft.ah;

import com.mojang.logging.LogUtils;
import net.ecocraft.ah.command.AHCommand;
import net.ecocraft.ah.data.AuctionListing;
import net.ecocraft.ah.data.EnchantmentEntry;
import net.ecocraft.ah.data.EnchantmentExtractor;
import net.ecocraft.ah.data.ItemFingerprint;
import net.ecocraft.ah.data.ItemStackSerializer;
import net.ecocraft.ah.service.AuctionService;
import net.ecocraft.ah.storage.AuctionStorageProvider;
import net.ecocraft.core.EcoServerEvents;
import net.minecraft.core.RegistryAccess;
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
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.List;

/**
 * Handles server lifecycle events for the auction house module.
 * Initializes storage, creates the AuctionService, registers commands,
 * and runs periodic expiration ticks.
 */
@EventBusSubscriber(modid = AuctionHouseMod.MOD_ID)
public class AHServerEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static AuctionStorageProvider storageProvider;
    private static AuctionService auctionService;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        var server = event.getServer();
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        Path dbPath = worldDir.resolve("ecocraft_ah.db");

        storageProvider = new AuctionStorageProvider(dbPath);
        storageProvider.initialize();

        auctionService = new AuctionService(
                storageProvider,
                EcoServerEvents.getEconomy(),
                EcoServerEvents.getCurrencyRegistry()
        );

        reindexEnchantments(server);
        backfillFingerprints(server);

        LOGGER.info("Auction House initialized with database at {}", dbPath);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (storageProvider != null) {
            storageProvider.shutdown();
            storageProvider = null;
        }
        auctionService = null;
        tickCounter = 0;
        LOGGER.info("Auction House shutdown complete.");
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AHCommand.register(event.getDispatcher(), AHServerEvents::getService);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        if (tickCounter >= 1200) {
            tickCounter = 0;
            if (auctionService != null) {
                try {
                    auctionService.expireListings();
                } catch (Exception e) {
                    LOGGER.error("Error during auction expiration tick", e);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (auctionService == null) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        try {
            int uncollected = auctionService.countUncollectedParcels(player.getUUID());
            if (uncollected > 0) {
                player.sendSystemMessage(Component.translatable(
                        "ecocraft_ah.login.parcels_pending", uncollected
                ));
            }
        } catch (Exception e) {
            LOGGER.error("Error checking uncollected parcels for player {}", player.getName().getString(), e);
        }
    }

    private static void reindexEnchantments(MinecraftServer server) {
        AuctionStorageProvider storage = getStorage();
        if (storage == null) return;

        List<AuctionListing> unindexed = storage.getListingsWithoutEnchantmentIndex();
        if (unindexed.isEmpty()) return;

        RegistryAccess registries = server.registryAccess();
        int count = 0;

        for (AuctionListing listing : unindexed) {
            if (listing.itemNbt() == null || listing.itemNbt().isEmpty()) continue;

            try {
                ItemStack stack = ItemStackSerializer.deserialize(listing.itemNbt(), registries);
                List<EnchantmentEntry> enchantments = EnchantmentExtractor.extract(stack);
                if (!enchantments.isEmpty()) {
                    storage.indexEnchantments(listing.id(), enchantments);
                    count++;
                }
            } catch (Exception e) {
                // Log and continue - don't fail server start for one bad listing
                System.err.println("Failed to reindex enchantments for listing " + listing.id() + ": " + e.getMessage());
            }
        }

        if (count > 0) {
            System.out.println("[EcoCraft AH] Reindexed enchantments for " + count + " existing listings.");
        }
    }

    private static void backfillFingerprints(MinecraftServer server) {
        AuctionStorageProvider storage = getStorage();
        if (storage == null) return;

        List<AuctionListing> unfingerprinted = storage.getListingsWithoutFingerprint();
        if (unfingerprinted.isEmpty()) return;

        RegistryAccess registries = server.registryAccess();
        int count = 0;

        for (AuctionListing listing : unfingerprinted) {
            String fingerprint;
            if (listing.itemNbt() != null && !listing.itemNbt().isEmpty()) {
                fingerprint = ItemFingerprint.computeFromNbt(listing.itemId(), listing.itemNbt(), registries);
            } else {
                fingerprint = listing.itemId();
            }
            storage.updateListingFingerprint(listing.id(), fingerprint);
            count++;
        }

        if (count > 0) {
            System.out.println("[EcoCraft AH] Backfilled fingerprints for " + count + " existing listings.");
        }
    }

    public static AuctionService getService() {
        return auctionService;
    }

    public static AuctionStorageProvider getStorage() {
        return storageProvider;
    }
}

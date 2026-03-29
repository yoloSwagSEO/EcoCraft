package net.ecocraft.ah;

import com.mojang.logging.LogUtils;
import net.ecocraft.ah.command.AHCommand;
import net.ecocraft.ah.data.AuctionListing;
import net.ecocraft.ah.data.EnchantmentEntry;
import net.ecocraft.ah.data.EnchantmentExtractor;
import net.ecocraft.ah.data.ItemFingerprint;
import net.ecocraft.ah.data.ItemStackSerializer;
import net.ecocraft.ah.network.payload.AHNotificationPayload;
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
import net.ecocraft.ah.permission.AHPermissions;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
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
        // Inject notification sender: online players get immediate toast,
        // offline players get notification queued in DB for delivery on login
        final MinecraftServer finalServer = server;
        auctionService.setNotificationSender((playerUuid, eventType, itemName, otherPlayerName, amount, currencyId) -> {
            ServerPlayer player = finalServer.getPlayerList().getPlayer(playerUuid);
            if (player != null) {
                PacketDistributor.sendToPlayer(player,
                        new AHNotificationPayload(eventType, itemName, otherPlayerName, amount, currencyId));
            } else if (storageProvider != null) {
                try {
                    storageProvider.createPendingNotification(playerUuid, eventType, itemName,
                            otherPlayerName, amount, currencyId);
                } catch (Exception e) {
                    LOGGER.error("Failed to queue notification for offline player {}", playerUuid, e);
                }
            }
        });
        // Inject profile resolver: resolves player names to UUIDs via game profile cache
        auctionService.setProfileResolver(playerName -> {
            var cache = finalServer.getProfileCache();
            if (cache == null) return null;
            var profile = cache.get(playerName);
            return profile.map(com.mojang.authlib.GameProfile::getId).orElse(null);
        });

        // Wire mail sender if mail module is loaded (via reflection to avoid compile-time dependency)
        if (net.neoforged.fml.ModList.get().isLoaded("ecocraft_mail")) {
            try {
                Class<?> mailEventsClass = Class.forName("net.ecocraft.mail.MailServerEvents");
                Object mailService = mailEventsClass.getMethod("getService").invoke(null);
                if (mailService != null) {
                    Class<?> attachmentClass = Class.forName("net.ecocraft.mail.data.MailItemAttachment");
                    var attachmentCtor = attachmentClass.getConstructor(String.class, String.class, String.class, int.class);
                    var sendMethod = mailService.getClass().getMethod("sendSystemMail",
                            String.class, java.util.UUID.class, String.class, String.class,
                            java.util.List.class, long.class, String.class, boolean.class, long.class);

                    auctionService.setMailSender((recipientUuid, subject, body, itemId, itemName, itemNbt, qty, currencyAmount, currencyId2, availableAt) -> {
                        try {
                            Object attachment = attachmentCtor.newInstance(itemId, itemName, itemNbt, qty);
                            sendMethod.invoke(mailService, "Hôtel des Ventes", recipientUuid, subject, body,
                                    java.util.List.of(attachment), currencyAmount, currencyId2, false, availableAt);
                        } catch (Exception ex) {
                            LOGGER.error("Failed to send mail via AH integration: {}", ex.getMessage());
                        }
                    });
                    LOGGER.info("Mail integration enabled for EcoCraft Auction House");
                }
            } catch (Exception e) {
                LOGGER.warn("Mail integration not available: {}", e.getMessage());
            }
        }

        // Wire KubeJS event dispatcher if KubeJS is loaded
        if (net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
            try {
                var dispatcher = new net.ecocraft.ah.compat.kubejs.AHEventDispatcherImpl(server);
                auctionService.setAHEventDispatcher(dispatcher);
                LOGGER.info("KubeJS integration enabled for EcoCraft Auction House");
            } catch (Exception e) {
                LOGGER.warn("Failed to initialize KubeJS AH integration: {}", e.getMessage());
            }
        }

        applyConfigDefaults();
        reindexEnchantments(server);
        backfillFingerprints(server);
        registerFakeProfiles(server);

        LOGGER.info("Auction House initialized with database at {}", dbPath);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (storageProvider != null) {
            storageProvider.shutdown();
            storageProvider = null;
        }
        if (auctionService != null) {
            auctionService.setNotificationSender(null);
            auctionService.setProfileResolver(null);
            auctionService.setAHEventDispatcher(null);
            auctionService.setMailSender(null);
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

        // Deliver pending notifications queued while player was offline
        if (storageProvider != null) {
            try {
                var pending = storageProvider.getPendingNotifications(player.getUUID());
                for (var notif : pending) {
                    PacketDistributor.sendToPlayer(player,
                            new AHNotificationPayload(notif.eventType(), notif.itemName(),
                                    notif.playerName(), notif.amount(), notif.currencyId()));
                }
                if (!pending.isEmpty()) {
                    storageProvider.deletePendingNotifications(player.getUUID());
                }
            } catch (Exception e) {
                LOGGER.error("Error delivering pending notifications for player {}", player.getName().getString(), e);
            }
        }
    }

    /**
     * Applies NeoForge config values as defaults to the default AH instance
     * when it has not been customized yet (still at hardcoded defaults).
     */
    private static void applyConfigDefaults() {
        if (storageProvider == null) return;
        try {
            var config = net.ecocraft.ah.config.AHConfig.CONFIG;
            net.ecocraft.ah.data.AHInstance defaultAh = storageProvider.getDefaultAHInstance();
            if (defaultAh == null) {
                net.ecocraft.ah.data.AHInstance newDefault = new net.ecocraft.ah.data.AHInstance(
                        net.ecocraft.ah.data.AHInstance.DEFAULT_ID, "default",
                        net.ecocraft.ah.data.AHInstance.DEFAULT_NAME,
                        config.saleRate.get(), config.depositRate.get(),
                        new java.util.ArrayList<>(config.durations.get()),
                        true, true, "", false,
                        net.ecocraft.ah.data.AHInstance.DEFAULT_DELIVERY_DELAY_PURCHASE,
                        net.ecocraft.ah.data.AHInstance.DEFAULT_DELIVERY_DELAY_EXPIRED);
                storageProvider.createAHInstance(newDefault);
            } else if (defaultAh.saleRate() == net.ecocraft.ah.data.AHInstance.DEFAULT_SALE_RATE
                    && defaultAh.depositRate() == net.ecocraft.ah.data.AHInstance.DEFAULT_DEPOSIT_RATE
                    && defaultAh.durations().equals(net.ecocraft.ah.data.AHInstance.DEFAULT_DURATIONS)) {
                int cfgSaleRate = config.saleRate.get();
                int cfgDepositRate = config.depositRate.get();
                java.util.List<Integer> cfgDurations = new java.util.ArrayList<>(config.durations.get());
                if (cfgSaleRate != net.ecocraft.ah.data.AHInstance.DEFAULT_SALE_RATE
                        || cfgDepositRate != net.ecocraft.ah.data.AHInstance.DEFAULT_DEPOSIT_RATE
                        || !cfgDurations.equals(net.ecocraft.ah.data.AHInstance.DEFAULT_DURATIONS)) {
                    net.ecocraft.ah.data.AHInstance updated = defaultAh.withConfig(
                            defaultAh.name(), cfgSaleRate, cfgDepositRate,
                            cfgDurations, defaultAh.allowBuyout(), defaultAh.allowAuction(),
                            defaultAh.taxRecipient(), defaultAh.overridePermTax(),
                            defaultAh.deliveryDelayPurchase(),
                            defaultAh.deliveryDelayExpired());
                    storageProvider.updateAHInstance(updated);
                    LOGGER.info("Applied NeoForge config defaults to default AH instance");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error applying config defaults to AH instance", e);
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
                LOGGER.error("Failed to reindex enchantments for listing {}: {}", listing.id(), e.getMessage());
            }
        }

        if (count > 0) {
            LOGGER.info("Reindexed enchantments for {} existing listings.", count);
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
            LOGGER.info("Backfilled fingerprints for {} existing listings.", count);
        }
    }

    private static void registerFakeProfiles(MinecraftServer server) {
        var profileCache = server.getProfileCache();
        if (profileCache == null) return;
        String[] fakeNames = {"Grimald", "Thoria", "Keldorn", "Sylvanas", "Brakk",
                "Elyndra", "Morgrim", "Vaelith", "Drogan", "Isildra"};
        for (String name : fakeNames) {
            java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(("ecocraft_fake:" + name).getBytes());
            profileCache.add(new com.mojang.authlib.GameProfile(uuid, name));
        }
    }

    @SubscribeEvent
    public static void onPermissionGather(PermissionGatherEvent.Nodes event) {
        event.addNodes(
            AHPermissions.USE,
            AHPermissions.SELL,
            AHPermissions.BID,
            AHPermissions.CANCEL,
            AHPermissions.ADMIN_CANCEL,
            AHPermissions.ADMIN_SETTINGS,
            AHPermissions.ADMIN_RELOAD,
            AHPermissions.MAX_LISTINGS,
            AHPermissions.TAX_RATE,
            AHPermissions.DEPOSIT_RATE
        );
        LOGGER.info("EcoCraft Auction House permission nodes registered.");
    }

    public static AuctionService getService() {
        return auctionService;
    }

    /** Alias for KubeJS bindings. */
    public static AuctionService getAuctionService() {
        return auctionService;
    }

    public static AuctionStorageProvider getStorage() {
        return storageProvider;
    }
}

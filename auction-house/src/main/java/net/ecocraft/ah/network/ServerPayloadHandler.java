package net.ecocraft.ah.network;

import com.mojang.logging.LogUtils;
import net.ecocraft.ah.AHServerEvents;
import net.ecocraft.ah.data.*;
import net.ecocraft.ah.network.payload.*;
import net.ecocraft.ah.service.AuctionService;
import net.ecocraft.ah.storage.AuctionStorageProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.util.*;

/**
 * Handles client-to-server packets for the auction house.
 * Delegates to {@link AuctionService} via {@link AHServerEvents}.
 */
public final class ServerPayloadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PAGE_SIZE = 20;
    /** 7 days in milliseconds for price history window. */
    private static final long SEVEN_DAYS_MS = 7L * 24 * 3600 * 1000;

    private ServerPayloadHandler() {}

    public static void handleRequestListings(RequestListingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                String search = payload.search().isEmpty() ? null : payload.search();
                ItemCategory category = payload.category().isEmpty() ? null : ItemCategory.valueOf(payload.category());

                // Fetch all results (no category filter in SQL) and filter dynamically
                List<AuctionStorageProvider.ListingGroupSummary> allResults =
                        service.searchListings(AHInstance.DEFAULT_ID, search, null, 0, Integer.MAX_VALUE);

                // Filter by dynamically detected category if requested
                List<AuctionStorageProvider.ListingGroupSummary> filtered;
                if (category != null) {
                    filtered = new ArrayList<>();
                    for (var r : allResults) {
                        if (ItemCategoryDetector.detectFromId(r.itemId()) == category) {
                            filtered.add(r);
                        }
                    }
                } else {
                    filtered = allResults;
                }

                // Server-side pagination with client-provided page size
                int clientPageSize = Math.max(1, Math.min(payload.pageSize(), 100));
                int start = payload.page() * clientPageSize;
                int end = Math.min(start + clientPageSize, filtered.size());
                List<AuctionStorageProvider.ListingGroupSummary> page =
                        start < filtered.size() ? filtered.subList(start, end) : List.of();

                List<ListingsResponsePayload.ListingSummary> summaries = new ArrayList<>();
                for (var r : page) {
                    summaries.add(new ListingsResponsePayload.ListingSummary(
                            r.itemId(),
                            r.itemName(),
                            0xFFFFFFFF,
                            r.bestPrice(),
                            r.listingCount(),
                            r.totalQuantity()
                    ));
                }

                int totalPages = (int) Math.ceil((double) filtered.size() / clientPageSize);
                context.reply(new ListingsResponsePayload(summaries, payload.page(), totalPages));
            } catch (Exception e) {
                LOGGER.error("Error handling RequestListings", e);
                context.reply(new ListingsResponsePayload(List.of(), payload.page(), 0));
            }
        });
    }

    public static void handleRequestListingDetail(RequestListingDetailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                AuctionStorageProvider storage = AHServerEvents.getStorage();

                // Fetch available enchantments for this item type
                List<String> availableEnchantments = List.of();
                if (storage != null) {
                    availableEnchantments = storage.getAvailableEnchantments(payload.itemId());
                }

                // Fetch listings, optionally filtered by enchantments
                List<AuctionListing> listings;
                Set<String> enchantFilters = payload.enchantmentFilters() != null
                        ? new LinkedHashSet<>(payload.enchantmentFilters()) : Set.of();

                if (!enchantFilters.isEmpty() && storage != null) {
                    listings = storage.getListingsForItemFiltered(AHInstance.DEFAULT_ID, payload.itemId(), enchantFilters, 0, Integer.MAX_VALUE);
                } else {
                    listings = service.getListingDetail(AHInstance.DEFAULT_ID, payload.itemId());
                }

                // Group by seller UUID + itemNbt hash + price
                Map<String, List<AuctionListing>> groups = new LinkedHashMap<>();
                for (AuctionListing l : listings) {
                    long price = l.listingType() == ListingType.BUYOUT ? l.buyoutPrice() : l.currentBid();
                    String key = l.sellerUuid() + "|" + (l.itemNbt() != null ? l.itemNbt() : "") + "|" + price;
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
                }

                // Build entries from groups
                List<ListingDetailResponsePayload.ListingEntry> entries = new ArrayList<>();
                String itemName = "";
                for (var group : groups.values()) {
                    AuctionListing first = group.get(0);
                    if (itemName.isEmpty()) itemName = first.itemName();
                    int totalQty = group.stream().mapToInt(AuctionListing::quantity).sum();
                    long displayPrice = first.listingType() == ListingType.BUYOUT ? first.buyoutPrice() : first.currentBid();
                    long expiresInMs = Math.max(0, first.expiresAt() - System.currentTimeMillis());
                    entries.add(new ListingDetailResponsePayload.ListingEntry(
                            first.id(),
                            first.sellerName(),
                            totalQty,
                            displayPrice,
                            first.listingType().name(),
                            expiresInMs,
                            first.itemNbt() != null ? first.itemNbt() : ""
                    ));
                }

                // Price history
                ListingDetailResponsePayload.PriceInfo priceInfo;
                if (storage != null) {
                    String currencyId = listings.isEmpty()
                            ? service.getDefaultCurrencyId()
                            : listings.get(0).currencyId();
                    AuctionStorageProvider.PriceStats stats = storage.getPriceHistory(payload.itemId(), currencyId, SEVEN_DAYS_MS);
                    if (stats != null) {
                        priceInfo = new ListingDetailResponsePayload.PriceInfo(
                                stats.avgPrice(), stats.minPrice(), stats.maxPrice(), stats.volume());
                    } else {
                        priceInfo = new ListingDetailResponsePayload.PriceInfo(0, 0, 0, 0);
                    }
                } else {
                    priceInfo = new ListingDetailResponsePayload.PriceInfo(0, 0, 0, 0);
                }

                context.reply(new ListingDetailResponsePayload(
                        payload.itemId(), itemName, 0xFFFFFFFF, entries, priceInfo, availableEnchantments));
            } catch (Exception e) {
                LOGGER.error("Error handling RequestListingDetail", e);
                context.reply(new ListingDetailResponsePayload(
                        payload.itemId(), "", 0xFFFFFFFF, List.of(),
                        new ListingDetailResponsePayload.PriceInfo(0, 0, 0, 0), List.of()));
            }
        });
    }

    public static void handleCreateListing(CreateListingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                // Get item from the specified slot, or main hand if -1
                ItemStack itemToSell;
                int slotIndex = payload.slotIndex();
                if (slotIndex >= 0 && slotIndex < player.getInventory().getContainerSize()) {
                    itemToSell = player.getInventory().getItem(slotIndex);
                } else {
                    itemToSell = player.getMainHandItem();
                    slotIndex = -1;
                }

                if (itemToSell.isEmpty()) {
                    context.reply(new AHActionResultPayload(false, "Aucun objet s\u00e9lectionn\u00e9!"));
                    return;
                }

                // Extract enchantments from the ItemStack before removing it
                List<EnchantmentEntry> enchantments = EnchantmentExtractor.extract(itemToSell);

                String itemId = BuiltInRegistries.ITEM.getKey(itemToSell.getItem()).toString();
                String itemName = itemToSell.getHoverName().getString();
                String nbt = ItemStackSerializer.serialize(itemToSell, player.registryAccess());
                String fingerprint = ItemFingerprint.compute(itemToSell);
                int quantity = itemToSell.getCount();
                ListingType type = ListingType.valueOf(payload.listingType());
                BigDecimal price = BigDecimal.valueOf(payload.price());

                String currencyId = service.getDefaultCurrencyId();
                ItemCategory category = ItemCategoryDetector.detect(itemToSell);
                AuctionListing listing = service.createListing(
                        player.getUUID(),
                        player.getName().getString(),
                        itemId,
                        itemName,
                        nbt,
                        quantity,
                        type,
                        price,
                        payload.durationHours(),
                        currencyId,
                        category,
                        fingerprint
                );

                // Index enchantments for server-side filtering
                AuctionStorageProvider storage = AHServerEvents.getStorage();
                if (storage != null && !enchantments.isEmpty()) {
                    storage.indexEnchantments(listing.id(), enchantments);
                }

                // Remove item from the correct slot
                if (slotIndex >= 0) {
                    player.getInventory().setItem(slotIndex, ItemStack.EMPTY);
                } else {
                    player.getMainHandItem().setCount(0);
                }

                context.reply(new AHActionResultPayload(true, "Listing created successfully."));
                sendBalanceUpdate(player);
            } catch (AuctionService.AuctionException e) {
                context.reply(new AHActionResultPayload(false, e.getMessage()));
            } catch (Exception e) {
                LOGGER.error("Error handling CreateListing", e);
                context.reply(new AHActionResultPayload(false, "Internal error creating listing."));
            }
        });
    }

    public static void handleBuyListing(BuyListingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                service.buyListing(player.getUUID(), player.getName().getString(), payload.listingId(), payload.quantity());
                context.reply(new AHActionResultPayload(true, "Purchase successful! Check parcels to collect your item."));
                sendBalanceUpdate(player);
            } catch (AuctionService.AuctionException e) {
                context.reply(new AHActionResultPayload(false, e.getMessage()));
            } catch (Exception e) {
                LOGGER.error("Error handling BuyListing", e);
                context.reply(new AHActionResultPayload(false, "Internal error processing purchase."));
            }
        });
    }

    public static void handlePlaceBid(PlaceBidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                BigDecimal amount = BigDecimal.valueOf(payload.amount());
                service.placeBid(player.getUUID(), player.getName().getString(), payload.listingId(), amount);
                context.reply(new AHActionResultPayload(true, "Bid placed successfully."));
                sendBalanceUpdate(player);
            } catch (AuctionService.AuctionException e) {
                context.reply(new AHActionResultPayload(false, e.getMessage()));
            } catch (Exception e) {
                LOGGER.error("Error handling PlaceBid", e);
                context.reply(new AHActionResultPayload(false, "Internal error placing bid."));
            }
        });
    }

    public static void handleCancelListing(CancelListingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                service.cancelListing(player.getUUID(), payload.listingId());
                context.reply(new AHActionResultPayload(true, "Listing cancelled."));
            } catch (AuctionService.AuctionException e) {
                context.reply(new AHActionResultPayload(false, e.getMessage()));
            } catch (Exception e) {
                LOGGER.error("Error handling CancelListing", e);
                context.reply(new AHActionResultPayload(false, "Internal error cancelling listing."));
            }
        });
    }

    public static void handleCollectParcels(CollectParcelsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                var parcels = service.collectParcels(player.getUUID());

                // Deliver item parcels to player inventory
                for (var parcel : parcels) {
                    if (parcel.hasItem() && parcel.itemId() != null) {
                        try {
                            ItemStack stack;
                            if (parcel.itemNbt() != null && !parcel.itemNbt().isEmpty()) {
                                // Deserialize the full ItemStack (preserves enchantments, components, etc.)
                                stack = ItemStackSerializer.deserialize(parcel.itemNbt(), player.registryAccess());
                                if (stack.isEmpty()) {
                                    // Fallback to basic item if deserialization fails
                                    var itemRL = net.minecraft.resources.ResourceLocation.parse(parcel.itemId());
                                    var item = BuiltInRegistries.ITEM.get(itemRL);
                                    stack = new ItemStack(item, parcel.quantity());
                                }
                            } else {
                                // Legacy parcel without serialized data — create plain item
                                var itemRL = net.minecraft.resources.ResourceLocation.parse(parcel.itemId());
                                var item = BuiltInRegistries.ITEM.get(itemRL);
                                stack = new ItemStack(item, parcel.quantity());
                            }
                            if (!player.getInventory().add(stack)) {
                                // Drop on ground if inventory full
                                player.drop(stack, false);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to deliver parcel item {} to player", parcel.itemId(), e);
                        }
                    }
                }

                String msg = parcels.isEmpty()
                        ? "No parcels to collect."
                        : "Collected " + parcels.size() + " parcel(s).";
                context.reply(new AHActionResultPayload(true, msg));
                sendBalanceUpdate(player);
            } catch (Exception e) {
                LOGGER.error("Error handling CollectParcels", e);
                context.reply(new AHActionResultPayload(false, "Internal error collecting parcels."));
            }
        });
    }

    public static void handleRequestMyListings(RequestMyListingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                List<AuctionListing> listings;
                switch (payload.subTab()) {
                    case "purchases" -> listings = service.getMyPurchases(player.getUUID(), 0, Integer.MAX_VALUE);
                    case "bids" -> listings = service.getMyBids(player.getUUID());
                    default -> listings = service.getMyListings(player.getUUID(), 0, Integer.MAX_VALUE);
                }

                boolean isPurchases = "purchases".equals(payload.subTab());
                List<MyListingsResponsePayload.MyListingEntry> entries = new ArrayList<>();
                for (AuctionListing l : listings) {
                    long displayPrice = l.listingType() == ListingType.BUYOUT ? l.buyoutPrice() : l.currentBid();
                    long expiresInMs = Math.max(0, l.expiresAt() - System.currentTimeMillis());
                    // canCollect: for seller's expired listings only; purchases use the global collect button
                    boolean canCollect = !isPurchases && l.status() == ListingStatus.EXPIRED;
                    entries.add(new MyListingsResponsePayload.MyListingEntry(
                            l.id(),
                            l.itemId(),
                            l.itemName(),
                            0xFFFFFFFF,
                            displayPrice,
                            l.listingType().name(),
                            l.status().name(),
                            expiresInMs,
                            0, // bid count — would need a query
                            canCollect,
                            l.itemNbt() != null ? l.itemNbt() : ""
                    ));
                }

                // Get player stats for the revenue/taxes summary
                long sinceMs = System.currentTimeMillis() - SEVEN_DAYS_MS;
                AuctionStorageProvider.PlayerStats stats = service.getPlayerStats(player.getUUID(), sinceMs);
                int parcelsCount = service.countUncollectedParcels(player.getUUID());

                context.reply(new MyListingsResponsePayload(
                        entries, stats.totalSalesRevenue(), stats.taxesPaid(), parcelsCount));
            } catch (Exception e) {
                LOGGER.error("Error handling RequestMyListings", e);
                context.reply(new MyListingsResponsePayload(List.of(), 0, 0, 0));
            }
        });
    }

    public static void handleRequestBestPrice(RequestBestPricePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                long bestPrice = service.getBestPrice(AHInstance.DEFAULT_ID, payload.fingerprint(), payload.itemId());
                context.reply(new BestPriceResponsePayload(payload.itemId(), bestPrice));
            } catch (Exception e) {
                LOGGER.error("Error handling RequestBestPrice", e);
                context.reply(new BestPriceResponsePayload(payload.itemId(), -1));
            }
        });
    }

    public static void handleRequestLedger(RequestLedgerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                // Determine time window from period
                long sinceMs;
                switch (payload.period()) {
                    case "24h" -> sinceMs = System.currentTimeMillis() - 24L * 3600 * 1000;
                    case "7j", "7d" -> sinceMs = System.currentTimeMillis() - SEVEN_DAYS_MS;
                    case "30j", "30d" -> sinceMs = System.currentTimeMillis() - 30L * 24 * 3600 * 1000;
                    default -> sinceMs = 0L;
                }

                // Parse type filter - map client filter names to ParcelSource
                ParcelSource sourceFilter = null;
                if (payload.typeFilter() != null && !payload.typeFilter().isEmpty()
                        && !"all".equalsIgnoreCase(payload.typeFilter())) {
                    sourceFilter = switch (payload.typeFilter().toLowerCase()) {
                        case "purchases" -> ParcelSource.HDV_PURCHASE;
                        case "sales" -> ParcelSource.HDV_SALE;
                        case "auctions" -> ParcelSource.HDV_OUTBID;
                        case "expired" -> ParcelSource.HDV_EXPIRED;
                        default -> {
                            try { yield ParcelSource.valueOf(payload.typeFilter()); }
                            catch (IllegalArgumentException ignored) { yield null; }
                        }
                    };
                }

                var parcels = service.getLedger(player.getUUID(), sourceFilter, sinceMs, 0, Integer.MAX_VALUE);

                List<LedgerResponsePayload.LedgerEntry> entries = new ArrayList<>();
                for (var p : parcels) {
                    entries.add(new LedgerResponsePayload.LedgerEntry(
                            p.itemId() != null ? p.itemId() : "",
                            p.itemName() != null ? p.itemName() : "",
                            0xFFFFFFFF,
                            p.source().name(),
                            p.amount(),
                            "", // counterparty — not tracked at parcel level
                            p.createdAt()
                    ));
                }

                // Get aggregate stats
                AuctionStorageProvider.PlayerStats stats = service.getPlayerStats(player.getUUID(), sinceMs);
                long netProfit = stats.totalSalesRevenue() - stats.totalPurchases() - stats.taxesPaid();
                int totalPages = parcels.size() < PAGE_SIZE ? payload.page() + 1 : payload.page() + 2;

                context.reply(new LedgerResponsePayload(
                        entries, netProfit, stats.totalSalesRevenue(), stats.totalPurchases(),
                        stats.taxesPaid(), payload.page(), totalPages));
            } catch (Exception e) {
                LOGGER.error("Error handling RequestLedger", e);
                context.reply(new LedgerResponsePayload(List.of(), 0, 0, 0, 0, payload.page(), 0));
            }
        });
    }

    public static void sendAHInstances(ServerPlayer player) {
        AuctionStorageProvider storage = AHServerEvents.getStorage();
        if (storage == null) return;
        List<AHInstance> instances = storage.getAllAHInstances();
        List<AHInstancesPayload.AHInstanceData> data = new ArrayList<>();
        for (var ah : instances) {
            data.add(new AHInstancesPayload.AHInstanceData(
                    ah.id(), ah.slug(), ah.name(), ah.saleRate(), ah.depositRate(), new ArrayList<>(ah.durations())));
        }
        PacketDistributor.sendToPlayer(player, new AHInstancesPayload(data));
    }

    public static void handleCreateAH(CreateAHPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                context.reply(new AHActionResultPayload(false, "Permission denied."));
                return;
            }
            AuctionStorageProvider storage = AHServerEvents.getStorage();
            if (storage == null) {
                context.reply(new AHActionResultPayload(false, "Storage non disponible."));
                return;
            }
            AHInstance ah = AHInstance.create(payload.name());
            storage.createAHInstance(ah);
            context.reply(new AHActionResultPayload(true, "AH créé: " + ah.name()));
            sendAHInstances(player);
        });
    }

    public static void handleDeleteAH(DeleteAHPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                context.reply(new AHActionResultPayload(false, "Permission denied."));
                return;
            }
            AuctionStorageProvider storage = AHServerEvents.getStorage();
            if (storage == null) {
                context.reply(new AHActionResultPayload(false, "Storage non disponible."));
                return;
            }
            AHInstance ah = storage.getAHInstance(payload.ahId());
            if (ah == null) {
                context.reply(new AHActionResultPayload(false, "AH introuvable."));
                return;
            }
            if ("default".equals(ah.slug())) {
                context.reply(new AHActionResultPayload(false, "Impossible de supprimer l'AH par défaut."));
                return;
            }

            AHInstance defaultAh = storage.getDefaultAHInstance();
            switch (payload.deleteMode()) {
                case "TRANSFER_TO_DEFAULT" -> storage.transferListings(payload.ahId(), defaultAh.id());
                case "DELETE_LISTINGS" -> storage.deleteActiveListings(payload.ahId());
                case "RETURN_ITEMS" -> {
                    var listings = storage.getActiveListingsForAH(payload.ahId());
                    for (var listing : listings) {
                        storage.createParcel(new AuctionParcel(
                                java.util.UUID.randomUUID().toString(), listing.sellerUuid(),
                                listing.itemId(), listing.itemName(), listing.itemNbt(), listing.quantity(),
                                0L, null, ParcelSource.HDV_EXPIRED,
                                System.currentTimeMillis(), false));
                    }
                    storage.deleteActiveListings(payload.ahId());
                }
            }
            storage.deleteAHInstance(payload.ahId());
            context.reply(new AHActionResultPayload(true, "AH supprimé: " + ah.name()));
            sendAHInstances(player);
        });
    }

    public static void handleUpdateAHInstance(UpdateAHInstancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                context.reply(new AHActionResultPayload(false, "Permission denied."));
                return;
            }
            AuctionStorageProvider storage = AHServerEvents.getStorage();
            if (storage == null) {
                context.reply(new AHActionResultPayload(false, "Storage non disponible."));
                return;
            }
            AHInstance existing = storage.getAHInstance(payload.ahId());
            if (existing == null) {
                context.reply(new AHActionResultPayload(false, "AH introuvable."));
                return;
            }
            AHInstance updated = existing.withConfig(payload.name(), payload.saleRate(), payload.depositRate(), payload.durations());
            storage.updateAHInstance(updated);
            context.reply(new AHActionResultPayload(true, "AH mis à jour: " + updated.name()));
            sendAHInstances(player);
        });
    }

    public static void sendAHContext(ServerPlayer player, String ahId) {
        AuctionStorageProvider storage = AHServerEvents.getStorage();
        if (storage == null) return;
        AHInstance ah = storage.getAHInstance(ahId);
        if (ah == null) ah = storage.getDefaultAHInstance();
        if (ah != null) {
            PacketDistributor.sendToPlayer(player, new AHContextPayload(ah.id(), ah.name()));
        }
    }

    public static void sendAHSettings(ServerPlayer player) {
        try {
            var config = net.ecocraft.ah.config.AHConfig.CONFIG;
            boolean isAdmin = player.hasPermissions(2);
            int saleRate = config.saleRate.get();
            int depositRate = config.depositRate.get();
            List<Integer> durations = new ArrayList<>(config.durations.get());
            PacketDistributor.sendToPlayer(player, new AHSettingsPayload(isAdmin, saleRate, depositRate, durations));
        } catch (Exception e) {
            LOGGER.error("Error sending AH settings", e);
        }
    }

    public static void handleUpdateAHSettings(UpdateAHSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                context.reply(new AHActionResultPayload(false, "Permission denied."));
                return;
            }
            try {
                var config = net.ecocraft.ah.config.AHConfig.CONFIG;
                config.saleRate.set(Math.max(0, Math.min(100, payload.saleRate())));
                config.depositRate.set(Math.max(0, Math.min(100, payload.depositRate())));
                List<Integer> validDurations = payload.durations().stream()
                        .filter(d -> d >= 1 && d <= 168).toList();
                if (!validDurations.isEmpty()) {
                    config.durations.set(validDurations);
                }
                net.ecocraft.ah.config.AHConfig.CONFIG_SPEC.save();
                context.reply(new AHActionResultPayload(true, "Paramètres sauvegardés."));
                // Re-send settings so client updates its cached values
                sendAHSettings(player);
            } catch (Exception e) {
                LOGGER.error("Error updating AH settings", e);
                context.reply(new AHActionResultPayload(false, "Erreur lors de la sauvegarde."));
            }
        });
    }

    public static void sendBalanceUpdate(ServerPlayer player) {
        try {
            AuctionService service = AHServerEvents.getService();
            if (service == null) return;
            long balance = service.getPlayerBalance(player.getUUID());
            String symbol = service.getDefaultCurrencySymbol();
            PacketDistributor.sendToPlayer(player, new BalanceUpdatePayload(balance, symbol));
        } catch (Exception e) {
            LOGGER.error("Error sending balance update", e);
        }
    }

    public static void sendNPCSkin(ServerPlayer player, int entityId) {
        if (entityId < 0) return;
        var entity = player.level().getEntity(entityId);
        if (entity instanceof net.ecocraft.ah.entity.AuctioneerEntity npc) {
            PacketDistributor.sendToPlayer(player, new NPCSkinPayload(entityId, npc.getSkinPlayerName(), npc.getLinkedAhId()));
        }
    }

    public static void handleUpdateNPCSkin(UpdateNPCSkinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                context.reply(new AHActionResultPayload(false, "Permission denied."));
                return;
            }

            var entity = player.level().getEntity(payload.entityId());
            if (!(entity instanceof net.ecocraft.ah.entity.AuctioneerEntity npc)) {
                context.reply(new AHActionResultPayload(false, "PNJ introuvable."));
                return;
            }

            String skinName = payload.skinPlayerName().trim();
            npc.setSkinPlayerName(skinName);
            npc.setLinkedAhId(payload.linkedAhId());

            if (skinName.isEmpty()) {
                npc.setSkinProfile(null);
                context.reply(new AHActionResultPayload(true, "Skin réinitialisé."));
                return;
            }

            // Resolve GameProfile asynchronously
            var server = player.getServer();
            if (server == null) {
                context.reply(new AHActionResultPayload(false, "Serveur non disponible."));
                return;
            }

            net.minecraft.Util.backgroundExecutor().execute(() -> {
                try {
                    var profileCache = server.getProfileCache();
                    if (profileCache == null) {
                        server.execute(() -> context.reply(new AHActionResultPayload(false, "Cache de profils non disponible.")));
                        return;
                    }
                    var optProfile = profileCache.get(skinName);
                    if (optProfile.isEmpty()) {
                        server.execute(() -> context.reply(new AHActionResultPayload(false, "Joueur introuvable: " + skinName)));
                        return;
                    }

                    var profile = optProfile.get();
                    // Fetch full profile with skin textures from Mojang API
                    var profileResult = server.getSessionService().fetchProfile(profile.getId(), true);
                    if (profileResult == null) {
                        server.execute(() -> context.reply(new AHActionResultPayload(false, "Impossible de résoudre le profil: " + skinName)));
                        return;
                    }
                    var filledProfile = profileResult.profile();

                    server.execute(() -> {
                        npc.setSkinProfile(filledProfile);
                        context.reply(new AHActionResultPayload(true, "Skin mis à jour: " + skinName));
                    });
                } catch (Exception e) {
                    LOGGER.error("Error resolving skin for " + skinName, e);
                    server.execute(() -> context.reply(new AHActionResultPayload(false, "Erreur lors de la résolution du skin.")));
                }
            });
        });
    }

    private static AuctionService requireService() {
        AuctionService service = AHServerEvents.getService();
        if (service == null) {
            throw new IllegalStateException("Auction service not initialized");
        }
        return service;
    }
}

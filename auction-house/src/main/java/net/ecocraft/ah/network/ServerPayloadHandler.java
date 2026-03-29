package net.ecocraft.ah.network;

import com.mojang.logging.LogUtils;
import net.ecocraft.ah.AHServerEvents;
import net.ecocraft.ah.data.*;
import net.ecocraft.ah.network.payload.*;
import net.ecocraft.ah.permission.AHPermissions;
import net.ecocraft.ah.service.AuctionService;
import net.ecocraft.ah.storage.AuctionStorageProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.permission.PermissionAPI;
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
                LOGGER.info("[AH] handleRequestListings: ahId={} search='{}' category='{}'", payload.ahId(), payload.search(), payload.category());
                String search = payload.search().isEmpty() ? null : payload.search();
                ItemCategory category = payload.category().isEmpty() ? null : ItemCategory.valueOf(payload.category());

                // Pass category directly to SQL for efficient server-side filtering
                int clientPageSize = Math.max(1, Math.min(payload.pageSize(), 100));
                List<AuctionStorageProvider.ListingGroupSummary> page =
                        service.searchListings(payload.ahId(), search, category, payload.page(), clientPageSize);

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

                // Estimate total pages (if full page returned, there may be more)
                int totalPages = page.size() < clientPageSize ? payload.page() + 1 : payload.page() + 2;
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
                    listings = storage.getListingsForItemFiltered(payload.ahId(), payload.itemId(), enchantFilters, 0, Integer.MAX_VALUE);
                } else {
                    listings = service.getListingDetail(payload.ahId(), payload.itemId());
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

                // Fetch top 3 bids for the first AUCTION listing
                List<BidHistoryResponsePayload.BidEntry> recentBids = List.of();
                if (!entries.isEmpty() && "AUCTION".equals(entries.get(0).type())) {
                    List<net.ecocraft.ah.data.AuctionBid> bids = service.getBidsForListing(entries.get(0).listingId());
                    recentBids = bids.stream()
                            .limit(3)
                            .map(b -> new BidHistoryResponsePayload.BidEntry(b.bidderName(), b.amount(), b.timestamp()))
                            .toList();
                }

                context.reply(new ListingDetailResponsePayload(
                        payload.itemId(), itemName, 0xFFFFFFFF, entries, priceInfo, availableEnchantments, recentBids));
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

                if (!PermissionAPI.getPermission(player, AHPermissions.SELL)) {
                    context.reply(new AHActionResultPayload(false, "Permission refusée"));
                    return;
                }

                // Check max listings limit
                int maxListings = PermissionAPI.getPermission(player, AHPermissions.MAX_LISTINGS);
                if (maxListings >= 0) {
                    int currentCount = service.getMyListings(player.getUUID(), 0, Integer.MAX_VALUE).size();
                    if (currentCount >= maxListings) {
                        context.reply(new AHActionResultPayload(false,
                                "Nombre maximum d'annonces atteint (" + maxListings + ")"));
                        return;
                    }
                }

                LOGGER.info("[AH] handleCreateListing: ahId={} type={} price={}", payload.ahId(), payload.listingType(), payload.price());

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
                    context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.no_item_selected").getString()));
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

                // Validate listing type against AH instance configuration
                AuctionStorageProvider validationStorage = AHServerEvents.getStorage();
                if (validationStorage != null) {
                    AHInstance ahInstance = validationStorage.getAHInstance(payload.ahId());
                    if (ahInstance == null) ahInstance = validationStorage.getDefaultAHInstance();
                    if (ahInstance != null) {
                        if (type == ListingType.AUCTION && !ahInstance.allowAuction()) {
                            context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.auction_not_allowed").getString()));
                            return;
                        }
                        if (type == ListingType.BUYOUT && !ahInstance.allowBuyout()) {
                            context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.buyout_not_allowed").getString()));
                            return;
                        }
                    }
                }

                String currencyId = service.getDefaultCurrencyId();
                ItemCategory category = ItemCategoryDetector.detect(itemToSell);
                int taxPerm = PermissionAPI.getPermission(player, AHPermissions.TAX_RATE);
                int depositPerm = PermissionAPI.getPermission(player, AHPermissions.DEPOSIT_RATE);
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
                        fingerprint,
                        payload.ahId(),
                        taxPerm,
                        depositPerm
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

                context.reply(new AHActionResultPayload(true, Component.translatable("ecocraft_ah.message.listing_created").getString()));
                sendBalanceUpdate(player);
            } catch (AuctionService.AuctionException e) {
                context.reply(new AHActionResultPayload(false, e.getMessage()));
            } catch (Exception e) {
                LOGGER.error("Error handling CreateListing", e);
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.internal_error_create").getString()));
            }
        });
    }

    public static void handleBuyListing(BuyListingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                if (!PermissionAPI.getPermission(player, AHPermissions.USE)) {
                    context.reply(new AHActionResultPayload(false, "Permission refusée"));
                    return;
                }

                int taxPerm = PermissionAPI.getPermission(player, AHPermissions.TAX_RATE);
                service.buyListing(player.getUUID(), player.getName().getString(), payload.listingId(), payload.quantity(), taxPerm);
                context.reply(new AHActionResultPayload(true, Component.translatable("ecocraft_ah.message.purchase_success").getString()));
                sendBalanceUpdate(player);
            } catch (AuctionService.AuctionException e) {
                context.reply(new AHActionResultPayload(false, e.getMessage()));
            } catch (Exception e) {
                LOGGER.error("Error handling BuyListing", e);
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.internal_error_buy").getString()));
            }
        });
    }

    public static void handlePlaceBid(PlaceBidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                if (!PermissionAPI.getPermission(player, AHPermissions.BID)) {
                    context.reply(new AHActionResultPayload(false, "Permission refusée"));
                    return;
                }

                BigDecimal amount = BigDecimal.valueOf(payload.amount());
                service.placeBid(player.getUUID(), player.getName().getString(), payload.listingId(), amount);
                context.reply(new AHActionResultPayload(true, Component.translatable("ecocraft_ah.message.bid_success").getString()));
                sendBalanceUpdate(player);
            } catch (AuctionService.AuctionException e) {
                context.reply(new AHActionResultPayload(false, e.getMessage()));
            } catch (Exception e) {
                LOGGER.error("Error handling PlaceBid", e);
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.internal_error_bid").getString()));
            }
        });
    }

    public static void handleCancelListing(CancelListingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                if (!PermissionAPI.getPermission(player, AHPermissions.CANCEL)) {
                    context.reply(new AHActionResultPayload(false, "Permission refusée"));
                    return;
                }

                service.cancelListing(player.getUUID(), payload.listingId());
                context.reply(new AHActionResultPayload(true, Component.translatable("ecocraft_ah.message.listing_cancelled").getString()));
            } catch (AuctionService.AuctionException e) {
                context.reply(new AHActionResultPayload(false, e.getMessage()));
            } catch (Exception e) {
                LOGGER.error("Error handling CancelListing", e);
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.internal_error_cancel").getString()));
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
                        ? Component.translatable("ecocraft_ah.message.no_parcels").getString()
                        : Component.translatable("ecocraft_ah.message.parcels_collected", parcels.size()).getString();
                context.reply(new AHActionResultPayload(true, msg));
                sendBalanceUpdate(player);
            } catch (Exception e) {
                LOGGER.error("Error handling CollectParcels", e);
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.internal_error_collect").getString()));
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

                // Build ahId -> ahName cache
                AuctionStorageProvider storage = AHServerEvents.getStorage();
                Map<String, String> ahNames = new HashMap<>();
                if (storage != null) {
                    for (var ah : storage.getAllAHInstances()) {
                        ahNames.put(ah.id(), ah.name());
                    }
                }

                boolean isPurchases = "purchases".equals(payload.subTab());
                List<MyListingsResponsePayload.MyListingEntry> entries = new ArrayList<>();
                for (AuctionListing l : listings) {
                    long displayPrice = l.listingType() == ListingType.BUYOUT ? l.buyoutPrice() : l.currentBid();
                    long expiresInMs = Math.max(0, l.expiresAt() - System.currentTimeMillis());
                    // canCollect: for seller's expired listings only; purchases use the global collect button
                    boolean canCollect = !isPurchases && l.status() == ListingStatus.EXPIRED;
                    String listingAhId = l.ahId() != null ? l.ahId() : "";
                    String listingAhName = ahNames.getOrDefault(listingAhId, "");
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
                            l.itemNbt() != null ? l.itemNbt() : "",
                            listingAhId,
                            listingAhName
                    ));
                }

                // Get player stats for the revenue/taxes summary
                long sinceMs = System.currentTimeMillis() - SEVEN_DAYS_MS;
                AuctionStorageProvider.PlayerStats stats = service.getPlayerStats(player.getUUID(), sinceMs);
                int parcelsCount = service.countUncollectedParcels(player.getUUID());

                // Determine effective delivery mode (use default AH instance if multiple)
                String deliveryMode = "DIRECT";
                if (storage != null) {
                    var defaultAh = storage.getDefaultAHInstance();
                    if (defaultAh != null && defaultAh.deliveryMode() != null) {
                        deliveryMode = defaultAh.deliveryMode();
                    }
                }

                context.reply(new MyListingsResponsePayload(
                        entries, stats.totalSalesRevenue(), stats.taxesPaid(), parcelsCount, deliveryMode));
            } catch (Exception e) {
                LOGGER.error("Error handling RequestMyListings", e);
                context.reply(new MyListingsResponsePayload(List.of(), 0, 0, 0, "DIRECT"));
            }
        });
    }

    public static void handleRequestBestPrice(RequestBestPricePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                long bestPrice = service.getBestPrice(payload.ahId(), payload.fingerprint(), payload.itemId());
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

                // Build ahId -> ahName cache
                AuctionStorageProvider storage = AHServerEvents.getStorage();
                Map<String, String> ahNames = new HashMap<>();
                if (storage != null) {
                    for (var ah : storage.getAllAHInstances()) {
                        ahNames.put(ah.id(), ah.name());
                    }
                }

                List<LedgerResponsePayload.LedgerEntry> entries = new ArrayList<>();
                for (var p : parcels) {
                    String parcelAhId = p.ahId() != null ? p.ahId() : "";
                    String parcelAhName = ahNames.getOrDefault(parcelAhId, "");
                    entries.add(new LedgerResponsePayload.LedgerEntry(
                            p.itemId() != null ? p.itemId() : "",
                            p.itemName() != null ? p.itemName() : "",
                            0xFFFFFFFF,
                            p.source().name(),
                            p.amount(),
                            "", // counterparty — not tracked at parcel level
                            p.createdAt(),
                            parcelAhId,
                            parcelAhName,
                            p.itemNbt() != null ? p.itemNbt() : ""
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
                    ah.id(), ah.slug(), ah.name(), ah.saleRate(), ah.depositRate(), new ArrayList<>(ah.durations()),
                    ah.allowBuyout(), ah.allowAuction(), ah.taxRecipient(), ah.overridePermTax(),
                    ah.deliveryMode(), ah.deliveryDelayPurchase(), ah.deliveryDelayExpired()));
        }
        PacketDistributor.sendToPlayer(player, new AHInstancesPayload(data));
    }

    public static void handleCreateAH(CreateAHPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!PermissionAPI.getPermission(player, AHPermissions.ADMIN_SETTINGS)) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.permission_denied").getString()));
                return;
            }
            AuctionStorageProvider storage = AHServerEvents.getStorage();
            if (storage == null) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.storage_unavailable").getString()));
                return;
            }
            AHInstance ah = AHInstance.create(payload.name());
            storage.createAHInstance(ah);
            context.reply(new AHActionResultPayload(true, Component.translatable("ecocraft_ah.message.ah_created", ah.name()).getString()));
            sendAHInstances(player);
        });
    }

    public static void handleDeleteAH(DeleteAHPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!PermissionAPI.getPermission(player, AHPermissions.ADMIN_SETTINGS)) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.permission_denied").getString()));
                return;
            }
            AuctionStorageProvider storage = AHServerEvents.getStorage();
            if (storage == null) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.storage_unavailable").getString()));
                return;
            }
            AHInstance ah = storage.getAHInstance(payload.ahId());
            if (ah == null) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.ah_not_found").getString()));
                return;
            }
            if (AHInstance.DEFAULT_ID.equals(ah.id())) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.cannot_delete_default").getString()));
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
                                System.currentTimeMillis(), false, listing.ahId()));
                    }
                    storage.deleteActiveListings(payload.ahId());
                }
            }
            storage.deleteAHInstance(payload.ahId());
            context.reply(new AHActionResultPayload(true, Component.translatable("ecocraft_ah.message.ah_deleted", ah.name()).getString()));
            sendAHInstances(player);
        });
    }

    public static void handleUpdateAHInstance(UpdateAHInstancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!PermissionAPI.getPermission(player, AHPermissions.ADMIN_SETTINGS)) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.permission_denied").getString()));
                return;
            }
            AuctionStorageProvider storage = AHServerEvents.getStorage();
            if (storage == null) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.storage_unavailable").getString()));
                return;
            }
            AHInstance existing = storage.getAHInstance(payload.ahId());
            if (existing == null) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.ah_not_found").getString()));
                return;
            }
            AHInstance updated = existing.withConfig(payload.name(), payload.saleRate(), payload.depositRate(), payload.durations(),
                    payload.allowBuyout(), payload.allowAuction(), payload.taxRecipient(), payload.overridePermTax(),
                    payload.deliveryMode(), payload.deliveryDelayPurchase(), payload.deliveryDelayExpired());
            storage.updateAHInstance(updated);
            context.reply(new AHActionResultPayload(true, Component.translatable("ecocraft_ah.message.ah_updated", updated.name()).getString()));
            sendAHInstances(player);
        });
    }

    public static String resolveAHName(String ahId) {
        AuctionStorageProvider storage = AHServerEvents.getStorage();
        if (storage == null) return "";
        AHInstance ah = storage.getAHInstance(ahId);
        if (ah == null) ah = storage.getDefaultAHInstance();
        return ah != null ? ah.name() : "";
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
            boolean isAdmin = PermissionAPI.getPermission(player, AHPermissions.ADMIN_SETTINGS);
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
            if (!PermissionAPI.getPermission(player, AHPermissions.ADMIN_SETTINGS)) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.permission_denied").getString()));
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
                context.reply(new AHActionResultPayload(true, Component.translatable("ecocraft_ah.message.settings_saved").getString()));
                // Re-send settings so client updates its cached values
                sendAHSettings(player);
            } catch (Exception e) {
                LOGGER.error("Error updating AH settings", e);
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.settings_save_error").getString()));
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
            if (!PermissionAPI.getPermission(player, AHPermissions.ADMIN_SETTINGS)) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.permission_denied").getString()));
                return;
            }

            var entity = player.level().getEntity(payload.entityId());
            if (!(entity instanceof net.ecocraft.ah.entity.AuctioneerEntity npc)) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.npc_not_found").getString()));
                return;
            }

            String skinName = payload.skinPlayerName().trim();
            npc.setSkinPlayerName(skinName);
            npc.setLinkedAhId(payload.linkedAhId());

            if (skinName.isEmpty()) {
                npc.setSkinProfile(null);
                context.reply(new AHActionResultPayload(true, Component.translatable("ecocraft_ah.message.skin_reset").getString()));
                return;
            }

            // Resolve GameProfile asynchronously
            var server = player.getServer();
            if (server == null) {
                context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.server_unavailable").getString()));
                return;
            }

            net.minecraft.Util.backgroundExecutor().execute(() -> {
                try {
                    var profileCache = server.getProfileCache();
                    if (profileCache == null) {
                        server.execute(() -> context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.profile_cache_unavailable").getString())));
                        return;
                    }
                    var optProfile = profileCache.get(skinName);
                    if (optProfile.isEmpty()) {
                        server.execute(() -> context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.player_not_found", skinName).getString())));
                        return;
                    }

                    var profile = optProfile.get();
                    // Fetch full profile with skin textures from Mojang API
                    var profileResult = server.getSessionService().fetchProfile(profile.getId(), true);
                    if (profileResult == null) {
                        server.execute(() -> context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.profile_resolve_error", skinName).getString())));
                        return;
                    }
                    var filledProfile = profileResult.profile();

                    server.execute(() -> {
                        npc.setSkinProfile(filledProfile);
                        context.reply(new AHActionResultPayload(true, Component.translatable("ecocraft_ah.message.skin_updated", skinName).getString()));
                    });
                } catch (Exception e) {
                    LOGGER.error("Error resolving skin for " + skinName, e);
                    server.execute(() -> context.reply(new AHActionResultPayload(false, Component.translatable("ecocraft_ah.message.skin_resolve_error").getString())));
                }
            });
        });
    }

    public static void handleRequestBidHistory(RequestBidHistoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AuctionService service = requireService();
                List<net.ecocraft.ah.data.AuctionBid> bids = service.getBidsForListing(payload.listingId());
                List<BidHistoryResponsePayload.BidEntry> entries = bids.stream()
                        .map(b -> new BidHistoryResponsePayload.BidEntry(
                                b.bidderName(), b.amount(), b.timestamp()))
                        .toList();
                context.reply(new BidHistoryResponsePayload(payload.listingId(), entries));
            } catch (Exception e) {
                LOGGER.error("Error handling RequestBidHistory", e);
            }
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

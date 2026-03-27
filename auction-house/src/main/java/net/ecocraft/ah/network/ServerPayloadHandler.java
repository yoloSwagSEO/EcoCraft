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
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

                List<AuctionStorageProvider.ListingGroupSummary> results =
                        service.searchListings(search, category, payload.page(), PAGE_SIZE);

                List<ListingsResponsePayload.ListingSummary> summaries = new ArrayList<>();
                for (var r : results) {
                    summaries.add(new ListingsResponsePayload.ListingSummary(
                            r.itemId(),
                            r.itemName(),
                            0xFFFFFFFF,
                            r.bestPrice(),
                            r.listingCount(),
                            r.totalQuantity()
                    ));
                }

                // Approximate total pages
                int totalPages = results.size() < PAGE_SIZE ? payload.page() + 1 : payload.page() + 2;
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
                List<AuctionListing> listings = service.getListingDetail(payload.itemId());

                List<ListingDetailResponsePayload.ListingEntry> entries = new ArrayList<>();
                String itemName = "";
                for (AuctionListing l : listings) {
                    if (itemName.isEmpty()) itemName = l.itemName();
                    long displayPrice = l.listingType() == ListingType.BUYOUT ? l.buyoutPrice() : l.currentBid();
                    long expiresInMs = Math.max(0, l.expiresAt() - System.currentTimeMillis());
                    entries.add(new ListingDetailResponsePayload.ListingEntry(
                            l.id(),
                            l.sellerName(),
                            l.quantity(),
                            displayPrice,
                            l.listingType().name(),
                            expiresInMs
                    ));
                }

                // Price history
                AuctionStorageProvider storage = AHServerEvents.getStorage();
                ListingDetailResponsePayload.PriceInfo priceInfo;
                if (storage != null) {
                    // Use first listing's currency for price history
                    String currencyId = listings.isEmpty()
                            ? net.ecocraft.core.EcoServerEvents.getCurrencyRegistry().getDefault().id()
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
                        payload.itemId(), itemName, 0xFFFFFFFF, entries, priceInfo));
            } catch (Exception e) {
                LOGGER.error("Error handling RequestListingDetail", e);
                context.reply(new ListingDetailResponsePayload(
                        payload.itemId(), "", 0xFFFFFFFF, List.of(),
                        new ListingDetailResponsePayload.PriceInfo(0, 0, 0, 0)));
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
                    context.reply(new AHActionResultPayload(false, "Aucun objet sélectionné!"));
                    return;
                }

                String itemId = BuiltInRegistries.ITEM.getKey(itemToSell.getItem()).toString();
                String itemName = itemToSell.getHoverName().getString();
                String nbt = null;
                int quantity = itemToSell.getCount();
                ListingType type = ListingType.valueOf(payload.listingType());
                BigDecimal price = BigDecimal.valueOf(payload.price());

                String currencyId = net.ecocraft.core.EcoServerEvents.getCurrencyRegistry().getDefault().id();
                ItemCategory category = ItemCategoryDetector.detect(itemToSell);
                service.createListing(
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
                        category
                );

                // Remove item from the correct slot
                if (slotIndex >= 0) {
                    player.getInventory().setItem(slotIndex, ItemStack.EMPTY);
                } else {
                    player.getMainHandItem().setCount(0);
                }

                context.reply(new AHActionResultPayload(true, "Listing created successfully."));
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

                service.buyListing(player.getUUID(), player.getName().getString(), payload.listingId());
                context.reply(new AHActionResultPayload(true, "Purchase successful! Check parcels to collect your item."));
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
                            var itemRL = net.minecraft.resources.ResourceLocation.parse(parcel.itemId());
                            var item = BuiltInRegistries.ITEM.get(itemRL);
                            ItemStack stack = new ItemStack(item, parcel.quantity());
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
                    case "purchases" -> listings = service.getMyPurchases(player.getUUID(), 0, PAGE_SIZE);
                    case "bids" -> listings = service.getMyBids(player.getUUID());
                    default -> listings = service.getMyListings(player.getUUID(), 0, PAGE_SIZE);
                }

                List<MyListingsResponsePayload.MyListingEntry> entries = new ArrayList<>();
                for (AuctionListing l : listings) {
                    long displayPrice = l.listingType() == ListingType.BUYOUT ? l.buyoutPrice() : l.currentBid();
                    long expiresInMs = Math.max(0, l.expiresAt() - System.currentTimeMillis());
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
                            l.status() == ListingStatus.EXPIRED || l.status() == ListingStatus.SOLD
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

                var parcels = service.getLedger(player.getUUID(), sourceFilter, sinceMs, payload.page(), PAGE_SIZE);

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

    private static AuctionService requireService() {
        AuctionService service = AHServerEvents.getService();
        if (service == null) {
            throw new IllegalStateException("Auction service not initialized");
        }
        return service;
    }
}

package net.ecocraft.ah.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ecocraft.ah.AHServerEvents;
import net.ecocraft.ah.data.AHInstance;
import net.ecocraft.ah.data.AuctionBid;
import net.ecocraft.ah.data.AuctionListing;
import net.ecocraft.ah.data.ItemCategory;
import net.ecocraft.ah.data.ListingType;
import net.ecocraft.ah.network.ServerPayloadHandler;
import net.ecocraft.ah.network.payload.AHNotificationPayload;
import net.ecocraft.ah.network.payload.OpenAHPayload;
import net.ecocraft.ah.permission.AHPermissions;
import net.ecocraft.ah.service.AuctionService;
import net.ecocraft.ah.storage.AuctionStorageProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Registers the /ah command tree for the auction house.
 */
public final class AHCommand {

    private AHCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<AuctionService> serviceSupplier) {
        dispatcher.register(Commands.literal("ah")
                // /ah — open auction house GUI
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    PacketDistributor.sendToPlayer(player, new OpenAHPayload(-1, AHInstance.DEFAULT_ID,
                            ServerPayloadHandler.resolveAHName(AHInstance.DEFAULT_ID)));
                    ServerPayloadHandler.sendBalanceUpdate(player);
                    ServerPayloadHandler.sendAHSettings(player);
                    ServerPayloadHandler.sendAHInstances(player);
                    return 1;
                })

                // /ah sell <price>
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    double price = DoubleArgumentType.getDouble(ctx, "price");
                                    return executeSell(ctx.getSource(), player, price, serviceSupplier);
                                })
                        )
                )

                // /ah search <term>
                .then(Commands.literal("search")
                        .then(Commands.argument("term", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    // For now, just open the AH — search pre-fill can come later
                                    PacketDistributor.sendToPlayer(player, new OpenAHPayload(-1, AHInstance.DEFAULT_ID,
                                            ServerPayloadHandler.resolveAHName(AHInstance.DEFAULT_ID)));
                                    ServerPayloadHandler.sendBalanceUpdate(player);
                                    ServerPayloadHandler.sendAHSettings(player);
                                    ServerPayloadHandler.sendAHInstances(player);
                                    return 1;
                                })
                        )
                )

                // /ah browse <slug>
                .then(Commands.literal("browse")
                        .then(Commands.argument("slug", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String slug = StringArgumentType.getString(ctx, "slug");
                                    return executeBrowse(ctx.getSource(), player, slug);
                                })
                        )
                )

                // /ah collect
                .then(Commands.literal("collect")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return executeCollect(ctx.getSource(), player, serviceSupplier);
                        })
                )

                // /ah admin ...
                .then(Commands.literal("admin")
                        .requires(src -> AHPermissions.check(src, AHPermissions.ADMIN_RELOAD))

                        // /ah admin reload
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable("ecocraft_ah.command.reload_success"), true);
                                    return 1;
                                })
                        )

                        // /ah admin expire
                        .then(Commands.literal("expire")
                                .executes(ctx -> {
                                    AuctionService service = serviceSupplier.get();
                                    if (service == null) {
                                        ctx.getSource().sendFailure(Component.translatable("ecocraft_ah.command.service_unavailable"));
                                        return 0;
                                    }
                                    service.expireListings();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable("ecocraft_ah.command.expire_success"), true);
                                    return 1;
                                })
                        )

                        // /ah admin create <name>
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            return executeAdminCreate(ctx.getSource(), name);
                                        })
                                )
                        )

                        // /ah admin delete <slug>
                        .then(Commands.literal("delete")
                                .then(Commands.argument("slug", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String slug = StringArgumentType.getString(ctx, "slug");
                                            return executeAdminDelete(ctx.getSource(), slug);
                                        })
                                )
                        )

                        // /ah admin list
                        .then(Commands.literal("list")
                                .executes(ctx -> executeAdminList(ctx.getSource()))
                        )

                        // /ah admin rename <slug> <new_name>
                        .then(Commands.literal("rename")
                                .then(Commands.argument("slug", StringArgumentType.word())
                                        .then(Commands.argument("new_name", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String slug = StringArgumentType.getString(ctx, "slug");
                                                    String newName = StringArgumentType.getString(ctx, "new_name");
                                                    return executeAdminRename(ctx.getSource(), slug, newName);
                                                })
                                        )
                                )
                        )
                )

                // /ah testnotif <type> — sends a fake notification packet to the executing player
                .then(Commands.literal("testnotif")
                        .requires(s -> AHPermissions.check(s, AHPermissions.ADMIN_RELOAD))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (String t : List.of("outbid", "auction_won", "auction_lost",
                                            "sale_completed", "listing_expired")) {
                                        builder.suggest(t);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String type = StringArgumentType.getString(ctx, "type");
                                    PacketDistributor.sendToPlayer(player, new AHNotificationPayload(
                                            type,
                                            "Épée en diamant",
                                            "TestPlayer",
                                            150L,
                                            "gold"
                                    ));
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Test notification envoyée : " + type), false);
                                    return 1;
                                })
                        )
                )

                // /ah testtoast — sends a quick outbid toast for rendering validation
                .then(Commands.literal("testtoast")
                        .requires(s -> AHPermissions.check(s, AHPermissions.ADMIN_RELOAD))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            PacketDistributor.sendToPlayer(player, new AHNotificationPayload(
                                    "outbid", "Test Item", "TestPlayer", 100L, "gold"));
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("Toast test envoyé !"), false);
                            return 1;
                        })
                )

                // /ah testbids — injects fake bids on the first active AUCTION listing
                .then(Commands.literal("testbids")
                        .requires(s -> AHPermissions.check(s, AHPermissions.ADMIN_RELOAD))
                        .executes(ctx -> {
                            return executeTestBids(ctx.getSource(), serviceSupplier);
                        })
                )
        );

        // Test data commands (/ah populate, /ah simulate)
        AHTestCommand.register(dispatcher, serviceSupplier);
    }

    private static int executeTestBids(CommandSourceStack source, Supplier<AuctionService> serviceSupplier) {
        AuctionService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.literal("Service non disponible"));
            return 0;
        }
        AuctionStorageProvider storage = AHServerEvents.getStorage();
        if (storage == null) {
            source.sendFailure(Component.literal("Storage non disponible"));
            return 0;
        }

        // Find first active AUCTION listing
        List<AuctionListing> listings = storage.getActiveListingsForAH(AHInstance.DEFAULT_ID);
        AuctionListing auction = null;
        for (AuctionListing l : listings) {
            if (l.listingType() == ListingType.AUCTION) {
                auction = l;
                break;
            }
        }
        if (auction == null) {
            source.sendFailure(Component.literal("Aucun listing AUCTION actif. Créez-en un d'abord avec /ah sell."));
            return 0;
        }

        // Inject 5 fake bids with increasing amounts and varying timestamps
        String[] fakeNames = {"Grimald", "Thoria", "Keldorn", "Sylvanas", "Brakk"};
        long baseAmount = auction.startingBid() > 0 ? auction.startingBid() : 10;
        long now = System.currentTimeMillis();

        for (int i = 0; i < fakeNames.length; i++) {
            long bidAmount = baseAmount + (i + 1) * 5;
            long timestamp = now - (fakeNames.length - i) * 3600_000L; // staggered hours ago
            AuctionBid bid = new AuctionBid(
                    UUID.randomUUID().toString(),
                    auction.id(),
                    UUID.randomUUID(), // fake UUID
                    fakeNames[i],
                    bidAmount,
                    timestamp
            );
            storage.placeBid(bid);
        }

        // Update the listing's current bid to the highest
        long highestBid = baseAmount + fakeNames.length * 5;
        storage.updateListingBid(auction.id(), highestBid, UUID.randomUUID());

        String itemName = auction.itemName();
        String shortId = auction.id().substring(0, 8);
        source.sendSuccess(() -> Component.literal(
                "5 enchères injectées sur '" + itemName + "' (ID: " + shortId + ")"),
                false);
        return 1;
    }

    private static int executeSell(CommandSourceStack source, ServerPlayer player, double price,
                                   Supplier<AuctionService> serviceSupplier) {
        AuctionService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.service_unavailable"));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.sell.must_hold"));
            return 0;
        }

        try {
            String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
            String itemName = held.getHoverName().getString();
            String nbt = net.ecocraft.ah.data.ItemStackSerializer.serialize(held, player.registryAccess());
            int quantity = held.getCount();

            String currencyId = net.ecocraft.core.EcoServerEvents.getCurrencyRegistry().getDefault().id();
            String fingerprint = net.ecocraft.ah.data.ItemFingerprint.compute(held);
            net.ecocraft.ah.data.ItemCategory category = net.ecocraft.ah.data.ItemCategoryDetector.detect(held);
            service.createListing(
                    player.getUUID(),
                    player.getName().getString(),
                    itemId,
                    itemName,
                    nbt,
                    quantity,
                    ListingType.BUYOUT,
                    BigDecimal.valueOf(price),
                    24,
                    currencyId,
                    category,
                    fingerprint,
                    AHInstance.DEFAULT_ID
            );

            // Remove item from hand
            player.getMainHandItem().setCount(0);

            source.sendSuccess(() -> Component.translatable("ecocraft_ah.command.sell.success", quantity, itemName, price), false);
            return 1;
        } catch (AuctionService.AuctionException e) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.sell.fail", e.getMessage()));
            return 0;
        }
    }

    private static int executeBrowse(CommandSourceStack source, ServerPlayer player, String slug) {
        AuctionStorageProvider storage = AHServerEvents.getStorage();
        if (storage == null) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.storage_unavailable"));
            return 0;
        }
        AHInstance ah = storage.getAHInstanceBySlug(slug);
        if (ah == null) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.ah_not_found", slug));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, new OpenAHPayload(-1, ah.id(), ah.name()));
        ServerPayloadHandler.sendBalanceUpdate(player);
        ServerPayloadHandler.sendAHSettings(player);
        ServerPayloadHandler.sendAHInstances(player);
        return 1;
    }

    private static int executeAdminCreate(CommandSourceStack source, String name) {
        AuctionStorageProvider storage = AHServerEvents.getStorage();
        if (storage == null) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.storage_unavailable"));
            return 0;
        }
        AHInstance ah = AHInstance.create(name);
        storage.createAHInstance(ah);
        source.sendSuccess(() -> Component.translatable("ecocraft_ah.command.ah_created", ah.name(), ah.slug()), true);
        return 1;
    }

    private static int executeAdminDelete(CommandSourceStack source, String slug) {
        AuctionStorageProvider storage = AHServerEvents.getStorage();
        if (storage == null) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.storage_unavailable"));
            return 0;
        }
        AHInstance ah = storage.getAHInstanceBySlug(slug);
        if (ah == null) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.ah_not_found", slug));
            return 0;
        }
        if (AHInstance.DEFAULT_ID.equals(ah.id())) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.cannot_delete_default"));
            return 0;
        }
        AHInstance defaultAh = storage.getDefaultAHInstance();
        int transferred = storage.transferListings(ah.id(), defaultAh.id());
        storage.deleteAHInstance(ah.id());
        source.sendSuccess(() -> Component.translatable("ecocraft_ah.command.ah_deleted", ah.name(), transferred, defaultAh.name()), true);
        return 1;
    }

    private static int executeAdminList(CommandSourceStack source) {
        AuctionStorageProvider storage = AHServerEvents.getStorage();
        if (storage == null) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.storage_unavailable"));
            return 0;
        }
        var instances = storage.getAllAHInstances();
        if (instances.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("ecocraft_ah.command.no_ah_configured"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("ecocraft_ah.command.ah_list_header"), false);
        for (AHInstance ah : instances) {
            int count = storage.countActiveListings(ah.id());
            source.sendSuccess(() -> Component.translatable("ecocraft_ah.command.ah_list_entry", ah.slug(), ah.name(), count), false);
        }
        return 1;
    }

    private static int executeAdminRename(CommandSourceStack source, String slug, String newName) {
        AuctionStorageProvider storage = AHServerEvents.getStorage();
        if (storage == null) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.storage_unavailable"));
            return 0;
        }
        AHInstance ah = storage.getAHInstanceBySlug(slug);
        if (ah == null) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.ah_not_found", slug));
            return 0;
        }
        AHInstance updated = ah.withConfig(newName, ah.saleRate(), ah.depositRate(), ah.durations(), ah.allowBuyout(), ah.allowAuction(), ah.taxRecipient());
        storage.updateAHInstance(updated);
        source.sendSuccess(() -> Component.translatable("ecocraft_ah.command.ah_renamed", updated.name(), updated.slug()), true);
        return 1;
    }

    private static int executeCollect(CommandSourceStack source, ServerPlayer player,
                                      Supplier<AuctionService> serviceSupplier) {
        AuctionService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.service_unavailable"));
            return 0;
        }

        try {
            var parcels = service.collectParcels(player.getUUID());
            if (parcels.isEmpty()) {
                source.sendSuccess(() -> Component.translatable("ecocraft_ah.command.no_parcels"), false);
            } else {
                // Deliver item parcels to player inventory (mirrors ServerPayloadHandler.handleCollectParcels)
                for (var parcel : parcels) {
                    if (parcel.hasItem() && parcel.itemId() != null) {
                        try {
                            ItemStack stack;
                            if (parcel.itemNbt() != null && !parcel.itemNbt().isEmpty()) {
                                stack = net.ecocraft.ah.data.ItemStackSerializer.deserialize(
                                        parcel.itemNbt(), player.registryAccess());
                                if (stack.isEmpty()) {
                                    var itemRL = net.minecraft.resources.ResourceLocation.parse(parcel.itemId());
                                    var item = BuiltInRegistries.ITEM.get(itemRL);
                                    stack = new ItemStack(item, parcel.quantity());
                                }
                            } else {
                                var itemRL = net.minecraft.resources.ResourceLocation.parse(parcel.itemId());
                                var item = BuiltInRegistries.ITEM.get(itemRL);
                                stack = new ItemStack(item, parcel.quantity());
                            }
                            if (!player.getInventory().add(stack)) {
                                player.drop(stack, false);
                            }
                        } catch (Exception itemEx) {
                            com.mojang.logging.LogUtils.getLogger().error(
                                    "Failed to deliver parcel item {} via /ah collect", parcel.itemId(), itemEx);
                        }
                    }
                }
                int count = parcels.size();
                source.sendSuccess(() -> Component.translatable("ecocraft_ah.command.parcels_collected", count), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("ecocraft_ah.command.collect_fail", e.getMessage()));
            return 0;
        }
    }
}

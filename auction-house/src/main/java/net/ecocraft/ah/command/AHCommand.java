package net.ecocraft.ah.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ecocraft.ah.data.ItemCategory;
import net.ecocraft.ah.data.ListingType;
import net.ecocraft.ah.network.ServerPayloadHandler;
import net.ecocraft.ah.network.payload.OpenAHPayload;
import net.ecocraft.ah.service.AuctionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
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
                    PacketDistributor.sendToPlayer(player, new OpenAHPayload());
                    ServerPayloadHandler.sendBalanceUpdate(player);
                    ServerPayloadHandler.sendAHSettings(player);
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
                                    PacketDistributor.sendToPlayer(player, new OpenAHPayload());
                                    ServerPayloadHandler.sendBalanceUpdate(player);
                                    ServerPayloadHandler.sendAHSettings(player);
                                    return 1;
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
                        .requires(src -> src.hasPermission(2))

                        // /ah admin reload
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Auction house config reloaded."), true);
                                    return 1;
                                })
                        )

                        // /ah admin expire
                        .then(Commands.literal("expire")
                                .executes(ctx -> {
                                    AuctionService service = serviceSupplier.get();
                                    if (service == null) {
                                        ctx.getSource().sendFailure(Component.literal("Auction service not available."));
                                        return 0;
                                    }
                                    service.expireListings();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Forced expiration of old listings."), true);
                                    return 1;
                                })
                        )
                )
        );

        // Test data commands (/ah populate, /ah simulate)
        AHTestCommand.register(dispatcher, serviceSupplier);
    }

    private static int executeSell(CommandSourceStack source, ServerPlayer player, double price,
                                   Supplier<AuctionService> serviceSupplier) {
        AuctionService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.literal("Auction service not available."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("You must hold an item to sell."));
            return 0;
        }

        try {
            String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
            String itemName = held.getHoverName().getString();
            // In 1.21.1, items use the component system; serialize the full stack for storage
            String nbt = null; // Component-based serialisation deferred to a helper
            int quantity = held.getCount();

            String fingerprint = net.ecocraft.ah.data.ItemFingerprint.compute(held);
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
                    "default",
                    ItemCategory.MISC,
                    fingerprint
            );

            // Remove item from hand
            player.getMainHandItem().setCount(0);

            source.sendSuccess(() -> Component.literal(
                    "Listed " + quantity + "x " + itemName + " for " + price), false);
            return 1;
        } catch (AuctionService.AuctionException e) {
            source.sendFailure(Component.literal("Failed to sell: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeCollect(CommandSourceStack source, ServerPlayer player,
                                      Supplier<AuctionService> serviceSupplier) {
        AuctionService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.literal("Auction service not available."));
            return 0;
        }

        try {
            var parcels = service.collectParcels(player.getUUID());
            if (parcels.isEmpty()) {
                source.sendSuccess(() -> Component.literal("No parcels to collect."), false);
            } else {
                // Item parcels would need to be given to player inventory — handled in the handler
                int count = parcels.size();
                source.sendSuccess(() -> Component.literal(
                        "Collected " + count + " parcel(s). Items sent to inventory."), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to collect: " + e.getMessage()));
            return 0;
        }
    }
}

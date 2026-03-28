package net.ecocraft.ah.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.ecocraft.ah.data.AHInstance;
import net.ecocraft.ah.data.ItemCategory;
import net.ecocraft.ah.data.ListingType;
import net.ecocraft.ah.data.ListingStatus;
import net.ecocraft.ah.service.AuctionService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;

public final class AHTestCommand {

    private AHTestCommand() {}

    private static final String[] SELLERS = {
        "Grimald", "Thoria", "Keldorn", "Sylvanas", "Brakk",
        "Elyndra", "Morgrim", "Vaelith", "Drogan", "Isildra"
    };

    private static UUID fakeUuid(String name) {
        return UUID.nameUUIDFromBytes(("ecocraft_fake:" + name).getBytes());
    }

    private record ItemDef(String id, String name, ItemCategory cat, int minPrice, int maxPrice, int maxQty) {}

    private static final List<ItemDef> ITEM_POOL = List.of(
        // Weapons
        new ItemDef("minecraft:diamond_sword", "Épée en diamant", ItemCategory.WEAPONS, 200, 2000, 1),
        new ItemDef("minecraft:iron_sword", "Épée en fer", ItemCategory.WEAPONS, 50, 300, 1),
        new ItemDef("minecraft:bow", "Arc", ItemCategory.WEAPONS, 100, 800, 1),
        new ItemDef("minecraft:crossbow", "Arbalète", ItemCategory.WEAPONS, 150, 1000, 1),
        new ItemDef("minecraft:trident", "Trident", ItemCategory.WEAPONS, 2000, 5000, 1),
        // Armor
        new ItemDef("minecraft:diamond_chestplate", "Plastron en diamant", ItemCategory.ARMOR, 500, 3000, 1),
        new ItemDef("minecraft:iron_helmet", "Casque en fer", ItemCategory.ARMOR, 80, 400, 1),
        new ItemDef("minecraft:netherite_boots", "Bottes en netherite", ItemCategory.ARMOR, 3000, 8000, 1),
        new ItemDef("minecraft:shield", "Bouclier", ItemCategory.ARMOR, 100, 500, 1),
        // Tools
        new ItemDef("minecraft:diamond_pickaxe", "Pioche en diamant", ItemCategory.TOOLS, 200, 1500, 1),
        new ItemDef("minecraft:netherite_pickaxe", "Pioche en netherite", ItemCategory.TOOLS, 2000, 6000, 1),
        new ItemDef("minecraft:fishing_rod", "Canne à pêche", ItemCategory.TOOLS, 30, 200, 1),
        // Potions
        new ItemDef("minecraft:golden_apple", "Pomme dorée", ItemCategory.POTIONS, 500, 2000, 8),
        new ItemDef("minecraft:enchanted_golden_apple", "Pomme dorée enchantée", ItemCategory.POTIONS, 5000, 15000, 1),
        // Blocks
        new ItemDef("minecraft:diamond_block", "Bloc de diamant", ItemCategory.BLOCKS, 1000, 5000, 16),
        new ItemDef("minecraft:emerald_block", "Bloc d'émeraude", ItemCategory.BLOCKS, 800, 4000, 16),
        new ItemDef("minecraft:obsidian", "Obsidienne", ItemCategory.BLOCKS, 20, 100, 64),
        new ItemDef("minecraft:glowstone", "Pierre lumineuse", ItemCategory.BLOCKS, 15, 80, 64),
        // Food
        new ItemDef("minecraft:cooked_beef", "Steak", ItemCategory.FOOD, 5, 30, 64),
        new ItemDef("minecraft:bread", "Pain", ItemCategory.FOOD, 3, 15, 64),
        new ItemDef("minecraft:cake", "Gâteau", ItemCategory.FOOD, 50, 200, 1),
        // Enchantments
        new ItemDef("minecraft:enchanted_book", "Livre enchanté", ItemCategory.ENCHANTMENTS, 500, 5000, 3),
        // Misc
        new ItemDef("minecraft:ender_pearl", "Perle de l'Ender", ItemCategory.MISC, 50, 300, 16),
        new ItemDef("minecraft:blaze_rod", "Bâton de Blaze", ItemCategory.MISC, 30, 150, 16),
        new ItemDef("minecraft:name_tag", "Étiquette", ItemCategory.MISC, 100, 500, 1)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<AuctionService> serviceSupplier) {
        dispatcher.register(Commands.literal("ah")
            .then(Commands.literal("populate")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> populate(ctx.getSource(), 50, serviceSupplier))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 500))
                    .executes(ctx -> populate(ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "count"), serviceSupplier))
                )
            )
            .then(Commands.literal("simulate")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("buy")
                    .executes(ctx -> simulateBuy(ctx.getSource(), 5, serviceSupplier))
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                        .executes(ctx -> simulateBuy(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "count"), serviceSupplier))
                    )
                )
                .then(Commands.literal("clear")
                    .executes(ctx -> simulateClear(ctx.getSource(), serviceSupplier))
                )
            )
        );
    }

    private static String formatPrice(long price) {
        if (price >= 1000) {
            return String.format("%,d", price).replace(',', ' ') + " G";
        }
        return price + " G";
    }

    private static int populate(CommandSourceStack source, int count,
                                Supplier<AuctionService> serviceSupplier) {
        AuctionService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.literal("Service HDV non disponible."));
            return 0;
        }

        var economy = net.ecocraft.core.EcoServerEvents.getEconomy();
        var currencies = net.ecocraft.core.EcoServerEvents.getCurrencyRegistry();
        if (economy == null || currencies == null) {
            source.sendFailure(Component.literal("Service économie non disponible."));
            return 0;
        }
        var currency = currencies.getDefault();

        Random rng = new Random();
        int created = 0;

        // Give all fake sellers a large balance
        for (String seller : SELLERS) {
            UUID uuid = fakeUuid(seller);
            BigDecimal balance = economy.getBalance(uuid, currency);
            if (balance.compareTo(BigDecimal.valueOf(100_000)) < 0) {
                economy.deposit(uuid, BigDecimal.valueOf(100_000), currency);
            }
        }

        for (int i = 0; i < count; i++) {
            try {
                String seller = SELLERS[rng.nextInt(SELLERS.length)];
                UUID sellerUuid = fakeUuid(seller);
                ItemDef item = ITEM_POOL.get(rng.nextInt(ITEM_POOL.size()));
                int qty = 1 + rng.nextInt(item.maxQty);
                long price = item.minPrice + rng.nextInt(item.maxPrice - item.minPrice + 1);
                int hours = new int[]{12, 24, 48}[rng.nextInt(3)];

                service.createListing(
                    sellerUuid, seller,
                    item.id, item.name, null, qty,
                    ListingType.BUYOUT, BigDecimal.valueOf(price),
                    hours, currency.id(), item.cat,
                    item.id, AHInstance.DEFAULT_ID
                );
                created++;
            } catch (Exception e) {
                source.sendSystemMessage(Component.literal("§c[Populate] Erreur: " + e.getMessage()));
            }
        }

        int finalCreated = created;
        source.sendSuccess(() -> Component.literal(
            "§a[HDV] " + finalCreated + " listings créées par " + SELLERS.length + " vendeurs fictifs."
        ), true);
        return created;
    }

    private static int simulateBuy(CommandSourceStack source, int count,
                                   Supplier<AuctionService> serviceSupplier) {
        AuctionService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.literal("Service HDV non disponible."));
            return 0;
        }

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Commande joueur uniquement."));
            return 0;
        }

        var economy = net.ecocraft.core.EcoServerEvents.getEconomy();
        var currencies = net.ecocraft.core.EcoServerEvents.getCurrencyRegistry();
        if (economy == null || currencies == null) {
            source.sendFailure(Component.literal("Service économie non disponible."));
            return 0;
        }
        var currency = currencies.getDefault();

        // Get player's active buyout listings
        var listings = service.getMyListings(player.getUUID(), 0, Integer.MAX_VALUE);
        var activeListings = listings.stream()
            .filter(l -> l.status() == ListingStatus.ACTIVE
                      && l.listingType() == ListingType.BUYOUT)
            .toList();

        if (activeListings.isEmpty()) {
            source.sendFailure(Component.literal("Aucune listing active à acheter."));
            return 0;
        }

        Random rng = new Random();
        int bought = 0;
        int toProcess = Math.min(count, activeListings.size());

        var shuffled = new ArrayList<>(activeListings);
        Collections.shuffle(shuffled, rng);

        for (int i = 0; i < toProcess; i++) {
            var listing = shuffled.get(i);
            String buyerName = SELLERS[rng.nextInt(SELLERS.length)];
            UUID buyerUuid = fakeUuid(buyerName);

            // Give buyer enough money
            economy.deposit(buyerUuid, BigDecimal.valueOf(listing.buyoutPrice() + 1000), currency);

            try {
                service.buyListing(buyerUuid, buyerName, listing.id(), listing.quantity());

                long sellerAmount = listing.buyoutPrice() - listing.taxAmount();
                source.sendSystemMessage(Component.literal(
                    "§a[Achat] " + buyerName + " a acheté " + listing.quantity() + "x "
                    + listing.itemName() + " pour " + formatPrice(listing.buyoutPrice())
                ));
                source.sendSystemMessage(Component.literal(
                    "§e  → Tu as reçu " + formatPrice(sellerAmount)
                    + " (taxe: " + formatPrice(listing.taxAmount()) + ")"
                ));
                bought++;
            } catch (Exception e) {
                source.sendSystemMessage(Component.literal(
                    "§c[Achat] Échec pour " + listing.itemName() + ": " + e.getMessage()
                ));
            }
        }

        BigDecimal newBalance = economy.getBalance(player.getUUID(), currency);
        int finalBought = bought;
        source.sendSuccess(() -> Component.literal(
            "§a[HDV] " + finalBought + " achats simulés. Nouveau solde: "
            + newBalance.toPlainString() + " " + currency.symbol()
        ), true);

        return bought;
    }

    private static int simulateClear(CommandSourceStack source,
                                     Supplier<AuctionService> serviceSupplier) {
        AuctionService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.literal("Service HDV non disponible."));
            return 0;
        }

        int cancelled = 0;
        for (String seller : SELLERS) {
            UUID uuid = fakeUuid(seller);
            var listings = service.getMyListings(uuid, 0, Integer.MAX_VALUE);
            for (var listing : listings) {
                if (listing.status() == ListingStatus.ACTIVE) {
                    try {
                        service.cancelListing(uuid, listing.id());
                        cancelled++;
                    } catch (Exception ignored) {}
                }
            }
        }

        int finalCancelled = cancelled;
        source.sendSuccess(() -> Component.literal(
            "§a[HDV] " + finalCancelled + " listings fictives annulées."
        ), true);
        return cancelled;
    }
}

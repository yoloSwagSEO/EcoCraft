# AH Test Data Commands Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add admin commands to populate the AH with fake listings and simulate purchases for testing.

**Architecture:** Single new file `AHTestCommand.java` registered alongside `AHCommand`. Uses existing `AuctionService.createListing()` and `AuctionService.buyListing()` for full flow consistency. Fake players get temporary economy balances via `EconomyProvider.deposit()`.

**Tech Stack:** Java 21, NeoForge Brigadier commands, existing AuctionService/EconomyProvider APIs.

---

### Task 1: Create AHTestCommand with `/ah populate`

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/command/AHTestCommand.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/command/AHCommand.java` (register sub-commands)

- [ ] **Step 1: Create AHTestCommand.java**

```java
package net.ecocraft.ah.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.ecocraft.ah.data.ItemCategory;
import net.ecocraft.ah.data.ListingType;
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

    // 10 fake seller names
    private static final String[] SELLERS = {
        "Grimald", "Thoria", "Keldorn", "Sylvanas", "Brakk",
        "Elyndra", "Morgrim", "Vaelith", "Drogan", "Isildra"
    };

    // Stable UUIDs for fake players (derived from name hash)
    private static UUID fakeUuid(String name) {
        return UUID.nameUUIDFromBytes(("ecocraft_fake:" + name).getBytes());
    }

    // Item pool: {registryName, displayName, category, minPrice, maxPrice, maxQty}
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
                    hours, currency.id(), item.cat
                );
                created++;
            } catch (Exception e) {
                source.sendSystemMessage(Component.literal("§c[Populate] Erreur: " + e.getMessage()));
            }
        }

        source.sendSuccess(() -> Component.literal(
            "§a[HDV] " + created + " listings créées par " + SELLERS.length + " vendeurs fictifs."
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

        // Get player's active listings
        var listings = service.getMyListings(player.getUUID(), 0, Integer.MAX_VALUE);
        var activeListings = listings.stream()
            .filter(l -> l.status() == net.ecocraft.ah.data.ListingStatus.ACTIVE
                      && l.listingType() == ListingType.BUYOUT)
            .toList();

        if (activeListings.isEmpty()) {
            source.sendFailure(Component.literal("Aucune listing active à acheter."));
            return 0;
        }

        Random rng = new Random();
        int bought = 0;
        int toProcess = Math.min(count, activeListings.size());

        // Shuffle and pick
        var shuffled = new ArrayList<>(activeListings);
        Collections.shuffle(shuffled, rng);

        for (int i = 0; i < toProcess; i++) {
            var listing = shuffled.get(i);
            String buyerName = SELLERS[rng.nextInt(SELLERS.length)];
            UUID buyerUuid = fakeUuid(buyerName);

            // Give buyer enough money
            long totalCost = listing.buyoutPrice() * listing.quantity();
            economy.deposit(buyerUuid, BigDecimal.valueOf(totalCost + 1000), currency);

            try {
                service.buyListing(buyerUuid, buyerName, listing.id());

                long sellerAmount = listing.buyoutPrice() - listing.taxAmount();
                source.sendSystemMessage(Component.literal(
                    "§a[Achat] " + buyerName + " a acheté " + listing.quantity() + "x "
                    + listing.itemName() + " pour " + BuyTab.formatPrice(listing.buyoutPrice())
                ));
                source.sendSystemMessage(Component.literal(
                    "§e  → Tu as reçu " + BuyTab.formatPrice(sellerAmount)
                    + " (taxe: " + BuyTab.formatPrice(listing.taxAmount()) + ")"
                ));
                bought++;
            } catch (Exception e) {
                source.sendSystemMessage(Component.literal(
                    "§c[Achat] Échec pour " + listing.itemName() + ": " + e.getMessage()
                ));
            }
        }

        // Show updated balance
        BigDecimal newBalance = economy.getBalance(player.getUUID(), currency);
        source.sendSuccess(() -> Component.literal(
            "§a[HDV] " + bought + " achats simulés. Nouveau solde: "
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
                if (listing.status() == net.ecocraft.ah.data.ListingStatus.ACTIVE) {
                    try {
                        service.cancelListing(uuid, listing.id());
                        cancelled++;
                    } catch (Exception ignored) {}
                }
            }
        }

        source.sendSuccess(() -> Component.literal(
            "§a[HDV] " + cancelled + " listings fictives annulées."
        ), true);
        return cancelled;
    }
}
```

- [ ] **Step 2: Register AHTestCommand in AHCommand.java**

In `AHCommand.register()`, add at the end before the closing `);`:

```java
AHTestCommand.register(dispatcher, serviceSupplier);
```

Note: `AHTestCommand.register` adds `populate` and `simulate` as sub-commands of the existing `/ah` literal. Since Brigadier merges commands with the same root, this works correctly.

- [ ] **Step 3: Add missing import in AHTestCommand**

The `BuyTab.formatPrice()` is a client-side class. We need a server-safe price formatter. Add a static helper in AHTestCommand instead:

```java
private static String formatPrice(long price) {
    if (price >= 1000) {
        return String.format("%,d", price).replace(',', ' ') + " G";
    }
    return price + " G";
}
```

Replace all `BuyTab.formatPrice(...)` calls with `formatPrice(...)` in the file.

- [ ] **Step 4: Build and verify compilation**

Run: `./gradlew :auction-house:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Build all and deploy**

```bash
./gradlew clean build
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
```

- [ ] **Step 6: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/command/AHTestCommand.java
git add auction-house/src/main/java/net/ecocraft/ah/command/AHCommand.java
git commit -m "feat: add /ah populate and /ah simulate commands for test data"
```

---

### Testing Instructions

In-game (op level 2 required):

1. **`/ah populate`** — creates 50 listings from 10 fake sellers
2. **`/ah populate 200`** — creates 200 listings
3. **`/ah simulate buy`** — 5 fake buyers purchase your listings
4. **`/ah simulate buy 15`** — 15 purchases
5. **`/ah simulate clear`** — cancels all fake seller listings
6. **`/balance`** — verify your balance changed after simulate buy
7. Open HDV → check Buy tab, Ledger, Mes enchères for data

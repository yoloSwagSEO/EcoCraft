# Economy V2 Phase 1-3: Foundation (API + GUI)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the Currency API with composite sub-units, icons, exchange support, and build the GUI widgets (EcoIcon, EcoCurrencyInput, CurrencyFormatter) that all other phases depend on.

**Architecture:** The Currency record gains new optional fields with backward-compatible factory methods. New records (SubUnit, Icon) are added to economy-api. GUI widgets are built in gui-lib. The CurrencyFormatter bridges both — it lives in gui-lib but depends on economy-api types. Existing code continues to compile unchanged via compatibility factory methods.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit 5, gui-lib widget tree (BaseWidget/EcoScreen)

---

## Scope

This plan covers spec **Phases 1-3**:
- Phase 1: API Currency V2 (SubUnit, Icon, exchangeable, referenceRate)
- Phase 2: gui-lib icons (EcoIcon, EcoImage, IconRegistry)
- Phase 3: gui-lib currency widgets (EcoCurrencyInput, CurrencyFormatter)

**NOT in scope** (separate plans): economy-core changes, Vault refonte, Bureau de change, Numismatics adapter, AH/Mail migration.

---

## File Map

### New files

| File | Responsibility |
|------|---------------|
| `economy-api/.../currency/SubUnit.java` | Sub-unit record (code, name, multiplier, icon) |
| `economy-api/.../currency/Icon.java` | Sealed interface for icon sources (texture, item, file, text) |
| `economy-api/.../currency/CurrencyFormatter.java` | Format long → display string based on Currency config |
| `economy-api/.../currency/ExternalCurrencyAdapter.java` | Interface for third-party mod adapters |
| `economy-api/.../currency/EcoCraftCurrencyApi.java` | Public API for adapter registration |
| `economy-core/src/test/.../currency/CurrencyV2Test.java` | Tests for Currency V2 features |
| `economy-core/src/test/.../currency/CurrencyFormatterTest.java` | Tests for formatting |
| `gui-lib/.../core/EcoIcon.java` | Widget that renders an Icon |
| `gui-lib/.../core/EcoImage.java` | Rich image widget (resize, tint, tooltip) |
| `gui-lib/.../core/IconRegistry.java` | Built-in icon library + custom registration |
| `gui-lib/.../core/EcoCurrencyInput.java` | Composite currency input with ▲/▼ per sub-unit |

### Modified files

| File | Changes |
|------|---------|
| `economy-api/.../currency/Currency.java` | Add icon, subUnits, exchangeable, referenceRate fields + compat factories |
| `economy-api/.../exchange/ExchangeRate.java` | Add BigDecimal referenceRate support |
| `economy-core/.../impl/CurrencyRegistryImpl.java` | Support adapter-registered currencies |
| `economy-core/.../config/EcoConfig.java` | Add sub-unit config structure |
| `economy-core/.../EcoServerEvents.java` | Load sub-units from config on startup |

---

## Tasks

### Task 1: SubUnit record + Icon sealed interface

**Files:**
- Create: `economy-api/src/main/java/net/ecocraft/api/currency/SubUnit.java`
- Create: `economy-api/src/main/java/net/ecocraft/api/currency/Icon.java`

- [ ] **Step 1: Create the Icon sealed interface**

```java
package net.ecocraft.api.currency;

import org.jetbrains.annotations.Nullable;

/**
 * Represents an icon source for currencies and sub-units.
 * Can be a mod texture, a Minecraft item, an external file, or a text fallback.
 */
public sealed interface Icon {

    record TextureIcon(String resourceLocation) implements Icon {}
    record ItemIcon(String itemId) implements Icon {}
    record FileIcon(String path) implements Icon {}
    record TextIcon(String symbol) implements Icon {}

    static Icon texture(String resourceLocation) { return new TextureIcon(resourceLocation); }
    static Icon item(String itemId) { return new ItemIcon(itemId); }
    static Icon file(String path) { return new FileIcon(path); }
    static Icon text(String symbol) { return new TextIcon(symbol); }

    /**
     * Create an Icon from a string descriptor.
     * Format: "texture:modid:path", "item:minecraft:diamond", "file:/path/to.png", or plain text.
     */
    static Icon parse(String descriptor) {
        if (descriptor.startsWith("texture:")) return texture(descriptor.substring(8));
        if (descriptor.startsWith("item:")) return item(descriptor.substring(5));
        if (descriptor.startsWith("file:")) return file(descriptor.substring(5));
        return text(descriptor);
    }
}
```

- [ ] **Step 2: Create the SubUnit record**

```java
package net.ecocraft.api.currency;

import org.jetbrains.annotations.Nullable;

/**
 * A sub-unit within a composite currency.
 * Example: "PO" (Pièce d'or) with multiplier 100 means 1 PO = 100 base units.
 *
 * @param code       Short code displayed in UI ("PP", "PO", "PA", "PC")
 * @param name       Full display name ("Pièce de platine")
 * @param multiplier Value in base units (1000, 100, 10, 1). Must be > 0.
 * @param itemId     Optional linked physical item ID for this sub-unit
 * @param icon       Optional icon for this sub-unit
 */
public record SubUnit(
        String code,
        String name,
        long multiplier,
        @Nullable String itemId,
        @Nullable Icon icon
) {
    public SubUnit {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("SubUnit code cannot be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("SubUnit name cannot be blank");
        if (multiplier <= 0) throw new IllegalArgumentException("SubUnit multiplier must be positive");
    }

    /** Convenience constructor without itemId and icon. */
    public SubUnit(String code, String name, long multiplier) {
        this(code, name, multiplier, null, null);
    }
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `./gradlew :economy-api:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add economy-api/src/main/java/net/ecocraft/api/currency/Icon.java \
       economy-api/src/main/java/net/ecocraft/api/currency/SubUnit.java
git commit -m "feat(economy-api): add Icon sealed interface and SubUnit record"
```

---

### Task 2: Extend Currency record with V2 fields (backward-compatible)

**Files:**
- Modify: `economy-api/src/main/java/net/ecocraft/api/currency/Currency.java`

- [ ] **Step 1: Read current Currency.java**

Read the file to understand exact current structure.

- [ ] **Step 2: Add new fields with backward-compatible constructors**

Add these fields to the record:
- `@Nullable Icon icon`
- `List<SubUnit> subUnits` (empty list = simple currency)
- `boolean exchangeable`
- `BigDecimal referenceRate` (1.0 = reference currency)

Keep existing factory methods working by providing defaults for new fields.

```java
package net.ecocraft.api.currency;

import org.jetbrains.annotations.Nullable;
import java.math.BigDecimal;
import java.util.List;

public record Currency(
        String id,
        String name,
        String symbol,
        int decimals,
        boolean physical,
        @Nullable String itemId,
        @Nullable Icon icon,
        List<SubUnit> subUnits,
        boolean exchangeable,
        BigDecimal referenceRate
) {
    public Currency {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Currency id cannot be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Currency name cannot be blank");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Currency symbol cannot be blank");
        if (decimals < 0) throw new IllegalArgumentException("Currency decimals cannot be negative");
        if (physical && (itemId == null || itemId.isBlank())) {
            throw new IllegalArgumentException("Physical currency must have an itemId");
        }
        if (subUnits == null) subUnits = List.of();
        if (referenceRate == null) referenceRate = BigDecimal.ONE;
    }

    /** Check if this currency uses composite sub-units. */
    public boolean isComposite() { return !subUnits.isEmpty(); }

    /** Check if this is the reference currency (rate = 1.0). */
    public boolean isReference() { return referenceRate.compareTo(BigDecimal.ONE) == 0; }

    // --- Backward-compatible factory methods (existing code keeps compiling) ---

    public static Currency virtual(String id, String name, String symbol, int decimals) {
        return new Currency(id, name, symbol, decimals, false, null, null, List.of(), false, BigDecimal.ONE);
    }

    public static Currency physical(String id, String name, String symbol, int decimals, String itemId) {
        return new Currency(id, name, symbol, decimals, true, itemId, null, List.of(), false, BigDecimal.ONE);
    }

    // --- V2 builder for full configuration ---

    public static Builder builder(String id, String name, String symbol) {
        return new Builder(id, name, symbol);
    }

    public static class Builder {
        private final String id, name, symbol;
        private int decimals = 0;
        private boolean physical = false;
        private @Nullable String itemId;
        private @Nullable Icon icon;
        private List<SubUnit> subUnits = List.of();
        private boolean exchangeable = false;
        private BigDecimal referenceRate = BigDecimal.ONE;

        Builder(String id, String name, String symbol) {
            this.id = id; this.name = name; this.symbol = symbol;
        }

        public Builder decimals(int d) { this.decimals = d; return this; }
        public Builder physical(String itemId) { this.physical = true; this.itemId = itemId; return this; }
        public Builder icon(Icon icon) { this.icon = icon; return this; }
        public Builder subUnits(List<SubUnit> units) { this.subUnits = units; return this; }
        public Builder subUnits(SubUnit... units) { this.subUnits = List.of(units); return this; }
        public Builder exchangeable(boolean ex) { this.exchangeable = ex; return this; }
        public Builder referenceRate(BigDecimal rate) { this.referenceRate = rate; return this; }
        public Builder referenceRate(double rate) { this.referenceRate = BigDecimal.valueOf(rate); return this; }

        public Currency build() {
            return new Currency(id, name, symbol, decimals, physical, itemId, icon, subUnits, exchangeable, referenceRate);
        }
    }
}
```

- [ ] **Step 3: Build all modules to verify backward compatibility**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL (all modules compile, all tests pass)

- [ ] **Step 4: Commit**

```bash
git add economy-api/src/main/java/net/ecocraft/api/currency/Currency.java
git commit -m "feat(economy-api): extend Currency with icon, subUnits, exchangeable, referenceRate (backward-compatible)"
```

---

### Task 3: CurrencyFormatter utility

**Files:**
- Create: `economy-api/src/main/java/net/ecocraft/api/currency/CurrencyFormatter.java`
- Create: `economy-core/src/test/java/net/ecocraft/core/currency/CurrencyFormatterTest.java`

- [ ] **Step 1: Write tests for CurrencyFormatter**

```java
package net.ecocraft.core.currency;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyFormatter;
import net.ecocraft.api.currency.SubUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyFormatterTest {

    private static final Currency GOLD = Currency.virtual("gold", "Gold", "G", 2);
    private static final Currency GOLD_NO_DECIMALS = Currency.virtual("gold2", "Gold", "G", 0);

    private static final Currency COMPOSITE = Currency.builder("platine", "Platine", "PP")
            .subUnits(
                    new SubUnit("PP", "Platine", 1000),
                    new SubUnit("PO", "Or", 100),
                    new SubUnit("PA", "Argent", 10),
                    new SubUnit("PC", "Cuivre", 1)
            ).build();

    private static final Currency BASE8 = Currency.builder("spurs", "Spurs", "⚙")
            .subUnits(
                    new SubUnit("Sun", "Sun", 4096),
                    new SubUnit("Crown", "Crown", 512),
                    new SubUnit("Cog", "Cog", 64),
                    new SubUnit("Spur", "Spur", 1)
            ).build();

    @Test
    void formatSimpleCurrencyWithDecimals() {
        assertEquals("1.50 G", CurrencyFormatter.format(150, GOLD));
        assertEquals("100.00 G", CurrencyFormatter.format(10000, GOLD));
        assertEquals("0.01 G", CurrencyFormatter.format(1, GOLD));
        assertEquals("0.00 G", CurrencyFormatter.format(0, GOLD));
    }

    @Test
    void formatSimpleCurrencyNoDecimals() {
        assertEquals("150 G", CurrencyFormatter.format(150, GOLD_NO_DECIMALS));
        assertEquals("0 G", CurrencyFormatter.format(0, GOLD_NO_DECIMALS));
    }

    @Test
    void formatComposite() {
        assertEquals("1 PP 5 PO", CurrencyFormatter.format(1500, COMPOSITE));
        assertEquals("1 PP", CurrencyFormatter.format(1000, COMPOSITE));
        assertEquals("5 PO", CurrencyFormatter.format(500, COMPOSITE));
        assertEquals("3 PA 2 PC", CurrencyFormatter.format(32, COMPOSITE));
        assertEquals("0 PC", CurrencyFormatter.format(0, COMPOSITE));
        assertEquals("1 PP 2 PO 3 PA 4 PC", CurrencyFormatter.format(1234, COMPOSITE));
    }

    @Test
    void formatCompositeBase8() {
        assertEquals("1 Sun", CurrencyFormatter.format(4096, BASE8));
        assertEquals("1 Sun 1 Crown", CurrencyFormatter.format(4608, BASE8));
        assertEquals("63 Spur", CurrencyFormatter.format(63, BASE8));
        assertEquals("1 Cog", CurrencyFormatter.format(64, BASE8));
    }

    @Test
    void splitComposite() {
        long[] split = CurrencyFormatter.split(1234, COMPOSITE);
        assertArrayEquals(new long[]{1, 2, 3, 4}, split); // 1 PP, 2 PO, 3 PA, 4 PC
    }

    @Test
    void splitAndCombineRoundTrips() {
        long original = 9876;
        long[] split = CurrencyFormatter.split(original, COMPOSITE);
        long combined = CurrencyFormatter.combine(split, COMPOSITE);
        assertEquals(original, combined);
    }

    @Test
    void combineComposite() {
        long result = CurrencyFormatter.combine(new long[]{1, 5, 0, 0}, COMPOSITE);
        assertEquals(1500, result);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :economy-core:test --tests "*CurrencyFormatterTest*"`
Expected: FAIL — CurrencyFormatter class does not exist

- [ ] **Step 3: Implement CurrencyFormatter**

```java
package net.ecocraft.api.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.StringJoiner;

/**
 * Formats currency amounts for display.
 * Handles simple currencies (with decimals) and composite currencies (with sub-units).
 */
public final class CurrencyFormatter {

    private CurrencyFormatter() {}

    /**
     * Format a raw amount (in smallest unit) for display.
     *
     * @param amount   raw amount in smallest unit
     * @param currency the currency definition
     * @return formatted string, e.g. "1.50 G" or "1 PP 5 PO"
     */
    public static String format(long amount, Currency currency) {
        if (currency.isComposite()) {
            return formatComposite(amount, currency.subUnits());
        }
        return formatSimple(amount, currency);
    }

    /**
     * Format with symbol only (no sub-unit codes), for compact display.
     */
    public static String formatCompact(long amount, Currency currency) {
        if (currency.isComposite()) {
            return formatComposite(amount, currency.subUnits());
        }
        return formatSimple(amount, currency);
    }

    private static String formatSimple(long amount, Currency currency) {
        if (currency.decimals() == 0) {
            return amount + " " + currency.symbol();
        }
        BigDecimal display = BigDecimal.valueOf(amount)
                .movePointLeft(currency.decimals())
                .setScale(currency.decimals(), RoundingMode.DOWN);
        return display.toPlainString() + " " + currency.symbol();
    }

    private static String formatComposite(long amount, List<SubUnit> subUnits) {
        if (subUnits.isEmpty()) return String.valueOf(amount);

        long[] parts = splitInternal(amount, subUnits);
        StringJoiner joiner = new StringJoiner(" ");
        boolean anyNonZero = false;

        for (int i = 0; i < parts.length; i++) {
            if (parts[i] > 0) {
                joiner.add(parts[i] + " " + subUnits.get(i).code());
                anyNonZero = true;
            }
        }

        if (!anyNonZero) {
            // All zero — show the smallest unit as "0 PC"
            SubUnit smallest = subUnits.get(subUnits.size() - 1);
            return "0 " + smallest.code();
        }

        return joiner.toString();
    }

    /**
     * Split a raw amount into sub-unit parts.
     * Returns an array where index i corresponds to subUnits.get(i).
     * Sub-units must be ordered from largest to smallest multiplier.
     */
    public static long[] split(long amount, Currency currency) {
        return splitInternal(amount, currency.subUnits());
    }

    private static long[] splitInternal(long amount, List<SubUnit> subUnits) {
        long[] parts = new long[subUnits.size()];
        long remaining = amount;
        for (int i = 0; i < subUnits.size(); i++) {
            long mult = subUnits.get(i).multiplier();
            parts[i] = remaining / mult;
            remaining = remaining % mult;
        }
        return parts;
    }

    /**
     * Combine sub-unit parts back into a raw amount.
     * Inverse of split().
     */
    public static long combine(long[] parts, Currency currency) {
        List<SubUnit> subUnits = currency.subUnits();
        long total = 0;
        for (int i = 0; i < Math.min(parts.length, subUnits.size()); i++) {
            total += parts[i] * subUnits.get(i).multiplier();
        }
        return total;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :economy-core:test --tests "*CurrencyFormatterTest*"`
Expected: ALL PASS

- [ ] **Step 5: Run full build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add economy-api/src/main/java/net/ecocraft/api/currency/CurrencyFormatter.java \
       economy-core/src/test/java/net/ecocraft/core/currency/CurrencyFormatterTest.java
git commit -m "feat(economy-api): add CurrencyFormatter for simple and composite currencies"
```

---

### Task 4: ExternalCurrencyAdapter interface + API registration point

**Files:**
- Create: `economy-api/src/main/java/net/ecocraft/api/currency/ExternalCurrencyAdapter.java`
- Create: `economy-api/src/main/java/net/ecocraft/api/EcoCraftCurrencyApi.java`

- [ ] **Step 1: Create ExternalCurrencyAdapter interface**

```java
package net.ecocraft.api.currency;

import java.util.UUID;

/**
 * Adapter interface for third-party economy mods.
 * Implementors bridge their mod's balance system into EcoCraft's currency registry.
 *
 * <p>The adapter is the SOURCE OF TRUTH — EcoCraft never duplicates the balance.
 * All operations delegate to the external mod's API.</p>
 */
public interface ExternalCurrencyAdapter {

    /** The mod ID this adapter bridges (e.g., "numismatics"). */
    String modId();

    /** The currency definition to register in EcoCraft's registry. */
    Currency getCurrency();

    /** Get the player's balance in the smallest unit of this currency. */
    long getBalance(UUID player);

    /** Withdraw amount (in smallest unit). Returns true if successful. */
    boolean withdraw(UUID player, long amount);

    /** Deposit amount (in smallest unit). Returns true if successful. */
    boolean deposit(UUID player, long amount);

    /** Check if the player can afford the amount. */
    boolean canAfford(UUID player, long amount);
}
```

- [ ] **Step 2: Create EcoCraftCurrencyApi**

```java
package net.ecocraft.api;

import net.ecocraft.api.currency.ExternalCurrencyAdapter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Public API for third-party mods to register currency adapters.
 *
 * <p>Usage from a third-party mod (at server start):
 * <pre>
 * EcoCraftCurrencyApi.registerAdapter(new MyModCurrencyAdapter());
 * </pre>
 */
public final class EcoCraftCurrencyApi {

    private static final List<ExternalCurrencyAdapter> adapters = new CopyOnWriteArrayList<>();

    private EcoCraftCurrencyApi() {}

    public static void registerAdapter(ExternalCurrencyAdapter adapter) {
        if (adapter == null) throw new IllegalArgumentException("Adapter cannot be null");
        adapters.add(adapter);
    }

    public static List<ExternalCurrencyAdapter> getAdapters() {
        return List.copyOf(adapters);
    }

    public static @Nullable ExternalCurrencyAdapter getAdapter(String modId) {
        for (ExternalCurrencyAdapter adapter : adapters) {
            if (adapter.modId().equals(modId)) return adapter;
        }
        return null;
    }

    public static @Nullable ExternalCurrencyAdapter getAdapterForCurrency(String currencyId) {
        for (ExternalCurrencyAdapter adapter : adapters) {
            if (adapter.getCurrency().id().equals(currencyId)) return adapter;
        }
        return null;
    }

    /** Called on server stop to clean up. */
    public static void clearAdapters() {
        adapters.clear();
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :economy-api:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add economy-api/src/main/java/net/ecocraft/api/currency/ExternalCurrencyAdapter.java \
       economy-api/src/main/java/net/ecocraft/api/EcoCraftCurrencyApi.java
git commit -m "feat(economy-api): add ExternalCurrencyAdapter interface and public registration API"
```

---

### Task 5: EcoIcon widget

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/core/EcoIcon.java`

- [ ] **Step 1: Create EcoIcon widget**

```java
package net.ecocraft.gui.core;

import net.ecocraft.api.currency.Icon;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Widget that renders an {@link Icon} at a given size.
 * Supports texture, item, file, and text icon sources.
 */
public class EcoIcon extends BaseWidget {

    private final Icon icon;
    private final int renderSize;
    private final Theme theme;

    public EcoIcon(int x, int y, int size, Icon icon, Theme theme) {
        super(x, y, size, size);
        this.icon = icon;
        this.renderSize = size;
        this.theme = theme;
    }

    public EcoIcon(int x, int y, int size, Icon icon) {
        this(x, y, size, icon, Theme.dark());
    }

    @Override
    public boolean isFocusable() { return false; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible() || icon == null) return;

        int x = getX();
        int y = getY();

        switch (icon) {
            case Icon.TextureIcon tex -> renderTexture(graphics, tex.resourceLocation(), x, y);
            case Icon.ItemIcon item -> renderItem(graphics, item.itemId(), x, y);
            case Icon.FileIcon file -> renderFile(graphics, file.path(), x, y);
            case Icon.TextIcon text -> renderText(graphics, text.symbol(), x, y);
        }
    }

    private void renderTexture(GuiGraphics graphics, String rl, int x, int y) {
        try {
            ResourceLocation location = ResourceLocation.parse(rl);
            graphics.blit(location, x, y, 0, 0, renderSize, renderSize, renderSize, renderSize);
        } catch (Exception e) {
            // Fallback to text "?"
            renderText(graphics, "?", x, y);
        }
    }

    private void renderItem(GuiGraphics graphics, String itemId, int x, int y) {
        try {
            ResourceLocation rl = ResourceLocation.parse(itemId);
            var item = BuiltInRegistries.ITEM.get(rl);
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                // Items render at 16x16 — center if renderSize differs
                int offset = (renderSize - 16) / 2;
                graphics.renderItem(stack, x + offset, y + offset);
                return;
            }
        } catch (Exception ignored) {}
        renderText(graphics, "?", x, y);
    }

    private void renderFile(GuiGraphics graphics, String path, int x, int y) {
        // External file icons require dynamic texture loading.
        // For now, fallback to text "📁". Full implementation will use
        // NativeImage + DynamicTexture to load PNGs from disk.
        renderText(graphics, "?", x, y);
    }

    private void renderText(GuiGraphics graphics, String text, int x, int y) {
        Font font = Minecraft.getInstance().font;
        int textW = font.width(text);
        int textX = x + (renderSize - textW) / 2;
        int textY = y + (renderSize - font.lineHeight) / 2;
        graphics.drawString(font, text, textX, textY, theme.textWhite, false);
    }
}
```

- [ ] **Step 2: Build gui-lib**

Run: `./gradlew :gui-lib:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/core/EcoIcon.java
git commit -m "feat(gui-lib): add EcoIcon widget for rendering Icon sources"
```

---

### Task 6: EcoCurrencyInput widget

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/core/EcoCurrencyInput.java`

- [ ] **Step 1: Create EcoCurrencyInput widget**

```java
package net.ecocraft.gui.core;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyFormatter;
import net.ecocraft.api.currency.Icon;
import net.ecocraft.api.currency.SubUnit;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.function.LongConsumer;

/**
 * Composite currency input widget.
 *
 * <p>For composite currencies (with sub-units), displays one input per sub-unit with ▲/▼ arrows:
 * <pre>
 *       ▲           ▲           ▲           ▲
 *   [  1  ] 🪙PP  [  5  ] 🪙PO  [  0  ] 🪙PA  [  0  ] 🪙PC
 *       ▼           ▼           ▼           ▼
 * </pre>
 *
 * <p>For simple currencies, delegates to a single {@link EcoNumberInput}.
 *
 * <p>Internally stores a single {@code long} value (total in base units).
 * Conversion between sub-units is automatic (e.g., 10 PO → 1 PP 0 PO).
 */
public class EcoCurrencyInput extends BaseWidget {

    private static final int ARROW_H = 10;
    private static final int FIELD_H = 16;
    private static final int UNIT_GAP = 4;

    private final Currency currency;
    private final Theme theme;
    private final Font font;
    private long value = 0;
    private long min = 0;
    private long max = Long.MAX_VALUE;
    private LongConsumer responder;

    // For simple currencies — delegate to EcoNumberInput
    private EcoNumberInput simpleInput;

    // For composite currencies — one sub-input per unit
    private SubUnitField[] fields;

    public EcoCurrencyInput(Font font, int x, int y, int width, Currency currency, Theme theme) {
        super(x, y, width, currency.isComposite() ? ARROW_H + FIELD_H + ARROW_H : FIELD_H);
        this.currency = currency;
        this.theme = theme;
        this.font = font;

        if (currency.isComposite()) {
            buildCompositeFields();
        } else {
            buildSimpleField();
        }
    }

    private void buildSimpleField() {
        simpleInput = new EcoNumberInput(font, getX(), getY(), getWidth(), FIELD_H, theme);
        simpleInput.min(0).max(999999999).step(1).showButtons(true);
        simpleInput.setValue(0);
        simpleInput.responder(val -> {
            this.value = val;
            if (responder != null) responder.accept(value);
        });
        addChild(simpleInput);
    }

    private void buildCompositeFields() {
        List<SubUnit> units = currency.subUnits();
        fields = new SubUnitField[units.size()];

        int unitW = (getWidth() - (units.size() - 1) * UNIT_GAP) / units.size();
        int x = getX();

        for (int i = 0; i < units.size(); i++) {
            fields[i] = new SubUnitField(units.get(i), x, getY(), unitW, i);
            x += unitW + UNIT_GAP;
        }
    }

    public void setValue(long value) {
        this.value = Math.max(min, Math.min(max, value));
        if (simpleInput != null) {
            simpleInput.setValue(this.value);
        }
    }

    public long getValue() { return value; }

    public EcoCurrencyInput min(long min) { this.min = min; return this; }
    public EcoCurrencyInput max(long max) { this.max = max; return this; }

    public EcoCurrencyInput responder(LongConsumer responder) {
        this.responder = responder;
        return this;
    }

    private void adjustValue(int unitIndex, int delta) {
        long mult = currency.subUnits().get(unitIndex).multiplier();
        long newVal = value + (long) delta * mult;
        newVal = Math.max(min, Math.min(max, newVal));
        this.value = newVal;
        if (responder != null) responder.accept(value);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        if (simpleInput != null) {
            // Simple mode — EcoNumberInput renders itself + symbol
            int symbolX = getX() + getWidth() + 4;
            int symbolY = getY() + (FIELD_H - font.lineHeight) / 2;
            Icon icon = currency.icon();
            if (icon != null && !(icon instanceof Icon.TextIcon)) {
                // Render icon
                EcoIcon ecoIcon = new EcoIcon(symbolX, symbolY, font.lineHeight, icon, theme);
                ecoIcon.render(graphics, mouseX, mouseY, partialTick);
            } else {
                graphics.drawString(font, currency.symbol(), symbolX, symbolY, theme.textLight, false);
            }
            return;
        }

        // Composite mode
        if (fields == null) return;
        long[] parts = CurrencyFormatter.split(value, currency);

        for (int i = 0; i < fields.length; i++) {
            fields[i].render(graphics, font, theme, parts[i], mouseX, mouseY);
        }
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!containsPoint(mouseX, mouseY)) return false;
        if (fields == null) return false;

        for (int i = 0; i < fields.length; i++) {
            SubUnitField f = fields[i];
            // Up arrow
            if (mouseX >= f.x && mouseX < f.x + f.width && mouseY >= f.y && mouseY < f.y + ARROW_H) {
                adjustValue(i, 1);
                return true;
            }
            // Down arrow
            int downY = f.y + ARROW_H + FIELD_H;
            if (mouseX >= f.x && mouseX < f.x + f.width && mouseY >= downY && mouseY < downY + ARROW_H) {
                adjustValue(i, -1);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!containsPoint(mouseX, mouseY)) return false;
        if (fields == null) return false;

        for (int i = 0; i < fields.length; i++) {
            SubUnitField f = fields[i];
            if (mouseX >= f.x && mouseX < f.x + f.width) {
                adjustValue(i, scrollY > 0 ? 1 : -1);
                return true;
            }
        }
        return false;
    }

    /** Internal representation of one sub-unit's UI column. */
    private static class SubUnitField {
        final SubUnit unit;
        final int x, y, width;
        final int index;

        SubUnitField(SubUnit unit, int x, int y, int width, int index) {
            this.unit = unit;
            this.x = x;
            this.y = y;
            this.width = width;
            this.index = index;
        }

        void render(GuiGraphics graphics, Font font, Theme theme, long partValue, int mouseX, int mouseY) {
            int arrowX = x + width / 2;
            int fieldY = y + ARROW_H;

            // Up arrow ▲
            boolean hoverUp = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + ARROW_H;
            String upArrow = "▲";
            int upW = font.width(upArrow);
            graphics.drawString(font, upArrow, arrowX - upW / 2, y + 1,
                    hoverUp ? theme.accent : theme.textDim, false);

            // Value field
            DrawUtils.drawPanel(graphics, x, fieldY, width, FIELD_H, theme.bgDark, theme.border);
            String valStr = String.valueOf(partValue);
            int valW = font.width(valStr);
            graphics.drawString(font, valStr, x + (width - valW) / 2, fieldY + (FIELD_H - font.lineHeight) / 2,
                    theme.textWhite, false);

            // Down arrow ▼
            int downY = fieldY + FIELD_H;
            boolean hoverDown = mouseX >= x && mouseX < x + width && mouseY >= downY && mouseY < downY + ARROW_H;
            String downArrow = "▼";
            int downW = font.width(downArrow);
            graphics.drawString(font, downArrow, arrowX - downW / 2, downY + 1,
                    hoverDown ? theme.accent : theme.textDim, false);

            // Unit label (below down arrow)
            String label = unit.code();
            int labelW = font.width(label);
            graphics.drawString(font, label, arrowX - labelW / 2, downY + ARROW_H + 1,
                    theme.textGrey, false);
        }
    }
}
```

- [ ] **Step 2: Build gui-lib**

Run: `./gradlew :gui-lib:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/core/EcoCurrencyInput.java
git commit -m "feat(gui-lib): add EcoCurrencyInput widget with composite sub-unit support"
```

---

### Task 7: Integration test — full build + deploy

**Files:**
- No new files

- [ ] **Step 1: Run full build with all modules**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL — all modules compile, all tests pass. Existing AH and Mail code unaffected.

- [ ] **Step 2: Deploy to Minecraft**

```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
cp mail/build/libs/*.jar /home/florian/.minecraft/mods/
```

- [ ] **Step 3: Commit and push all**

```bash
git push origin master
```

---

## What's Next

This plan covers **Phases 1-3** (Foundation). The following plans are needed:

| Plan | Content | Depends on |
|------|---------|------------|
| **Phase 4-6** | economy-core: multi-currency commands, persisted exchange rates, Vault refonte, Bureau de change | This plan |
| **Phase 7** | Numismatics adapter | Phase 4-6 |
| **Phase 8** | AH + Mail migration to Currency V2 | Phase 4-6 |
| **Phase 9** | Public API for third-party adapters | Phase 7 |

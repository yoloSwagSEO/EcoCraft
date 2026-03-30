package net.ecocraft.api.currency;

import org.jetbrains.annotations.Nullable;
import java.math.BigDecimal;
import java.util.List;

public record Currency(
        String id, String name, String symbol, int decimals,
        boolean physical, @Nullable String itemId,
        @Nullable Icon icon, List<SubUnit> subUnits,
        boolean exchangeable, BigDecimal referenceRate
) {
    public Currency {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Currency id cannot be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Currency name cannot be blank");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Currency symbol cannot be blank");
        if (decimals < 0) throw new IllegalArgumentException("Currency decimals cannot be negative");
        if (physical && (itemId == null || itemId.isBlank())) throw new IllegalArgumentException("Physical currency must have an itemId");
        if (subUnits == null) subUnits = List.of();
        if (referenceRate == null) referenceRate = BigDecimal.ONE;
    }
    public boolean isComposite() { return !subUnits.isEmpty(); }
    public boolean isReference() { return referenceRate.compareTo(BigDecimal.ONE) == 0; }

    public static Currency virtual(String id, String name, String symbol, int decimals) {
        return new Currency(id, name, symbol, decimals, false, null, null, List.of(), false, BigDecimal.ONE);
    }
    public static Currency physical(String id, String name, String symbol, int decimals, String itemId) {
        return new Currency(id, name, symbol, decimals, true, itemId, null, List.of(), false, BigDecimal.ONE);
    }
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

        Builder(String id, String name, String symbol) { this.id = id; this.name = name; this.symbol = symbol; }
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

package net.ecocraft.ah.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class AHConfig {
    public static final AHConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    static {
        Pair<AHConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(AHConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    public final ModConfigSpec.IntValue saleRate;
    public final ModConfigSpec.IntValue depositRate;
    public final ModConfigSpec.ConfigValue<List<? extends Integer>> durations;

    private AHConfig(ModConfigSpec.Builder builder) {
        builder.push("taxes");
        saleRate = builder
            .comment("Sale tax rate as percentage (0-100)")
            .defineInRange("saleRate", 5, 0, 100);
        depositRate = builder
            .comment("Listing deposit rate as percentage (0-100)")
            .defineInRange("depositRate", 2, 0, 100);
        builder.pop();

        builder.push("listings");
        durations = builder
            .comment("Available listing durations in hours")
            .defineListAllowEmpty("durations", List.of(12, 24, 48),
                    () -> 24, v -> v instanceof Integer i && i >= 1 && i <= 168);
        builder.pop();
    }

    public double getSaleRateDecimal() {
        return saleRate.get() / 100.0;
    }

    public double getDepositRateDecimal() {
        return depositRate.get() / 100.0;
    }

    public int[] getDurationsArray() {
        return durations.get().stream().mapToInt(Integer::intValue).toArray();
    }
}

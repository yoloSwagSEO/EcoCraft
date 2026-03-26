package net.ecocraft.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class EcoConfig {
    public static final EcoConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    static {
        Pair<EcoConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(EcoConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    // Storage
    public final ModConfigSpec.ConfigValue<String> storageType;
    public final ModConfigSpec.ConfigValue<String> mysqlHost;
    public final ModConfigSpec.IntValue mysqlPort;
    public final ModConfigSpec.ConfigValue<String> mysqlDatabase;
    public final ModConfigSpec.ConfigValue<String> mysqlUsername;
    public final ModConfigSpec.ConfigValue<String> mysqlPassword;

    // Economy
    public final ModConfigSpec.ConfigValue<String> defaultCurrencyId;
    public final ModConfigSpec.ConfigValue<String> defaultCurrencyName;
    public final ModConfigSpec.ConfigValue<String> defaultCurrencySymbol;
    public final ModConfigSpec.IntValue defaultCurrencyDecimals;
    public final ModConfigSpec.DoubleValue startingBalance;

    // Vault
    public final ModConfigSpec.BooleanValue vaultEnabled;

    private EcoConfig(ModConfigSpec.Builder builder) {
        builder.push("storage");
        storageType = builder
            .comment("Storage type: 'sqlite' or 'mysql'")
            .define("type", "sqlite");
        mysqlHost = builder.define("mysql.host", "localhost");
        mysqlPort = builder.defineInRange("mysql.port", 3306, 1, 65535);
        mysqlDatabase = builder.define("mysql.database", "ecocraft");
        mysqlUsername = builder.define("mysql.username", "root");
        mysqlPassword = builder.define("mysql.password", "");
        builder.pop();

        builder.push("economy");
        defaultCurrencyId = builder
            .comment("ID of the default server currency")
            .define("defaultCurrency.id", "gold");
        defaultCurrencyName = builder.define("defaultCurrency.name", "Gold");
        defaultCurrencySymbol = builder.define("defaultCurrency.symbol", "\u26C1");
        defaultCurrencyDecimals = builder.defineInRange("defaultCurrency.decimals", 2, 0, 4);
        startingBalance = builder
            .comment("Starting balance for new players")
            .defineInRange("startingBalance", 100.0, 0.0, Double.MAX_VALUE);
        builder.pop();

        builder.push("vault");
        vaultEnabled = builder
            .comment("Enable the vault block for physical/virtual currency sync")
            .define("enabled", true);
        builder.pop();
    }
}

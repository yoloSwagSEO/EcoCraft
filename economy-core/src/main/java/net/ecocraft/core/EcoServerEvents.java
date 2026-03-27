package net.ecocraft.core;

import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.api.transaction.TransactionLog;
import net.ecocraft.core.command.EcoCommands;
import net.ecocraft.core.config.EcoConfig;
import net.ecocraft.core.impl.CurrencyRegistryImpl;
import net.ecocraft.core.impl.EconomyProviderImpl;
import net.ecocraft.core.impl.ExchangeServiceImpl;
import net.ecocraft.core.impl.TransactionLogImpl;
import net.ecocraft.core.permission.DefaultPermissionChecker;
import net.ecocraft.core.permission.PermissionChecker;
import net.ecocraft.core.storage.StorageManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.math.BigDecimal;

@EventBusSubscriber(modid = EcoCraftCoreMod.MOD_ID)
public class EcoServerEvents {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StorageManager storage = new StorageManager();
    private static CurrencyRegistryImpl currencyRegistry;
    private static EconomyProviderImpl economyProvider;
    private static ExchangeServiceImpl exchangeService;
    private static TransactionLogImpl transactionLog;
    private static PermissionChecker permissions;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("EcoCraft Economy Core initializing...");
        var server = event.getServer();
        var worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);

        // Initialize storage
        storage.initialize(worldDir);

        // Initialize currency registry with default currency from config
        currencyRegistry = new CurrencyRegistryImpl();
        var defaultCurrency = Currency.virtual(
            EcoConfig.CONFIG.defaultCurrencyId.get(),
            EcoConfig.CONFIG.defaultCurrencyName.get(),
            EcoConfig.CONFIG.defaultCurrencySymbol.get(),
            EcoConfig.CONFIG.defaultCurrencyDecimals.get()
        );
        currencyRegistry.register(defaultCurrency);

        // Initialize services
        economyProvider = new EconomyProviderImpl(storage.getProvider(), currencyRegistry);
        exchangeService = new ExchangeServiceImpl(economyProvider);
        transactionLog = new TransactionLogImpl(storage.getProvider(), currencyRegistry);
        permissions = new DefaultPermissionChecker();
        LOGGER.info("EcoCraft Economy Core initialized with currency: {} ({})",
                defaultCurrency.name(), defaultCurrency.symbol());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        storage.shutdown();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // Commands are registered early (before ServerStartingEvent), so use lazy accessors
        EcoCommands.register(event.getDispatcher(),
                () -> economyProvider, () -> currencyRegistry,
                () -> exchangeService, () -> permissions);
        LOGGER.info("EcoCraft Economy commands registered.");
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (economyProvider == null) return;
        var player = event.getEntity();
        var currency = currencyRegistry.getDefault();
        var balance = economyProvider.getBalance(player.getUUID(), currency);

        // Give starting balance to new players
        if (balance.compareTo(BigDecimal.ZERO) == 0) {
            double startingBalance = EcoConfig.CONFIG.startingBalance.get();
            if (startingBalance > 0) {
                economyProvider.deposit(player.getUUID(), BigDecimal.valueOf(startingBalance), currency);
            }
        }
    }

    // Accessors for other modules
    public static EconomyProvider getEconomy() { return economyProvider; }
    public static CurrencyRegistry getCurrencyRegistry() { return currencyRegistry; }
    public static ExchangeService getExchangeService() { return exchangeService; }
    public static TransactionLog getTransactionLog() { return transactionLog; }
    public static PermissionChecker getPermissions() { return permissions; }
}

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
    private static volatile EcoServerContext context;

    public static EcoServerContext getContext() { return context; }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("EcoCraft Economy Core initializing...");
        var server = event.getServer();
        var worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);

        // Initialize storage
        var storage = new StorageManager();
        storage.initialize(worldDir);

        // Initialize currency registry with default currency from config
        var currencyRegistry = new CurrencyRegistryImpl();
        var defaultCurrency = Currency.virtual(
            EcoConfig.CONFIG.defaultCurrencyId.get(),
            EcoConfig.CONFIG.defaultCurrencyName.get(),
            EcoConfig.CONFIG.defaultCurrencySymbol.get(),
            EcoConfig.CONFIG.defaultCurrencyDecimals.get()
        );
        currencyRegistry.register(defaultCurrency);

        // Initialize services
        var economyProvider = new EconomyProviderImpl(storage.getProvider(), currencyRegistry);
        var exchangeService = new ExchangeServiceImpl(economyProvider);
        var transactionLog = new TransactionLogImpl(storage.getProvider(), currencyRegistry);
        var permissions = new DefaultPermissionChecker();

        context = new EcoServerContext(storage, currencyRegistry, economyProvider,
                exchangeService, transactionLog, permissions);

        // Wire KubeJS event dispatcher if KubeJS is loaded
        if (net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
            try {
                var dispatcher = new net.ecocraft.core.compat.kubejs.EcoEventDispatcher(server);
                economyProvider.setEventDispatcher(dispatcher);
                LOGGER.info("KubeJS integration enabled for EcoCraft Economy");
            } catch (Exception e) {
                LOGGER.warn("Failed to initialize KubeJS integration: {}", e.getMessage());
            }
        }

        LOGGER.info("EcoCraft Economy Core initialized with currency: {} ({})",
                defaultCurrency.name(), defaultCurrency.symbol());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        var ctx = context;
        if (ctx != null) {
            ctx.getStorage().shutdown();
            context = null;
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // Commands are registered early (before ServerStartingEvent), so use lazy accessors
        EcoCommands.register(event.getDispatcher(),
                () -> context != null ? context.getEconomyProvider() : null,
                () -> context != null ? context.getCurrencyRegistry() : null,
                () -> context != null ? context.getExchangeService() : null,
                () -> context != null ? context.getPermissions() : null);
        LOGGER.info("EcoCraft Economy commands registered.");
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        var ctx = context;
        if (ctx == null) return;
        var player = event.getEntity();
        var currency = ctx.getCurrencyRegistry().getDefault();
        var db = ctx.getStorage().getProvider();

        // Give starting balance to new players (only if they have no account row at all)
        if (!db.hasAccount(player.getUUID(), currency.id())) {
            double startingBalance = EcoConfig.CONFIG.startingBalance.get();
            if (startingBalance > 0) {
                ctx.getEconomyProvider().deposit(player.getUUID(), BigDecimal.valueOf(startingBalance), currency);
            }
        }
    }

    // Accessors for other modules — delegate to context
    public static EconomyProvider getEconomy() { return context != null ? context.getEconomyProvider() : null; }
    public static CurrencyRegistry getCurrencyRegistry() { return context != null ? context.getCurrencyRegistry() : null; }
    public static ExchangeService getExchangeService() { return context != null ? context.getExchangeService() : null; }
    public static StorageManager getStorage() { return context != null ? context.getStorage() : null; }
    public static TransactionLog getTransactionLog() { return context != null ? context.getTransactionLog() : null; }
    public static PermissionChecker getPermissions() { return context != null ? context.getPermissions() : null; }
}

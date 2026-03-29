package net.ecocraft.core;

import net.ecocraft.core.impl.CurrencyRegistryImpl;
import net.ecocraft.core.impl.EconomyProviderImpl;
import net.ecocraft.core.impl.ExchangeServiceImpl;
import net.ecocraft.core.impl.TransactionLogImpl;
import net.ecocraft.core.permission.PermissionChecker;
import net.ecocraft.core.storage.StorageManager;

/**
 * Holds all server-scoped services for the economy module.
 * Created on ServerStarting, nulled on ServerStopped.
 */
public class EcoServerContext {

    private final StorageManager storage;
    private final CurrencyRegistryImpl currencyRegistry;
    private final EconomyProviderImpl economyProvider;
    private final ExchangeServiceImpl exchangeService;
    private final TransactionLogImpl transactionLog;
    private final PermissionChecker permissions;

    public EcoServerContext(StorageManager storage,
                            CurrencyRegistryImpl currencyRegistry,
                            EconomyProviderImpl economyProvider,
                            ExchangeServiceImpl exchangeService,
                            TransactionLogImpl transactionLog,
                            PermissionChecker permissions) {
        this.storage = storage;
        this.currencyRegistry = currencyRegistry;
        this.economyProvider = economyProvider;
        this.exchangeService = exchangeService;
        this.transactionLog = transactionLog;
        this.permissions = permissions;
    }

    public StorageManager getStorage() { return storage; }
    public CurrencyRegistryImpl getCurrencyRegistry() { return currencyRegistry; }
    public EconomyProviderImpl getEconomyProvider() { return economyProvider; }
    public ExchangeServiceImpl getExchangeService() { return exchangeService; }
    public TransactionLogImpl getTransactionLog() { return transactionLog; }
    public PermissionChecker getPermissions() { return permissions; }
}

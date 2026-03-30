package net.ecocraft.core.impl;

import net.ecocraft.api.EcoCraftCurrencyApi;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.ExternalCurrencyAdapter;
import net.ecocraft.core.storage.SqliteDatabaseProvider;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AdapterRoutingTest {

    private SqliteDatabaseProvider db;
    private EconomyProviderImpl economy;
    private CurrencyRegistryImpl registry;
    private Path tempDir;

    private static final Currency GOLD = Currency.virtual("gold", "Gold", "\u26C1", 0);
    private static final Currency GEMS = Currency.virtual("gems", "Gems", "\u2666", 0);

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecocraft-adapter-test");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
        registry = new CurrencyRegistryImpl();
        registry.register(GOLD);
        registry.register(GEMS);
        economy = new EconomyProviderImpl(db, registry);
    }

    @AfterEach
    void tearDown() {
        EcoCraftCurrencyApi.clearAdapters();
        db.shutdown();
    }

    /** Creates a simple in-memory adapter backed by AtomicLong per player. */
    private ExternalCurrencyAdapter createFakeAdapter(Currency currency, String modId) {
        Map<UUID, AtomicLong> balances = new ConcurrentHashMap<>();
        return new ExternalCurrencyAdapter() {
            @Override public String modId() { return modId; }
            @Override public Currency getCurrency() { return currency; }

            @Override
            public long getBalance(UUID player) {
                return balances.computeIfAbsent(player, k -> new AtomicLong(0)).get();
            }

            @Override
            public boolean withdraw(UUID player, long amount) {
                AtomicLong bal = balances.computeIfAbsent(player, k -> new AtomicLong(0));
                long current = bal.get();
                if (current < amount) return false;
                bal.addAndGet(-amount);
                return true;
            }

            @Override
            public boolean deposit(UUID player, long amount) {
                balances.computeIfAbsent(player, k -> new AtomicLong(0)).addAndGet(amount);
                return true;
            }

            @Override
            public boolean canAfford(UUID player, long amount) {
                return getBalance(player) >= amount;
            }
        };
    }

    @Test
    void nativeCurrencyUsesDatabase() {
        var player = UUID.randomUUID();
        // No adapter registered for GOLD -> native DB path
        var result = economy.deposit(player, new BigDecimal("100"), GOLD);
        assertTrue(result.successful());
        assertEquals(0, new BigDecimal("100").compareTo(economy.getVirtualBalance(player, GOLD)));
    }

    @Test
    void externalCurrencyRoutesGetBalanceToAdapter() {
        var adapter = createFakeAdapter(GEMS, "external-mod");
        EcoCraftCurrencyApi.registerAdapter(adapter);

        var player = UUID.randomUUID();
        // Seed adapter directly
        adapter.deposit(player, 500);

        BigDecimal balance = economy.getVirtualBalance(player, GEMS);
        assertEquals(0, new BigDecimal("500").compareTo(balance));
    }

    @Test
    void externalCurrencyRoutesWithdrawToAdapter() {
        var adapter = createFakeAdapter(GEMS, "external-mod");
        EcoCraftCurrencyApi.registerAdapter(adapter);

        var player = UUID.randomUUID();
        adapter.deposit(player, 200);

        var result = economy.withdraw(player, new BigDecimal("50"), GEMS);
        assertTrue(result.successful());
        assertEquals(150, adapter.getBalance(player));
    }

    @Test
    void externalCurrencyWithdrawFailsOnInsufficientFunds() {
        var adapter = createFakeAdapter(GEMS, "external-mod");
        EcoCraftCurrencyApi.registerAdapter(adapter);

        var player = UUID.randomUUID();
        adapter.deposit(player, 10);

        var result = economy.withdraw(player, new BigDecimal("50"), GEMS);
        assertFalse(result.successful());
        assertEquals("Insufficient funds", result.errorMessage());
        assertEquals(10, adapter.getBalance(player));
    }

    @Test
    void externalCurrencyRoutesDepositToAdapter() {
        var adapter = createFakeAdapter(GEMS, "external-mod");
        EcoCraftCurrencyApi.registerAdapter(adapter);

        var player = UUID.randomUUID();
        adapter.deposit(player, 100);

        var result = economy.deposit(player, new BigDecimal("75"), GEMS);
        assertTrue(result.successful());
        assertEquals(175, adapter.getBalance(player));
    }

    @Test
    void canAffordDelegatesToAdapter() {
        var adapter = createFakeAdapter(GEMS, "external-mod");
        EcoCraftCurrencyApi.registerAdapter(adapter);

        var player = UUID.randomUUID();
        adapter.deposit(player, 100);

        assertTrue(economy.canAfford(player, new BigDecimal("100"), GEMS));
        assertFalse(economy.canAfford(player, new BigDecimal("101"), GEMS));
    }

    @Test
    void noAdapterMeansNativeBehavior() {
        // No adapter registered for GEMS -> native DB path, should not crash
        var player = UUID.randomUUID();
        var result = economy.deposit(player, new BigDecimal("50"), GEMS);
        assertTrue(result.successful());
        assertEquals(0, new BigDecimal("50").compareTo(economy.getVirtualBalance(player, GEMS)));
    }

    @Test
    void transferRoutesToAdapterForExternalCurrency() {
        var adapter = createFakeAdapter(GEMS, "external-mod");
        EcoCraftCurrencyApi.registerAdapter(adapter);

        var p1 = UUID.randomUUID();
        var p2 = UUID.randomUUID();
        adapter.deposit(p1, 200);

        var result = economy.transfer(p1, p2, new BigDecimal("80"), GEMS);
        assertTrue(result.successful());
        assertEquals(120, adapter.getBalance(p1));
        assertEquals(80, adapter.getBalance(p2));
    }

    @Test
    void getAccountRoutesToAdapterForExternalCurrency() {
        var adapter = createFakeAdapter(GEMS, "external-mod");
        EcoCraftCurrencyApi.registerAdapter(adapter);

        var player = UUID.randomUUID();
        adapter.deposit(player, 999);

        var account = economy.getAccount(player, GEMS);
        assertEquals(0, new BigDecimal("999").compareTo(account.virtualBalance()));
    }
}

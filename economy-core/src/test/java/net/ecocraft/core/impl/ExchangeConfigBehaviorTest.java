package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.exchange.ExchangeRate;
import net.ecocraft.core.storage.SqliteDatabaseProvider;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that exchange config settings (fees, limits, restrictions) produce correct behavior.
 */
class ExchangeConfigBehaviorTest {

    private SqliteDatabaseProvider db;
    private EconomyProviderImpl economy;
    private CurrencyRegistryImpl registry;
    private Path tempDir;

    private static final Currency GOLD = Currency.builder("gold", "Gold", "\u26C1")
        .decimals(2).exchangeable(true).referenceRate(1.0).build();
    private static final Currency SILVER = Currency.builder("silver", "Silver", "\u26C0")
        .decimals(2).exchangeable(true).referenceRate(0.1).build();

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecocraft-exchange-config-test");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
        registry = new CurrencyRegistryImpl();
        registry.register(GOLD);
        registry.register(SILVER);
        economy = new EconomyProviderImpl(db, registry);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private static void assertAmountEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual),
            () -> "Expected " + expected + " but got " + actual);
    }

    // -------------------------------------------------------------------------
    // Global fee
    // -------------------------------------------------------------------------

    @Test
    void exchange_GlobalFee_AppliedCorrectly() {
        // 2% global fee, no min/max/daily limits
        var config = new ExchangeServiceImpl.ExchangeConfig(2.0, 0, 0, 0);
        var exchangeService = new ExchangeServiceImpl(economy, db, registry, config);

        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("1000.00"), GOLD);

        // Cross-rate: gold ref=1.0, silver ref=0.1 => 1 gold = 10 silver
        // Global fee 2% applied via cross-rate calculation
        var result = exchangeService.convert(player, new BigDecimal("1000.00"), GOLD, SILVER);
        assertTrue(result.successful());

        // 1000 * 10 = 10000 silver, minus 2% fee = 9800
        assertAmountEquals(new BigDecimal("9800.00"), economy.getVirtualBalance(player, SILVER));
        assertAmountEquals(BigDecimal.ZERO, economy.getVirtualBalance(player, GOLD));
    }

    @Test
    void exchange_ZeroFee_NoDeduction() {
        var config = new ExchangeServiceImpl.ExchangeConfig(0.0, 0, 0, 0);
        var exchangeService = new ExchangeServiceImpl(economy, db, registry, config);

        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("1000.00"), GOLD);

        var result = exchangeService.convert(player, new BigDecimal("1000.00"), GOLD, SILVER);
        assertTrue(result.successful());

        // 1000 * 10 = 10000 silver, no fee
        assertAmountEquals(new BigDecimal("10000.00"), economy.getVirtualBalance(player, SILVER));
    }

    // -------------------------------------------------------------------------
    // Min/Max amount
    // -------------------------------------------------------------------------

    @Test
    void exchange_MinAmount_RejectsSmallConversion() {
        var config = new ExchangeServiceImpl.ExchangeConfig(0.0, 100, 0, 0);
        var exchangeService = new ExchangeServiceImpl(economy, db, registry, config);

        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("1000.00"), GOLD);

        // Try converting 50 (below min of 100)
        var result = exchangeService.convert(player, new BigDecimal("50.00"), GOLD, SILVER);
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("minimum"));

        // Balance unchanged
        assertAmountEquals(new BigDecimal("1000.00"), economy.getVirtualBalance(player, GOLD));
    }

    @Test
    void exchange_MaxAmount_RejectsLargeConversion() {
        var config = new ExchangeServiceImpl.ExchangeConfig(0.0, 0, 10000, 0);
        var exchangeService = new ExchangeServiceImpl(economy, db, registry, config);

        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100000.00"), GOLD);

        // Try converting 50000 (above max of 10000)
        var result = exchangeService.convert(player, new BigDecimal("50000.00"), GOLD, SILVER);
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("maximum"));

        // Balance unchanged
        assertAmountEquals(new BigDecimal("100000.00"), economy.getVirtualBalance(player, GOLD));
    }

    // -------------------------------------------------------------------------
    // Daily limit
    // -------------------------------------------------------------------------

    @Test
    void exchange_DailyLimit_BlocksAfterReached() {
        var config = new ExchangeServiceImpl.ExchangeConfig(0.0, 0, 0, 5000);
        var exchangeService = new ExchangeServiceImpl(economy, db, registry, config);

        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("10000.00"), GOLD);

        // First conversion: 3000 (within daily limit of 5000)
        var result1 = exchangeService.convert(player, new BigDecimal("3000.00"), GOLD, SILVER);
        assertTrue(result1.successful());

        // Second conversion: 3000 (would exceed daily limit: 3000 + 3000 = 6000 > 5000)
        var result2 = exchangeService.convert(player, new BigDecimal("3000.00"), GOLD, SILVER);
        assertFalse(result2.successful());
        assertTrue(result2.errorMessage().contains("Daily exchange limit"));

        // Only first conversion went through
        assertAmountEquals(new BigDecimal("7000.00"), economy.getVirtualBalance(player, GOLD));
        assertAmountEquals(new BigDecimal("30000.00"), economy.getVirtualBalance(player, SILVER));
    }

    // -------------------------------------------------------------------------
    // Non-exchangeable currency
    // -------------------------------------------------------------------------

    @Test
    void exchange_NonExchangeableCurrency_Rejected() {
        Currency copper = Currency.builder("copper", "Copper", "C").decimals(2).build();
        registry.register(copper);

        var config = new ExchangeServiceImpl.ExchangeConfig(0.0, 0, 0, 0);
        var exchangeService = new ExchangeServiceImpl(economy, db, registry, config);

        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100.00"), copper);

        var result = exchangeService.convert(player, new BigDecimal("50.00"), copper, SILVER);
        assertFalse(result.successful());
        assertTrue(result.errorMessage().contains("not exchangeable"));

        // Balance unchanged
        assertAmountEquals(new BigDecimal("100.00"), economy.getVirtualBalance(player, copper));
    }

    // -------------------------------------------------------------------------
    // Manual rate overrides cross-rate
    // -------------------------------------------------------------------------

    @Test
    void exchange_ManualRateOverridesCrossRate() {
        var config = new ExchangeServiceImpl.ExchangeConfig(2.0, 0, 0, 0);
        var exchangeService = new ExchangeServiceImpl(economy, db, registry, config);

        // Register a manual rate: 1 gold = 5 silver (instead of cross-rate 10), 0% fee
        exchangeService.registerRate(new ExchangeRate(GOLD, SILVER, new BigDecimal("5"), BigDecimal.ZERO));

        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100.00"), GOLD);

        var result = exchangeService.convert(player, new BigDecimal("100.00"), GOLD, SILVER);
        assertTrue(result.successful());

        // Manual rate: 100 * 5 = 500 silver (not 100 * 10 = 1000 from cross-rate)
        // Manual rate has 0% fee (overrides global 2% fee which only applies to cross-rates)
        assertAmountEquals(new BigDecimal("500.00"), economy.getVirtualBalance(player, SILVER));
    }
}

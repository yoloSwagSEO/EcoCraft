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

class ExchangeServiceImplTest {

    private SqliteDatabaseProvider db;
    private EconomyProviderImpl economy;
    private CurrencyRegistryImpl registry;
    private ExchangeServiceImpl exchangeService;
    private Path tempDir;

    private static final Currency GOLD = Currency.builder("gold", "Gold", "\u26C1")
        .decimals(2).exchangeable(true).referenceRate(1.0).build();
    private static final Currency SILVER = Currency.builder("silver", "Silver", "\u26C0")
        .decimals(2).exchangeable(true).referenceRate(0.1).build();

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecocraft-exchange-test");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
        registry = new CurrencyRegistryImpl();
        registry.register(GOLD);
        registry.register(SILVER);
        economy = new EconomyProviderImpl(db, registry);
        exchangeService = new ExchangeServiceImpl(economy, db, registry);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    private static void assertAmountEquals(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual),
            () -> "Expected " + expected + " but got " + actual);
    }

    @Test
    void successfulConversion() {
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100.00"), GOLD);

        // 1 gold = 10 silver, no fee
        exchangeService.registerRate(new ExchangeRate(GOLD, SILVER, new BigDecimal("10"), BigDecimal.ZERO));

        var result = exchangeService.convert(player, new BigDecimal("50.00"), GOLD, SILVER);
        assertTrue(result.successful());
        assertAmountEquals(new BigDecimal("50.00"), economy.getVirtualBalance(player, GOLD));
        assertAmountEquals(new BigDecimal("500.00"), economy.getVirtualBalance(player, SILVER));
    }

    @Test
    void failsWhenInsufficientFunds() {
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("10.00"), GOLD);

        exchangeService.registerRate(new ExchangeRate(GOLD, SILVER, new BigDecimal("10"), BigDecimal.ZERO));

        var result = exchangeService.convert(player, new BigDecimal("50.00"), GOLD, SILVER);
        assertFalse(result.successful());
        // Balance should be unchanged
        assertAmountEquals(new BigDecimal("10.00"), economy.getVirtualBalance(player, GOLD));
        assertAmountEquals(BigDecimal.ZERO, economy.getVirtualBalance(player, SILVER));
    }

    @Test
    void failsWhenCurrencyNotExchangeable() {
        Currency copper = Currency.builder("copper", "Copper", "C").decimals(2).build();
        registry.register(copper);
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100.00"), copper);

        // Copper is not exchangeable — should fail
        var result = exchangeService.convert(player, new BigDecimal("50.00"), copper, SILVER);
        assertFalse(result.successful());
        // Balance should be unchanged
        assertAmountEquals(new BigDecimal("100.00"), economy.getVirtualBalance(player, copper));
    }

    @Test
    void conversionWithFee() {
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100.00"), GOLD);

        // 1 gold = 10 silver, 10% fee
        exchangeService.registerRate(new ExchangeRate(GOLD, SILVER, new BigDecimal("10"), new BigDecimal("0.10")));

        var result = exchangeService.convert(player, new BigDecimal("50.00"), GOLD, SILVER);
        assertTrue(result.successful());
        assertAmountEquals(new BigDecimal("50.00"), economy.getVirtualBalance(player, GOLD));
        // 50 * 10 = 500, minus 10% fee (50) = 450
        assertAmountEquals(new BigDecimal("450.00"), economy.getVirtualBalance(player, SILVER));
    }
}

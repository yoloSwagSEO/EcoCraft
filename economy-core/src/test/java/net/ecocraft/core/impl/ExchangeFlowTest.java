package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyFormatter;
import net.ecocraft.api.exchange.ExchangeRate;
import net.ecocraft.core.storage.SqliteDatabaseProvider;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end financial flow tests for the exchange system.
 * Uses real SQLite database and production service implementations.
 */
class ExchangeFlowTest {

    private SqliteDatabaseProvider db;
    private EconomyProviderImpl economy;
    private CurrencyRegistryImpl registry;
    private ExchangeServiceImpl exchangeService;
    private Path tempDir;

    private static final Currency GOLD = Currency.builder("gold", "Gold", "G")
        .decimals(2).exchangeable(true).referenceRate(1.0).build();
    private static final Currency GEMS = Currency.builder("gems", "Gems", "\u2666")
        .decimals(0).exchangeable(true).referenceRate(5.0).build();
    private static final Currency TOKENS = Currency.builder("tokens", "Tokens", "T")
        .decimals(2).exchangeable(true).referenceRate(2.0).build();

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecocraft-exchange-flow-test");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
        registry = new CurrencyRegistryImpl();
        registry.register(GOLD);
        registry.register(GEMS);
        registry.register(TOKENS);
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
    void fullExchangeFlow_GoldToGems() {
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100.00"), GOLD);
        assertAmountEquals(new BigDecimal("100.00"), economy.getVirtualBalance(player, GOLD));

        var result = exchangeService.convert(player, new BigDecimal("50.00"), GOLD, GEMS);
        assertTrue(result.successful(), "Exchange should succeed");

        assertAmountEquals(new BigDecimal("50.00"), economy.getVirtualBalance(player, GOLD));

        BigDecimal gemsBalance = economy.getVirtualBalance(player, GEMS);
        assertTrue(gemsBalance.compareTo(BigDecimal.ZERO) > 0,
            "Should have received some gems, got: " + gemsBalance);
    }

    @Test
    void exchangeRoundTrip_ShouldLoseToFees() {
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100.00"), GOLD);
        BigDecimal originalGold = economy.getVirtualBalance(player, GOLD);

        var toGemsResult = exchangeService.convert(player, new BigDecimal("100.00"), GOLD, GEMS);
        assertTrue(toGemsResult.successful(), "Gold to Gems should succeed");

        BigDecimal gemsAfterFirst = economy.getVirtualBalance(player, GEMS);
        assertTrue(gemsAfterFirst.compareTo(BigDecimal.ZERO) > 0,
            "Should have gems after first exchange");

        var toGoldResult = exchangeService.convert(player, gemsAfterFirst, GEMS, GOLD);
        assertTrue(toGoldResult.successful(), "Gems to Gold should succeed");

        BigDecimal finalGold = economy.getVirtualBalance(player, GOLD);
        assertTrue(finalGold.compareTo(originalGold) < 0,
            "Round-trip should lose to fees. Original: " + originalGold + ", final: " + finalGold);
    }

    @Test
    void exchangeInsufficientFunds() {
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("10.00"), GOLD);

        var result = exchangeService.convert(player, new BigDecimal("50.00"), GOLD, GEMS);
        assertFalse(result.successful(), "Should fail with insufficient funds");

        assertAmountEquals(new BigDecimal("10.00"), economy.getVirtualBalance(player, GOLD));
        assertAmountEquals(BigDecimal.ZERO, economy.getVirtualBalance(player, GEMS));
    }

    @Test
    void exchangeNonExchangeableCurrency() {
        Currency copper = Currency.builder("copper", "Copper", "C")
            .decimals(2).exchangeable(false).build();
        registry.register(copper);

        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100.00"), copper);

        var result = exchangeService.convert(player, new BigDecimal("50.00"), copper, GOLD);
        assertFalse(result.successful(), "Non-exchangeable currency should fail");
        assertTrue(result.errorMessage().contains("not exchangeable"),
            "Error should mention non-exchangeable: " + result.errorMessage());

        assertAmountEquals(new BigDecimal("100.00"), economy.getVirtualBalance(player, copper));
    }

    @Test
    void decimalFormatConsistency() {
        String formatted = CurrencyFormatter.format(15050, GOLD);
        assertEquals("150.50 G", formatted,
            "CurrencyFormatter should format 15050 smallest units as '150.50 G'");

        BigDecimal display = CurrencyFormatter.fromSmallestUnit(15050, GOLD);
        assertEquals(new BigDecimal("150.50"), display);
        assertEquals(15050L, CurrencyFormatter.toSmallestUnit(display, GOLD));
    }

    @Test
    void crossRateCalculation() {
        ExchangeRate rate = exchangeService.getRate(GEMS, TOKENS);
        assertNotNull(rate, "Cross-rate GEMS to TOKENS should be computed");

        BigDecimal expectedRate = GEMS.referenceRate().divide(TOKENS.referenceRate(), 10, RoundingMode.HALF_UP);
        assertEquals(0, expectedRate.compareTo(rate.rate()),
            "Cross rate should be 5.0/2.0=2.5, got: " + rate.rate());

        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("10"), GEMS);

        var result = exchangeService.convert(player, new BigDecimal("10"), GEMS, TOKENS);
        assertTrue(result.successful());

        BigDecimal tokensBalance = economy.getVirtualBalance(player, TOKENS);
        assertAmountEquals(new BigDecimal("24.50"), tokensBalance);
    }
}

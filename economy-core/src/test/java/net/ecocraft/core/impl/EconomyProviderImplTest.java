package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.core.storage.SqliteDatabaseProvider;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EconomyProviderImplTest {

    private SqliteDatabaseProvider db;
    private EconomyProviderImpl economy;
    private CurrencyRegistryImpl registry;
    private static final Currency GOLD = Currency.virtual("gold", "Gold", "\u26C1", 2);
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecocraft-test");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
        registry = new CurrencyRegistryImpl();
        registry.register(GOLD);
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

    @Test
    void newPlayerHasZeroBalance() {
        var player = UUID.randomUUID();
        assertAmountEquals(BigDecimal.ZERO, economy.getBalance(player, GOLD));
    }

    @Test
    void depositIncreasesBalance() {
        var player = UUID.randomUUID();
        var result = economy.deposit(player, new BigDecimal("100"), GOLD);
        assertTrue(result.successful());
        assertAmountEquals(new BigDecimal("100"), economy.getBalance(player, GOLD));
    }

    @Test
    void withdrawDecreasesBalance() {
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100"), GOLD);
        var result = economy.withdraw(player, new BigDecimal("30"), GOLD);
        assertTrue(result.successful());
        assertAmountEquals(new BigDecimal("70.00"), economy.getBalance(player, GOLD));
    }

    @Test
    void withdrawFailsIfInsufficientFunds() {
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("10"), GOLD);
        var result = economy.withdraw(player, new BigDecimal("50"), GOLD);
        assertFalse(result.successful());
        assertEquals("Insufficient funds", result.errorMessage());
        assertAmountEquals(new BigDecimal("10"), economy.getBalance(player, GOLD));
    }

    @Test
    void transferMovesMoney() {
        var p1 = UUID.randomUUID();
        var p2 = UUID.randomUUID();
        economy.deposit(p1, new BigDecimal("100"), GOLD);

        var result = economy.transfer(p1, p2, new BigDecimal("40"), GOLD);
        assertTrue(result.successful());
        assertAmountEquals(new BigDecimal("60.00"), economy.getBalance(p1, GOLD));
        assertAmountEquals(new BigDecimal("40"), economy.getBalance(p2, GOLD));
    }

    @Test
    void transferFailsIfSenderCantAfford() {
        var p1 = UUID.randomUUID();
        var p2 = UUID.randomUUID();
        economy.deposit(p1, new BigDecimal("10"), GOLD);

        var result = economy.transfer(p1, p2, new BigDecimal("50"), GOLD);
        assertFalse(result.successful());
    }

    @Test
    void canAffordChecksBalance() {
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100"), GOLD);
        assertTrue(economy.canAfford(player, new BigDecimal("100"), GOLD));
        assertFalse(economy.canAfford(player, new BigDecimal("101"), GOLD));
    }
}

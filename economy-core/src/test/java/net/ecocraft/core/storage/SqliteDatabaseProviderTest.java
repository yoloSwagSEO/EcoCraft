package net.ecocraft.core.storage;

import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqliteDatabaseProviderTest {

    private SqliteDatabaseProvider db;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecocraft-test");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void getBalanceReturnsZeroForNewPlayer() {
        var player = UUID.randomUUID();
        assertEquals(BigDecimal.ZERO, db.getVirtualBalance(player, "gold"));
    }

    @Test
    void setAndGetBalance() {
        var player = UUID.randomUUID();
        db.setVirtualBalance(player, "gold", new BigDecimal("150.50"));
        assertEquals(new BigDecimal("150.50"), db.getVirtualBalance(player, "gold"));
    }

    @Test
    void multiplePlayersIndependent() {
        var p1 = UUID.randomUUID();
        var p2 = UUID.randomUUID();
        db.setVirtualBalance(p1, "gold", new BigDecimal("100"));
        db.setVirtualBalance(p2, "gold", new BigDecimal("200"));

        assertEquals(new BigDecimal("100"), db.getVirtualBalance(p1, "gold"));
        assertEquals(new BigDecimal("200"), db.getVirtualBalance(p2, "gold"));
    }

    @Test
    void multipleCurrenciesIndependent() {
        var player = UUID.randomUUID();
        db.setVirtualBalance(player, "gold", new BigDecimal("100"));
        db.setVirtualBalance(player, "silver", new BigDecimal("5000"));

        assertEquals(new BigDecimal("100"), db.getVirtualBalance(player, "gold"));
        assertEquals(new BigDecimal("5000"), db.getVirtualBalance(player, "silver"));
    }

    @Test
    void logAndQueryTransactions() {
        var p1 = UUID.randomUUID();
        var p2 = UUID.randomUUID();
        var txId = UUID.randomUUID();

        db.logTransaction(txId, p1, p2, new BigDecimal("50"), "gold", "PAYMENT", Instant.now());

        var history = db.getTransactionHistory(p1, null, null, null, 0, 10);
        assertEquals(1, history.size());
        assertEquals(new BigDecimal("50"), history.get(0).amount());
        assertEquals("PAYMENT", history.get(0).type());
    }

    @Test
    void transactionCountForPagination() {
        var player = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            db.logTransaction(UUID.randomUUID(), player, null,
                new BigDecimal("10"), "gold", "PAYMENT", Instant.now());
        }

        long count = db.getTransactionCount(player, null, null, null);
        assertEquals(5, count);
    }

    // C3: getAllBalances numeric sort

    @Test
    void getAllBalancesReturnedInNumericDescOrder() {
        var p1 = UUID.randomUUID();
        var p2 = UUID.randomUUID();
        var p3 = UUID.randomUUID();
        var p4 = UUID.randomUUID();

        db.setVirtualBalance(p1, "gold", new BigDecimal("9"));
        db.setVirtualBalance(p2, "gold", new BigDecimal("80"));
        db.setVirtualBalance(p3, "gold", new BigDecimal("200"));
        db.setVirtualBalance(p4, "gold", new BigDecimal("1000"));

        var balances = db.getAllBalances("gold");
        assertEquals(4, balances.size());
        assertEquals(new BigDecimal("1000"), balances.get(0).amount());
        assertEquals(new BigDecimal("200"), balances.get(1).amount());
        assertEquals(new BigDecimal("80"), balances.get(2).amount());
        assertEquals(new BigDecimal("9"), balances.get(3).amount());
    }

    // C4: hasAccount / starting balance

    @Test
    void hasAccountReturnsFalseForUnknownPlayer() {
        var player = UUID.randomUUID();
        assertFalse(db.hasAccount(player, "gold"));
    }

    @Test
    void hasAccountReturnsTrueAfterDeposit() {
        var player = UUID.randomUUID();
        db.setVirtualBalance(player, "gold", new BigDecimal("100"));
        assertTrue(db.hasAccount(player, "gold"));
    }

    @Test
    void hasAccountReturnsTrueEvenWhenBalanceIsZero() {
        var player = UUID.randomUUID();
        // Player gets a balance, then it goes to zero
        db.setVirtualBalance(player, "gold", new BigDecimal("100"));
        db.setVirtualBalance(player, "gold", BigDecimal.ZERO);

        // Account row still exists, so hasAccount must be true
        // This ensures starting balance is NOT given again
        assertTrue(db.hasAccount(player, "gold"));
    }
}

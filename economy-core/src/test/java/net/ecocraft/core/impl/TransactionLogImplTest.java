package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.transaction.TransactionFilter;
import net.ecocraft.api.transaction.TransactionType;
import net.ecocraft.core.storage.SqliteDatabaseProvider;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionLogImplTest {

    private SqliteDatabaseProvider db;
    private CurrencyRegistryImpl registry;
    private TransactionLogImpl transactionLog;
    private static final Currency GOLD = Currency.virtual("gold", "Gold", "\u26C1", 2);
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecocraft-txlog-test");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
        registry = new CurrencyRegistryImpl();
        registry.register(GOLD);
        transactionLog = new TransactionLogImpl(db, registry);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void logAndRetrieveTransaction() {
        var player = UUID.randomUUID();
        var txId = UUID.randomUUID();
        var now = Instant.now();

        db.logTransaction(txId, null, player, new BigDecimal("50.00"), "gold",
                TransactionType.DEPOSIT.name(), now);

        var filter = new TransactionFilter(player, null, null, null, 0, 10);
        var page = transactionLog.getHistory(filter);

        assertEquals(1, page.totalCount());
        assertEquals(1, page.items().size());
        var tx = page.items().getFirst();
        assertEquals(txId, tx.id());
        assertNull(tx.from());
        assertEquals(player, tx.to());
        assertEquals(0, new BigDecimal("50.00").compareTo(tx.amount()));
        assertEquals(GOLD.id(), tx.currency().id());
        assertEquals(TransactionType.DEPOSIT, tx.type());
    }

    @Test
    void fallbackCurrencyWhenCurrencyDeleted() {
        var player = UUID.randomUUID();
        var txId = UUID.randomUUID();
        var now = Instant.now();

        // Log a transaction with a currency that is NOT registered
        db.logTransaction(txId, null, player, new BigDecimal("25.00"), "silver",
                TransactionType.DEPOSIT.name(), now);

        var filter = new TransactionFilter(player, null, null, null, 0, 10);
        var page = transactionLog.getHistory(filter);

        assertEquals(1, page.items().size());
        var tx = page.items().getFirst();
        // Fallback currency should have the currencyId as both id and name, "?" as symbol
        assertEquals("silver", tx.currency().id());
        assertEquals("silver", tx.currency().name());
        assertEquals("?", tx.currency().symbol());
    }

    @Test
    void paginationOffsetAndLimit() {
        var player = UUID.randomUUID();

        // Insert 5 transactions
        for (int i = 0; i < 5; i++) {
            db.logTransaction(UUID.randomUUID(), null, player, new BigDecimal("10.00"), "gold",
                    TransactionType.DEPOSIT.name(), Instant.now().plusMillis(i));
        }

        // Get first page (2 items)
        var page1 = transactionLog.getHistory(new TransactionFilter(player, null, null, null, 0, 2));
        assertEquals(5, page1.totalCount());
        assertEquals(2, page1.items().size());
        assertTrue(page1.hasNext());

        // Get second page
        var page2 = transactionLog.getHistory(new TransactionFilter(player, null, null, null, 2, 2));
        assertEquals(5, page2.totalCount());
        assertEquals(2, page2.items().size());
        assertTrue(page2.hasNext());

        // Get third page
        var page3 = transactionLog.getHistory(new TransactionFilter(player, null, null, null, 4, 2));
        assertEquals(5, page3.totalCount());
        assertEquals(1, page3.items().size());
        assertFalse(page3.hasNext());
    }

    @Test
    void filterByType() {
        var player = UUID.randomUUID();

        db.logTransaction(UUID.randomUUID(), null, player, new BigDecimal("100.00"), "gold",
                TransactionType.DEPOSIT.name(), Instant.now());
        db.logTransaction(UUID.randomUUID(), player, null, new BigDecimal("30.00"), "gold",
                TransactionType.WITHDRAWAL.name(), Instant.now().plusMillis(1));
        db.logTransaction(UUID.randomUUID(), null, player, new BigDecimal("50.00"), "gold",
                TransactionType.DEPOSIT.name(), Instant.now().plusMillis(2));

        var depositsOnly = transactionLog.getHistory(
                new TransactionFilter(player, TransactionType.DEPOSIT, null, null, 0, 10));
        assertEquals(2, depositsOnly.totalCount(), "Should have 2 deposit transactions");
        assertEquals(2, depositsOnly.items().size());
        for (var item : depositsOnly.items()) {
            assertEquals(TransactionType.DEPOSIT, item.type(),
                    "All filtered items should be DEPOSIT, got " + item.type().name());
        }

        var withdrawalsOnly = transactionLog.getHistory(
                new TransactionFilter(player, TransactionType.WITHDRAWAL, null, null, 0, 10));
        assertEquals(1, withdrawalsOnly.totalCount());
        assertEquals(TransactionType.WITHDRAWAL, withdrawalsOnly.items().getFirst().type());
    }
}

package net.ecocraft.core.storage;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeRatePersistenceTest {

    private SqliteDatabaseProvider db;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecocraft-exchange-test");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void saveAndLoadExchangeRate() {
        db.saveExchangeRate("gold", "silver", new BigDecimal("10.5"), new BigDecimal("0.05"));

        var rate = db.getExchangeRate("gold", "silver");
        assertNotNull(rate);
        assertEquals("gold", rate.fromCurrency());
        assertEquals("silver", rate.toCurrency());
        assertEquals(new BigDecimal("10.5"), rate.rate());
        assertEquals(new BigDecimal("0.05"), rate.feeRate());
    }

    @Test
    void updateExchangeRate() {
        db.saveExchangeRate("gold", "silver", new BigDecimal("10"), new BigDecimal("0.05"));
        db.saveExchangeRate("gold", "silver", new BigDecimal("12"), new BigDecimal("0.03"));

        var rate = db.getExchangeRate("gold", "silver");
        assertNotNull(rate);
        assertEquals(new BigDecimal("12"), rate.rate());
        assertEquals(new BigDecimal("0.03"), rate.feeRate());
    }

    @Test
    void deleteExchangeRate() {
        db.saveExchangeRate("gold", "silver", new BigDecimal("10"), new BigDecimal("0.05"));
        db.deleteExchangeRate("gold", "silver");

        assertNull(db.getExchangeRate("gold", "silver"));
    }

    @Test
    void getAllExchangeRates() {
        db.saveExchangeRate("gold", "silver", new BigDecimal("10"), new BigDecimal("0.05"));
        db.saveExchangeRate("silver", "gold", new BigDecimal("0.1"), new BigDecimal("0.02"));
        db.saveExchangeRate("gold", "copper", new BigDecimal("100"), new BigDecimal("0"));

        var rates = db.getAllExchangeRates();
        assertEquals(3, rates.size());
    }

    @Test
    void getNonExistentRateReturnsNull() {
        assertNull(db.getExchangeRate("gold", "diamond"));
    }

    @Test
    void dailyExchangeTrackingSumsUp() {
        String playerUuid = "550e8400-e29b-41d4-a716-446655440000";

        db.recordDailyExchange(playerUuid, "gold", "silver", 500);
        assertEquals(500, db.getDailyExchangeTotal(playerUuid, "gold", "silver"));

        db.recordDailyExchange(playerUuid, "gold", "silver", 300);
        assertEquals(800, db.getDailyExchangeTotal(playerUuid, "gold", "silver"));
    }

    @Test
    void dailyExchangeTotalZeroForNoRecords() {
        assertEquals(0, db.getDailyExchangeTotal("unknown-player", "gold", "silver"));
    }

    @Test
    void dailyExchangeIndependentPerCurrencyPair() {
        String playerUuid = "550e8400-e29b-41d4-a716-446655440000";

        db.recordDailyExchange(playerUuid, "gold", "silver", 500);
        db.recordDailyExchange(playerUuid, "gold", "copper", 200);

        assertEquals(500, db.getDailyExchangeTotal(playerUuid, "gold", "silver"));
        assertEquals(200, db.getDailyExchangeTotal(playerUuid, "gold", "copper"));
    }
}

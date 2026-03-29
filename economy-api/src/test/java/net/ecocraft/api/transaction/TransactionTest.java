package net.ecocraft.api.transaction;

import net.ecocraft.api.currency.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private static final Currency GOLD = Currency.virtual("gold", "Or", "⛁", 2);

    @Test
    void transactionStoresAllFields() {
        var id = UUID.randomUUID();
        var from = UUID.randomUUID();
        var to = UUID.randomUUID();
        var now = Instant.now();

        var tx = new Transaction(id, from, to, new BigDecimal("50.00"), GOLD, TransactionType.PAYMENT, now);

        assertEquals(id, tx.id());
        assertEquals(from, tx.from());
        assertEquals(to, tx.to());
        assertEquals(new BigDecimal("50.00"), tx.amount());
        assertEquals(GOLD, tx.currency());
        assertEquals(TransactionType.PAYMENT, tx.type());
        assertEquals(now, tx.timestamp());
    }

    @Test
    void amountMustBePositive() {
        assertThrows(IllegalArgumentException.class, () ->
            new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.ZERO, GOLD, TransactionType.PAYMENT, Instant.now())
        );
    }

    @Test
    void successResultCarriesTransaction() {
        var tx = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("10"), GOLD, TransactionType.PAYMENT, Instant.now());
        var result = TransactionResult.success(tx);

        assertTrue(result.successful());
        assertEquals(tx, result.transaction());
        assertNull(result.errorMessage());
    }

    @Test
    void failureResultCarriesMessage() {
        var result = TransactionResult.failure("Insufficient funds");

        assertFalse(result.successful());
        assertNull(result.transaction());
        assertEquals("Insufficient funds", result.errorMessage());
    }

    @Test
    void filterBuilderSetsFields() {
        var player = UUID.randomUUID();
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-03-01T00:00:00Z");

        var hdvSale = TransactionType.of("HDV_SALE");
        var filter = new TransactionFilter(player, hdvSale, from, to, 0, 20);

        assertEquals(player, filter.player());
        assertEquals(hdvSale, filter.type());
        assertEquals(0, filter.offset());
        assertEquals(20, filter.limit());
    }
}

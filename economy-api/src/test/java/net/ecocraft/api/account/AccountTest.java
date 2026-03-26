package net.ecocraft.api.account;

import net.ecocraft.api.currency.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    private static final Currency GOLD = Currency.virtual("gold", "Or", "⛁", 2);
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void totalBalanceIsSumOfVirtualAndVault() {
        var account = new Account(PLAYER, GOLD, new BigDecimal("100.50"), new BigDecimal("49.50"));
        assertEquals(new BigDecimal("150.00"), account.totalBalance());
    }

    @Test
    void newAccountStartsAtZero() {
        var account = Account.empty(PLAYER, GOLD);
        assertEquals(BigDecimal.ZERO, account.virtualBalance());
        assertEquals(BigDecimal.ZERO, account.vaultBalance());
        assertEquals(BigDecimal.ZERO, account.totalBalance());
    }

    @Test
    void balancesCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            new Account(PLAYER, GOLD, new BigDecimal("-1"), BigDecimal.ZERO)
        );
        assertThrows(IllegalArgumentException.class, () ->
            new Account(PLAYER, GOLD, BigDecimal.ZERO, new BigDecimal("-1"))
        );
    }
}

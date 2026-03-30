package net.ecocraft.core.network;

import net.ecocraft.core.network.payload.*;
import net.ecocraft.core.network.payload.VaultDataPayload.VaultCurrencyData;
import net.ecocraft.core.network.payload.VaultDataPayload.VaultSubUnitData;
import net.ecocraft.core.network.payload.ExchangeDataPayload.CurrencyData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EcoPayloadRoundTripTest {

    @Test
    void exchangeDataPayload() {
        var currency1 = new CurrencyData("gold", "Gold", "G", 2, 10000L, 1.0, true);
        var currency2 = new CurrencyData("silver", "Silver", "S", 0, 500L, 0.01, false);
        var original = new ExchangeDataPayload(List.of(currency1, currency2), 2.5);

        var decoded = PayloadTestHelper.roundTrip(original, ExchangeDataPayload.STREAM_CODEC);

        assertEquals(2, decoded.currencies().size());
        assertEquals(original.feePercent(), decoded.feePercent());
        var c1 = decoded.currencies().get(0);
        assertEquals("gold", c1.id());
        assertEquals("Gold", c1.name());
        assertEquals("G", c1.symbol());
        assertEquals(2, c1.decimals());
        assertEquals(10000L, c1.balance());
        assertEquals(1.0, c1.referenceRate());
        assertTrue(c1.exchangeable());
        assertFalse(decoded.currencies().get(1).exchangeable());
    }

    @Test
    void exchangeRequestPayload() {
        var original = new ExchangeRequestPayload(5000L, "gold", "silver");
        assertEquals(original, PayloadTestHelper.roundTrip(original, ExchangeRequestPayload.STREAM_CODEC));
    }

    @Test
    void exchangeResultPayload() {
        var original = new ExchangeResultPayload(true, "Conversion successful", 4500L);
        assertEquals(original, PayloadTestHelper.roundTrip(original, ExchangeResultPayload.STREAM_CODEC));
    }

    @Test
    void exchangerSkinPayload() {
        var original = new ExchangerSkinPayload(42, "Steve");
        assertEquals(original, PayloadTestHelper.roundTrip(original, ExchangerSkinPayload.STREAM_CODEC));
    }

    @Test
    void updateExchangerSkinPayload() {
        var original = new UpdateExchangerSkinPayload(99, "Alex");
        assertEquals(original, PayloadTestHelper.roundTrip(original, UpdateExchangerSkinPayload.STREAM_CODEC));
    }

    @Test
    void openExchangePayload() {
        var original = new OpenExchangePayload(7);
        assertEquals(original, PayloadTestHelper.roundTrip(original, OpenExchangePayload.STREAM_CODEC));
    }

    @Test
    void openVaultPayload() {
        var decoded = PayloadTestHelper.roundTrip(new OpenVaultPayload(), OpenVaultPayload.STREAM_CODEC);
        assertNotNull(decoded);
    }

    @Test
    void vaultDataPayload() {
        var subUnit1 = new VaultSubUnitData("gp", "Gold Piece", 100L, "minecraft:gold_ingot");
        var subUnit2 = new VaultSubUnitData("sp", "Silver Piece", 10L, "minecraft:iron_ingot");
        var currency = new VaultCurrencyData("gold", "Gold", "G", 2, 50000L, true, List.of(subUnit1, subUnit2));
        var original = new VaultDataPayload(List.of(currency));

        var decoded = PayloadTestHelper.roundTrip(original, VaultDataPayload.STREAM_CODEC);

        assertEquals(1, decoded.currencies().size());
        var c = decoded.currencies().get(0);
        assertEquals("gold", c.id());
        assertEquals(50000L, c.balance());
        assertTrue(c.physical());
        assertEquals(2, c.subUnits().size());
        assertEquals("gp", c.subUnits().get(0).code());
        assertEquals(100L, c.subUnits().get(0).multiplier());
    }

    @Test
    void vaultDepositPayload() {
        var original = new VaultDepositPayload(5, 64);
        assertEquals(original, PayloadTestHelper.roundTrip(original, VaultDepositPayload.STREAM_CODEC));
    }

    @Test
    void vaultWithdrawPayload() {
        var original = new VaultWithdrawPayload("gold", 2500L);
        assertEquals(original, PayloadTestHelper.roundTrip(original, VaultWithdrawPayload.STREAM_CODEC));
    }

    @Test
    void vaultResultPayload() {
        var original = new VaultResultPayload(false, "Insufficient balance");
        assertEquals(original, PayloadTestHelper.roundTrip(original, VaultResultPayload.STREAM_CODEC));
    }
}

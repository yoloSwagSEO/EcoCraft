package net.ecocraft.ah.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemFingerprintTest {

    @Test
    void plainItemIdIsValidFingerprint() {
        String fingerprint = "minecraft:diamond_sword";
        assertTrue(fingerprint.startsWith("minecraft:"));
        assertFalse(fingerprint.contains("|"));
    }

    @Test
    void fingerprintWithEnchantmentsFormat() {
        String fingerprint = "minecraft:diamond_sword|e:minecraft:fire_aspect:2,minecraft:sharpness:5";
        String[] parts = fingerprint.split("\\|");
        assertEquals(2, parts.length);
        assertEquals("minecraft:diamond_sword", parts[0]);
        assertTrue(parts[1].startsWith("e:"));
    }

    @Test
    void fingerprintWithStoredEnchantmentsFormat() {
        String fingerprint = "minecraft:enchanted_book|se:minecraft:mending:1";
        String[] parts = fingerprint.split("\\|");
        assertEquals(2, parts.length);
        assertTrue(parts[1].startsWith("se:"));
    }

    @Test
    void fingerprintWithPotionFormat() {
        String fingerprint = "minecraft:potion|p:minecraft:strength:1:3600";
        String[] parts = fingerprint.split("\\|");
        assertEquals(2, parts.length);
        assertTrue(parts[1].startsWith("p:"));
    }

    @Test
    void fingerprintWithCustomNameFormat() {
        String fingerprint = "minecraft:diamond_sword|n:Excalibur|e:minecraft:sharpness:5";
        assertTrue(fingerprint.contains("|n:Excalibur"));
    }
}

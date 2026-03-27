package net.ecocraft.ah.data;

/**
 * Represents a single enchantment on an item, used for indexing enchantments in the auction house.
 *
 * @param name        Registry name of the enchantment (e.g. "minecraft:sharpness")
 * @param level       Enchantment level
 * @param displayName Human-readable display name (e.g. "Sharpness V")
 */
public record EnchantmentEntry(String name, int level, String displayName) {}

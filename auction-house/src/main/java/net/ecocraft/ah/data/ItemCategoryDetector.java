package net.ecocraft.ah.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

/**
 * Auto-detects the {@link ItemCategory} of a vanilla Minecraft item
 * based on its class hierarchy and item properties.
 */
public final class ItemCategoryDetector {

    private ItemCategoryDetector() {}

    /**
     * Returns the best-matching category for an item given its registry name
     * (e.g. {@code "minecraft:diamond_sword"}).
     *
     * <p>Creates a default ItemStack from the registry name and delegates to
     * {@link #detect(ItemStack)}. This allows dynamic category detection without
     * needing the original stack.</p>
     *
     * @param itemId the item registry name
     * @return the detected category, or {@link ItemCategory#MISC} if unknown
     */
    public static ItemCategory detectFromId(String itemId) {
        if (itemId == null || itemId.isEmpty()) return ItemCategory.MISC;
        try {
            var rl = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == Items.AIR) return ItemCategory.MISC;
            return detect(new ItemStack(item));
        } catch (Exception e) {
            return ItemCategory.MISC;
        }
    }

    /**
     * Returns the best-matching category for the given stack.
     * Falls back to {@link ItemCategory#MISC} when no specific category matches.
     */
    public static ItemCategory detect(ItemStack stack) {
        if (stack.isEmpty()) return ItemCategory.MISC;

        Item item = stack.getItem();

        // Weapons
        if (item instanceof SwordItem || item instanceof BowItem || item instanceof CrossbowItem
                || item instanceof TridentItem || item instanceof MaceItem) {
            return ItemCategory.WEAPONS;
        }

        // Armor
        if (item instanceof ArmorItem) {
            return ItemCategory.ARMOR;
        }

        // Tools (DiggerItem covers PickaxeItem, AxeItem, ShovelItem, HoeItem)
        if (item instanceof DiggerItem || item instanceof ShearsItem
                || item instanceof FishingRodItem || item instanceof FlintAndSteelItem) {
            return ItemCategory.TOOLS;
        }

        // Potions (covers regular, splash, and lingering)
        if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
            return ItemCategory.POTIONS;
        }

        // Enchanted books
        if (item instanceof EnchantedBookItem) {
            return ItemCategory.ENCHANTMENTS;
        }

        // Food — check the item's default food component
        if (item.components().has(net.minecraft.core.component.DataComponents.FOOD)) {
            return ItemCategory.FOOD;
        }

        // Blocks
        if (item instanceof BlockItem) {
            return ItemCategory.BLOCKS;
        }

        return ItemCategory.MISC;
    }
}

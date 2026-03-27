package net.ecocraft.ah.data;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Computes a deterministic fingerprint string from the significant components
 * of an ItemStack. Two items with the same fingerprint are considered
 * equivalent for pricing purposes.
 *
 * Included: itemId, enchantments, stored enchantments, potion effects, custom name.
 * Excluded: durability, count, repair cost.
 */
public final class ItemFingerprint {

    private ItemFingerprint() {}

    public static String compute(ItemStack stack) {
        if (stack.isEmpty()) return "";

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        List<String> parts = new ArrayList<>();
        parts.add(itemId);

        // Enchantments (on the item itself)
        ItemEnchantments enchants = stack.getEnchantments();
        String enchantStr = formatEnchantments(enchants);
        if (!enchantStr.isEmpty()) {
            parts.add("e:" + enchantStr);
        }

        // Stored enchantments (enchanted books)
        ItemEnchantments storedEnchants = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!storedEnchants.isEmpty()) {
            String storedStr = formatEnchantments(storedEnchants);
            if (!storedStr.isEmpty()) {
                parts.add("se:" + storedStr);
            }
        }

        // Potion effects
        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null) {
            List<String> effectParts = new ArrayList<>();
            for (MobEffectInstance effect : potionContents.getAllEffects()) {
                String effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString();
                effectParts.add(effectId + ":" + effect.getAmplifier() + ":" + effect.getDuration());
            }
            Collections.sort(effectParts);
            if (!effectParts.isEmpty()) {
                parts.add("p:" + String.join(",", effectParts));
            }
        }

        // Custom name
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            parts.add("n:" + customName.getString());
        }

        return String.join("|", parts);
    }

    public static String computeFromNbt(String itemId, String nbt, net.minecraft.core.RegistryAccess registries) {
        if (nbt == null || nbt.isEmpty()) return itemId;
        ItemStack stack = ItemStackSerializer.deserialize(nbt, registries);
        if (stack.isEmpty()) return itemId;
        return compute(stack);
    }

    private static String formatEnchantments(ItemEnchantments enchantments) {
        List<String> entries = new ArrayList<>();
        enchantments.entrySet().forEach(e -> {
            Holder<Enchantment> holder = e.getKey();
            int level = e.getIntValue();
            String name = holder.unwrapKey().map(k -> k.location().toString()).orElse("unknown");
            entries.add(name + ":" + level);
        });
        Collections.sort(entries);
        return String.join(",", entries);
    }
}

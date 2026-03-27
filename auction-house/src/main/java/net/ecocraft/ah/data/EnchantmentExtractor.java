package net.ecocraft.ah.data;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

public class EnchantmentExtractor {

    public static List<EnchantmentEntry> extract(ItemStack stack) {
        List<EnchantmentEntry> entries = new ArrayList<>();

        ItemEnchantments enchants = stack.getEnchantments();
        ItemEnchantments storedEnchants = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);

        ItemEnchantments toProcess = storedEnchants.isEmpty() ? enchants : storedEnchants;

        toProcess.entrySet().forEach(e -> {
            Holder<Enchantment> holder = e.getKey();
            int level = e.getIntValue();
            String displayName;
            try {
                displayName = Enchantment.getFullname(holder, level).getString();
            } catch (Exception ex) {
                displayName = holder.unwrapKey().map(k -> k.location().getPath()).orElse("unknown") + " " + level;
            }
            String enchantName = holder.unwrapKey().map(k -> k.location().toString()).orElse("unknown");
            entries.add(new EnchantmentEntry(enchantName, level, displayName));
        });

        return entries;
    }
}

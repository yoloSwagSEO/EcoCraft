package net.ecocraft.ah.data;

import com.mojang.logging.LogUtils;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Serializes and deserializes {@link ItemStack} instances to/from NBT strings,
 * preserving all DataComponents (enchantments, durability, custom names, etc.).
 *
 * <p>Uses the {@code ItemStack.OPTIONAL_CODEC} with the registry-aware serialization
 * context available in NeoForge 1.21.1.</p>
 */
public final class ItemStackSerializer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ItemStackSerializer() {}

    /**
     * Serializes an {@link ItemStack} to a string representation that preserves
     * all DataComponents (enchantments, durability, custom names, etc.).
     *
     * @param stack      the item stack to serialize
     * @param registries the registry access (from player or server level)
     * @return the serialized NBT string, or {@code null} if the stack is empty
     */
    public static String serialize(ItemStack stack, RegistryAccess registries) {
        if (stack.isEmpty()) return null;
        try {
            Tag tag = ItemStack.OPTIONAL_CODEC.encodeStart(
                    registries.createSerializationContext(NbtOps.INSTANCE), stack
            ).getOrThrow();
            return tag.toString();
        } catch (Exception e) {
            LOGGER.error("Failed to serialize ItemStack {}", stack, e);
            return null;
        }
    }

    /**
     * Deserializes an {@link ItemStack} from a previously serialized NBT string.
     *
     * @param nbt        the serialized NBT string (as produced by {@link #serialize})
     * @param registries the registry access (from player or server level)
     * @return the deserialized item stack, or {@link ItemStack#EMPTY} on failure
     */
    public static ItemStack deserialize(String nbt, RegistryAccess registries) {
        if (nbt == null || nbt.isEmpty()) return ItemStack.EMPTY;
        try {
            CompoundTag tag = TagParser.parseTag(nbt);
            return ItemStack.OPTIONAL_CODEC.parse(
                    registries.createSerializationContext(NbtOps.INSTANCE), tag
            ).getOrThrow();
        } catch (Exception e) {
            LOGGER.error("Failed to deserialize ItemStack from NBT: {}", nbt, e);
            return ItemStack.EMPTY;
        }
    }
}

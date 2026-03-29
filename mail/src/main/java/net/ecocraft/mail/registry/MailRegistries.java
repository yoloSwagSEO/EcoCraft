package net.ecocraft.mail.registry;

import net.ecocraft.mail.MailMod;
import net.ecocraft.mail.block.MailboxBlock;
import net.ecocraft.mail.block.MailboxBlockEntity;
import net.ecocraft.mail.entity.PostmanEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Registry holder for mail blocks, items, entities, and creative tab.
 */
public class MailRegistries {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MailMod.MOD_ID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MailMod.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MailMod.MOD_ID);

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MailMod.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MailMod.MOD_ID);

    // --- Blocks ---
    public static final DeferredBlock<Block> MAILBOX = BLOCKS.register(
            "mailbox",
            () -> new MailboxBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f, 3.0f)
                    .sound(SoundType.WOOD))
    );

    // --- Items ---
    public static final DeferredItem<BlockItem> MAILBOX_ITEM = ITEMS.register(
            "mailbox",
            () -> new BlockItem(MAILBOX.get(), new Item.Properties())
    );

    // --- Block Entities ---
    public static final Supplier<BlockEntityType<MailboxBlockEntity>> MAILBOX_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("mailbox_block_entity", () ->
                    BlockEntityType.Builder.of(MailboxBlockEntity::new, MAILBOX.get()).build(null)
            );

    // --- Entities ---
    public static final Supplier<EntityType<PostmanEntity>> POSTMAN = ENTITY_TYPES.register(
            "postman",
            () -> EntityType.Builder.<PostmanEntity>of(PostmanEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(80)
                    .build("postman")
    );

    public static final DeferredItem<SpawnEggItem> POSTMAN_SPAWN_EGG = ITEMS.register(
            "postman_spawn_egg",
            () -> new DeferredSpawnEggItem(POSTMAN, 0x3B5998, 0xF5D442, new Item.Properties())
    );

    // --- Creative Tab ---
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIL_TAB = CREATIVE_TABS.register(
            "mail_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ecocraft_mail.mail_tab"))
                    .icon(() -> MAILBOX_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(MAILBOX_ITEM.get());
                        output.accept(POSTMAN_SPAWN_EGG.get());
                    })
                    .build()
    );

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITY_TYPES.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
}

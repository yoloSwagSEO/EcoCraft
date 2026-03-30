package net.ecocraft.core.registry;

import net.ecocraft.core.EcoCraftCoreMod;
import net.ecocraft.core.exchange.ExchangeBlock;
import net.ecocraft.core.exchange.ExchangeBlockEntity;
import net.ecocraft.core.exchange.ExchangerEntity;
import net.ecocraft.core.vault.VaultBlock;
import net.ecocraft.core.vault.VaultBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class EcoRegistries {
    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(EcoCraftCoreMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(EcoCraftCoreMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EcoCraftCoreMod.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, EcoCraftCoreMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EcoCraftCoreMod.MOD_ID);

    public static final DeferredBlock<VaultBlock> VAULT_BLOCK =
        BLOCKS.register("vault_block", () -> new VaultBlock(
            BlockBehaviour.Properties.of().strength(3.0f).requiresCorrectToolForDrops()
        ));

    public static final DeferredItem<BlockItem> VAULT_BLOCK_ITEM =
        ITEMS.register("vault_block", () -> new BlockItem(
            VAULT_BLOCK.get(), new Item.Properties()
        ));

    public static final Supplier<BlockEntityType<VaultBlockEntity>> VAULT_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("vault_block_entity", () ->
            BlockEntityType.Builder.of(VaultBlockEntity::new, VAULT_BLOCK.get()).build(null)
        );

    // --- Exchange Block ---
    public static final DeferredBlock<ExchangeBlock> EXCHANGE_BLOCK =
        BLOCKS.register("exchange_block", () -> new ExchangeBlock(
            BlockBehaviour.Properties.of().strength(3.0f).requiresCorrectToolForDrops()
        ));

    public static final DeferredItem<BlockItem> EXCHANGE_BLOCK_ITEM =
        ITEMS.register("exchange_block", () -> new BlockItem(
            EXCHANGE_BLOCK.get(), new Item.Properties()
        ));

    public static final Supplier<BlockEntityType<ExchangeBlockEntity>> EXCHANGE_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("exchange_block_entity", () ->
            BlockEntityType.Builder.of(ExchangeBlockEntity::new, EXCHANGE_BLOCK.get()).build(null)
        );

    // --- Exchanger NPC ---
    public static final Supplier<EntityType<ExchangerEntity>> EXCHANGER = ENTITY_TYPES.register(
        "exchanger",
        () -> EntityType.Builder.<ExchangerEntity>of(ExchangerEntity::new, MobCategory.MISC)
                .sized(0.6f, 1.95f)
                .clientTrackingRange(80)
                .build("exchanger")
    );

    public static final DeferredItem<SpawnEggItem> EXCHANGER_SPAWN_EGG = ITEMS.register(
        "exchanger_spawn_egg",
        () -> new DeferredSpawnEggItem(EXCHANGER, 0xD4AF37, 0x228B22, new Item.Properties())
    );

    // --- Creative Tab ---
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ECO_TAB = CREATIVE_TABS.register(
        "economy_tab",
        () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.ecocraft_core"))
                .icon(() -> VAULT_BLOCK_ITEM.get().getDefaultInstance())
                .displayItems((params, output) -> {
                    output.accept(VAULT_BLOCK_ITEM.get());
                    output.accept(EXCHANGE_BLOCK_ITEM.get());
                    output.accept(EXCHANGER_SPAWN_EGG.get());
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

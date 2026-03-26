package net.ecocraft.core.registry;

import net.ecocraft.core.EcoCraftCoreMod;
import net.ecocraft.core.vault.VaultBlock;
import net.ecocraft.core.vault.VaultBlockEntity;
import net.ecocraft.core.vault.VaultMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
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
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, EcoCraftCoreMod.MOD_ID);

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

    public static final Supplier<MenuType<VaultMenu>> VAULT_MENU =
        MENUS.register("vault_menu", () ->
            new MenuType<>(VaultMenu::new, FeatureFlags.DEFAULT_FLAGS)
        );

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }
}

package net.ecocraft.core.registry;

import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class EcoRegistries {
    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(EcoCraftCoreMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(EcoCraftCoreMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EcoCraftCoreMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, EcoCraftCoreMod.MOD_ID);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }
}

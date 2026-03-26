package net.ecocraft.ah.registry;

import net.ecocraft.ah.AuctionHouseMod;
import net.ecocraft.ah.block.AuctionTerminalBlock;
import net.ecocraft.ah.entity.AuctioneerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Registry holder for auction-house blocks, items, entities, and creative tab.
 */
public class AHRegistries {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(AuctionHouseMod.MOD_ID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(AuctionHouseMod.MOD_ID);

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AuctionHouseMod.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AuctionHouseMod.MOD_ID);

    // --- Blocks ---
    public static final DeferredBlock<Block> AUCTION_TERMINAL = BLOCKS.register(
            "auction_terminal",
            () -> new AuctionTerminalBlock(BlockBehaviour.Properties.of().strength(2.0f))
    );

    // --- Items ---
    public static final DeferredItem<BlockItem> AUCTION_TERMINAL_ITEM = ITEMS.register(
            "auction_terminal",
            () -> new BlockItem(AUCTION_TERMINAL.get(), new Item.Properties())
    );

    public static final Supplier<EntityType<AuctioneerEntity>> AUCTIONEER = ENTITY_TYPES.register(
            "auctioneer",
            () -> EntityType.Builder.<AuctioneerEntity>of(AuctioneerEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f)
                    .build("auctioneer")
    );

    public static final DeferredItem<SpawnEggItem> AUCTIONEER_SPAWN_EGG = ITEMS.register(
            "auctioneer_spawn_egg",
            () -> new SpawnEggItem(AUCTIONEER.get(), 0x4E7F3E, 0xD4AF37, new Item.Properties())
    );

    // --- Creative Tab ---
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> AH_TAB = CREATIVE_TABS.register(
            "auction_house_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ecocraft_ah.auction_house"))
                    .icon(() -> AUCTION_TERMINAL_ITEM.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(AUCTION_TERMINAL_ITEM.get());
                        output.accept(AUCTIONEER_SPAWN_EGG.get());
                    })
                    .build()
    );

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);
        CREATIVE_TABS.register(modBus);
    }
}

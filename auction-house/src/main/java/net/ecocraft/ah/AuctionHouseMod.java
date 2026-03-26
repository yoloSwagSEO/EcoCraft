package net.ecocraft.ah;

import net.ecocraft.ah.entity.AuctioneerEntity;
import net.ecocraft.ah.entity.AuctioneerRenderer;
import net.ecocraft.ah.network.AHNetworkHandler;
import net.ecocraft.ah.registry.AHRegistries;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(AuctionHouseMod.MOD_ID)
public class AuctionHouseMod {

    public static final String MOD_ID = "ecocraft_ah";

    public AuctionHouseMod(IEventBus modBus, ModContainer container) {
        AHRegistries.register(modBus);
        modBus.register(AHNetworkHandler.class);
        modBus.addListener(this::registerRenderers);
        modBus.addListener(this::registerAttributes);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AHRegistries.AUCTIONEER.get(), AuctioneerRenderer::new);
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(AHRegistries.AUCTIONEER.get(),
                AuctioneerEntity.createMobAttributes()
                        .add(Attributes.MAX_HEALTH, 20.0)
                        .add(Attributes.MOVEMENT_SPEED, 0.0)
                        .build()
        );
    }
}

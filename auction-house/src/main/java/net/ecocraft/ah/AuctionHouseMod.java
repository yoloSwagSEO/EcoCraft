package net.ecocraft.ah;

import net.ecocraft.ah.entity.AuctioneerRenderer;
import net.ecocraft.ah.network.AHNetworkHandler;
import net.ecocraft.ah.registry.AHRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(AuctionHouseMod.MOD_ID)
public class AuctionHouseMod {

    public static final String MOD_ID = "ecocraft_ah";

    public AuctionHouseMod(IEventBus modBus, ModContainer container) {
        AHRegistries.register(modBus);
        modBus.register(AHNetworkHandler.class);
        modBus.addListener(this::registerRenderers);
    }

    private void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AHRegistries.AUCTIONEER.get(), AuctioneerRenderer::new);
    }
}

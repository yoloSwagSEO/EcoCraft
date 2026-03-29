package net.ecocraft.ah.client;

import net.ecocraft.ah.AuctionHouseMod;
import net.ecocraft.gui.core.EcoToastManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

@EventBusSubscriber(modid = AuctionHouseMod.MOD_ID, value = Dist.CLIENT)
public class AHClientEvents {

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiLayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        EcoToastManager.getInstance().render(event.getGuiGraphics(), screenWidth, screenHeight);
    }
}

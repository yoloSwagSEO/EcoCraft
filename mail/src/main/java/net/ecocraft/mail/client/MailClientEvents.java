package net.ecocraft.mail.client;

import net.ecocraft.gui.core.EcoToastManager;
import net.ecocraft.mail.MailMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

@EventBusSubscriber(modid = MailMod.MOD_ID, value = Dist.CLIENT)
public class MailClientEvents {

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiLayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        EcoToastManager.getInstance().render(event.getGuiGraphics(), screenWidth, screenHeight);
    }
}

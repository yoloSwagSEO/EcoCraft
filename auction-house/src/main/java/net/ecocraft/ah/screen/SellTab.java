package net.ecocraft.ah.screen;

import net.ecocraft.ah.network.payload.AHActionResultPayload;
import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Sell tab — stub for Task 3, fully implemented in Task 4.
 */
public class SellTab {

    private final AuctionHouseScreen parent;
    private final int x, y, w, h;

    public SellTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void init(Consumer<AbstractWidget> addWidget) {
        // Populated in Task 4
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.literal("Vendre - En cours de construction"),
                x + 10, y + 10, EcoColors.TEXT_GREY, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    public void onActionResult(AHActionResultPayload payload) {
        // Implemented in Task 4
    }
}

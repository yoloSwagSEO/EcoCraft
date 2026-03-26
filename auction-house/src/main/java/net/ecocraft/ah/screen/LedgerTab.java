package net.ecocraft.ah.screen;

import net.ecocraft.ah.network.payload.LedgerResponsePayload;
import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Ledger tab — stub for Task 3, fully implemented in Task 4.
 */
public class LedgerTab {

    private final AuctionHouseScreen parent;
    private final int x, y, w, h;

    public LedgerTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
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
        graphics.drawString(font, Component.literal("Livre de compte - En cours de construction"),
                x + 10, y + 10, EcoColors.TEXT_GREY, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public void onReceiveLedger(LedgerResponsePayload payload) {
        // Implemented in Task 4
    }
}

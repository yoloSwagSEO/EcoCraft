package net.ecocraft.core.vault;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class VaultScreen extends AbstractContainerScreen<VaultMenu> {

    private static final Theme THEME = Theme.dark();

    public VaultScreen(VaultMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 200;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        DrawUtils.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight,
            THEME.bgDarkest, THEME.borderAccent);

        // Title
        graphics.drawString(font, title, leftPos + 8, topPos + 8, THEME.accent, false);
        DrawUtils.drawAccentSeparator(graphics, leftPos + 4, topPos + 20, imageWidth - 8, THEME);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}

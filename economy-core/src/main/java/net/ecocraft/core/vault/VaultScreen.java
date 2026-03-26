package net.ecocraft.core.vault;

import net.ecocraft.gui.theme.EcoColors;
import net.ecocraft.gui.theme.EcoTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class VaultScreen extends AbstractContainerScreen<VaultMenu> {

    public VaultScreen(VaultMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 200;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        EcoTheme.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight,
            EcoColors.BG_DARKEST, EcoColors.BORDER_GOLD);

        // Title
        graphics.drawString(font, title, leftPos + 8, topPos + 8, EcoColors.GOLD, false);
        EcoTheme.drawGoldSeparator(graphics, leftPos + 4, topPos + 20, imageWidth - 8);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}

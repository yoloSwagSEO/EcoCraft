package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

public class EcoItemSlot extends AbstractWidget {

    private @Nullable ItemStack itemStack;
    private int rarityColor;

    public EcoItemSlot(int x, int y, int size) {
        super(x, y, size, size, Component.empty());
        this.rarityColor = EcoColors.RARITY_COMMON;
    }

    public void setItem(@Nullable ItemStack stack, int rarityColor) {
        this.itemStack = stack;
        this.rarityColor = rarityColor;
    }

    public void setItem(@Nullable ItemStack stack) {
        this.itemStack = stack;
        if (stack != null && !stack.isEmpty()) {
            this.rarityColor = getRarityColor(stack);
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + width, getY() + height, EcoColors.BG_LIGHT);

        graphics.fill(getX(), getY(), getX() + width, getY() + 2, rarityColor);
        graphics.fill(getX(), getY() + height - 2, getX() + width, getY() + height, rarityColor);
        graphics.fill(getX(), getY(), getX() + 2, getY() + height, rarityColor);
        graphics.fill(getX() + width - 2, getY(), getX() + width, getY() + height, rarityColor);

        if (itemStack != null && !itemStack.isEmpty()) {
            int itemX = getX() + (width - 16) / 2;
            int itemY = getY() + (height - 16) / 2;
            graphics.renderItem(itemStack, itemX, itemY);
            graphics.renderItemDecorations(Minecraft.getInstance().font, itemStack, itemX, itemY);
        }

        if (isHovered() && itemStack != null && !itemStack.isEmpty()) {
            graphics.renderTooltip(Minecraft.getInstance().font, itemStack, mouseX, mouseY);
        }
    }

    private static int getRarityColor(ItemStack stack) {
        return switch (stack.getRarity()) {
            case COMMON -> EcoColors.RARITY_COMMON;
            case UNCOMMON -> EcoColors.RARITY_UNCOMMON;
            case RARE -> EcoColors.RARITY_RARE;
            case EPIC -> EcoColors.RARITY_EPIC;
        };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

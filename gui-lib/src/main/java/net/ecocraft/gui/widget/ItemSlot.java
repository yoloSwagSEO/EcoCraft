package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Item display slot with rarity border and built-in tooltip on hover.
 * Uses Theme for border and background colors.
 */
public class ItemSlot extends AbstractWidget {

    private final Theme theme;
    private @Nullable ItemStack itemStack;
    private int rarityColor;

    public ItemSlot(int x, int y, int size, Theme theme) {
        super(x, y, size, size, Component.empty());
        this.theme = theme;
        this.rarityColor = theme.rarityCommon;
    }

    public ItemSlot(int x, int y, int size) {
        this(x, y, size, Theme.dark());
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

    public @Nullable ItemStack getItem() {
        return itemStack;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getX() + width, getY() + height, theme.bgLight);

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

        // Built-in tooltip on hover
        if (isHovered() && itemStack != null && !itemStack.isEmpty()) {
            graphics.renderTooltip(Minecraft.getInstance().font, itemStack, mouseX, mouseY);
        }
    }

    private int getRarityColor(ItemStack stack) {
        return switch (stack.getRarity()) {
            case COMMON -> theme.rarityCommon;
            case UNCOMMON -> theme.rarityUncommon;
            case RARE -> theme.rarityRare;
            case EPIC -> theme.rarityEpic;
        };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

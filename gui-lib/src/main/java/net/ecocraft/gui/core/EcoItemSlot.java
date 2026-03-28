package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * V2 item display slot extending {@link BaseWidget}.
 * <p>
 * Displays a Minecraft {@link ItemStack} with a rarity-colored border
 * and built-in tooltip on hover.
 * Not focusable, no events.
 */
public class EcoItemSlot extends BaseWidget {

    private final Theme theme;
    private @Nullable ItemStack itemStack;
    private int rarityColor;

    public EcoItemSlot(int x, int y, int size, Theme theme) {
        super(x, y, size, size);
        this.theme = theme;
        this.rarityColor = theme.rarityCommon;
    }

    public EcoItemSlot(int x, int y, int size) {
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
    public boolean isFocusable() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int w = getWidth();
        int h = getHeight();
        int x = getX();
        int y = getY();

        // Background
        graphics.fill(x, y, x + w, y + h, theme.bgLight);

        // Rarity border (2px)
        graphics.fill(x, y, x + w, y + 2, rarityColor);
        graphics.fill(x, y + h - 2, x + w, y + h, rarityColor);
        graphics.fill(x, y, x + 2, y + h, rarityColor);
        graphics.fill(x + w - 2, y, x + w, y + h, rarityColor);

        // Item rendering
        if (itemStack != null && !itemStack.isEmpty()) {
            int itemX = x + (w - 16) / 2;
            int itemY = y + (h - 16) / 2;
            graphics.renderItem(itemStack, itemX, itemY);
            graphics.renderItemDecorations(Minecraft.getInstance().font, itemStack, itemX, itemY);
        }

        // Built-in tooltip on hover
        boolean hovered = containsPoint(mouseX, mouseY);
        if (hovered && itemStack != null && !itemStack.isEmpty()) {
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
}

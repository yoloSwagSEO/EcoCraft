package net.ecocraft.gui.core;

import net.ecocraft.api.currency.Icon;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Widget that renders an {@link Icon} at a given size.
 * Supports texture, item, file (fallback to "?"), and text icons.
 * Not focusable — purely decorative.
 */
public class EcoIcon extends BaseWidget {

    private final Theme theme;
    private Icon icon;

    public EcoIcon(int x, int y, int size, Icon icon, Theme theme) {
        super(x, y, size, size);
        this.icon = icon;
        this.theme = theme;
    }

    public Icon getIcon() {
        return icon;
    }

    public void setIcon(Icon icon) {
        this.icon = icon;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        int x = getX();
        int y = getY();
        int size = getWidth();

        switch (icon) {
            case Icon.TextureIcon tex -> renderTexture(graphics, tex.resourceLocation(), x, y, size);
            case Icon.ItemIcon item -> renderItem(graphics, item.itemId(), x, y, size);
            case Icon.FileIcon file -> renderFallback(graphics, x, y, size);
            case Icon.TextIcon text -> renderText(graphics, text.symbol(), x, y, size);
        }
    }

    private void renderTexture(GuiGraphics graphics, String resourceLocationStr, int x, int y, int size) {
        ResourceLocation location = ResourceLocation.parse(resourceLocationStr);
        graphics.blit(location, x, y, 0, 0, size, size, size, size);
    }

    private void renderItem(GuiGraphics graphics, String itemId, int x, int y, int size) {
        ResourceLocation location = ResourceLocation.parse(itemId);
        var item = BuiltInRegistries.ITEM.get(location);
        ItemStack stack = new ItemStack(item);

        // Items render at 16x16; center within our size
        int itemSize = 16;
        int offsetX = x + (size - itemSize) / 2;
        int offsetY = y + (size - itemSize) / 2;

        if (size != itemSize) {
            float scale = size / (float) itemSize;
            graphics.pose().pushPose();
            graphics.pose().translate(x + size / 2f, y + size / 2f, 0);
            graphics.pose().scale(scale, scale, 1f);
            graphics.pose().translate(-8f, -8f, 0); // items render from (0,0) with 16x16
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
        } else {
            graphics.renderItem(stack, offsetX, offsetY);
        }
    }

    private void renderFallback(GuiGraphics graphics, int x, int y, int size) {
        // FileIcon: fallback to "?" text until dynamic texture loading is implemented
        renderText(graphics, "?", x, y, size);
    }

    private void renderText(GuiGraphics graphics, String symbol, int x, int y, int size) {
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(symbol);
        int textX = x + (size - textWidth) / 2;
        int textY = y + (size - font.lineHeight) / 2;
        graphics.drawString(font, symbol, textX, textY, theme.textLight, false);
    }
}

package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class EcoButton extends AbstractWidget {

    public enum Style {
        PRIMARY(EcoColors.GOLD_BG, EcoColors.BORDER_GOLD, EcoColors.GOLD, EcoColors.GOLD_BG_DIM),
        SUCCESS(EcoColors.SUCCESS_BG, EcoColors.SUCCESS, EcoColors.TEXT_WHITE, 0xFF2A4A2A),
        AUCTION(EcoColors.WARNING_BG, EcoColors.WARNING, EcoColors.TEXT_WHITE, 0xFF3A2A1A),
        DANGER(EcoColors.DANGER_BG, EcoColors.DANGER, EcoColors.DANGER, 0xFF3A1A1A),
        GHOST(EcoColors.BG_MEDIUM, EcoColors.BORDER_LIGHT, EcoColors.TEXT_GREY, EcoColors.BG_LIGHT);

        final int bgColor;
        final int borderColor;
        final int textColor;
        final int hoverBg;

        Style(int bgColor, int borderColor, int textColor, int hoverBg) {
            this.bgColor = bgColor;
            this.borderColor = borderColor;
            this.textColor = textColor;
            this.hoverBg = hoverBg;
        }
    }

    private final Style style;
    private final Runnable onPress;

    public EcoButton(int x, int y, int width, int height, Component label, Style style, Runnable onPress) {
        super(x, y, width, height, label);
        this.style = style;
        this.onPress = onPress;
    }

    public static EcoButton primary(int x, int y, int width, int height, Component label, Runnable onPress) {
        return new EcoButton(x, y, width, height, label, Style.PRIMARY, onPress);
    }

    public static EcoButton success(int x, int y, int width, int height, Component label, Runnable onPress) {
        return new EcoButton(x, y, width, height, label, Style.SUCCESS, onPress);
    }

    public static EcoButton auction(int x, int y, int width, int height, Component label, Runnable onPress) {
        return new EcoButton(x, y, width, height, label, Style.AUCTION, onPress);
    }

    public static EcoButton danger(int x, int y, int width, int height, Component label, Runnable onPress) {
        return new EcoButton(x, y, width, height, label, Style.DANGER, onPress);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered();
        int bg = hovered ? style.hoverBg : style.bgColor;

        graphics.fill(getX(), getY(), getX() + width, getY() + height, bg);

        graphics.fill(getX(), getY(), getX() + width, getY() + 1, style.borderColor);
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, style.borderColor);
        graphics.fill(getX(), getY(), getX() + 1, getY() + height, style.borderColor);
        graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, style.borderColor);

        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int textWidth = font.width(getMessage());
        int textX = getX() + (width - textWidth) / 2;
        int textY = getY() + (height - 8) / 2;
        graphics.drawString(font, getMessage(), textX, textY, style.textColor, false);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

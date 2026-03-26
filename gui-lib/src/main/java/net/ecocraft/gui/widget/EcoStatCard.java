package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.ecocraft.gui.theme.EcoTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

public class EcoStatCard extends AbstractWidget {

    private final Component label;
    private Component value;
    private int valueColor;
    private @Nullable Component subtitle;
    private int subtitleColor;

    public EcoStatCard(int x, int y, int width, int height,
                       Component label, Component value, int valueColor) {
        super(x, y, width, height, label);
        this.label = label;
        this.value = value;
        this.valueColor = valueColor;
        this.subtitleColor = EcoColors.TEXT_GREY;
    }

    public void setValue(Component value, int color) {
        this.value = value;
        this.valueColor = color;
    }

    public void setSubtitle(@Nullable Component subtitle, int color) {
        this.subtitle = subtitle;
        this.subtitleColor = color;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        EcoTheme.drawPanel(graphics, getX(), getY(), width, height);

        int padding = 10;
        int innerX = getX() + padding;

        int labelY = getY() + padding;
        graphics.drawString(font, label, innerX, labelY, EcoColors.TEXT_DIM, false);

        int valueY = labelY + 14;
        graphics.drawString(font, value, innerX, valueY, valueColor, false);

        if (subtitle != null) {
            int subtitleY = valueY + 14;
            graphics.drawString(font, subtitle, innerX, subtitleY, subtitleColor, false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

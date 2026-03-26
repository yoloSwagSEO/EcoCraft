package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

public class EcoFilterTags extends AbstractWidget {

    private static final int TAG_HEIGHT = 20;
    private static final int TAG_GAP = 6;
    private static final int TAG_PADDING_H = 12;

    private final List<Component> tags;
    private int activeTag = 0;
    private final IntConsumer onTagChanged;

    public EcoFilterTags(int x, int y, List<Component> tags, IntConsumer onTagChanged) {
        super(x, y, 0, TAG_HEIGHT, Component.empty());
        this.tags = tags;
        this.onTagChanged = onTagChanged;
        this.width = calculateTotalWidth();
    }

    private int calculateTotalWidth() {
        Font font = Minecraft.getInstance().font;
        int total = 0;
        for (Component tag : tags) {
            total += font.width(tag) + TAG_PADDING_H * 2 + TAG_GAP;
        }
        return total - TAG_GAP;
    }

    public int getActiveTag() {
        return activeTag;
    }

    public void setActiveTag(int index) {
        if (index >= 0 && index < tags.size()) {
            this.activeTag = index;
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int currentX = getX();

        for (int i = 0; i < tags.size(); i++) {
            Component label = tags.get(i);
            int tagWidth = font.width(label) + TAG_PADDING_H * 2;
            boolean isActive = i == activeTag;
            boolean isHovered = mouseX >= currentX && mouseX < currentX + tagWidth
                    && mouseY >= getY() && mouseY < getY() + TAG_HEIGHT;

            int bg = isActive ? EcoColors.GOLD_BG_DIM : (isHovered ? EcoColors.BG_LIGHT : EcoColors.BG_MEDIUM);
            int border = isActive ? EcoColors.BORDER_GOLD : EcoColors.BORDER_LIGHT;
            int textColor = isActive ? EcoColors.GOLD : EcoColors.TEXT_GREY;

            graphics.fill(currentX, getY(), currentX + tagWidth, getY() + TAG_HEIGHT, bg);
            graphics.fill(currentX, getY(), currentX + tagWidth, getY() + 1, border);
            graphics.fill(currentX, getY() + TAG_HEIGHT - 1, currentX + tagWidth, getY() + TAG_HEIGHT, border);
            graphics.fill(currentX, getY(), currentX + 1, getY() + TAG_HEIGHT, border);
            graphics.fill(currentX + tagWidth - 1, getY(), currentX + tagWidth, getY() + TAG_HEIGHT, border);

            int textX = currentX + TAG_PADDING_H;
            int textY = getY() + (TAG_HEIGHT - 8) / 2;
            graphics.drawString(font, label, textX, textY, textColor, false);

            currentX += tagWidth + TAG_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;

        Font font = Minecraft.getInstance().font;
        int currentX = getX();

        for (int i = 0; i < tags.size(); i++) {
            int tagWidth = font.width(tags.get(i)) + TAG_PADDING_H * 2;
            if (mouseX >= currentX && mouseX < currentX + tagWidth) {
                if (i != activeTag) {
                    activeTag = i;
                    onTagChanged.accept(i);
                }
                return true;
            }
            currentX += tagWidth + TAG_GAP;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + width
                && mouseY >= getY() && mouseY < getY() + TAG_HEIGHT;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

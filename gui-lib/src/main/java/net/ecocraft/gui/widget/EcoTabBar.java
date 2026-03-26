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

public class EcoTabBar extends AbstractWidget {

    private static final int TAB_HEIGHT = 22;
    private static final int TAB_GAP = 4;
    private static final int TAB_PADDING_H = 16;

    private final List<Component> tabs;
    private int activeTab = 0;
    private final IntConsumer onTabChanged;

    public EcoTabBar(int x, int y, List<Component> tabs, IntConsumer onTabChanged) {
        super(x, y, 0, TAB_HEIGHT, Component.empty());
        this.tabs = tabs;
        this.onTabChanged = onTabChanged;
        this.width = calculateTotalWidth();
    }

    private int calculateTotalWidth() {
        Font font = Minecraft.getInstance().font;
        int total = 0;
        for (Component tab : tabs) {
            total += font.width(tab) + TAB_PADDING_H * 2 + TAB_GAP;
        }
        return total - TAB_GAP;
    }

    public int getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            this.activeTab = index;
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int currentX = getX();

        for (int i = 0; i < tabs.size(); i++) {
            Component label = tabs.get(i);
            int tabWidth = font.width(label) + TAB_PADDING_H * 2;
            boolean isActive = i == activeTab;
            boolean isHovered = mouseX >= currentX && mouseX < currentX + tabWidth
                    && mouseY >= getY() && mouseY < getY() + TAB_HEIGHT;

            int bg = isActive ? EcoColors.GOLD_BG : (isHovered ? EcoColors.BG_LIGHT : EcoColors.BG_MEDIUM);
            int border = isActive ? EcoColors.BORDER_GOLD : EcoColors.BORDER;
            int textColor = isActive ? EcoColors.GOLD : EcoColors.TEXT_DARK;

            graphics.fill(currentX, getY(), currentX + tabWidth, getY() + TAB_HEIGHT, bg);
            graphics.fill(currentX, getY(), currentX + tabWidth, getY() + 1, border);
            graphics.fill(currentX, getY() + TAB_HEIGHT - 1, currentX + tabWidth, getY() + TAB_HEIGHT, border);
            graphics.fill(currentX, getY(), currentX + 1, getY() + TAB_HEIGHT, border);
            graphics.fill(currentX + tabWidth - 1, getY(), currentX + tabWidth, getY() + TAB_HEIGHT, border);

            int textX = currentX + TAB_PADDING_H;
            int textY = getY() + (TAB_HEIGHT - 8) / 2;
            graphics.drawString(font, label, textX, textY, textColor, false);

            currentX += tabWidth + TAB_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;

        Font font = Minecraft.getInstance().font;
        int currentX = getX();

        for (int i = 0; i < tabs.size(); i++) {
            int tabWidth = font.width(tabs.get(i)) + TAB_PADDING_H * 2;
            if (mouseX >= currentX && mouseX < currentX + tabWidth) {
                if (i != activeTab) {
                    activeTab = i;
                    onTabChanged.accept(i);
                }
                return true;
            }
            currentX += tabWidth + TAB_GAP;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + width
                && mouseY >= getY() && mouseY < getY() + TAB_HEIGHT;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

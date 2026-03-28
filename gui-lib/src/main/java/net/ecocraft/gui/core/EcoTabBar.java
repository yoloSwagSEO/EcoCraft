package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Horizontal tab bar extending {@link BaseWidget}.
 * One tab is active at a time. Active tab uses accent colors (gold border/text),
 * inactive tabs use grey. Tab widths are cached to avoid recomputation every frame.
 */
public class EcoTabBar extends BaseWidget {

    private static final int TAB_HEIGHT = 22;
    private static final int TAB_GAP = 4;
    private static final int TAB_PADDING_H = 16;

    private final List<Component> labels;
    private final Consumer<Integer> onTabChanged;
    private final Theme theme;
    private int activeTab = 0;

    /** Cached tab widths (computed once). */
    private int[] tabWidths;
    private int totalWidth;

    // Mouse position cached from last render for hover detection
    private double lastMouseX;
    private double lastMouseY;

    public EcoTabBar(int x, int y, List<Component> labels, Consumer<Integer> onTabChanged) {
        this(x, y, labels, onTabChanged, Theme.dark());
    }

    public EcoTabBar(int x, int y, List<Component> labels, Consumer<Integer> onTabChanged, Theme theme) {
        super(x, y, 0, TAB_HEIGHT);
        this.labels = labels;
        this.onTabChanged = onTabChanged;
        this.theme = theme;
        computeTabWidths();
    }

    /** Compute and cache tab widths. Called once in constructor. */
    private void computeTabWidths() {
        Font font = Minecraft.getInstance().font;
        tabWidths = new int[labels.size()];
        totalWidth = 0;
        for (int i = 0; i < labels.size(); i++) {
            tabWidths[i] = font.width(labels.get(i)) + TAB_PADDING_H * 2;
            totalWidth += tabWidths[i];
            if (i > 0) {
                totalWidth += TAB_GAP;
            }
        }
        setSize(totalWidth, TAB_HEIGHT);
    }

    public int getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(int index) {
        if (index >= 0 && index < labels.size()) {
            this.activeTab = index;
        }
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        Font font = Minecraft.getInstance().font;
        int currentX = getX();

        for (int i = 0; i < labels.size(); i++) {
            int tabW = tabWidths[i];
            boolean isActive = i == activeTab;
            boolean isHovered = mouseX >= currentX && mouseX < currentX + tabW
                    && mouseY >= getY() && mouseY < getY() + TAB_HEIGHT;

            int bg = isActive ? theme.accentBg : (isHovered ? theme.bgLight : theme.bgMedium);
            int border = isActive ? theme.borderAccent : theme.border;
            int textColor = isActive ? theme.accent : theme.textDark;

            DrawUtils.drawPanel(graphics, currentX, getY(), tabW, TAB_HEIGHT, bg, border);

            int textX = currentX + TAB_PADDING_H;
            int textY = getY() + (TAB_HEIGHT - 8) / 2;
            graphics.drawString(font, labels.get(i), textX, textY, textColor, false);

            currentX += tabW + TAB_GAP;
        }
    }

    // --- Events ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !containsPoint(mouseX, mouseY)) return false;

        int currentX = getX();
        for (int i = 0; i < labels.size(); i++) {
            int tabW = tabWidths[i];
            if (mouseX >= currentX && mouseX < currentX + tabW) {
                if (i != activeTab) {
                    activeTab = i;
                    onTabChanged.accept(i);
                }
                return true;
            }
            currentX += tabW + TAB_GAP;
        }
        return false;
    }
}

package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Themed dropdown (select) widget. Shows the current selected value;
 * when clicked, opens a list of options below. Clicking an option selects
 * it and closes the list. Clicking outside closes the list.
 */
public class Dropdown extends AbstractWidget {

    private static final int H_PADDING = 6;
    private static final String ARROW = "\u25BC"; // ▼

    private final Font font;
    private final Theme theme;
    private List<String> options = new ArrayList<>();
    private int selectedIndex = -1;
    private boolean open = false;
    private IntConsumer responder;

    public Dropdown(Font font, int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.theme = theme;
    }

    // --- API ---

    /** Set the list of options. */
    public Dropdown options(List<String> options) {
        this.options = new ArrayList<>(options);
        return this;
    }

    /** Set the selected index. */
    public Dropdown selectedIndex(int index) {
        this.selectedIndex = index;
        return this;
    }

    /** Set callback fired when selection changes. */
    public Dropdown responder(IntConsumer responder) {
        this.responder = responder;
        return this;
    }

    /** Get the currently selected index (-1 if none). */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /** Get the currently selected value, or empty string if none. */
    public String getSelectedValue() {
        if (selectedIndex >= 0 && selectedIndex < options.size()) {
            return options.get(selectedIndex);
        }
        return "";
    }

    /** Whether the dropdown list is currently open. */
    public boolean isOpen() {
        return open;
    }

    // --- Geometry helpers ---

    private int itemHeight() {
        return height;
    }

    private int listHeight() {
        return options.size() * itemHeight();
    }

    private int listY() {
        return getY() + height;
    }

    // --- Rendering ---

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Main closed field
        int bg = open ? theme.bgMedium : theme.bgDark;
        int border = open ? theme.borderAccent : theme.border;
        DrawUtils.drawPanel(graphics, getX(), getY(), width, height, bg, border);

        // Selected value text
        String text = getSelectedValue();
        int arrowWidth = font.width(ARROW) + H_PADDING;
        int maxTextWidth = width - H_PADDING * 2 - arrowWidth;
        String displayText = DrawUtils.truncateText(font, text, maxTextWidth);
        int textY = getY() + (height - font.lineHeight) / 2;
        graphics.drawString(font, displayText, getX() + H_PADDING, textY, theme.textLight, false);

        // Arrow
        int arrowX = getX() + width - font.width(ARROW) - H_PADDING;
        graphics.drawString(font, ARROW, arrowX, textY, theme.textGrey, false);

        // Options list (rendered on top via pose stack translation)
        if (open && !options.isEmpty()) {
            renderOptionsList(graphics, mouseX, mouseY);
        }
    }

    private void renderOptionsList(GuiGraphics graphics, int mouseX, int mouseY) {
        int lx = getX();
        int ly = listY();
        int lw = width;
        int lh = listHeight();

        // Determine screen height for scissor clamping
        int screenHeight = graphics.guiHeight();
        int clampedH = Math.min(lh, screenHeight - ly);
        if (clampedH <= 0) return;

        // Push pose to render on top of other widgets
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);

        // Enable scissor if list would go off screen
        boolean useScissor = lh > clampedH;
        if (useScissor) {
            graphics.enableScissor(lx, ly, lx + lw, ly + clampedH);
        }

        // Background panel for entire list
        DrawUtils.drawPanel(graphics, lx, ly, lw, lh, theme.bgDark, theme.border);

        // Individual options
        int ih = itemHeight();
        for (int i = 0; i < options.size(); i++) {
            int optY = ly + i * ih;

            // Determine option background
            boolean isSelected = i == selectedIndex;
            boolean isHovered = mouseX >= lx && mouseX < lx + lw
                    && mouseY >= optY && mouseY < optY + ih;

            if (isSelected) {
                graphics.fill(lx + 1, optY + 1, lx + lw - 1, optY + ih - 1, theme.accentBg);
            } else if (isHovered) {
                graphics.fill(lx + 1, optY + 1, lx + lw - 1, optY + ih - 1, theme.bgMedium);
            }

            // Option text
            String optionText = DrawUtils.truncateText(font, options.get(i), lw - H_PADDING * 2);
            int textY = optY + (ih - font.lineHeight) / 2;
            int textColor = isSelected ? theme.accent : theme.textLight;
            graphics.drawString(font, optionText, lx + H_PADDING, textY, textColor, false);
        }

        if (useScissor) {
            graphics.disableScissor();
        }

        graphics.pose().popPose();
    }

    // --- Mouse interaction ---

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!this.active || !this.visible) return false;
        // Main widget area
        if (mouseX >= getX() && mouseX < getX() + width
                && mouseY >= getY() && mouseY < getY() + height) {
            return true;
        }
        // Options list area when open
        if (open && !options.isEmpty()) {
            int ly = listY();
            int lh = listHeight();
            return mouseX >= getX() && mouseX < getX() + width
                    && mouseY >= ly && mouseY < ly + lh;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible) return false;

        if (open) {
            // Check if click is on an option
            int ly = listY();
            int ih = itemHeight();
            if (mouseX >= getX() && mouseX < getX() + width
                    && mouseY >= ly && mouseY < ly + listHeight()) {
                int clickedIndex = (int) ((mouseY - ly) / ih);
                if (clickedIndex >= 0 && clickedIndex < options.size()) {
                    selectedIndex = clickedIndex;
                    if (responder != null) {
                        responder.accept(selectedIndex);
                    }
                }
                open = false;
                return true;
            }
            // Click on the main widget toggles closed
            if (mouseX >= getX() && mouseX < getX() + width
                    && mouseY >= getY() && mouseY < getY() + height) {
                open = false;
                return true;
            }
            // Click outside → close
            open = false;
            return true;
        } else {
            // Click on main widget → open
            if (mouseX >= getX() && mouseX < getX() + width
                    && mouseY >= getY() && mouseY < getY() + height) {
                open = true;
                return true;
            }
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

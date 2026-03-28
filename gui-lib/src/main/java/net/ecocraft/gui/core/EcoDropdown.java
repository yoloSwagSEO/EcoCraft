package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Themed dropdown (select) widget extending {@link BaseWidget}.
 * Shows the current selected value; when clicked, opens a list of options below.
 * Clicking an option selects it and closes the list.
 * Clicking outside closes the list and lets the click propagate.
 */
public class EcoDropdown extends BaseWidget {

    private static final int H_PADDING = 6;
    private static final String ARROW = "\u25BC"; // ▼

    private final Theme theme;
    private List<String> options = new ArrayList<>();
    private int selectedIndex = -1;
    private boolean open = false;
    private IntConsumer responder;

    // Mouse position cached from last render for hover detection
    private double lastMouseX;
    private double lastMouseY;

    public EcoDropdown(int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height);
        this.theme = theme;
    }

    // --- API ---

    /** Set the list of options. */
    public EcoDropdown options(List<String> options) {
        this.options = new ArrayList<>(options);
        return this;
    }

    /** Set the selected index. */
    public EcoDropdown selectedIndex(int index) {
        this.selectedIndex = index;
        return this;
    }

    /** Set callback fired when selection changes. */
    public EcoDropdown responder(IntConsumer responder) {
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

    @Override
    public boolean isFocusable() {
        return false;
    }

    // --- Geometry helpers ---

    private int itemHeight() {
        return getHeight();
    }

    private int listHeight() {
        return options.size() * itemHeight();
    }

    private int listY() {
        return getY() + getHeight();
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        Font font = Minecraft.getInstance().font;

        // Main closed field
        int bg = open ? theme.bgMedium : theme.bgDark;
        int border = open ? theme.borderAccent : theme.border;
        DrawUtils.drawPanel(graphics, getX(), getY(), getWidth(), getHeight(), bg, border);

        // Selected value text
        String text = getSelectedValue();
        int arrowWidth = font.width(ARROW) + H_PADDING;
        int maxTextWidth = getWidth() - H_PADDING * 2 - arrowWidth;
        String displayText = DrawUtils.truncateText(font, text, maxTextWidth);
        int textY = getY() + (getHeight() - font.lineHeight) / 2;
        graphics.drawString(font, displayText, getX() + H_PADDING, textY, theme.textLight, false);

        // Arrow
        int arrowX = getX() + getWidth() - font.width(ARROW) - H_PADDING;
        graphics.drawString(font, ARROW, arrowX, textY, theme.textGrey, false);

        // Options list (rendered on top via pose stack translation)
        if (open && !options.isEmpty()) {
            renderOptionsList(graphics, mouseX, mouseY, font);
        }
    }

    private void renderOptionsList(GuiGraphics graphics, int mouseX, int mouseY, Font font) {
        int lx = getX();
        int ly = listY();
        int lw = getWidth();
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
    public boolean containsPoint(double mx, double my) {
        // Main widget area
        if (mx >= getX() && mx < getX() + getWidth()
                && my >= getY() && my < getY() + getHeight()) {
            return true;
        }
        // Options list area when open
        if (open && !options.isEmpty()) {
            int ly = listY();
            int lh = listHeight();
            return mx >= getX() && mx < getX() + getWidth()
                    && my >= ly && my < ly + lh;
        }
        return false;
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (open) {
            // Check if click is on an option
            int ly = listY();
            int ih = itemHeight();
            if (mouseX >= getX() && mouseX < getX() + getWidth()
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
            if (mouseX >= getX() && mouseX < getX() + getWidth()
                    && mouseY >= getY() && mouseY < getY() + getHeight()) {
                open = false;
                return true;
            }
            // Click outside -> close, return false so click propagates
            open = false;
            return false;
        } else {
            // Click on main widget -> open
            if (mouseX >= getX() && mouseX < getX() + getWidth()
                    && mouseY >= getY() && mouseY < getY() + getHeight()) {
                open = true;
                return true;
            }
        }
        return false;
    }
}

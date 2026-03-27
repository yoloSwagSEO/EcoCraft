package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * A button that cycles through a list of string options on click.
 * Left click advances to the next option, right click goes to the previous.
 * Displays small arrows on the sides to indicate cyclability.
 */
public class CycleButton extends AbstractWidget {

    private final Font font;
    private final Theme theme;
    private List<String> options = List.of();
    private int selectedIndex = 0;
    private @Nullable IntConsumer responder;

    public CycleButton(Font font, int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.theme = theme;
    }

    // --- Builder-style setters ---

    public CycleButton options(List<String> options) {
        this.options = List.copyOf(options);
        if (selectedIndex >= this.options.size()) {
            selectedIndex = 0;
        }
        return this;
    }

    public CycleButton selectedIndex(int index) {
        if (!options.isEmpty()) {
            this.selectedIndex = Math.floorMod(index, options.size());
        }
        return this;
    }

    public CycleButton responder(IntConsumer responder) {
        this.responder = responder;
        return this;
    }

    // --- Getters ---

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public String getSelectedValue() {
        if (options.isEmpty()) return "";
        return options.get(selectedIndex);
    }

    // --- Rendering ---

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean hovered = isHovered();

        int bg = hovered ? theme.bgLight : theme.bgMedium;
        DrawUtils.drawPanel(graphics, x, y, width, height, bg, theme.border);

        int textY = y + (height - 8) / 2;

        // Draw left arrow
        String leftArrow = "\u25C0";
        int arrowColor = hovered ? theme.textLight : theme.textDim;
        graphics.drawString(font, leftArrow, x + 3, textY, arrowColor, false);

        // Draw right arrow
        String rightArrow = "\u25B6";
        int rightArrowWidth = font.width(rightArrow);
        graphics.drawString(font, rightArrow, x + width - 3 - rightArrowWidth, textY, arrowColor, false);

        // Draw current value centered
        String label = getSelectedValue();
        int labelWidth = font.width(label);
        int labelX = x + (width - labelWidth) / 2;
        graphics.drawString(font, label, labelX, textY, theme.textLight, false);
    }

    // --- Interaction ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible) return false;
        if (!isMouseOver(mouseX, mouseY)) return false;
        if (options.isEmpty()) return false;

        if (button == 0) {
            // Left click — next option
            cycle(1);
            return true;
        } else if (button == 1) {
            // Right click — previous option
            cycle(-1);
            return true;
        }
        return false;
    }

    private void cycle(int direction) {
        if (options.isEmpty()) return;
        selectedIndex = Math.floorMod(selectedIndex + direction, options.size());
        if (responder != null) {
            responder.accept(selectedIndex);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

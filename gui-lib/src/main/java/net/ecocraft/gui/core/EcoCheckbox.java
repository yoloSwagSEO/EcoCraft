package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Checkbox widget with optional label text.
 * Square box with a checkmark when checked, label displayed to the right.
 * Extends BaseWidget (not focusable — click-only interaction).
 */
public class EcoCheckbox extends BaseWidget {

    private static final int DEFAULT_BOX_SIZE = 14;
    private static final int LABEL_GAP = 4;

    private final Font font;
    private final Theme theme;
    private final @Nullable Component label;
    private final int boxSize;
    private boolean checked;
    private @Nullable Consumer<Boolean> responder;

    public EcoCheckbox(Font font, int x, int y, @Nullable Component label, Theme theme) {
        this(font, x, y, label, theme, DEFAULT_BOX_SIZE);
    }

    public EcoCheckbox(Font font, int x, int y, @Nullable Component label, Theme theme, int boxSize) {
        super(x, y, 0, 0);
        this.font = font;
        this.theme = theme;
        this.label = label;
        this.boxSize = boxSize;
        this.checked = false;
        recalcSize();
    }

    private void recalcSize() {
        int w = boxSize;
        if (label != null) {
            w += LABEL_GAP + font.width(label);
        }
        int h = Math.max(boxSize, font.lineHeight);
        setSize(w, h);
    }

    // --- Fluent API ---

    public EcoCheckbox checked(boolean checked) {
        this.checked = checked;
        return this;
    }

    public EcoCheckbox responder(Consumer<Boolean> responder) {
        this.responder = responder;
        return this;
    }

    // --- Getters ---

    public boolean isChecked() {
        return checked;
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        int x = getX();
        int y = getY();

        // Center the box vertically within the widget height
        int boxY = y + (getHeight() - boxSize) / 2;

        boolean hovered = mouseX >= x && mouseX < x + getWidth()
                && mouseY >= y && mouseY < y + getHeight();

        // Determine colors based on state
        int bg;
        int border;
        if (checked) {
            bg = theme.accentBg;
            border = theme.borderAccent;
        } else {
            bg = theme.bgDark;
            border = hovered ? theme.borderAccent : theme.border;
        }

        // Draw box using DrawUtils.drawPanel
        DrawUtils.drawPanel(graphics, x, boxY, boxSize, boxSize, bg, border);

        // Draw checkmark when checked
        if (checked) {
            drawCheckmark(graphics, x, boxY);
        }

        // Draw label to the right
        if (label != null) {
            int labelX = x + boxSize + LABEL_GAP;
            int labelY = y + (getHeight() - font.lineHeight) / 2;
            graphics.drawString(font, label, labelX, labelY, theme.textLight, false);
        }
    }

    /**
     * Draws a simple checkmark (✓) inside the box using line fills.
     * The mark is drawn as two diagonal strokes forming a V shape.
     */
    private void drawCheckmark(GuiGraphics graphics, int boxX, int boxY) {
        int color = theme.textWhite;
        int pad = 3;
        int left = boxX + pad;
        int top = boxY + pad;
        int inner = boxSize - pad * 2;

        // Draw a simple X/checkmark using pixel lines
        // Short descending stroke (left part of check)
        int midX = left + inner / 3;
        int midY = top + inner - 1;
        for (int i = 0; i < inner / 3 + 1; i++) {
            graphics.fill(midX - i, midY - i, midX - i + 1, midY - i + 1, color);
            // Make it 2px wide for visibility
            graphics.fill(midX - i + 1, midY - i, midX - i + 2, midY - i + 1, color);
        }
        // Long ascending stroke (right part of check)
        for (int i = 0; i < inner - inner / 3; i++) {
            graphics.fill(midX + i, midY - i, midX + i + 1, midY - i + 1, color);
            graphics.fill(midX + i + 1, midY - i, midX + i + 2, midY - i + 1, color);
        }
    }

    // --- Interaction ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        checked = !checked;
        if (responder != null) {
            responder.accept(checked);
        }
        return true;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }
}

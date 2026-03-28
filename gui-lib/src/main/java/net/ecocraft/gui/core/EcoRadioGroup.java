package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Vertical radio button group. Only one option can be selected at a time.
 * Extends BaseWidget (not focusable — click-only interaction).
 */
public class EcoRadioGroup extends BaseWidget {

    private static final int CIRCLE_SIZE = 12;
    private static final int INNER_DOT_SIZE = 6;
    private static final int LABEL_GAP = 4;
    private static final int DEFAULT_SPACING = 4;

    private final Font font;
    private final Theme theme;
    private List<String> options = new ArrayList<>();
    private int selectedIndex = -1;
    private int spacing = DEFAULT_SPACING;
    private @Nullable IntConsumer responder;

    public EcoRadioGroup(Font font, int x, int y, Theme theme) {
        super(x, y, 0, 0);
        this.font = font;
        this.theme = theme;
    }

    // --- Fluent API ---

    public EcoRadioGroup options(List<String> options) {
        this.options = new ArrayList<>(options);
        recalculateSize();
        return this;
    }

    public EcoRadioGroup selectedIndex(int index) {
        this.selectedIndex = index;
        return this;
    }

    public EcoRadioGroup responder(IntConsumer responder) {
        this.responder = responder;
        return this;
    }

    public EcoRadioGroup spacing(int spacing) {
        this.spacing = spacing;
        recalculateSize();
        return this;
    }

    // --- Getters ---

    public int getSelectedIndex() {
        return selectedIndex;
    }

    // --- Size calculation ---

    private void recalculateSize() {
        if (options.isEmpty()) {
            setSize(0, 0);
            return;
        }
        int maxLabelWidth = 0;
        for (String option : options) {
            int w = font.width(option);
            if (w > maxLabelWidth) maxLabelWidth = w;
        }
        int width = CIRCLE_SIZE + LABEL_GAP + maxLabelWidth;
        int height = (CIRCLE_SIZE + spacing) * options.size() - spacing;
        setSize(width, height);
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        int baseX = getX();
        int baseY = getY();

        for (int i = 0; i < options.size(); i++) {
            int optionY = baseY + i * (CIRCLE_SIZE + spacing);

            // Hit-test for hover
            boolean hovered = mouseX >= baseX && mouseX < baseX + getWidth()
                    && mouseY >= optionY && mouseY < optionY + CIRCLE_SIZE;
            boolean selected = (i == selectedIndex);

            // Determine ring color
            int ringColor = (selected || hovered) ? theme.borderAccent : theme.border;

            // Draw outer ring (filled circle)
            fillCircle(graphics, baseX, optionY, CIRCLE_SIZE, ringColor);

            // Draw inner background (filled circle, 2px inset = diameter - 4)
            int innerBg = theme.bgDark;
            fillCircle(graphics, baseX + 2, optionY + 2, CIRCLE_SIZE - 4, innerBg);

            // Draw selected dot
            if (selected) {
                int dotOffset = (CIRCLE_SIZE - INNER_DOT_SIZE) / 2;
                fillCircle(graphics, baseX + dotOffset, optionY + dotOffset, INNER_DOT_SIZE, theme.accent);
            }

            // Draw label
            int textX = baseX + CIRCLE_SIZE + LABEL_GAP;
            int textY = optionY + (CIRCLE_SIZE - 8) / 2; // 8 = font height
            int textColor = selected ? theme.textWhite : theme.textLight;
            graphics.drawString(font, options.get(i), textX, textY, textColor, false);
        }
    }

    /**
     * Fills a circle approximated via horizontal scanlines.
     */
    private void fillCircle(GuiGraphics graphics, int cx, int cy, int diameter, int color) {
        int radius = diameter / 2;
        int centerX = cx + radius;
        int centerY = cy + radius;

        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(centerX - dx, centerY + dy, centerX + dx, centerY + dy + 1, color);
        }
    }

    // --- Interaction ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        int baseX = getX();
        int baseY = getY();

        for (int i = 0; i < options.size(); i++) {
            int optionY = baseY + i * (CIRCLE_SIZE + spacing);
            if (mouseX >= baseX && mouseX < baseX + getWidth()
                    && mouseY >= optionY && mouseY < optionY + CIRCLE_SIZE) {
                if (i != selectedIndex) {
                    selectedIndex = i;
                    if (responder != null) {
                        responder.accept(selectedIndex);
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }
}

package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Numeric input with optional +/- buttons.
 * Extends {@link BaseWidget} and uses an {@link EcoEditBox} as a child for the number text.
 * Validates input is numeric, clamps to min/max range.
 *
 * <p>FIX from audit: increment/decrement call setValue() which updates the editBox text,
 * which fires the responder via the editBox's text responder. No double-fire.</p>
 */
public class EcoNumberInput extends BaseWidget {

    private static final int BUTTON_WIDTH = 20;

    private final Theme theme;
    private final Font font;
    private final EcoEditBox editBox;
    private long min;
    private long max;
    private long step;
    private long currentValue;
    private boolean showButtons;
    private @Nullable Consumer<Long> responder;


    public EcoNumberInput(Font font, int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height);
        this.theme = theme;
        this.font = font;
        this.min = 0;
        this.max = Long.MAX_VALUE;
        this.step = 1;
        this.currentValue = 0;
        this.showButtons = true;

        int editX = x + BUTTON_WIDTH;
        int editW = width - BUTTON_WIDTH * 2;
        this.editBox = new EcoEditBox(font, editX + 2, y, editW - 4, height, theme);
        this.editBox.setValue("0");
        this.editBox.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
        this.editBox.setResponder(this::onTextChanged);
        addChild(editBox);
    }

    // --- Fluent API ---

    public EcoNumberInput min(long min) { this.min = min; return this; }
    public EcoNumberInput max(long max) { this.max = max; return this; }
    public EcoNumberInput step(long step) { this.step = step; return this; }

    public EcoNumberInput showButtons(boolean show) {
        this.showButtons = show;
        recalcLayout();
        return this;
    }

    public EcoNumberInput responder(Consumer<Long> responder) {
        this.responder = responder;
        return this;
    }

    public long getValue() { return currentValue; }

    public void setValue(long value) {
        this.currentValue = clamp(value);
        // Setting editBox text will fire onTextChanged -> responder
        this.editBox.setValue(String.valueOf(this.currentValue));
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
    }

    public boolean isEnabled() {
        return super.isEnabled();
    }

    /** The inner EcoEditBox is the focusable widget, not this container. */
    @Override
    public boolean isFocusable() {
        return false;
    }

    // --- Layout ---

    private void recalcLayout() {
        int editX = showButtons ? getX() + BUTTON_WIDTH : getX();
        int editW = showButtons ? getWidth() - BUTTON_WIDTH * 2 : getWidth();
        editBox.setPosition(editX + 2, getY());
        editBox.setSize(editW - 4, getHeight());
    }

    // --- Text change handler ---

    private void onTextChanged(String text) {
        if (text.isEmpty() || text.equals("-")) return;
        try {
            long parsed = Long.parseLong(text);
            long clamped = clamp(parsed);
            if (clamped != parsed) {
                editBox.setValue(String.valueOf(clamped));
                return; // setValue will re-trigger onTextChanged with the clamped value
            }
            currentValue = clamped;
            if (responder != null) {
                responder.accept(currentValue);
            }
        } catch (NumberFormatException ignored) {
            // Invalid input, ignore
        }
    }

    private long clamp(long value) {
        return Math.max(min, Math.min(max, value));
    }

    // increment/decrement call setValue() which updates editBox text,
    // which fires onTextChanged -> responder. NO double-fire.
    private void increment() {
        if (!isEnabled()) return;
        setValue(currentValue + step);
    }

    private void decrement() {
        if (!isEnabled()) return;
        setValue(currentValue - step);
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int bg, border;
        if (!isEnabled()) {
            bg = theme.disabledBg;
            border = theme.disabledBorder;
        } else {
            bg = theme.bgDark;
            border = theme.border;
        }

        // Main background (only when showing buttons — otherwise EcoEditBox draws its own)
        if (showButtons) {
            DrawUtils.drawPanel(graphics, getX(), getY(), getWidth(), getHeight(), bg, border);
        }

        // +/- buttons
        if (showButtons) {
            int btnBg = isEnabled() ? theme.bgMedium : theme.disabledBg;
            int btnText = isEnabled() ? theme.textLight : theme.disabledText;

            // Minus button (left)
            int minusBtnX = getX();
            boolean minusHovered = isEnabled() && mouseX >= minusBtnX && mouseX < minusBtnX + BUTTON_WIDTH
                    && mouseY >= getY() && mouseY < getY() + getHeight();
            int minusBg = minusHovered ? theme.bgLight : btnBg;
            graphics.fill(minusBtnX, getY() + 1, minusBtnX + BUTTON_WIDTH, getY() + getHeight() - 1, minusBg);
            graphics.fill(minusBtnX + BUTTON_WIDTH - 1, getY() + 1, minusBtnX + BUTTON_WIDTH, getY() + getHeight() - 1, border);
            int minusTextX = minusBtnX + (BUTTON_WIDTH - font.width("-")) / 2;
            int textY = getY() + (getHeight() - font.lineHeight) / 2;
            graphics.drawString(font, "-", minusTextX, textY, btnText, false);

            // Plus button (right)
            int plusBtnX = getX() + getWidth() - BUTTON_WIDTH;
            boolean plusHovered = isEnabled() && mouseX >= plusBtnX && mouseX < plusBtnX + BUTTON_WIDTH
                    && mouseY >= getY() && mouseY < getY() + getHeight();
            int plusBg = plusHovered ? theme.bgLight : btnBg;
            graphics.fill(plusBtnX, getY() + 1, plusBtnX + BUTTON_WIDTH, getY() + getHeight() - 1, plusBg);
            graphics.fill(plusBtnX, getY() + 1, plusBtnX + 1, getY() + getHeight() - 1, border);
            int plusTextX = plusBtnX + (BUTTON_WIDTH - font.width("+")) / 2;
            graphics.drawString(font, "+", plusTextX, textY, btnText, false);
        }

        // EcoEditBox renders itself via the widget tree (renderChildren)
    }

    // --- Mouse events ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isEnabled()) return false;

        // Check minus button
        if (showButtons && mouseX >= getX() && mouseX < getX() + BUTTON_WIDTH
                && mouseY >= getY() && mouseY < getY() + getHeight()) {
            decrement();
            return true;
        }

        // Check plus button
        if (showButtons) {
            int plusBtnX = getX() + getWidth() - BUTTON_WIDTH;
            if (mouseX >= plusBtnX && mouseX < plusBtnX + BUTTON_WIDTH
                    && mouseY >= getY() && mouseY < getY() + getHeight()) {
                increment();
                return true;
            }
        }

        // Otherwise let the editBox handle it (via tree event dispatch)
        return false;
    }

    // --- Position updates ---

    @Override
    public void setPosition(int x, int y) {
        super.setPosition(x, y);
        recalcLayout();
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        recalcLayout();
    }
}

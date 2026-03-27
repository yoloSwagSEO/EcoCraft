package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.gui.util.NumberFormat;
import net.ecocraft.gui.util.NumberFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Numeric input with optional +/- buttons.
 * Validates input is numeric, clamps to min/max range.
 * Supports disabled state.
 */
public class NumberInput extends AbstractWidget {

    private static final int BUTTON_WIDTH = 20;

    private final Theme theme;
    private final EditBox editBox;
    private long min;
    private long max;
    private long step;
    private long currentValue;
    private boolean showButtons;
    private @Nullable NumberFormat numberFormat;
    private @Nullable Consumer<Long> responder;
    private boolean enabled = true;

    public NumberInput(Font font, int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height, Component.empty());
        this.theme = theme;
        this.min = 0;
        this.max = Long.MAX_VALUE;
        this.step = 1;
        this.currentValue = 0;
        this.showButtons = true;

        int editX = showButtons ? x + BUTTON_WIDTH : x;
        int editW = showButtons ? width - BUTTON_WIDTH * 2 : width;
        this.editBox = new EditBox(font, editX + 2, y + 2, editW - 4, height - 4, Component.empty());
        this.editBox.setBordered(false);
        this.editBox.setTextColor(theme.textLight & 0x00FFFFFF);
        this.editBox.setValue("0");
        this.editBox.setFilter(s -> s.isEmpty() || s.matches("-?\\d*"));
        this.editBox.setResponder(this::onTextChanged);
    }

    public NumberInput(Font font, int x, int y, int width, int height) {
        this(font, x, y, width, height, Theme.dark());
    }

    public NumberInput min(long min) { this.min = min; return this; }
    public NumberInput max(long max) { this.max = max; return this; }
    public NumberInput step(long step) { this.step = step; return this; }
    public NumberInput showButtons(boolean show) { this.showButtons = show; recalcLayout(); return this; }
    public NumberInput numberFormat(@Nullable NumberFormat format) { this.numberFormat = format; return this; }
    public NumberInput responder(Consumer<Long> responder) { this.responder = responder; return this; }

    public long getValue() { return currentValue; }

    public void setValue(long value) {
        this.currentValue = clamp(value);
        this.editBox.setValue(String.valueOf(this.currentValue));
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.editBox.setEditable(enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    private void recalcLayout() {
        int editX = showButtons ? getX() + BUTTON_WIDTH : getX();
        int editW = showButtons ? width - BUTTON_WIDTH * 2 : width;
        editBox.setX(editX + 2);
        editBox.setWidth(editW - 4);
    }

    private void onTextChanged(String text) {
        if (text.isEmpty() || text.equals("-")) return;
        try {
            long parsed = Long.parseLong(text);
            long clamped = clamp(parsed);
            if (clamped != parsed) {
                editBox.setValue(String.valueOf(clamped));
                return;
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

    private void increment() {
        if (!enabled) return;
        setValue(currentValue + step);
        if (responder != null) responder.accept(currentValue);
    }

    private void decrement() {
        if (!enabled) return;
        setValue(currentValue - step);
        if (responder != null) responder.accept(currentValue);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int bg, border;
        if (!enabled) {
            bg = theme.disabledBg;
            border = theme.disabledBorder;
        } else if (editBox.isFocused()) {
            bg = theme.bgMedium;
            border = theme.borderAccent;
        } else {
            bg = theme.bgDark;
            border = theme.border;
        }

        // Main background
        DrawUtils.drawPanel(graphics, getX(), getY(), width, height, bg, border);

        // +/- buttons
        if (showButtons) {
            int btnBg = enabled ? theme.bgMedium : theme.disabledBg;
            int btnText = enabled ? theme.textLight : theme.disabledText;
            Font font = Minecraft.getInstance().font;

            // Minus button (left)
            int minusBtnX = getX();
            boolean minusHovered = enabled && mouseX >= minusBtnX && mouseX < minusBtnX + BUTTON_WIDTH
                    && mouseY >= getY() && mouseY < getY() + height;
            int minusBg = minusHovered ? theme.bgLight : btnBg;
            graphics.fill(minusBtnX, getY() + 1, minusBtnX + BUTTON_WIDTH, getY() + height - 1, minusBg);
            graphics.fill(minusBtnX + BUTTON_WIDTH - 1, getY() + 1, minusBtnX + BUTTON_WIDTH, getY() + height - 1, border);
            int minusTextX = minusBtnX + (BUTTON_WIDTH - font.width("-")) / 2;
            int textY = getY() + (height - 8) / 2;
            graphics.drawString(font, "-", minusTextX, textY, btnText, false);

            // Plus button (right)
            int plusBtnX = getX() + width - BUTTON_WIDTH;
            boolean plusHovered = enabled && mouseX >= plusBtnX && mouseX < plusBtnX + BUTTON_WIDTH
                    && mouseY >= getY() && mouseY < getY() + height;
            int plusBg = plusHovered ? theme.bgLight : btnBg;
            graphics.fill(plusBtnX, getY() + 1, plusBtnX + BUTTON_WIDTH, getY() + height - 1, plusBg);
            graphics.fill(plusBtnX, getY() + 1, plusBtnX + 1, getY() + height - 1, border);
            int plusTextX = plusBtnX + (BUTTON_WIDTH - font.width("+")) / 2;
            graphics.drawString(font, "+", plusTextX, textY, btnText, false);
        }

        // EditBox
        editBox.renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || button != 0) return false;

        if (showButtons) {
            // Check minus button
            if (mouseX >= getX() && mouseX < getX() + BUTTON_WIDTH
                    && mouseY >= getY() && mouseY < getY() + height) {
                decrement();
                return true;
            }
            // Check plus button
            int plusBtnX = getX() + width - BUTTON_WIDTH;
            if (mouseX >= plusBtnX && mouseX < plusBtnX + BUTTON_WIDTH
                    && mouseY >= getY() && mouseY < getY() + height) {
                increment();
                return true;
            }
        }

        return editBox.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!enabled) return false;
        return editBox.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!enabled) return false;
        return editBox.charTyped(codePoint, modifiers);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        editBox.setFocused(focused);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

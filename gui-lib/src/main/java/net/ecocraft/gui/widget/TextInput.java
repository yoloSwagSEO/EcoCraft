package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Generic themed text input. Wraps an EditBox with automatic text centering
 * regardless of widget height. The text is always vertically and horizontally
 * padded inside the themed border.
 */
public class TextInput extends EditBox {

    private static final int H_PADDING = 4;

    private final Theme theme;
    private final int outerX, outerY, outerW, outerH;
    private boolean enabled = true;

    public TextInput(Font font, int x, int y, int width, int height,
                     Component placeholder, Theme theme) {
        // EditBox without border renders text at (x, y) directly.
        // We offset to center text vertically and add horizontal padding.
        super(font, x + H_PADDING, y + (height - font.lineHeight) / 2,
                width - H_PADDING * 2, font.lineHeight, placeholder);
        this.outerX = x;
        this.outerY = y;
        this.outerW = width;
        this.outerH = height;
        this.theme = theme;
        this.setHint(placeholder);
        this.setBordered(false);
        this.setTextColor(theme.textLight & 0x00FFFFFF);
    }

    public TextInput(Font font, int x, int y, int width, int height, Component placeholder) {
        this(font, x, y, width, height, placeholder, Theme.dark());
    }

    /** Set onChange callback. */
    public TextInput responder(Consumer<String> responder) {
        this.setResponder(responder);
        return this;
    }

    /** Set input validation filter. */
    public TextInput filter(Predicate<String> filter) {
        this.setFilter(filter);
        return this;
    }

    /** Set max character length. */
    public TextInput maxLength(int maxLength) {
        this.setMaxLength(maxLength);
        return this;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.setEditable(enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int bg, border;

        if (!enabled) {
            bg = theme.disabledBg;
            border = theme.disabledBorder;
        } else if (isFocused()) {
            bg = theme.bgMedium;
            border = theme.borderAccent;
        } else {
            bg = theme.bgDark;
            border = theme.border;
        }

        DrawUtils.drawPanel(graphics, outerX, outerY, outerW, outerH, bg, border);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || button != 0) return false;
        if (mouseX >= outerX && mouseX < outerX + outerW
                && mouseY >= outerY && mouseY < outerY + outerH) {
            this.setFocused(true);
            // Position cursor at click location
            super.mouseClicked(mouseX, getY(), button);
            return true;
        }
        this.setFocused(false);
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!enabled) return false;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!enabled) return false;
        return super.charTyped(codePoint, modifiers);
    }
}

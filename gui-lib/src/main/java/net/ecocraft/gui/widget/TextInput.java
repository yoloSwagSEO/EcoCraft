package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Generic themed text input. Replaces EcoSearchBar.
 * Supports placeholder, optional filter, responder callback, and disabled state.
 */
public class TextInput extends EditBox {

    private final Theme theme;
    private boolean enabled = true;

    public TextInput(Font font, int x, int y, int width, int height,
                     Component placeholder, Theme theme) {
        super(font, x, y, width, height, placeholder);
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

        graphics.fill(getX() - 2, getY() - 2, getX() + width + 2, getY() + height + 2, bg);
        graphics.fill(getX() - 2, getY() - 2, getX() + width + 2, getY() - 1, border);
        graphics.fill(getX() - 2, getY() + height + 1, getX() + width + 2, getY() + height + 2, border);
        graphics.fill(getX() - 2, getY() - 2, getX() - 1, getY() + height + 2, border);
        graphics.fill(getX() + width + 1, getY() - 2, getX() + width + 2, getY() + height + 2, border);

        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled) return false;
        return super.mouseClicked(mouseX, mouseY, button);
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

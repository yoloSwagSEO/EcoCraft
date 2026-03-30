package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Themed text input widget that wraps an {@link EcoEditBox} as a child in the widget tree.
 * EcoTextInput is a thin container — the inner EcoEditBox handles all text editing,
 * focus, cursor, selection, and renders its own themed background.
 */
public class EcoTextInput extends BaseWidget {

    private static final int H_PADDING = 4;

    private final Theme theme;
    private final EcoEditBox editBox;


    public EcoTextInput(Font font, int x, int y, int width, int height, Component placeholder, Theme theme) {
        super(x, y, width, height);
        this.theme = theme;
        // EcoEditBox positioned inside with horizontal padding
        this.editBox = new EcoEditBox(font, x + H_PADDING, y, width - H_PADDING * 2, height, theme);
        this.editBox.setHint(placeholder.getString());
        addChild(editBox);
    }

    // --- Delegate methods to editBox ---

    public void setValue(String value) { editBox.setValue(value); }
    public String getValue() { return editBox.getValue(); }

    public EcoTextInput setHint(Component hint) { editBox.setHint(hint.getString()); return this; }
    public EcoTextInput setHint(String hint) { editBox.setHint(hint); return this; }

    public EcoTextInput setFilter(Predicate<String> filter) { editBox.setFilter(filter); return this; }
    public EcoTextInput setMaxLength(int max) { editBox.setMaxLength(max); return this; }
    public EcoTextInput responder(Consumer<String> responder) { editBox.setResponder(responder); return this; }

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

    /** Dispatch clicks to the inner EcoEditBox (needed when inside ScrollPane). */
    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (containsPoint(mouseX, mouseY)) {
            return editBox.onMouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: EcoEditBox renders its own themed background and text.
        // Children (the EcoEditBox) are rendered by the widget tree via renderChildren().
    }

    @Override
    public void setPosition(int x, int y) {
        super.setPosition(x, y);
        editBox.setPosition(x + H_PADDING, y);
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        editBox.setSize(width - H_PADDING * 2, height);
    }
}

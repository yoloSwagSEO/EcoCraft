package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Modal dialog overlay (V2), extending {@link BaseWidget}.
 * Renders a darkened background with a centered panel containing title, body text, and buttons.
 * <p>
 * Three factory methods: {@link #alert}, {@link #confirm}, {@link #input}.
 * <p>
 * Designed to be used as a <b>portal</b> in the widget tree:
 * the consumer calls {@code tree.addPortal(dialog)} after creation.
 * <p>
 * FIX from audit: all close paths go through {@link #close()} (no manual {@code closed = true}).
 */
public class EcoDialog extends BaseWidget {

    private static final int OVERLAY_COLOR = 0x80000000; // 50% black
    private static final int DIALOG_WIDTH = 250;
    private static final int PADDING = 16;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;

    private final Theme theme;
    private final Component title;
    private final Component body;
    private final @Nullable EcoButton confirmButton;
    private final @Nullable EcoButton cancelButton;
    private final @Nullable EcoNumberInput numberInput;
    private final @Nullable Runnable onClose;
    private boolean closed = false;

    private int dialogX, dialogY, dialogW, dialogH;

    private EcoDialog(Theme theme, Component title, Component body,
                      @Nullable EcoButton confirmButton, @Nullable EcoButton cancelButton,
                      @Nullable EcoNumberInput numberInput, @Nullable Runnable onClose,
                      int screenWidth, int screenHeight) {
        super(0, 0, screenWidth, screenHeight);
        this.theme = theme;
        this.title = title;
        this.body = body;
        this.confirmButton = confirmButton;
        this.cancelButton = cancelButton;
        this.numberInput = numberInput;
        this.onClose = onClose;
        setModal(true);

        // Add child widgets to the tree
        if (cancelButton != null) addChild(cancelButton);
        if (confirmButton != null) addChild(confirmButton);
        if (numberInput != null) addChild(numberInput);

        recalcLayout(screenWidth, screenHeight);
    }

    public boolean isClosed() {
        return closed;
    }

    private void close() {
        closed = true;
        setVisible(false);
        if (onClose != null) onClose.run();
    }

    @Override
    public boolean isModal() {
        return true;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    private void recalcLayout(int screenWidth, int screenHeight) {
        Font font = Minecraft.getInstance().font;
        dialogW = DIALOG_WIDTH;

        // Calculate height
        int contentH = PADDING; // top padding
        contentH += 12; // title
        contentH += 8; // gap after title
        contentH += 2; // accent separator
        contentH += 8; // gap after separator
        // Body text (word-wrapped)
        var bodyLines = font.split(body, dialogW - PADDING * 2);
        contentH += bodyLines.size() * 10;
        contentH += 12; // gap before buttons/input
        if (numberInput != null) {
            contentH += 24; // number input height
            contentH += 8; // gap
        }
        contentH += BUTTON_HEIGHT; // buttons
        contentH += PADDING; // bottom padding

        dialogH = contentH;
        dialogX = (screenWidth - dialogW) / 2;
        dialogY = (screenHeight - dialogH) / 2;

        // Position buttons
        int btnY = dialogY + dialogH - PADDING - BUTTON_HEIGHT;

        if (confirmButton != null && cancelButton != null) {
            int totalBtnWidth = dialogW - PADDING * 2;
            int singleBtnWidth = (totalBtnWidth - BUTTON_GAP) / 2;
            cancelButton.setPosition(dialogX + PADDING, btnY);
            cancelButton.setSize(singleBtnWidth, BUTTON_HEIGHT);
            confirmButton.setPosition(dialogX + PADDING + singleBtnWidth + BUTTON_GAP, btnY);
            confirmButton.setSize(singleBtnWidth, BUTTON_HEIGHT);
        } else if (confirmButton != null) {
            int btnWidth = 80;
            confirmButton.setPosition(dialogX + (dialogW - btnWidth) / 2, btnY);
            confirmButton.setSize(btnWidth, BUTTON_HEIGHT);
        }

        // Position number input
        if (numberInput != null) {
            int inputY = btnY - 24 - 8;
            numberInput.setPosition(dialogX + PADDING, inputY);
            numberInput.setSize(dialogW - PADDING * 2, 20);
        }
    }

    // --- Factory methods ---

    /** Informational dialog with a single OK button. */
    public static EcoDialog alert(Component title, Component body, Runnable onClose) {
        return alert(Theme.dark(), title, body, onClose);
    }

    public static EcoDialog alert(Theme theme, Component title, Component body, Runnable onClose) {
        var mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        var dialog = new EcoDialog[1];
        EcoButton okButton = EcoButton.primary(theme, Component.literal("OK"), () -> dialog[0].close());

        dialog[0] = new EcoDialog(theme, title, body, okButton, null, null, onClose, sw, sh);
        return dialog[0];
    }

    /** Confirmation dialog with configurable Yes/No buttons. */
    public static EcoDialog confirm(Component title, Component body,
                                    Component confirmLabel, Component cancelLabel,
                                    Runnable onConfirm, Runnable onCancel) {
        return confirm(Theme.dark(), title, body, confirmLabel, cancelLabel, onConfirm, onCancel);
    }

    public static EcoDialog confirm(Theme theme, Component title, Component body,
                                    Component confirmLabel, Component cancelLabel,
                                    Runnable onConfirm, Runnable onCancel) {
        var mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        var dialog = new EcoDialog[1];
        EcoButton confirmBtn = EcoButton.success(theme, confirmLabel, () -> {
            dialog[0].close();
            onConfirm.run();
        });
        EcoButton cancelBtn = EcoButton.ghost(theme, cancelLabel, () -> {
            dialog[0].close();
            onCancel.run();
        });

        dialog[0] = new EcoDialog(theme, title, body, confirmBtn, cancelBtn, null, onCancel, sw, sh);
        return dialog[0];
    }

    /** Input dialog with a NumberInput field and OK/Cancel buttons. */
    public static EcoDialog input(Component title, Component body,
                                  Component submitLabel, Component cancelLabel,
                                  Consumer<Long> onSubmit, Runnable onCancel) {
        return input(Theme.dark(), title, body, submitLabel, cancelLabel, onSubmit, onCancel);
    }

    public static EcoDialog input(Theme theme, Component title, Component body,
                                  Component submitLabel, Component cancelLabel,
                                  Consumer<Long> onSubmit, Runnable onCancel) {
        var mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        Font font = mc.font;
        EcoNumberInput numInput = new EcoNumberInput(font, 0, 0, 100, 20, theme);
        numInput.min(0).max(Long.MAX_VALUE).step(1);

        var dialog = new EcoDialog[1];
        EcoButton confirmBtn = EcoButton.success(theme, submitLabel, () -> {
            dialog[0].close();
            onSubmit.accept(numInput.getValue());
        });
        EcoButton cancelBtn = EcoButton.ghost(theme, cancelLabel, () -> {
            dialog[0].close();
            onCancel.run();
        });

        dialog[0] = new EcoDialog(theme, title, body, confirmBtn, cancelBtn, numInput, onCancel, sw, sh);
        return dialog[0];
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (closed) return;

        Font font = Minecraft.getInstance().font;

        // Darkened overlay
        graphics.fill(0, 0, getWidth(), getHeight(), OVERLAY_COLOR);

        // Dialog panel
        DrawUtils.drawPanel(graphics, dialogX, dialogY, dialogW, dialogH, theme.bgDark, theme.borderAccent);

        // Title
        int titleX = dialogX + PADDING;
        int titleY = dialogY + PADDING;
        graphics.drawString(font, title, titleX, titleY, theme.accent, false);

        // Accent separator under title
        DrawUtils.drawAccentSeparator(graphics, dialogX + PADDING, titleY + 12 + 4, dialogW - PADDING * 2, theme);

        // Body text (word-wrapped)
        int bodyY = titleY + 12 + 8 + 2 + 8;
        var bodyLines = font.split(body, dialogW - PADDING * 2);
        for (var line : bodyLines) {
            graphics.drawString(font, line, dialogX + PADDING, bodyY, theme.textLight, false);
            bodyY += 10;
        }

        // Children (buttons, number input) are rendered by the widget tree via renderChildren()
    }

    // --- Event handling ---

    @Override
    public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (closed) return false;

        // Escape closes the dialog
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            close();
            return true;
        }

        return false;
    }

    @Override
    public boolean onCharTyped(char codePoint, int modifiers) {
        if (closed) return false;
        return false;
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (closed) return false;
        // Modal: consume all clicks (children are dispatched by the tree before this)
        return true;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (closed) return false;
        // Block scroll from reaching widgets behind
        return true;
    }
}

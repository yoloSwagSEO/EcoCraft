package net.ecocraft.gui.dialog;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.gui.widget.Button;
import net.ecocraft.gui.widget.NumberInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Modal dialog overlay. Renders a darkened background with a centered panel.
 * Three factory methods: alert(), confirm(), input().
 * Handles Escape key to close. Blocks input to widgets behind.
 */
public class Dialog extends AbstractWidget {

    private static final int OVERLAY_COLOR = 0x80000000; // 50% black
    private static final int DIALOG_WIDTH = 250;
    private static final int PADDING = 16;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;

    private final Theme theme;
    private final Component title;
    private final Component body;
    private final @Nullable Button confirmButton;
    private final @Nullable Button cancelButton;
    private final @Nullable NumberInput numberInput;
    private final @Nullable Runnable onClose;
    private boolean closed = false;

    private int dialogX, dialogY, dialogW, dialogH;

    private Dialog(Theme theme, Component title, Component body,
                   @Nullable Button confirmButton, @Nullable Button cancelButton,
                   @Nullable NumberInput numberInput, @Nullable Runnable onClose,
                   int screenWidth, int screenHeight) {
        super(0, 0, screenWidth, screenHeight, Component.empty());
        this.theme = theme;
        this.title = title;
        this.body = body;
        this.confirmButton = confirmButton;
        this.cancelButton = cancelButton;
        this.numberInput = numberInput;
        this.onClose = onClose;
        recalcLayout(screenWidth, screenHeight);
    }

    public boolean isClosed() {
        return closed;
    }

    private void close() {
        closed = true;
        if (onClose != null) onClose.run();
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
            cancelButton.setX(dialogX + PADDING);
            cancelButton.setY(btnY);
            cancelButton.setWidth(singleBtnWidth);
            cancelButton.setHeight(BUTTON_HEIGHT);
            confirmButton.setX(dialogX + PADDING + singleBtnWidth + BUTTON_GAP);
            confirmButton.setY(btnY);
            confirmButton.setWidth(singleBtnWidth);
            confirmButton.setHeight(BUTTON_HEIGHT);
        } else if (confirmButton != null) {
            int btnWidth = 80;
            confirmButton.setX(dialogX + (dialogW - btnWidth) / 2);
            confirmButton.setY(btnY);
            confirmButton.setWidth(btnWidth);
            confirmButton.setHeight(BUTTON_HEIGHT);
        }

        // Position number input
        if (numberInput != null) {
            int inputY = btnY - 24 - 8;
            numberInput.setX(dialogX + PADDING);
            numberInput.setY(inputY);
            numberInput.setWidth(dialogW - PADDING * 2);
            numberInput.setHeight(20);
        }
    }

    // --- Factory methods ---

    /** Informational dialog with a single OK button. */
    public static Dialog alert(Component title, Component body, Runnable onClose) {
        return alert(Theme.dark(), title, body, onClose);
    }

    public static Dialog alert(Theme theme, Component title, Component body, Runnable onClose) {
        var mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        var dialog = new Dialog[1];
        Button okButton = Button.primary(theme, Component.literal("OK"), () -> {
            dialog[0].closed = true;
            onClose.run();
        });

        dialog[0] = new Dialog(theme, title, body, okButton, null, null, onClose, sw, sh);
        return dialog[0];
    }

    /** Confirmation dialog with configurable Yes/No buttons. */
    public static Dialog confirm(Component title, Component body,
                                 Component confirmLabel, Component cancelLabel,
                                 Runnable onConfirm, Runnable onCancel) {
        return confirm(Theme.dark(), title, body, confirmLabel, cancelLabel, onConfirm, onCancel);
    }

    public static Dialog confirm(Theme theme, Component title, Component body,
                                 Component confirmLabel, Component cancelLabel,
                                 Runnable onConfirm, Runnable onCancel) {
        var mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        var dialog = new Dialog[1];
        Button confirmBtn = Button.success(theme, confirmLabel, () -> {
            dialog[0].closed = true;
            onConfirm.run();
        });
        Button cancelBtn = Button.ghost(theme, cancelLabel, () -> {
            dialog[0].closed = true;
            onCancel.run();
        });

        dialog[0] = new Dialog(theme, title, body, confirmBtn, cancelBtn, null, onCancel, sw, sh);
        return dialog[0];
    }

    /** Input dialog with a NumberInput field and OK/Cancel buttons. */
    public static Dialog input(Component title, Component body,
                               @Nullable net.ecocraft.gui.util.NumberFormat format,
                               Component submitLabel, Component cancelLabel,
                               Consumer<Long> onSubmit, Runnable onCancel) {
        return input(Theme.dark(), title, body, format, submitLabel, cancelLabel, onSubmit, onCancel);
    }

    public static Dialog input(Theme theme, Component title, Component body,
                               @Nullable net.ecocraft.gui.util.NumberFormat format,
                               Component submitLabel, Component cancelLabel,
                               Consumer<Long> onSubmit, Runnable onCancel) {
        var mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        Font font = mc.font;
        NumberInput numInput = new NumberInput(font, 0, 0, 100, 20, theme);
        if (format != null) numInput.numberFormat(format);
        numInput.min(0).max(Long.MAX_VALUE).step(1);

        var dialog = new Dialog[1];
        Button confirmBtn = Button.success(theme, submitLabel, () -> {
            dialog[0].closed = true;
            onSubmit.accept(numInput.getValue());
        });
        Button cancelBtn = Button.ghost(theme, cancelLabel, () -> {
            dialog[0].closed = true;
            onCancel.run();
        });

        dialog[0] = new Dialog(theme, title, body, confirmBtn, cancelBtn, numInput, onCancel, sw, sh);
        return dialog[0];
    }

    // --- Rendering ---

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (closed) return;

        Font font = Minecraft.getInstance().font;

        // Darkened overlay
        graphics.fill(0, 0, width, height, OVERLAY_COLOR);

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

        // Number input
        if (numberInput != null) {
            numberInput.renderWidget(graphics, mouseX, mouseY, partialTick);
        }

        // Buttons
        if (cancelButton != null) {
            cancelButton.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
        if (confirmButton != null) {
            confirmButton.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }

    // --- Input handling (blocks everything behind) ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closed) return false;

        // Forward to child widgets
        if (numberInput != null && numberInput.mouseClicked(mouseX, mouseY, button)) return true;
        if (confirmButton != null && confirmButton.isMouseOver(mouseX, mouseY)) {
            confirmButton.onClick(mouseX, mouseY);
            return true;
        }
        if (cancelButton != null && cancelButton.isMouseOver(mouseX, mouseY)) {
            cancelButton.onClick(mouseX, mouseY);
            return true;
        }

        // Block click from reaching widgets behind
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closed) return false;

        // Escape closes the dialog
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            close();
            return true;
        }

        // Forward to number input
        if (numberInput != null) {
            return numberInput.keyPressed(keyCode, scanCode, modifiers);
        }

        // Block key events
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (closed) return false;

        if (numberInput != null) {
            return numberInput.charTyped(codePoint, modifiers);
        }

        return true;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // Dialog covers the entire screen
        return !closed;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

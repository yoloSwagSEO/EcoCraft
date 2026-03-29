package net.ecocraft.mail.screen;

import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.mail.network.payload.SendMailPayload;
import net.ecocraft.mail.network.payload.SendMailResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Compose view for sending a new mail.
 * Contains recipient, subject, body, item count from hand, currency, COD toggle, and send/cancel buttons.
 */
public class MailComposeView extends BaseWidget {

    private static final Theme THEME = MailboxScreen.THEME;
    private static final int LABEL_HEIGHT = 12;
    private static final int FIELD_HEIGHT = 18;
    private static final int GAP = 6;

    private final MailboxScreen screen;

    // Form fields
    private EcoTextInput recipientInput;
    private EcoTextInput subjectInput;
    private EcoTextArea bodyArea;
    private EcoNumberInput itemCountInput;
    private EcoNumberInput currencyInput;
    private EcoToggle codToggle;
    private EcoNumberInput codAmountInput;
    private EcoButton sendButton;
    private EcoButton cancelButton;

    // State
    private Label statusLabel;

    public MailComposeView(MailboxScreen screen, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.screen = screen;
        buildWidgets();
    }

    private void buildWidgets() {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int padding = 8;
        int innerX = x + padding;
        int innerW = w - padding * 2;

        int currentY = y + padding;

        // --- Header ---
        Panel headerPanel = new Panel(x, y, w, 30, THEME);
        headerPanel.padding(6);
        headerPanel.title(Component.translatable("ecocraft_mail.compose.header"), font);
        headerPanel.titleUppercase(false);
        addChild(headerPanel);

        cancelButton = EcoButton.ghost(THEME, Component.translatable("ecocraft_mail.button.cancel"), () -> screen.showListView());
        cancelButton.setBounds(innerX, y + 8, 70, 16);
        headerPanel.addChild(cancelButton);

        currentY = y + 34;

        // --- Destinataire ---
        Label recipientLabel = new Label(font, innerX, currentY, Component.translatable("ecocraft_mail.compose.recipient"), THEME);
        recipientLabel.setColor(THEME.textLight);
        addChild(recipientLabel);
        currentY += LABEL_HEIGHT + 2;

        recipientInput = new EcoTextInput(font, innerX, currentY, innerW, FIELD_HEIGHT,
                Component.translatable("ecocraft_mail.compose.recipient_placeholder"), THEME);
        recipientInput.setMaxLength(64);
        addChild(recipientInput);
        currentY += FIELD_HEIGHT + GAP;

        // --- Objet ---
        Label subjectLabel = new Label(font, innerX, currentY, Component.translatable("ecocraft_mail.compose.subject"), THEME);
        subjectLabel.setColor(THEME.textLight);
        addChild(subjectLabel);
        currentY += LABEL_HEIGHT + 2;

        subjectInput = new EcoTextInput(font, innerX, currentY, innerW, FIELD_HEIGHT,
                Component.translatable("ecocraft_mail.compose.subject_placeholder"), THEME);
        subjectInput.setMaxLength(128);
        addChild(subjectInput);
        currentY += FIELD_HEIGHT + GAP;

        // --- Message ---
        Label bodyLabel = new Label(font, innerX, currentY, Component.translatable("ecocraft_mail.compose.message"), THEME);
        bodyLabel.setColor(THEME.textLight);
        addChild(bodyLabel);
        currentY += LABEL_HEIGHT + 2;

        int bodyHeight = 60;
        bodyArea = new EcoTextArea(font, innerX, currentY, innerW, bodyHeight, THEME);
        bodyArea.setMaxLength(2000);
        addChild(bodyArea);
        currentY += bodyHeight + GAP;

        // --- Item attachment (simplified: count from main hand) ---
        int halfW = (innerW - GAP) / 2;

        Label itemLabel = new Label(font, innerX, currentY,
                Component.translatable("ecocraft_mail.compose.items_label"), THEME);
        itemLabel.setColor(THEME.textLight);
        addChild(itemLabel);
        currentY += LABEL_HEIGHT + 2;

        itemCountInput = new EcoNumberInput(font, innerX, currentY, halfW, FIELD_HEIGHT, THEME);
        itemCountInput.min(0).max(64).step(1);
        itemCountInput.setValue(0);
        addChild(itemCountInput);
        currentY += FIELD_HEIGHT + GAP;

        // --- Currency ---
        Label currencyLabel = new Label(font, innerX, currentY, Component.translatable("ecocraft_mail.compose.currency_label"), THEME);
        currencyLabel.setColor(THEME.textLight);
        addChild(currencyLabel);
        currentY += LABEL_HEIGHT + 2;

        currencyInput = new EcoNumberInput(font, innerX, currentY, halfW, FIELD_HEIGHT, THEME);
        currencyInput.min(0).max(999999999).step(1);
        currencyInput.setValue(0);
        addChild(currencyInput);
        currentY += FIELD_HEIGHT + GAP;

        // --- COD toggle + amount ---
        Label codLabel = new Label(font, innerX, currentY, Component.translatable("ecocraft_mail.compose.cod_label"), THEME);
        codLabel.setColor(THEME.textLight);
        addChild(codLabel);
        currentY += LABEL_HEIGHT + 2;

        int toggleW = 40;
        int toggleH = 16;
        codToggle = new EcoToggle(innerX, currentY, toggleW, toggleH, THEME);
        codToggle.value(false);
        codToggle.showLabels(true);
        codToggle.responder(enabled -> {
            codAmountInput.setEnabled(enabled);
            if (!enabled) codAmountInput.setValue(0);
        });
        addChild(codToggle);

        codAmountInput = new EcoNumberInput(font, innerX + toggleW + GAP, currentY, halfW - toggleW - GAP, FIELD_HEIGHT, THEME);
        codAmountInput.min(0).max(999999999).step(1);
        codAmountInput.setValue(0);
        codAmountInput.setEnabled(false);
        addChild(codAmountInput);

        currentY += FIELD_HEIGHT + GAP + 4;

        // --- Send button + status label ---
        sendButton = EcoButton.success(THEME, Component.translatable("ecocraft_mail.button.send"), this::onSend);
        sendButton.setBounds(innerX, currentY, 90, 20);
        addChild(sendButton);

        statusLabel = new Label(font, innerX + 100, currentY + 4, Component.literal(""), THEME);
        statusLabel.setColor(THEME.textDim);
        addChild(statusLabel);
    }

    private void onSend() {
        String recipient = recipientInput.getValue().trim();
        String subject = subjectInput.getValue().trim();
        String body = bodyArea.getValue();

        // Validation
        if (recipient.isEmpty()) {
            showStatus(Component.translatable("ecocraft_mail.compose.error_recipient").getString(), THEME.danger);
            return;
        }
        if (subject.isEmpty()) {
            showStatus(Component.translatable("ecocraft_mail.compose.error_subject").getString(), THEME.danger);
            return;
        }

        long itemCount = itemCountInput.getValue();
        long currency = currencyInput.getValue();
        long codAmount = codToggle.getValue() ? codAmountInput.getValue() : 0;

        // Build inventory slot list: if itemCount > 0, we send the main hand slot index (36 = main hand in player inventory)
        // The server handler will extract items from the player's main hand
        List<Integer> slots = List.of();
        if (itemCount > 0) {
            // Slot index for main hand depends on selected hotbar slot
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                int hotbarSlot = mc.player.getInventory().selected;
                slots = List.of(hotbarSlot);
            }
        }

        showStatus(Component.translatable("ecocraft_mail.compose.sending").getString(), THEME.textDim);
        sendButton.setEnabled(false);

        PacketDistributor.sendToServer(new SendMailPayload(
                recipient, subject, body, slots, currency, codAmount
        ));
    }

    /**
     * Called when server responds with send result.
     */
    public void onReceiveSendResult(SendMailResultPayload payload) {
        sendButton.setEnabled(true);
        if (payload.success()) {
            // Show success toast and return to list
            EcoToast toast = EcoToast.builder(THEME)
                    .title(Component.translatable("ecocraft_mail.compose.sent").getString())
                    .message(payload.message())
                    .level(net.ecocraft.gui.core.ToastLevel.SUCCESS)
                    .animation(net.ecocraft.gui.core.ToastAnimation.SLIDE_RIGHT)
                    .duration(3000)
                    .dismissOnClick(true)
                    .build();
            EcoToastManager.getInstance().show(toast);
            screen.showListView();
        } else {
            showStatus(payload.message(), THEME.danger);
        }
    }

    private void showStatus(String text, int color) {
        statusLabel = findStatusLabel();
        if (statusLabel != null) {
            // Update existing label text by removing and re-adding
            // Label doesn't have setText, so we update the color at least
            // For simplicity, we just set the component directly
        }
        // Since Label doesn't expose setText, we use a workaround via the widget
        // The status is shown inline; for V1 a toast on error is sufficient
        EcoToast toast = EcoToast.builder(THEME)
                .title(text)
                .message("")
                .level(color == THEME.danger ? net.ecocraft.gui.core.ToastLevel.ERROR : net.ecocraft.gui.core.ToastLevel.INFO)
                .animation(net.ecocraft.gui.core.ToastAnimation.SLIDE_RIGHT)
                .duration(3000)
                .dismissOnClick(true)
                .build();
        EcoToastManager.getInstance().show(toast);
    }

    private Label findStatusLabel() {
        return statusLabel;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        // Background drawn by MailboxScreen panel
    }
}

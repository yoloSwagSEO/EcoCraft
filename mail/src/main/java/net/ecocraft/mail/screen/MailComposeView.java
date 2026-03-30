package net.ecocraft.mail.screen;

import net.ecocraft.api.currency.CurrencyFormatter;
import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.mail.network.payload.DraftsResponsePayload;
import net.ecocraft.mail.network.payload.SaveDraftPayload;
import net.ecocraft.mail.network.payload.SendMailPayload;
import net.ecocraft.mail.network.payload.SendMailResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Compose view for sending a new mail.
 * Left column: form fields (recipient, subject, body, currency, COD).
 * Right column: inventory grid + "À envoyer" attachment grid.
 * Footer: centered Cancel + Send buttons.
 */
public class MailComposeView extends BaseWidget {

    private static final Theme THEME = MailboxScreen.THEME;
    private static final int LABEL_HEIGHT = 12;
    private static final int FIELD_HEIGHT = 16;
    private static final int GAP = 5;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 42;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 2;
    private static final int ATTACH_COLS = 9;

    private final MailboxScreen screen;

    // Form fields
    private EcoTextInput recipientInput;
    private EcoTextInput subjectInput;
    private EcoTextArea bodyArea;
    private EcoNumberInput currencyInput;
    private EcoToggle codToggle;
    private EcoNumberInput codAmountInput;
    private EcoToggle readReceiptToggle;

    // Inventory
    private EcoInventoryGrid inventoryGrid;

    // Selected slots for attachment
    private final List<Integer> selectedSlots = new ArrayList<>();

    // Attachment grid bounds
    private int attachGridX, attachGridY, attachGridW, attachGridH;
    private int attachVisibleRows;
    private int attachScrollOffset = 0;

    // Confirmation state
    private boolean awaitingConfirmation = false;
    private long confirmationTimestamp = 0;
    private EcoButton sendButton;
    private EcoButton confirmButton;

    // Draft loaded from
    private String loadedDraftId = null;

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
        int h = getHeight();
        int padding = 6;

        // --- Header ---
        Label titleLabel = new Label(font, x + padding, y + 6,
                Component.translatable("ecocraft_mail.compose.header"), THEME);
        titleLabel.setColor(THEME.accent);
        addChild(titleLabel);

        // --- Layout: left form (55%) + right inventory (45%) ---
        int contentY = y + HEADER_HEIGHT + 2;
        int contentH = h - HEADER_HEIGHT - FOOTER_HEIGHT - 4;
        int colGap = 10;

        // Right column: need at least 9 * (SLOT_SIZE + SLOT_GAP) + padding for inventory
        int minRightW = ATTACH_COLS * (SLOT_SIZE + SLOT_GAP) + 16;
        int rightW = Math.max(minRightW, (int) ((w - padding * 2 - colGap) * 0.42));
        int leftW = w - padding * 2 - colGap - rightW;
        int leftX = x + padding;
        int rightX = leftX + leftW + colGap;

        buildLeftColumn(font, leftX, contentY, leftW, contentH);
        buildRightColumn(font, rightX, contentY, rightW, contentH);
        buildFooter(font, x, y + h - 26, w);
    }

    private void buildLeftColumn(Font font, int leftX, int startY, int leftW, int contentH) {
        int currentY = startY;

        // Destinataire
        Label recipientLabel = new Label(font, leftX, currentY,
                Component.translatable("ecocraft_mail.compose.recipient"), THEME);
        recipientLabel.setColor(THEME.textLight);
        addChild(recipientLabel);
        currentY += LABEL_HEIGHT + 2;

        recipientInput = new EcoTextInput(font, leftX, currentY, leftW, FIELD_HEIGHT,
                Component.translatable("ecocraft_mail.compose.recipient_placeholder"), THEME);
        recipientInput.setMaxLength(64);
        addChild(recipientInput);
        currentY += FIELD_HEIGHT + GAP;

        // Objet
        Label subjectLabel = new Label(font, leftX, currentY,
                Component.translatable("ecocraft_mail.compose.subject"), THEME);
        subjectLabel.setColor(THEME.textLight);
        addChild(subjectLabel);
        currentY += LABEL_HEIGHT + 2;

        subjectInput = new EcoTextInput(font, leftX, currentY, leftW, FIELD_HEIGHT,
                Component.translatable("ecocraft_mail.compose.subject_placeholder"), THEME);
        subjectInput.setMaxLength(128);
        addChild(subjectInput);
        currentY += FIELD_HEIGHT + GAP;

        // Message
        Label bodyLabel = new Label(font, leftX, currentY,
                Component.translatable("ecocraft_mail.compose.message"), THEME);
        bodyLabel.setColor(THEME.textLight);
        addChild(bodyLabel);
        currentY += LABEL_HEIGHT + 2;

        // Body: fill remaining space minus currency + COD + read receipt rows
        int currencyCodH = (LABEL_HEIGHT + 2 + FIELD_HEIGHT + GAP) * 2 + 4;
        // Add space for read receipt toggle if allowed
        if (screen.allowReadReceipt) {
            currencyCodH += FIELD_HEIGHT + GAP;
        }
        int bodyHeight = contentH - (currentY - startY) - currencyCodH;
        if (bodyHeight < 40) bodyHeight = 40;

        bodyArea = new EcoTextArea(font, leftX, currentY, leftW, bodyHeight, THEME);
        bodyArea.setMaxLength(2000);
        addChild(bodyArea);
        currentY += bodyHeight + GAP;

        // Montant
        int halfLeftW = (leftW - GAP) / 2;
        Label currencyLabel = new Label(font, leftX, currentY,
                Component.translatable("ecocraft_mail.compose.currency_label"), THEME);
        currencyLabel.setColor(THEME.textLight);
        addChild(currencyLabel);
        currentY += LABEL_HEIGHT + 2;

        currencyInput = new EcoNumberInput(font, leftX, currentY, halfLeftW, FIELD_HEIGHT, THEME);
        currencyInput.min(0).max(999999999).step(1).showButtons(true);
        currencyInput.setValue(0);
        addChild(currencyInput);
        currentY += FIELD_HEIGHT + GAP;

        // COD
        Label codLabel = new Label(font, leftX, currentY,
                Component.translatable("ecocraft_mail.compose.cod_label"), THEME);
        codLabel.setColor(THEME.textLight);
        addChild(codLabel);
        currentY += LABEL_HEIGHT + 2;

        int toggleW = 40;
        codToggle = new EcoToggle(leftX, currentY, toggleW, 14, THEME);
        codToggle.value(false);
        codToggle.showLabels(true);
        codToggle.responder(enabled -> {
            codAmountInput.setEnabled(enabled);
            if (!enabled) codAmountInput.setValue(0);
        });
        addChild(codToggle);

        int codInputW = Math.max(60, halfLeftW - toggleW - GAP);
        codAmountInput = new EcoNumberInput(font, leftX + toggleW + GAP, currentY, codInputW, FIELD_HEIGHT, THEME);
        codAmountInput.min(0).max(999999999).step(1).showButtons(true);
        codAmountInput.setValue(0);
        codAmountInput.setEnabled(false);
        addChild(codAmountInput);
        currentY += FIELD_HEIGHT + GAP;

        // Read receipt toggle (only if allowed)
        if (screen.allowReadReceipt) {
            String receiptLabel = Component.translatable("ecocraft_mail.compose.read_receipt").getString();
            if (screen.readReceiptCost > 0) {
                receiptLabel += " (" + CurrencyFormatter.format(screen.readReceiptCost, screen.currency) + ")";
            }
            Label readReceiptLabel = new Label(font, leftX, currentY + 2,
                    Component.literal(receiptLabel), THEME);
            readReceiptLabel.setColor(THEME.textLight);
            addChild(readReceiptLabel);

            readReceiptToggle = new EcoToggle(leftX + font.width(receiptLabel) + 6, currentY, toggleW, 14, THEME);
            readReceiptToggle.value(false);
            readReceiptToggle.showLabels(true);
            addChild(readReceiptToggle);
        }
    }

    private void buildRightColumn(Font font, int rightX, int startY, int rightW, int contentH) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        // Calculate attach section height based on config max
        int maxSlots = screen.maxItemAttachments;
        int maxRows = (maxSlots + ATTACH_COLS - 1) / ATTACH_COLS;
        attachVisibleRows = Math.min(maxRows, 3); // show up to 3 rows, scroll beyond
        int attachLabelH = font.lineHeight + 6;
        int attachRowsH = attachVisibleRows * (SLOT_SIZE + SLOT_GAP);
        int attachTotalH = attachLabelH + attachRowsH + 8;

        // Inventory grid takes remaining space
        int invH = contentH - attachTotalH;

        inventoryGrid = EcoInventoryGrid.builder()
                .inventory(player.getInventory())
                .columns(ATTACH_COLS)
                .slotSize(EcoInventoryGrid.SlotSize.MEDIUM)
                .scrollable(true)
                .showMain(true)
                .showHotbar(true)
                .showArmor(true)
                .showOffhand(true)
                .showOther(false)
                .alignLeft(true)
                .onSlotClicked(this::onInventorySlotClicked)
                .theme(THEME)
                .build();
        inventoryGrid.setBounds(rightX, startY, rightW, invH);
        addChild(inventoryGrid);

        // "À envoyer" section
        int attachY = startY + invH + 4;
        attachGridX = rightX;
        attachGridY = attachY + attachLabelH;
        attachGridW = rightW;
        attachGridH = attachRowsH;
    }

    private void buildFooter(Font font, int x, int footerY, int w) {
        int btnW = 100;
        int btnGap = 10;
        int draftBtnW = 80;
        int totalBtnW = btnW * 2 + draftBtnW + btnGap * 2;
        int btnStartX = x + (w - totalBtnW) / 2;

        // Total cost label is rendered dynamically in render()

        EcoButton cancelButton = EcoButton.ghost(THEME,
                Component.translatable("ecocraft_mail.button.cancel"), () -> screen.showListView());
        cancelButton.setPosition(btnStartX, footerY);
        cancelButton.setSize(btnW, 20);
        addChild(cancelButton);

        EcoButton draftButton = EcoButton.primary(THEME,
                Component.translatable("ecocraft_mail.button.draft"), this::onSaveDraft);
        draftButton.setPosition(btnStartX + btnW + btnGap, footerY);
        draftButton.setSize(draftBtnW, 20);
        addChild(draftButton);

        sendButton = EcoButton.success(THEME,
                Component.translatable("ecocraft_mail.button.send"), this::onSend);
        sendButton.setPosition(btnStartX + btnW + draftBtnW + btnGap * 2, footerY);
        sendButton.setSize(btnW, 20);
        addChild(sendButton);

        confirmButton = EcoButton.danger(THEME,
                Component.translatable("ecocraft_mail.compose.confirm_send"), this::onConfirmSend);
        confirmButton.setPosition(btnStartX + btnW + draftBtnW + btnGap * 2, footerY);
        confirmButton.setSize(btnW, 20);
        confirmButton.setVisible(false);
        addChild(confirmButton);
    }

    // --- Prefill methods for reply/forward ---

    public void prefillReply(String recipient, String subject) {
        if (recipientInput != null) recipientInput.setValue(recipient);
        if (subjectInput != null) {
            String prefix = subject.startsWith("Re: ") ? "" : "Re: ";
            subjectInput.setValue(prefix + subject);
        }
    }

    public void prefillForward(String subject, String body) {
        if (subjectInput != null) {
            String prefix = subject.startsWith("Fwd: ") ? "" : "Fwd: ";
            subjectInput.setValue(prefix + subject);
        }
        if (bodyArea != null && body != null) bodyArea.setValue(body);
    }

    public void prefillDraft(DraftsResponsePayload.DraftEntry draft) {
        if (recipientInput != null) recipientInput.setValue(draft.recipient());
        if (subjectInput != null) subjectInput.setValue(draft.subject());
        if (bodyArea != null) bodyArea.setValue(draft.body());
        if (currencyInput != null && draft.currencyAmount() > 0) currencyInput.setValue(draft.currencyAmount());
        if (draft.codAmount() > 0) {
            if (codToggle != null) codToggle.value(true);
            if (codAmountInput != null) {
                codAmountInput.setEnabled(true);
                codAmountInput.setValue(draft.codAmount());
            }
        }
        // Store draft ID so we can delete it after sending
        this.loadedDraftId = draft.id();
    }

    private void onSaveDraft() {
        String recipient = recipientInput != null ? recipientInput.getValue().trim() : "";
        String subject = subjectInput != null ? subjectInput.getValue().trim() : "";
        String body = bodyArea != null ? bodyArea.getValue() : "";
        long currency = currencyInput != null ? currencyInput.getValue() : 0;
        long codAmount = (codToggle != null && codToggle.getValue() && codAmountInput != null)
                ? codAmountInput.getValue() : 0;

        PacketDistributor.sendToServer(new SaveDraftPayload(recipient, subject, body, currency, codAmount));

        EcoToast toast = EcoToast.builder(THEME)
                .title(Component.translatable("ecocraft_mail.compose.draft_saved").getString())
                .message("")
                .level(ToastLevel.SUCCESS)
                .animation(ToastAnimation.SLIDE_RIGHT)
                .duration(2000)
                .dismissOnClick(true)
                .build();
        EcoToastManager.getInstance().show(toast);
        screen.showListView();
    }

    // --- Inventory slot selection ---

    private void onInventorySlotClicked(int inventoryIndex) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack stack = player.getInventory().getItem(inventoryIndex);
        if (stack.isEmpty()) return;

        // Toggle: if already selected, remove; otherwise add (respect config max)
        if (selectedSlots.contains(inventoryIndex)) {
            selectedSlots.remove(Integer.valueOf(inventoryIndex));
        } else if (selectedSlots.size() < screen.maxItemAttachments) {
            selectedSlots.add(inventoryIndex);
        }
        updateInventoryDimming();
    }

    private void updateInventoryDimming() {
        if (inventoryGrid != null) {
            inventoryGrid.setSelectedSlot(-1);
            inventoryGrid.setDimmedSlots(new java.util.HashSet<>(selectedSlots));
        }
    }

    // --- Render ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        // Reset confirmation after 3 seconds
        if (awaitingConfirmation && System.currentTimeMillis() - confirmationTimestamp > 3000) {
            resetConfirmation();
        }

        Font font = Minecraft.getInstance().font;

        // Header separator
        int sepY = getY() + HEADER_HEIGHT - 1;
        graphics.fill(getX(), sepY, getX() + getWidth(), sepY + 1, THEME.border);

        // Footer separator
        int footerSepY = getY() + getHeight() - FOOTER_HEIGHT - 1;
        graphics.fill(getX(), footerSepY, getX() + getWidth(), footerSepY + 1, THEME.border);

        // Dynamic total cost display
        long totalCost = computeTotalCost();
        if (totalCost > 0) {
            String costText = Component.translatable("ecocraft_mail.compose.total_cost_prefix").getString()
                    + CurrencyFormatter.format(totalCost, screen.currency);
            int costTextW = font.width(costText);
            int costX = getX() + (getWidth() - costTextW) / 2;
            int costY = footerSepY + 3;
            graphics.drawString(font, costText, costX, costY, THEME.accent, false);
        }

        // --- "À envoyer" section ---
        int maxSlots = screen.maxItemAttachments;
        int maxRows = (maxSlots + ATTACH_COLS - 1) / ATTACH_COLS;
        String countLabel = selectedSlots.size() + "/" + maxSlots;
        String attachLabel = Component.translatable("ecocraft_mail.compose.attach_label").getString() + "  " + countLabel;
        int labelY = attachGridY - font.lineHeight - 2;
        graphics.drawString(font, attachLabel, attachGridX, labelY, THEME.textLight, false);

        // Separator above attach section
        graphics.fill(attachGridX, labelY - 3, attachGridX + attachGridW, labelY - 2, THEME.border);

        // Clamp scroll
        int scrollMaxRow = Math.max(0, maxRows - attachVisibleRows);
        if (attachScrollOffset < 0) attachScrollOffset = 0;
        if (attachScrollOffset > scrollMaxRow) attachScrollOffset = scrollMaxRow;

        // Render with scissor
        var player = Minecraft.getInstance().player;
        int slotStep = SLOT_SIZE + SLOT_GAP;

        graphics.enableScissor(attachGridX, attachGridY, attachGridX + attachGridW, attachGridY + attachGridH);

        // Draw slot backgrounds for all visible positions
        for (int row = 0; row < maxRows; row++) {
            for (int col = 0; col < ATTACH_COLS; col++) {
                int i = row * ATTACH_COLS + col;
                if (i >= maxSlots) break;
                int sx = attachGridX + col * slotStep;
                int sy = attachGridY + (row - attachScrollOffset) * slotStep;
                boolean hasItem = i < selectedSlots.size();
                int bgColor = hasItem ? THEME.bgMedium : THEME.bgDark;
                int borderColor = hasItem ? THEME.success : THEME.border;
                DrawUtils.drawPanel(graphics, sx, sy, SLOT_SIZE, SLOT_SIZE, bgColor, borderColor);
            }
        }

        // Render items
        if (player != null) {
            for (int i = 0; i < selectedSlots.size(); i++) {
                int col = i % ATTACH_COLS;
                int row = i / ATTACH_COLS;
                int sx = attachGridX + col * slotStep;
                int sy = attachGridY + (row - attachScrollOffset) * slotStep;
                int itemX = sx + (SLOT_SIZE - 16) / 2;
                int itemY = sy + (SLOT_SIZE - 16) / 2;

                int slotIndex = selectedSlots.get(i);
                ItemStack stack = player.getInventory().getItem(slotIndex);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, itemX, itemY);
                    graphics.renderItemDecorations(font, stack, itemX, itemY);
                }
            }
        }

        graphics.disableScissor();

        // Scroll indicator if needed
        if (maxRows > attachVisibleRows) {
            int indicatorX = attachGridX + ATTACH_COLS * slotStep + 2;
            int totalH = attachGridH;
            int thumbH = Math.max(8, totalH * attachVisibleRows / maxRows);
            int thumbY = attachGridY + (totalH - thumbH) * attachScrollOffset / scrollMaxRow;
            graphics.fill(indicatorX, attachGridY, indicatorX + 3, attachGridY + totalH, THEME.bgDark);
            graphics.fill(indicatorX, thumbY, indicatorX + 3, thumbY + thumbH, THEME.textDim);
        }
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        // Check if click is on an attachment slot -> remove it
        if (!selectedSlots.isEmpty() && mouseX >= attachGridX && mouseY >= attachGridY
                && mouseY < attachGridY + attachGridH) {
            int slotStep = SLOT_SIZE + SLOT_GAP;
            int col = (int) ((mouseX - attachGridX) / slotStep);
            int row = (int) ((mouseY - attachGridY) / slotStep) + attachScrollOffset;
            int i = row * ATTACH_COLS + col;
            if (col >= 0 && col < ATTACH_COLS && i >= 0 && i < selectedSlots.size()) {
                selectedSlots.remove(i);
                updateInventoryDimming();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scroll the attach grid if mouse is over it
        if (mouseX >= attachGridX && mouseY >= attachGridY - 15 && mouseY < attachGridY + attachGridH) {
            int maxRows = (screen.maxItemAttachments + ATTACH_COLS - 1) / ATTACH_COLS;
            if (maxRows > attachVisibleRows) {
                attachScrollOffset -= (int) scrollY;
                int scrollMax = maxRows - attachVisibleRows;
                if (attachScrollOffset < 0) attachScrollOffset = 0;
                if (attachScrollOffset > scrollMax) attachScrollOffset = scrollMax;
                return true;
            }
        }
        return false;
    }

    // --- Send ---

    private void onSend() {
        String recipient = recipientInput.getValue().trim();
        String subject = subjectInput.getValue().trim();

        if (recipient.isEmpty()) {
            showError(Component.translatable("ecocraft_mail.compose.error_recipient").getString());
            return;
        }
        if (subject.isEmpty()) {
            showError(Component.translatable("ecocraft_mail.compose.error_subject").getString());
            return;
        }

        long currency = currencyInput.getValue();
        long codAmount = codToggle.getValue() ? codAmountInput.getValue() : 0;

        // Require confirmation if there are attachments, currency, or COD
        if (!selectedSlots.isEmpty() || currency > 0 || codAmount > 0) {
            awaitingConfirmation = true;
            confirmationTimestamp = System.currentTimeMillis();
            sendButton.setVisible(false);
            confirmButton.setVisible(true);
            return;
        }

        doSend();
    }

    private void onConfirmSend() {
        awaitingConfirmation = false;
        confirmButton.setVisible(false);
        sendButton.setVisible(true);
        doSend();
    }

    private void resetConfirmation() {
        awaitingConfirmation = false;
        confirmButton.setVisible(false);
        sendButton.setVisible(true);
    }

    private void doSend() {
        String recipient = recipientInput.getValue().trim();
        String subject = subjectInput.getValue().trim();
        String body = bodyArea.getValue();
        long currency = currencyInput.getValue();
        long codAmount = codToggle.getValue() ? codAmountInput.getValue() : 0;
        boolean readReceipt = readReceiptToggle != null && readReceiptToggle.getValue();

        PacketDistributor.sendToServer(new SendMailPayload(
                recipient, subject, body, List.copyOf(selectedSlots), currency, codAmount, readReceipt
        ));
    }

    private long computeTotalCost() {
        long baseCost = screen.sendCost;
        long perItemCost = screen.sendCostPerItem;
        long receiptCost = (readReceiptToggle != null && readReceiptToggle.getValue()) ? screen.readReceiptCost : 0;
        long codFee = 0;
        if (codToggle != null && codToggle.getValue() && codAmountInput != null) {
            long codAmount = codAmountInput.getValue();
            codFee = codAmount * screen.codFeePercent / 100;
        }
        return baseCost + (selectedSlots.size() * perItemCost) + receiptCost + codFee;
    }

    public void onReceiveSendResult(SendMailResultPayload payload) {
        if (payload.success()) {
            // Delete the draft if this was composed from one
            if (loadedDraftId != null) {
                PacketDistributor.sendToServer(new net.ecocraft.mail.network.payload.DeleteDraftPayload(loadedDraftId));
                loadedDraftId = null;
            }
            EcoToast toast = EcoToast.builder(THEME)
                    .title(Component.translatable("ecocraft_mail.compose.sent").getString())
                    .message(payload.message())
                    .level(ToastLevel.SUCCESS)
                    .animation(ToastAnimation.SLIDE_RIGHT)
                    .duration(3000)
                    .dismissOnClick(true)
                    .build();
            EcoToastManager.getInstance().show(toast);
            selectedSlots.clear();
            screen.showListView();
        } else {
            showError(payload.message());
        }
    }

    private void showError(String text) {
        EcoToast toast = EcoToast.builder(THEME)
                .title(text)
                .message("")
                .level(ToastLevel.ERROR)
                .animation(ToastAnimation.SLIDE_RIGHT)
                .duration(3000)
                .dismissOnClick(true)
                .build();
        EcoToastManager.getInstance().show(toast);
    }
}

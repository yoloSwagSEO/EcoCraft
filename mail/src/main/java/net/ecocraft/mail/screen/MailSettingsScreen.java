package net.ecocraft.mail.screen;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.mail.client.MailNotificationChannel;
import net.ecocraft.mail.client.MailNotificationConfig;
import net.ecocraft.mail.client.MailNotificationEventType;
import net.ecocraft.mail.config.MailConfig;
import net.ecocraft.mail.network.payload.MailSettingsPayload;
import net.ecocraft.mail.network.payload.UpdateMailSettingsPayload;
import net.ecocraft.mail.network.payload.UpdatePostmanSkinPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Mail settings screen with sidebar tabs:
 * - Tab 0: Notifications (all players)
 * - Tab 1: General config (admin only)
 */
public class MailSettingsScreen extends EcoScreen {

    private static final Theme THEME = Theme.dark();
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 4;

    private int guiWidth, guiHeight, guiLeft, guiTop;
    private int sidebarWidth;

    private final Screen parentScreen;
    private final boolean isAdmin;
    private final int postmanEntityId;
    private final Currency currency;
    private String skinPlayerName;
    private int selectedTab = 0;
    private final List<EcoButton> sidebarButtons = new ArrayList<>();

    // Editable config values (loaded from MailConfig on open, synced via payload)
    private boolean editAllowPlayerMail;
    private boolean editAllowItemAttachments;
    private boolean editAllowCurrencyAttachments;
    private boolean editAllowCOD;
    private boolean editAllowMailboxCraft;
    private int editMaxItemAttachments;
    private int editMailExpiryDays;
    private long editSendCost;
    private int editCodFeePercent;
    private boolean editAllowReadReceipt;
    private long editReadReceiptCost;
    private long editSendCostPerItem;

    public MailSettingsScreen(Screen parent, boolean isAdmin, int postmanEntityId, String skinPlayerName, Currency currency) {
        super(Component.translatable("ecocraft_mail.screen.settings_title"));
        this.parentScreen = parent;
        this.isAdmin = isAdmin;
        this.postmanEntityId = postmanEntityId;
        this.currency = currency;
        this.skinPlayerName = skinPlayerName != null ? skinPlayerName : "";
        // Initialize from current config values
        var config = MailConfig.CONFIG;
        this.editAllowPlayerMail = config.allowPlayerMail.get();
        this.editAllowItemAttachments = config.allowItemAttachments.get();
        this.editAllowCurrencyAttachments = config.allowCurrencyAttachments.get();
        this.editAllowCOD = config.allowCOD.get();
        this.editAllowMailboxCraft = config.allowMailboxCraft.get();
        this.editMaxItemAttachments = config.maxItemAttachments.get();
        this.editMailExpiryDays = config.mailExpiryDays.get();
        this.editSendCost = config.sendCost.get();
        this.editCodFeePercent = config.codFeePercent.get();
        this.editAllowReadReceipt = config.allowReadReceipt.get();
        this.editReadReceiptCost = config.readReceiptCost.get();
        this.editSendCostPerItem = config.sendCostPerItem.get();
    }

    /** Called when the server sends updated settings back. */
    public void receiveSettings(MailSettingsPayload payload) {
        this.editAllowPlayerMail = payload.allowPlayerMail();
        this.editAllowItemAttachments = payload.allowItemAttachments();
        this.editAllowCurrencyAttachments = payload.allowCurrencyAttachments();
        this.editAllowCOD = payload.allowCOD();
        this.editAllowMailboxCraft = payload.allowMailboxCraft();
        this.editMaxItemAttachments = payload.maxItemAttachments();
        this.editMailExpiryDays = payload.mailExpiryDays();
        this.editSendCost = payload.sendCost();
        this.editCodFeePercent = payload.codFeePercent();
        this.editAllowReadReceipt = payload.allowReadReceipt();
        this.editReadReceiptCost = payload.readReceiptCost();
        this.editSendCostPerItem = payload.sendCostPerItem();
        if (selectedTab == 1) rebuildScreen();
    }

    @Override
    protected void init() {
        super.init();

        guiWidth = (int) (width * 0.9);
        guiHeight = (int) (height * 0.9);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        sidebarWidth = (int) (guiWidth * 0.20);

        rebuildScreen();
    }

    private void rebuildScreen() {
        getTree().clear();
        sidebarButtons.clear();

        initSidebar();
        initRightPanel();
        initFooter();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight, THEME.bgDarkest, THEME.borderAccent);

        // Sidebar background
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, sidebarWidth, guiHeight, THEME.bgDark, THEME.border);
    }

    // --- Sidebar ---

    private void initSidebar() {
        int btnX = guiLeft + 4;
        int btnW = sidebarWidth - 8;
        int btnH = 18;
        int y = guiTop + 8;

        // Tab 0: Notifications (always visible)
        EcoButton notifBtn = createSidebarButton(Component.translatable("ecocraft_mail.settings.tab_notifications").getString(), 0, btnX, y, btnW, btnH);
        getTree().addChild(notifBtn);
        sidebarButtons.add(notifBtn);
        y += btnH + 4;

        if (isAdmin) {
            // Tab 1: General config (admin only)
            EcoButton generalBtn = createSidebarButton(Component.translatable("ecocraft_mail.settings.tab_general").getString(), 1, btnX, y, btnW, btnH);
            getTree().addChild(generalBtn);
            sidebarButtons.add(generalBtn);
        }
    }

    private EcoButton createSidebarButton(String label, int tabIndex, int x, int y, int w, int h) {
        boolean selected = tabIndex == selectedTab;
        return EcoButton.builder(Component.literal(label), () -> onTabClicked(tabIndex))
                .theme(THEME).bounds(x, y, w, h)
                .bgColor(selected ? THEME.accentBg : THEME.bgMedium)
                .borderColor(selected ? THEME.borderAccent : THEME.borderLight)
                .textColor(selected ? THEME.accent : THEME.textLight)
                .hoverBg(selected ? THEME.accentBg : THEME.bgLight)
                .build();
    }

    private void onTabClicked(int tabIndex) {
        selectedTab = tabIndex;
        rebuildScreen();
    }

    // --- Right panel ---

    private void initRightPanel() {
        int panelX = guiLeft + sidebarWidth + 4;
        int panelY = guiTop + 4;
        int panelW = guiWidth - sidebarWidth - 8;
        int panelH = guiHeight - 40; // leave room for footer

        if (selectedTab == 0) {
            buildNotificationsTab(panelX, panelY, panelW, panelH);
        } else if (selectedTab == 1 && isAdmin) {
            buildGeneralTab(panelX, panelY, panelW, panelH);
        }
    }

    // --- Notifications tab ---

    private void buildNotificationsTab(int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;

        List<String> channelLabels = List.of(
                Component.translatable("ecocraft_mail.settings.channel_chat").getString(),
                Component.translatable("ecocraft_mail.settings.channel_toast").getString(),
                Component.translatable("ecocraft_mail.settings.channel_both").getString(),
                Component.translatable("ecocraft_mail.settings.channel_none").getString()
        );
        MailNotificationChannel[] channelOrder = {
                MailNotificationChannel.CHAT, MailNotificationChannel.TOAST,
                MailNotificationChannel.BOTH, MailNotificationChannel.NONE
        };

        int contentX = x + 8;
        int contentW = w - 16;
        int currentY = y + 8;

        // Title
        Label title = new Label(font, contentX, currentY, Component.translatable("ecocraft_mail.settings.notifications_title"), THEME);
        title.setColor(THEME.accent);
        getTree().addChild(title);
        currentY += font.lineHeight + 8;

        // One row per event type
        MailNotificationConfig config = MailNotificationConfig.getInstance();
        int dropdownW = (int) (contentW * 0.35);
        int dropdownX = contentX + contentW - dropdownW;

        for (MailNotificationEventType eventType : MailNotificationEventType.values()) {
            String labelText = getEventLabel(eventType);
            Label label = new Label(font, contentX, currentY + (ROW_HEIGHT - font.lineHeight) / 2,
                    Component.literal(labelText), THEME);
            label.setColor(THEME.textLight);
            getTree().addChild(label);

            int currentIndex = indexOf(channelOrder, config.getChannel(eventType));
            EcoDropdown dropdown = new EcoDropdown(dropdownX, currentY, dropdownW, ROW_HEIGHT - 4, THEME);
            dropdown.options(channelLabels);
            dropdown.selectedIndex(currentIndex);

            final MailNotificationEventType type = eventType;
            dropdown.responder(index -> {
                if (index >= 0 && index < channelOrder.length) {
                    config.setChannel(type, channelOrder[index]);
                }
            });
            getTree().addChild(dropdown);

            currentY += ROW_HEIGHT + ROW_GAP;
        }
    }

    private static String getEventLabel(MailNotificationEventType type) {
        return switch (type) {
            case NEW_MAIL -> Component.translatable("ecocraft_mail.settings.event.new_mail").getString();
            case COD_RECEIVED -> Component.translatable("ecocraft_mail.settings.event.cod_received").getString();
            case MAIL_RETURNED -> Component.translatable("ecocraft_mail.settings.event.mail_returned").getString();
            case READ_RECEIPT -> Component.translatable("ecocraft_mail.settings.event.read_receipt").getString();
        };
    }

    private static int indexOf(MailNotificationChannel[] arr, MailNotificationChannel target) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) return i;
        }
        return 2; // default to BOTH
    }

    // --- General tab (admin only, AH-style panels) ---

    private static final int PANEL_PADDING = 8;
    private static final int SECTION_GAP = 6;

    private void buildGeneralTab(int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;

        int titleBlockH = font.lineHeight + 2 + 6;
        int toggleRowH = 20;
        int inputRowH = font.lineHeight + 4 + 16 + 8; // label + input + gap

        // Count visible cost rows
        int costRows = 2; // send cost + expiry (always)
        if (editAllowItemAttachments) costRows += 1; // cost per item + max attachments (side by side)
        if (editAllowCOD) costRows += 1; // COD fee
        if (editAllowReadReceipt) costRows += 1; // receipt cost

        // Section heights
        boolean hasPostman = postmanEntityId > 0;
        int postmanH = hasPostman ? PANEL_PADDING * 2 + titleBlockH + font.lineHeight + 4 + 16 + 2 : 0;
        int featuresH = PANEL_PADDING * 2 + titleBlockH + toggleRowH * 6 + 4 * 5;
        int costsH = PANEL_PADDING * 2 + titleBlockH + inputRowH * costRows;

        int totalContentH = postmanH + featuresH + costsH + SECTION_GAP * (hasPostman ? 3 : 2) + 20;

        ScrollPane scrollPane = new ScrollPane(x, y, w, h, THEME);
        scrollPane.setContentHeight(totalContentH);
        getTree().addChild(scrollPane);

        // Panels
        Panel postmanPanel = null;
        if (hasPostman) {
            postmanPanel = new Panel(0, 0, 0, 0, THEME);
            postmanPanel.padding(PANEL_PADDING).titleUppercase(true).titleMarginBottom(6)
                    .separatorStyle(Panel.SeparatorStyle.NONE)
                    .title(Component.literal("\u263A Facteur"), font);
        }

        Panel featuresPanel = new Panel(0, 0, 0, 0, THEME);
        featuresPanel.padding(PANEL_PADDING).titleUppercase(true).titleMarginBottom(6)
                .separatorStyle(Panel.SeparatorStyle.NONE)
                .title(Component.literal("\u2302 Fonctionnalités"), font);

        Panel costsPanel = new Panel(0, 0, 0, 0, THEME);
        costsPanel.padding(PANEL_PADDING).titleUppercase(true).titleMarginBottom(6)
                .separatorStyle(Panel.SeparatorStyle.NONE)
                .title(Component.literal("\u272A Coûts & Limites"), font);

        EcoGrid grid = new EcoGrid(x, y, w, totalContentH, 0);
        grid.rowGap(SECTION_GAP);
        scrollPane.addChild(grid);

        if (hasPostman && postmanPanel != null) {
            grid.addRow(postmanH).addCol(12).addChild(postmanPanel);
        }
        grid.addRow(featuresH).addCol(12).addChild(featuresPanel);
        grid.addRow(costsH).addCol(12).addChild(costsPanel);
        grid.relayout();

        // --- Facteur ---
        if (hasPostman && postmanPanel != null) {
            int pcx = postmanPanel.getContentX();
            int pcy = postmanPanel.getContentY();
            int pcw = postmanPanel.getContentWidth();

            Label skinLabel = new Label(font, pcx, pcy,
                    Component.translatable("ecocraft_mail.settings.skin_label"), THEME);
            skinLabel.setColor(THEME.textGrey);
            scrollPane.addChild(skinLabel);

            EcoTextInput skinInput = new EcoTextInput(font, pcx, pcy + font.lineHeight + 4, (pcw - 8) / 2, 16,
                    Component.translatable("ecocraft_mail.settings.skin_placeholder"), THEME);
            skinInput.setValue(skinPlayerName);
            skinInput.responder(val -> skinPlayerName = val);
            scrollPane.addChild(skinInput);
        }

        // --- Fonctionnalités (toggles rebuild on change) ---
        int cx = featuresPanel.getContentX();
        int cy = featuresPanel.getContentY();
        int cw = featuresPanel.getContentWidth();
        int toggleAreaW = cw / 3;
        int toggleW = 40;

        cy = addPanelToggle(scrollPane, font, cx, cy, toggleAreaW, toggleW,
                Component.translatable("ecocraft_mail.settings.toggle.player_mail").getString(),
                editAllowPlayerMail, val -> { editAllowPlayerMail = val; rebuildScreen(); });
        cy = addPanelToggle(scrollPane, font, cx, cy, toggleAreaW, toggleW,
                Component.translatable("ecocraft_mail.settings.toggle.item_attachments").getString(),
                editAllowItemAttachments, val -> { editAllowItemAttachments = val; rebuildScreen(); });
        cy = addPanelToggle(scrollPane, font, cx, cy, toggleAreaW, toggleW,
                Component.translatable("ecocraft_mail.settings.toggle.currency_attachments").getString(),
                editAllowCurrencyAttachments, val -> { editAllowCurrencyAttachments = val; rebuildScreen(); });
        cy = addPanelToggle(scrollPane, font, cx, cy, toggleAreaW, toggleW,
                Component.translatable("ecocraft_mail.settings.toggle.cod").getString(),
                editAllowCOD, val -> { editAllowCOD = val; rebuildScreen(); });
        cy = addPanelToggle(scrollPane, font, cx, cy, toggleAreaW, toggleW,
                Component.translatable("ecocraft_mail.settings.toggle.mailbox_craft").getString(),
                editAllowMailboxCraft, val -> editAllowMailboxCraft = val);
        addPanelToggle(scrollPane, font, cx, cy, toggleAreaW, toggleW,
                Component.translatable("ecocraft_mail.settings.toggle.read_receipt").getString(),
                editAllowReadReceipt, val -> { editAllowReadReceipt = val; rebuildScreen(); });

        // --- Coûts & Limites (dynamic based on toggles) ---
        cx = costsPanel.getContentX();
        cy = costsPanel.getContentY();
        cw = costsPanel.getContentWidth();
        int halfW = (cw - 8) / 2;

        // Send cost (always)
        Label sendCostLabel = new Label(font, cx, cy,
                Component.translatable("ecocraft_mail.settings.input.send_cost"), THEME);
        sendCostLabel.setColor(THEME.textGrey);
        scrollPane.addChild(sendCostLabel);
        cy += font.lineHeight + 4;

        EcoCurrencyInput sendCostInput = new EcoCurrencyInput(font, cx, cy, halfW, currency, THEME);
        sendCostInput.min(0).max(999999);
        sendCostInput.setValue(editSendCost);
        sendCostInput.responder(val -> editSendCost = val);
        scrollPane.addChild(sendCostInput);
        cy += 16 + 8;

        // Cost per item + max attachments (only if item attachments enabled)
        if (editAllowItemAttachments) {
            Label costPerItemLabel = new Label(font, cx, cy,
                    Component.translatable("ecocraft_mail.settings.input.cost_per_item"), THEME);
            costPerItemLabel.setColor(THEME.textGrey);
            scrollPane.addChild(costPerItemLabel);

            Label maxAttachLabel = new Label(font, cx + halfW + 8, cy,
                    Component.translatable("ecocraft_mail.settings.input.max_attachments"), THEME);
            maxAttachLabel.setColor(THEME.textGrey);
            scrollPane.addChild(maxAttachLabel);
            cy += font.lineHeight + 4;

            EcoCurrencyInput costPerItemInput = new EcoCurrencyInput(font, cx, cy, halfW, currency, THEME);
            costPerItemInput.min(0).max(999999);
            costPerItemInput.setValue(editSendCostPerItem);
            costPerItemInput.responder(val -> editSendCostPerItem = val);
            scrollPane.addChild(costPerItemInput);

            EcoNumberInput maxAttachInput = new EcoNumberInput(font, cx + halfW + 8, cy, halfW, 16, THEME);
            maxAttachInput.min(1).max(54).step(1).showButtons(true).setValue(editMaxItemAttachments);
            maxAttachInput.responder(val -> editMaxItemAttachments = val.intValue());
            scrollPane.addChild(maxAttachInput);
            cy += 16 + 8;
        }

        // COD fee (only if COD enabled)
        if (editAllowCOD) {
            Label codFeeLabel = new Label(font, cx, cy,
                    Component.translatable("ecocraft_mail.settings.input.cod_fee"), THEME);
            codFeeLabel.setColor(THEME.textGrey);
            scrollPane.addChild(codFeeLabel);
            cy += font.lineHeight + 4;

            EcoSlider codFeeSlider = new EcoSlider(font, cx, cy, halfW, 16, THEME);
            codFeeSlider.min(0).max(100).step(1).value(editCodFeePercent).suffix("%")
                    .labelPosition(EcoSlider.LabelPosition.AFTER);
            codFeeSlider.responder(val -> editCodFeePercent = val.intValue());
            scrollPane.addChild(codFeeSlider);
            cy += 16 + 8;
        }

        // Read receipt cost (only if enabled)
        if (editAllowReadReceipt) {
            Label receiptCostLabel = new Label(font, cx, cy,
                    Component.translatable("ecocraft_mail.settings.input.read_receipt_cost"), THEME);
            receiptCostLabel.setColor(THEME.textGrey);
            scrollPane.addChild(receiptCostLabel);
            cy += font.lineHeight + 4;

            EcoCurrencyInput receiptCostInput = new EcoCurrencyInput(font, cx, cy, halfW, currency, THEME);
            receiptCostInput.min(0).max(999999);
            receiptCostInput.setValue(editReadReceiptCost);
            receiptCostInput.responder(val -> editReadReceiptCost = val);
            scrollPane.addChild(receiptCostInput);
            cy += 16 + 8;
        }

        // Expiry days (always)
        Label expiryLabel = new Label(font, cx, cy,
                Component.translatable("ecocraft_mail.settings.input.expiry_days"), THEME);
        expiryLabel.setColor(THEME.textGrey);
        scrollPane.addChild(expiryLabel);
        cy += font.lineHeight + 4;

        EcoNumberInput expiryInput = new EcoNumberInput(font, cx, cy, halfW, 16, THEME);
        expiryInput.min(1).max(365).step(1).showButtons(true).setValue(editMailExpiryDays);
        expiryInput.responder(val -> editMailExpiryDays = val.intValue());
        scrollPane.addChild(expiryInput);
    }

    private int addPanelToggle(ScrollPane scrollPane, Font font, int cx, int cy, int toggleAreaW, int toggleW,
                                String labelText, boolean value, java.util.function.Consumer<Boolean> responder) {
        Label label = new Label(font, cx, cy + 3, Component.literal(labelText), THEME);
        label.setColor(THEME.textLight);
        scrollPane.addChild(label);

        EcoToggle toggle = new EcoToggle(cx + toggleAreaW - toggleW, cy, toggleW, 14, THEME);
        toggle.value(value);
        toggle.responder(responder);
        scrollPane.addChild(toggle);

        return cy + 20;
    }

    private void onSaveSettings() {
        PacketDistributor.sendToServer(new UpdateMailSettingsPayload(
                editAllowPlayerMail, editAllowItemAttachments,
                editAllowCurrencyAttachments, editAllowCOD, editAllowMailboxCraft,
                editMaxItemAttachments, editMailExpiryDays, editSendCost, editCodFeePercent,
                editAllowReadReceipt, editReadReceiptCost, editSendCostPerItem
        ));
        if (postmanEntityId > 0) {
            PacketDistributor.sendToServer(new UpdatePostmanSkinPayload(postmanEntityId, skinPlayerName));
        }
        onClose();
    }

    // --- Footer ---

    private void initFooter() {
        int footerY = guiTop + guiHeight - 30;
        int btnW = 100;
        int gap = 10;
        int totalBtnW = btnW * 2 + gap;
        int btnStartX = guiLeft + (guiWidth - totalBtnW) / 2;

        EcoButton footerCancelBtn = EcoButton.ghost(THEME,
                Component.translatable("ecocraft_mail.button.close"), this::onClose);
        footerCancelBtn.setPosition(btnStartX, footerY);
        footerCancelBtn.setSize(btnW, 20);
        getTree().addChild(footerCancelBtn);

        EcoButton saveBtn = EcoButton.success(THEME,
                Component.translatable("ecocraft_ah.button.save"), this::onSaveSettings);
        saveBtn.setPosition(btnStartX + btnW + gap, footerY);
        saveBtn.setSize(btnW, 20);
        getTree().addChild(saveBtn);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parentScreen);
    }
}

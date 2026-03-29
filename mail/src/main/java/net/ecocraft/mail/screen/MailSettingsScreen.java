package net.ecocraft.mail.screen;

import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.mail.client.MailNotificationChannel;
import net.ecocraft.mail.client.MailNotificationConfig;
import net.ecocraft.mail.client.MailNotificationEventType;
import net.ecocraft.mail.config.MailConfig;
import net.ecocraft.mail.network.payload.MailSettingsPayload;
import net.ecocraft.mail.network.payload.UpdateMailSettingsPayload;
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

    public MailSettingsScreen(Screen parent, boolean isAdmin) {
        super(Component.translatable("ecocraft_mail.screen.settings_title"));
        this.parentScreen = parent;
        this.isAdmin = isAdmin;
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
        };
    }

    private static int indexOf(MailNotificationChannel[] arr, MailNotificationChannel target) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) return i;
        }
        return 2; // default to BOTH
    }

    // --- General tab (admin only) ---

    private void buildGeneralTab(int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;
        int contentX = x + 8;
        int contentW = w - 16;
        int currentY = y + 8;

        // Title
        Label title = new Label(font, contentX, currentY, Component.translatable("ecocraft_mail.settings.general_title"), THEME);
        title.setColor(THEME.accent);
        getTree().addChild(title);
        currentY += font.lineHeight + 12;

        int toggleW = 40;
        int inputW = (int) (contentW * 0.35);
        int rightX = contentX + contentW - toggleW;
        int rightInputX = contentX + contentW - inputW;

        // --- Boolean toggles ---

        currentY = addToggleRow(font, contentX, currentY, rightX, toggleW,
                Component.translatable("ecocraft_mail.settings.toggle.player_mail").getString(),
                editAllowPlayerMail, val -> editAllowPlayerMail = val);

        currentY = addToggleRow(font, contentX, currentY, rightX, toggleW,
                Component.translatable("ecocraft_mail.settings.toggle.item_attachments").getString(),
                editAllowItemAttachments, val -> editAllowItemAttachments = val);

        currentY = addToggleRow(font, contentX, currentY, rightX, toggleW,
                Component.translatable("ecocraft_mail.settings.toggle.currency_attachments").getString(),
                editAllowCurrencyAttachments, val -> editAllowCurrencyAttachments = val);

        currentY = addToggleRow(font, contentX, currentY, rightX, toggleW,
                Component.translatable("ecocraft_mail.settings.toggle.cod").getString(),
                editAllowCOD, val -> editAllowCOD = val);

        currentY = addToggleRow(font, contentX, currentY, rightX, toggleW,
                Component.translatable("ecocraft_mail.settings.toggle.mailbox_craft").getString(),
                editAllowMailboxCraft, val -> editAllowMailboxCraft = val);

        currentY += 4;

        // --- Numeric inputs ---

        currentY = addNumberRow(font, contentX, currentY, rightInputX, inputW,
                Component.translatable("ecocraft_mail.settings.input.max_attachments").getString(),
                editMaxItemAttachments, 1, 54, val -> editMaxItemAttachments = val.intValue());

        currentY = addNumberRow(font, contentX, currentY, rightInputX, inputW,
                Component.translatable("ecocraft_mail.settings.input.expiry_days").getString(),
                editMailExpiryDays, 1, 365, val -> editMailExpiryDays = val.intValue());

        currentY = addNumberRow(font, contentX, currentY, rightInputX, inputW,
                Component.translatable("ecocraft_mail.settings.input.send_cost").getString(),
                (int) editSendCost, 0, 999999, val -> editSendCost = val);

        currentY = addNumberRow(font, contentX, currentY, rightInputX, inputW,
                Component.translatable("ecocraft_mail.settings.input.cod_fee").getString(),
                editCodFeePercent, 0, 100, val -> editCodFeePercent = val.intValue());

        // Save button
        currentY += 8;
        int saveBtnW = 120;
        int saveBtnX = contentX + (contentW - saveBtnW) / 2;
        EcoButton saveBtn = EcoButton.success(THEME,
                Component.translatable("ecocraft_ah.button.save"), this::onSaveSettings);
        saveBtn.setPosition(saveBtnX, currentY);
        saveBtn.setSize(saveBtnW, 20);
        getTree().addChild(saveBtn);
    }

    private int addToggleRow(Font font, int labelX, int y, int toggleX, int toggleW,
                              String labelText, boolean value, java.util.function.Consumer<Boolean> responder) {
        Label label = new Label(font, labelX, y + 3,
                Component.literal(labelText), THEME);
        label.setColor(THEME.textLight);
        getTree().addChild(label);

        EcoToggle toggle = new EcoToggle(toggleX, y, toggleW, 14, THEME);
        toggle.value(value);
        toggle.responder(responder);
        getTree().addChild(toggle);

        return y + ROW_HEIGHT;
    }

    private int addNumberRow(Font font, int labelX, int y, int inputX, int inputW,
                              String labelText, int value, int min, int max,
                              java.util.function.Consumer<Long> responder) {
        Label label = new Label(font, labelX, y + 3,
                Component.literal(labelText), THEME);
        label.setColor(THEME.textLight);
        getTree().addChild(label);

        EcoNumberInput input = new EcoNumberInput(font, inputX, y, inputW, 16, THEME);
        input.min(min).max(max).step(1).showButtons(true);
        input.setValue(value);
        input.responder(responder);
        getTree().addChild(input);

        return y + ROW_HEIGHT + 2;
    }

    private void onSaveSettings() {
        PacketDistributor.sendToServer(new UpdateMailSettingsPayload(
                editAllowPlayerMail, editAllowItemAttachments,
                editAllowCurrencyAttachments, editAllowCOD, editAllowMailboxCraft,
                editMaxItemAttachments, editMailExpiryDays, editSendCost, editCodFeePercent
        ));
    }

    // --- Footer ---

    private void initFooter() {
        int btnW = 80;
        int btnH = 18;
        int btnY = guiTop + guiHeight - btnH - 8;
        int btnX = guiLeft + guiWidth - btnW - 8;

        EcoButton closeBtn = EcoButton.ghost(THEME, Component.translatable("ecocraft_mail.button.close"), this::onClose);
        closeBtn.setBounds(btnX, btnY, btnW, btnH);
        getTree().addChild(closeBtn);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parentScreen);
    }
}

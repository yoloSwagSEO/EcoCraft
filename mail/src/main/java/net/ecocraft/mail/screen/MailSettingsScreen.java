package net.ecocraft.mail.screen;

import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.mail.client.MailNotificationChannel;
import net.ecocraft.mail.client.MailNotificationConfig;
import net.ecocraft.mail.client.MailNotificationEventType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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

    public MailSettingsScreen(Screen parent, boolean isAdmin) {
        super(Component.translatable("ecocraft_mail.screen.settings_title"));
        this.parentScreen = parent;
        this.isAdmin = isAdmin;
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
        int currentY = y + 8;

        // Title
        Label title = new Label(font, contentX, currentY, Component.translatable("ecocraft_mail.settings.general_title"), THEME);
        title.setColor(THEME.accent);
        getTree().addChild(title);
        currentY += font.lineHeight + 8;

        // Info label — admin config is server-side, these are display-only indicators
        // Real config changes would require UpdateMailSettingsPayload (future enhancement)
        Label infoLabel = new Label(font, contentX, currentY,
                Component.translatable("ecocraft_mail.settings.general_info"), THEME);
        infoLabel.setColor(THEME.textDim);
        getTree().addChild(infoLabel);
        currentY += font.lineHeight + 12;

        // Display current config values as read-only labels
        String[] configKeys = {
                "ecocraft_mail.settings.config.player_mail",
                "ecocraft_mail.settings.config.item_attachments",
                "ecocraft_mail.settings.config.currency_attachments",
                "ecocraft_mail.settings.config.cod",
                "ecocraft_mail.settings.config.max_attachments",
                "ecocraft_mail.settings.config.expiry_days",
                "ecocraft_mail.settings.config.send_cost",
                "ecocraft_mail.settings.config.cod_fee",
                "ecocraft_mail.settings.config.mailbox_craft"
        };

        for (String key : configKeys) {
            Label configLabel = new Label(font, contentX, currentY, Component.translatable(key), THEME);
            configLabel.setColor(THEME.textLight);
            getTree().addChild(configLabel);
            currentY += font.lineHeight + 6;
        }
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

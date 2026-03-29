package net.ecocraft.mail.screen;

import com.mojang.logging.LogUtils;
import net.ecocraft.gui.core.EcoButton;
import net.ecocraft.gui.core.EcoScreen;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.mail.network.payload.CollectMailResultPayload;
import net.ecocraft.mail.network.payload.MailDetailResponsePayload;
import net.ecocraft.mail.network.payload.MailListResponsePayload;
import net.ecocraft.mail.network.payload.RequestMailListPayload;
import net.ecocraft.mail.network.payload.SendMailResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Main mailbox screen. Manages list, detail, and compose views.
 * Only one view is visible at a time.
 */
public class MailboxScreen extends EcoScreen {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final Theme THEME = Theme.dark();

    int guiWidth;
    int guiHeight;
    int guiLeft;
    int guiTop;

    private MailListView listView;
    private MailDetailView detailView;
    private MailComposeView composeView;
    private EcoButton settingsButton;

    public MailboxScreen() {
        super(Component.translatable("ecocraft_mail.screen.title"));
    }

    @Override
    protected void init() {
        super.init();

        guiWidth = (int) (width * 0.9);
        guiHeight = (int) (height * 0.9);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int contentX = guiLeft + 4;
        int contentY = guiTop + 4;
        int contentW = guiWidth - 8;
        int contentH = guiHeight - 8;

        listView = new MailListView(this, contentX, contentY, contentW, contentH);
        detailView = new MailDetailView(this, contentX, contentY, contentW, contentH);
        composeView = new MailComposeView(this, contentX, contentY, contentW, contentH);

        getTree().addChild(listView);
        getTree().addChild(detailView);
        getTree().addChild(composeView);

        // Gear button for settings (top-right corner of the panel)
        settingsButton = EcoButton.ghost(THEME, Component.literal("\u2699"), this::openSettings);
        settingsButton.setBounds(guiLeft + guiWidth - 22, guiTop + 2, 18, 18);
        getTree().addChild(settingsButton);

        showListView();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight, THEME.bgDarkest, THEME.borderAccent);
    }

    // --- View navigation ---

    public void showListView() {
        listView.setVisible(true);
        detailView.setVisible(false);
        composeView.setVisible(false);
        // Request fresh mail list from server
        PacketDistributor.sendToServer(new RequestMailListPayload());
    }

    public void showDetailView(String mailId) {
        listView.setVisible(false);
        detailView.setVisible(true);
        composeView.setVisible(false);
        detailView.requestDetail(mailId);
    }

    public void showComposeView() {
        listView.setVisible(false);
        detailView.setVisible(false);
        composeView.setVisible(true);
    }

    private void openSettings() {
        // Determine admin status: check if player has OP level 2
        boolean isAdmin = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            isAdmin = mc.player.hasPermissions(2);
        }
        mc.setScreen(new MailSettingsScreen(this, isAdmin));
    }

    // --- Static payload receivers (called from MailClientPayloadHandler) ---

    public static void receiveMailList(MailListResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof MailboxScreen screen) {
            screen.listView.onReceiveMailList(payload.mails());
        }
    }

    public static void receiveMailDetail(MailDetailResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof MailboxScreen screen) {
            screen.detailView.onReceiveMailDetail(payload);
        }
    }

    public static void receiveCollectResult(CollectMailResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof MailboxScreen screen) {
            if (payload.success()) {
                // Refresh the list after successful collection
                screen.showListView();
            } else {
                LOGGER.warn("Collect failed: {}", payload.message());
            }
        }
    }

    public static void receiveSendResult(SendMailResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof MailboxScreen screen) {
            screen.composeView.onReceiveSendResult(payload);
        }
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new MailboxScreen());
    }
}

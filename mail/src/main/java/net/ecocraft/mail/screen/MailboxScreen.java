package net.ecocraft.mail.screen;

import com.mojang.logging.LogUtils;
import net.ecocraft.gui.core.EcoScreen;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.mail.network.payload.CollectMailResultPayload;
import net.ecocraft.mail.network.payload.MailDetailResponsePayload;
import net.ecocraft.mail.network.payload.MailListResponsePayload;
import net.ecocraft.mail.network.payload.RequestMailListPayload;
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
    // MailComposeView will be added in Task 8

    public MailboxScreen() {
        super(Component.literal("Boite aux lettres"));
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

        getTree().addChild(listView);
        getTree().addChild(detailView);

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
        // Request fresh mail list from server
        PacketDistributor.sendToServer(new RequestMailListPayload());
    }

    public void showDetailView(String mailId) {
        listView.setVisible(false);
        detailView.setVisible(true);
        detailView.requestDetail(mailId);
    }

    public void showComposeView() {
        // Task 8: will switch to compose view
        LOGGER.info("Compose view not yet implemented");
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

    public static void open() {
        Minecraft.getInstance().setScreen(new MailboxScreen());
    }
}

package net.ecocraft.mail.screen;

import com.mojang.logging.LogUtils;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.gui.core.EcoButton;
import net.ecocraft.gui.core.EcoScreen;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.mail.network.payload.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.List;

/**
 * Main mailbox screen. Manages list, detail, and compose views.
 * Resizes dynamically: compact for list/detail, wider for compose.
 */
public class MailboxScreen extends EcoScreen {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final Theme THEME = Theme.dark();

    // Current panel bounds (updated on view switch)
    int guiWidth;
    int guiHeight;
    int guiLeft;
    int guiTop;

    String currencySymbol = "G";
    Currency currency = Currency.virtual("gold", "Gold", "G", 2);
    int maxItemAttachments = 12;
    long sendCost = 0;
    long sendCostPerItem = 0;
    boolean allowReadReceipt = true;
    long readReceiptCost = 0;
    int codFeePercent = 0;
    int postmanEntityId = 0;
    String postmanSkinName = "";

    List<MailListResponsePayload.MailSummary> sentMails = List.of();
    List<DraftsResponsePayload.DraftEntry> drafts = List.of();

    private MailListView listView;
    private MailDetailView detailView;
    private MailComposeView composeView;
    private EcoButton settingsButton;

    private enum ViewMode { LIST, DETAIL, COMPOSE }
    private ViewMode currentMode = ViewMode.LIST;

    public MailboxScreen() {
        super(Component.translatable("ecocraft_mail.screen.title"));
    }

    @Override
    protected void init() {
        super.init();
        rebuildForMode(currentMode);
    }

    private void rebuildForMode(ViewMode mode) {
        this.currentMode = mode;
        getTree().clear();

        // Size depends on mode
        if (mode == ViewMode.COMPOSE) {
            guiWidth = (int) (width * 0.65);
            guiHeight = (int) (height * 0.85);
        } else {
            guiWidth = (int) (width * 0.50);
            guiHeight = (int) (height * 0.80);
        }
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        int contentX = guiLeft + 4;
        int contentY = guiTop + 4;
        int contentW = guiWidth - 8;
        int contentH = guiHeight - 8;

        // Only create the active view
        switch (mode) {
            case LIST -> {
                listView = new MailListView(this, contentX, contentY, contentW, contentH);
                getTree().addChild(listView);
                // Gear button
                settingsButton = EcoButton.ghost(THEME, Component.literal("\u2699"), this::openSettings);
                settingsButton.setBounds(guiLeft + guiWidth - 20, guiTop + 6, 14, 14);
                getTree().addChild(settingsButton);
                PacketDistributor.sendToServer(new RequestMailListPayload());
            }
            case DETAIL -> {
                detailView = new MailDetailView(this, contentX, contentY, contentW, contentH);
                getTree().addChild(detailView);
            }
            case COMPOSE -> {
                composeView = new MailComposeView(this, contentX, contentY, contentW, contentH);
                getTree().addChild(composeView);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight, THEME.bgDarkest, THEME.borderAccent);
    }

    // --- View navigation ---

    public void showListView() {
        rebuildForMode(ViewMode.LIST);
    }

    public void showDetailView(String mailId) {
        rebuildForMode(ViewMode.DETAIL);
        detailView.requestDetail(mailId);
    }

    public void showComposeView() {
        rebuildForMode(ViewMode.COMPOSE);
    }

    public void showComposeViewFromDraft(DraftsResponsePayload.DraftEntry draft) {
        rebuildForMode(ViewMode.COMPOSE);
        if (composeView != null) {
            composeView.prefillDraft(draft);
        }
    }

    public void showComposeViewWithReply(String recipient, String originalSubject) {
        rebuildForMode(ViewMode.COMPOSE);
        if (composeView != null) {
            composeView.prefillReply(recipient, originalSubject);
        }
    }

    public void showComposeViewWithForward(String originalSubject, String originalBody) {
        rebuildForMode(ViewMode.COMPOSE);
        if (composeView != null) {
            composeView.prefillForward(originalSubject, originalBody);
        }
    }

    private void openSettings() {
        boolean isAdmin = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            isAdmin = mc.player.hasPermissions(2);
        }
        mc.setScreen(new MailSettingsScreen(this, isAdmin, postmanEntityId, postmanSkinName, currency));
    }

    // --- Static payload receivers (called from MailClientPayloadHandler) ---

    public static void receiveMailList(MailListResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof MailboxScreen screen) {
            screen.currencySymbol = payload.currencySymbol();
            screen.currency = Currency.virtual("gold", "Gold", payload.currencySymbol(), 2);
            screen.maxItemAttachments = payload.maxItemAttachments();
            screen.sendCost = payload.sendCost();
            screen.sendCostPerItem = payload.sendCostPerItem();
            screen.allowReadReceipt = payload.allowReadReceipt();
            screen.readReceiptCost = payload.readReceiptCost();
            screen.codFeePercent = payload.codFeePercent();
            if (screen.listView != null) {
                screen.listView.onReceiveMailList(payload.mails());
            }
        }
    }

    public static void receiveSentMails(SentMailsResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof MailboxScreen screen) {
            screen.sentMails = payload.sentMails();
            if (screen.listView != null) {
                screen.listView.onReceiveSentMails(payload.sentMails());
            }
        }
    }

    public static void receiveDrafts(DraftsResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof MailboxScreen screen) {
            screen.drafts = payload.drafts();
            if (screen.listView != null) {
                screen.listView.onReceiveDrafts(payload.drafts());
            }
        }
    }

    public static void receiveMailDetail(MailDetailResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof MailboxScreen screen) {
            if (screen.detailView != null) {
                screen.detailView.onReceiveMailDetail(payload);
            }
        }
    }

    public static void receiveCollectResult(CollectMailResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof MailboxScreen screen) {
            if (payload.success()) {
                screen.showListView();
            } else {
                LOGGER.warn("Collect failed: {}", payload.message());
            }
        }
    }

    public static void receiveSendResult(SendMailResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof MailboxScreen screen) {
            if (screen.composeView != null) {
                screen.composeView.onReceiveSendResult(payload);
            }
        }
    }

    public static void open(int entityId) {
        MailboxScreen screen = new MailboxScreen();
        screen.postmanEntityId = entityId;
        Minecraft.getInstance().setScreen(screen);
    }

    public static void open() {
        open(0);
    }

    public static void receivePostmanSkin(PostmanSkinPayload payload) {
        if (Minecraft.getInstance().screen instanceof MailboxScreen screen) {
            screen.postmanEntityId = payload.entityId();
            screen.postmanSkinName = payload.skinPlayerName();
        }
    }
}

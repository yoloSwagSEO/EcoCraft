package net.ecocraft.ah.screen;

import com.mojang.logging.LogUtils;
import net.ecocraft.ah.network.payload.*;
import net.ecocraft.gui.theme.EcoColors;
import net.ecocraft.gui.theme.EcoTheme;
import net.ecocraft.gui.widget.EcoTabBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.List;

/**
 * Main auction house screen with 4 tabs:
 * Buy, Sell, My Auctions, Ledger.
 */
public class AuctionHouseScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int GUI_WIDTH = 320;
    public static final int GUI_HEIGHT = 220;

    private int guiLeft;
    private int guiTop;

    private EcoTabBar tabBar;
    private int activeTab = 0;

    private BuyTab buyTab;
    private SellTab sellTab;
    private MyAuctionsTab myAuctionsTab;
    private LedgerTab ledgerTab;

    public AuctionHouseScreen() {
        super(Component.literal("Auction House"));
    }

    @Override
    protected void init() {
        guiLeft = (width - GUI_WIDTH) / 2;
        guiTop = (height - GUI_HEIGHT) / 2;

        List<Component> tabLabels = List.of(
                Component.literal("\uD83D\uDED2 Acheter"),
                Component.literal("\uD83D\uDCB0 Vendre"),
                Component.literal("\uD83D\uDCCB Mes ench\u00e8res"),
                Component.literal("\uD83D\uDCD2 Livre de compte")
        );

        tabBar = new EcoTabBar(guiLeft + 4, guiTop + 4, tabLabels, this::onTabChanged);
        addRenderableWidget(tabBar);

        int contentX = guiLeft + 4;
        int contentY = guiTop + 30;
        int contentW = GUI_WIDTH - 8;
        int contentH = GUI_HEIGHT - 34;

        buyTab = new BuyTab(this, contentX, contentY, contentW, contentH);
        sellTab = new SellTab(this, contentX, contentY, contentW, contentH);
        myAuctionsTab = new MyAuctionsTab(this, contentX, contentY, contentW, contentH);
        ledgerTab = new LedgerTab(this, contentX, contentY, contentW, contentH);

        activateTab(activeTab);
    }

    private void onTabChanged(int newTab) {
        activeTab = newTab;
        activateTab(newTab);
    }

    private void activateTab(int tab) {
        // Remove all child widgets except the tab bar, then add current tab's widgets
        clearWidgets();
        addRenderableWidget(tabBar);
        tabBar.setActiveTab(tab);

        switch (tab) {
            case 0 -> buyTab.init(this::addRenderableWidget);
            case 1 -> sellTab.init(this::addRenderableWidget);
            case 2 -> myAuctionsTab.init(this::addRenderableWidget);
            case 3 -> ledgerTab.init(this::addRenderableWidget);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        EcoTheme.drawPanel(graphics, guiLeft, guiTop, GUI_WIDTH, GUI_HEIGHT, EcoColors.BG_DARKEST, EcoColors.BORDER_GOLD);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        switch (activeTab) {
            case 0 -> buyTab.render(graphics, mouseX, mouseY, partialTick);
            case 1 -> sellTab.render(graphics, mouseX, mouseY, partialTick);
            case 2 -> myAuctionsTab.render(graphics, mouseX, mouseY, partialTick);
            case 3 -> ledgerTab.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (!handled) {
            switch (activeTab) {
                case 0 -> handled = buyTab.mouseClicked(mouseX, mouseY, button);
                case 1 -> handled = sellTab.mouseClicked(mouseX, mouseY, button);
                case 2 -> handled = myAuctionsTab.mouseClicked(mouseX, mouseY, button);
                case 3 -> handled = ledgerTab.mouseClicked(mouseX, mouseY, button);
            }
        }
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = false;
        switch (activeTab) {
            case 0 -> handled = buyTab.keyPressed(keyCode, scanCode, modifiers);
            case 1 -> handled = sellTab.keyPressed(keyCode, scanCode, modifiers);
        }
        if (handled) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        boolean handled = false;
        switch (activeTab) {
            case 0 -> handled = buyTab.charTyped(codePoint, modifiers);
            case 1 -> handled = sellTab.charTyped(codePoint, modifiers);
        }
        if (handled) return true;
        return super.charTyped(codePoint, modifiers);
    }

    /**
     * Called by tabs to trigger a full widget rebuild for the current tab.
     */
    public void rebuildCurrentTab() {
        activateTab(activeTab);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Static methods called by ClientPayloadHandler ---

    public static void open() {
        Minecraft.getInstance().setScreen(new AuctionHouseScreen());
    }

    public static void receiveListings(ListingsResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveListings(payload);
        }
    }

    public static void receiveListingDetail(ListingDetailResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveListingDetail(payload);
        }
    }

    public static void receiveActionResult(AHActionResultPayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveActionResult(payload);
        }
    }

    public static void receiveMyListings(MyListingsResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveMyListings(payload);
        }
    }

    public static void receiveLedger(LedgerResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveLedger(payload);
        }
    }

    // --- Instance dispatch methods ---

    protected void onReceiveListings(ListingsResponsePayload payload) {
        buyTab.onReceiveListings(payload);
    }

    protected void onReceiveListingDetail(ListingDetailResponsePayload payload) {
        buyTab.onReceiveListingDetail(payload);
    }

    protected void onReceiveActionResult(AHActionResultPayload payload) {
        LOGGER.debug("ActionResult: success={} message='{}'", payload.success(), payload.message());
        // Could show a toast/notification; for now tabs can refresh data
        if (activeTab == 1) sellTab.onActionResult(payload);
        if (activeTab == 2) myAuctionsTab.onActionResult(payload);
    }

    protected void onReceiveMyListings(MyListingsResponsePayload payload) {
        myAuctionsTab.onReceiveMyListings(payload);
    }

    protected void onReceiveLedger(LedgerResponsePayload payload) {
        ledgerTab.onReceiveLedger(payload);
    }
}

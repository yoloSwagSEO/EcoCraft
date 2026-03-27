package net.ecocraft.ah.screen;

import com.mojang.logging.LogUtils;
import net.ecocraft.ah.data.AHInstance;
import net.ecocraft.ah.network.payload.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.gui.widget.TabBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.List;

/**
 * Main auction house screen with 4 tabs:
 * Buy, Sell, My Auctions, Ledger.
 */
public class AuctionHouseScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Theme THEME = Theme.dark();

    private int guiWidth;
    private int guiHeight;
    private int guiLeft;
    private int guiTop;

    private TabBar tabBar;
    private int activeTab = 0;

    // Modal dialog overlay — when non-null, captures all input
    private net.ecocraft.gui.dialog.Dialog activeDialog;

    // Balance display
    private long playerBalance = -1; // -1 = not yet received
    private String currencySymbol = "";

    // Settings (received from server)
    private boolean isAdmin = false;
    private int settingsSaleRate = 5;
    private int settingsDepositRate = 2;
    private java.util.List<Integer> settingsDurations = java.util.List.of(12, 24, 48);

    private int npcEntityId = -1;
    private String npcSkinName = "";

    // AH context (received from server)
    private String currentAhId = AHInstance.DEFAULT_ID;
    private String currentAhName = "";

    // AH instances list (received from server)
    private java.util.List<AHInstancesPayload.AHInstanceData> ahInstances = java.util.List.of();

    private BuyTab buyTab;
    private SellTab sellTab;
    private MyAuctionsTab myAuctionsTab;
    private LedgerTab ledgerTab;

    public AuctionHouseScreen() {
        super(Component.literal("Auction House"));
    }

    public String getCurrentAhId() { return currentAhId; }

    public static void receiveAHContext(AHContextPayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.currentAhId = payload.ahId();
            screen.currentAhName = payload.ahName();
        }
    }

    public static void receiveAHInstances(AHInstancesPayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.ahInstances = new java.util.ArrayList<>(payload.instances());
        }
    }

    public java.util.List<AHInstancesPayload.AHInstanceData> getAHInstances() { return ahInstances; }

    /**
     * Helper to create ItemStack from registry name (e.g. "minecraft:golden_apple").
     */
    public static ItemStack itemFromId(String itemId) {
        try {
            ResourceLocation rl = ResourceLocation.parse(itemId);
            var item = BuiltInRegistries.ITEM.get(rl);
            if (item != null) return new ItemStack(item);
        } catch (Exception e) { /* ignore */ }
        return ItemStack.EMPTY;
    }

    @Override
    protected void init() {
        guiWidth = (int) (width * 0.9);
        guiHeight = (int) (height * 0.9);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        List<Component> tabLabels = List.of(
                Component.literal("\uD83D\uDED2 Acheter"),
                Component.literal("\uD83D\uDCB0 Vendre"),
                Component.literal("\uD83D\uDCCB Mes ench\u00e8res"),
                Component.literal("\uD83D\uDCD2 Livre de compte")
        );

        tabBar = new TabBar(guiLeft + 4, guiTop + 4, tabLabels, this::onTabChanged);
        addRenderableWidget(tabBar);

        int contentX = guiLeft + 4;
        int contentY = guiTop + 30;
        int contentW = guiWidth - 8;
        int contentH = guiHeight - 34;

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
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight, THEME.bgDarkest, THEME.borderAccent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render tab backgrounds BEFORE widgets (so panels don't cover buttons)
        switch (activeTab) {
            case 0 -> buyTab.renderBackground(graphics);
            case 1 -> sellTab.renderBackground(graphics);
            case 2 -> myAuctionsTab.renderBackground(graphics);
            case 3 -> ledgerTab.renderBackground(graphics);
        }

        // Render widgets — hide mouse from widgets when dialog is open (prevents tooltips)
        boolean dialogOpen = activeDialog != null && !activeDialog.isClosed();
        int wmx = dialogOpen ? -1 : mouseX;
        int wmy = dialogOpen ? -1 : mouseY;
        super.render(graphics, wmx, wmy, partialTick);

        // Render balance in top-right corner (after widgets so it's on top)
        if (playerBalance >= 0) {
            Font font = Minecraft.getInstance().font;
            String balanceText = BuyTab.formatPrice(playerBalance);
            int textW = font.width(balanceText);
            int rightEdge = isAdmin ? guiLeft + guiWidth - font.width("\u2699") - 12 : guiLeft + guiWidth;
            int bx = rightEdge - textW - 8;
            int by = guiTop + 10;
            graphics.drawString(font, balanceText, bx, by, THEME.accent, false);
        }

        // Gear icon for admin settings
        if (isAdmin) {
            Font font = Minecraft.getInstance().font;
            String gear = "\u2699";
            int gearW = font.width(gear);
            int gearX = guiLeft + guiWidth - gearW - 4;
            int gearY = guiTop + 8;
            boolean gearHovered = mouseX >= gearX - 2 && mouseX <= gearX + gearW + 2
                    && mouseY >= gearY - 2 && mouseY <= gearY + font.lineHeight + 2;
            int gearColor = gearHovered ? THEME.textWhite : THEME.accent;
            graphics.drawString(font, gear, gearX, gearY, gearColor, false);
        }

        // Render foreground text/overlays AFTER widgets
        switch (activeTab) {
            case 0 -> buyTab.renderForeground(graphics, mouseX, mouseY, partialTick);
            case 1 -> sellTab.renderForeground(graphics, mouseX, mouseY, partialTick);
            case 2 -> myAuctionsTab.renderForeground(graphics, mouseX, mouseY, partialTick);
            case 3 -> ledgerTab.renderForeground(graphics, mouseX, mouseY, partialTick);
        }

        // Dialog overlay — rendered last, on top of everything
        if (activeDialog != null) {
            if (activeDialog.isClosed()) {
                activeDialog = null;
            } else {
                activeDialog.renderWidget(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Gear icon click → open settings
        if (isAdmin) {
            Font font = Minecraft.getInstance().font;
            String gear = "\u2699";
            int gearW = font.width(gear);
            int gearX = guiLeft + guiWidth - gearW - 4;
            int gearY = guiTop + 8;
            if (mouseX >= gearX - 2 && mouseX <= gearX + gearW + 2
                    && mouseY >= gearY - 2 && mouseY <= gearY + font.lineHeight + 2) {
                Minecraft.getInstance().setScreen(new AHSettingsScreen(
                        this, settingsSaleRate, settingsDepositRate, settingsDurations,
                        npcEntityId, npcSkinName));
                return true;
            }
        }

        // Modal dialog captures all input
        if (activeDialog != null && !activeDialog.isClosed()) {
            return activeDialog.mouseClicked(mouseX, mouseY, button);
        }
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
        if (activeDialog != null && !activeDialog.isClosed()) {
            return activeDialog.keyPressed(keyCode, scanCode, modifiers);
        }
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
        if (activeDialog != null && !activeDialog.isClosed()) {
            return activeDialog.charTyped(codePoint, modifiers);
        }
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

    /**
     * Opens a modal dialog overlay. While open, all input is captured by the dialog.
     */
    public void openDialog(net.ecocraft.gui.dialog.Dialog dialog) {
        this.activeDialog = dialog;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Static methods called by ClientPayloadHandler ---

    public static void open(int entityId) {
        AuctionHouseScreen screen = new AuctionHouseScreen();
        screen.npcEntityId = entityId;
        Minecraft.getInstance().setScreen(screen);
    }

    public static void open() { open(-1); }

    public static void receiveNPCSkin(NPCSkinPayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.npcEntityId = payload.entityId();
            screen.npcSkinName = payload.skinPlayerName();
        }
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

    public static void receiveBestPrice(BestPriceResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveBestPrice(payload);
        }
    }

    public static void receiveBalanceUpdate(BalanceUpdatePayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveBalanceUpdate(payload);
        }
    }

    public static void receiveSettings(AHSettingsPayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveSettings(payload);
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
        if (activeTab == 0) buyTab.onActionResult(payload);
        if (activeTab == 1) sellTab.onActionResult(payload);
        if (activeTab == 2) myAuctionsTab.onActionResult(payload);
    }

    protected void onReceiveMyListings(MyListingsResponsePayload payload) {
        myAuctionsTab.onReceiveMyListings(payload);
    }

    protected void onReceiveLedger(LedgerResponsePayload payload) {
        ledgerTab.onReceiveLedger(payload);
    }

    protected void onReceiveBestPrice(BestPriceResponsePayload payload) {
        sellTab.onReceiveBestPrice(payload);
    }

    protected void onReceiveBalanceUpdate(BalanceUpdatePayload payload) {
        this.playerBalance = payload.balance();
        this.currencySymbol = payload.currencySymbol();
    }

    protected void onReceiveSettings(AHSettingsPayload payload) {
        this.isAdmin = payload.isAdmin();
        this.settingsSaleRate = payload.saleRate();
        this.settingsDepositRate = payload.depositRate();
        this.settingsDurations = new java.util.ArrayList<>(payload.durations());
        // Update SellTab with current settings
        SellTab.activeDurations = payload.durations().stream().mapToInt(Integer::intValue).toArray();
        SellTab.activeTaxRate = payload.saleRate() / 100.0;
        SellTab.activeDepositRate = payload.depositRate() / 100.0;
    }
}

package net.ecocraft.ah.screen;

import com.mojang.logging.LogUtils;
import net.ecocraft.ah.data.AHInstance;
import net.ecocraft.ah.network.payload.*;
import net.ecocraft.gui.core.EcoButton;
import net.ecocraft.gui.core.EcoScreen;
import net.ecocraft.gui.core.EcoTabBar;
import net.ecocraft.gui.core.Label;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
public class AuctionHouseScreen extends EcoScreen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Theme THEME = Theme.dark();

    private int guiWidth;
    private int guiHeight;
    private int guiLeft;
    private int guiTop;

    private EcoTabBar tabBar;
    private int activeTab = 0;

    // Balance display
    private long playerBalance = -1; // -1 = not yet received
    private String currencySymbol = "G";
    private Label balanceLabel;

    // Settings (received from server)
    private boolean isAdmin = false;
    private int settingsSaleRate = 5;
    private int settingsDepositRate = 2;
    private java.util.List<Integer> settingsDurations = java.util.List.of(12, 24, 48);

    // Gear button for admin settings
    private EcoButton gearButton;

    private int npcEntityId = -1;
    private String npcSkinName = "";
    private String npcLinkedAhId = AHInstance.DEFAULT_ID;

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
        super(Component.translatable("ecocraft_ah.screen.title"));
    }

    public String getCurrencySymbol() { return currencySymbol; }
    public String getCurrentAhId() { return currentAhId; }
    public void setCurrentAhId(String ahId) {
        this.currentAhId = ahId;
        updateSellTabFromCurrentAH();
    }
    public void setCurrentAhName(String name) { this.currentAhName = name; }

    public static void receiveAHContext(AHContextPayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.currentAhId = payload.ahId();
            screen.currentAhName = payload.ahName();
        }
    }

    public static void receiveAHInstances(AHInstancesPayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.ahInstances = new java.util.ArrayList<>(payload.instances());
            // Update SellTab with the current AH's rates and durations
            screen.updateSellTabFromCurrentAH();
        }
    }

    private void updateSellTabFromCurrentAH() {
        for (var inst : ahInstances) {
            if (inst.id().equals(currentAhId)) {
                sellTab.activeTaxRate = inst.saleRate() / 100.0;
                sellTab.activeDepositRate = inst.depositRate() / 100.0;
                sellTab.activeDurations = inst.durations().stream().mapToInt(Integer::intValue).toArray();
                sellTab.activeAllowBuyout = inst.allowBuyout();
                sellTab.activeAllowAuction = inst.allowAuction();
                // Disable sell tab if neither buyout nor auction is allowed
                boolean sellEnabled = inst.allowBuyout() || inst.allowAuction();
                if (tabBar != null) {
                    tabBar.setTabEnabled(1, sellEnabled);
                }
                if (sellTab != null && !sellEnabled && activeTab == 1) {
                    activateTab(0); // switch away from disabled sell tab
                }
                return;
            }
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
        super.init();

        guiWidth = (int) (width * 0.9);
        guiHeight = (int) (height * 0.9);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        List<Component> tabLabels = List.of(
                Component.translatable("ecocraft_ah.tab.buy"),
                Component.translatable("ecocraft_ah.tab.sell"),
                Component.translatable("ecocraft_ah.tab.my_auctions"),
                Component.translatable("ecocraft_ah.tab.ledger")
        );

        tabBar = new EcoTabBar(guiLeft + 4, guiTop + 4, tabLabels, this::onTabChanged);
        getTree().addChild(tabBar);

        int contentX = guiLeft + 4;
        int contentY = guiTop + 30;
        int contentW = guiWidth - 8;
        int contentH = guiHeight - 34;

        buyTab = new BuyTab(this, contentX, contentY, contentW, contentH);
        sellTab = new SellTab(this, contentX, contentY, contentW, contentH);
        myAuctionsTab = new MyAuctionsTab(this, contentX, contentY, contentW, contentH);
        ledgerTab = new LedgerTab(this, contentX, contentY, contentW, contentH);

        getTree().addChild(buyTab);
        getTree().addChild(sellTab);
        getTree().addChild(myAuctionsTab);
        getTree().addChild(ledgerTab);

        // Balance label in top-right corner
        Font font = Minecraft.getInstance().font;
        balanceLabel = new Label(font, 0, guiTop + 10, Component.literal(""), THEME);
        balanceLabel.setColor(THEME.accent);
        getTree().addChild(balanceLabel);

        // Gear button for admin settings
        gearButton = EcoButton.builder(Component.literal("\u2699"), this::onGearClicked)
                .theme(THEME).bounds(guiLeft + guiWidth - 20, guiTop + 6, 16, 16)
                .bgColor(THEME.bgMedium).borderColor(THEME.borderLight)
                .textColor(THEME.accent).hoverBg(THEME.bgLight).build();
        gearButton.setVisible(true);
        getTree().addChild(gearButton);

        activateTab(activeTab);
    }

    private void onTabChanged(int newTab) {
        activeTab = newTab;
        activateTab(newTab);
    }

    private void activateTab(int tab) {
        tabBar.setActiveTab(tab);
        buyTab.setVisible(tab == 0);
        sellTab.setVisible(tab == 1 && (sellTab.activeAllowBuyout || sellTab.activeAllowAuction));
        myAuctionsTab.setVisible(tab == 2);
        ledgerTab.setVisible(tab == 3);

        // Request data for the newly activated tab
        switch (tab) {
            case 0 -> buyTab.onActivated();
            case 1 -> sellTab.onActivated();
            case 2 -> myAuctionsTab.onActivated();
            case 3 -> ledgerTab.onActivated();
        }
    }

    private void onGearClicked() {
        Minecraft.getInstance().setScreen(new AHSettingsScreen(
                this, npcEntityId, npcSkinName,
                npcLinkedAhId, new java.util.ArrayList<>(ahInstances), isAdmin));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight, THEME.bgDarkest, THEME.borderAccent);
    }

    private void updateBalanceLabel() {
        if (playerBalance >= 0 && balanceLabel != null) {
            String balanceText = BuyTab.formatPrice(playerBalance, currencySymbol);
            balanceLabel.setText(Component.literal(balanceText));
            int textW = Minecraft.getInstance().font.width(balanceText);
            int rightEdge = guiLeft + guiWidth - 24; // gear button always visible
            balanceLabel.setPosition(rightEdge - textW - 8, guiTop + 10);
        }
    }

    /**
     * Called by tabs that need to rebuild their children.
     * In V2, tabs manage their own children, so this just triggers a re-init.
     */
    public void rebuildCurrentTab() {
        // In V2, tabs manage their own state. This is called for backward compat
        // when tabs need to rebuild (e.g. category change, mode switch).
        // Re-init the screen to rebuild everything.
        init();
    }

    /**
     * Opens a modal dialog overlay via the tree portal system.
     */
    public void openDialog(net.ecocraft.gui.core.EcoDialog dialog) {
        getTree().addPortal(dialog);
    }

    // --- Static methods called by ClientPayloadHandler ---

    public static void open(int entityId, String ahId, String ahName) {
        AuctionHouseScreen screen = new AuctionHouseScreen();
        screen.npcEntityId = entityId;
        screen.currentAhId = ahId;
        screen.currentAhName = ahName;
        Minecraft.getInstance().setScreen(screen);
    }

    public static void open(int entityId) { open(entityId, AHInstance.DEFAULT_ID, ""); }
    public static void open() { open(-1); }

    public static void receiveNPCSkin(NPCSkinPayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.npcEntityId = payload.entityId();
            screen.npcSkinName = payload.skinPlayerName();
            screen.npcLinkedAhId = payload.linkedAhId();
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

    public static void receiveBidHistory(BidHistoryResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.buyTab.onReceiveBidHistory(payload);
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
        updateBalanceLabel();
    }

    protected void onReceiveSettings(AHSettingsPayload payload) {
        this.isAdmin = payload.isAdmin();
        this.settingsSaleRate = payload.saleRate();
        this.settingsDepositRate = payload.depositRate();
        this.settingsDurations = new java.util.ArrayList<>(payload.durations());
        // Update SellTab with current settings
        sellTab.activeDurations = payload.durations().stream().mapToInt(Integer::intValue).toArray();
        sellTab.activeTaxRate = payload.saleRate() / 100.0;
        sellTab.activeDepositRate = payload.depositRate() / 100.0;
        // Update gear button visibility
        if (gearButton != null) {
            gearButton.setVisible(true);
            updateBalanceLabel();
        }
    }
}

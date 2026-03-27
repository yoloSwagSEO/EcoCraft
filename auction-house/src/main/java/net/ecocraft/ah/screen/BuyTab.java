package net.ecocraft.ah.screen;

import net.ecocraft.ah.data.ItemCategory;
import net.ecocraft.ah.data.ItemStackSerializer;
import net.ecocraft.ah.network.payload.*;
import net.ecocraft.gui.table.PaginatedTable;
import net.ecocraft.gui.table.TableColumn;
import net.ecocraft.gui.table.TableRow;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.gui.widget.Button;
import net.ecocraft.gui.widget.TextInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Buy tab with two modes: BROWSE and DETAIL.
 */
public class BuyTab {

    private enum Mode { BROWSE, DETAIL }

    private static final Theme THEME = Theme.dark();

    private final AuctionHouseScreen parent;
    private final int x, y, w, h;

    private Mode mode = Mode.BROWSE;

    // Browse mode state
    private String searchText = "";
    private String selectedCategory = "";
    private int currentPage = 0;
    private int totalPages = 0;
    private List<ListingsResponsePayload.ListingSummary> currentItems = List.of();

    // Browse mode widgets
    private TextInput searchBar;
    private PaginatedTable browseTable;
    private Button prevPageBtn;
    private Button nextPageBtn;
    private final List<Button> categoryButtons = new ArrayList<>();

    // Detail mode state
    private String detailItemId = "";
    private String detailItemName = "";
    private int detailRarityColor = THEME.rarityCommon;
    private List<ListingDetailResponsePayload.ListingEntry> detailEntries = List.of();
    private ListingDetailResponsePayload.PriceInfo detailPriceInfo;

    // Detail mode widgets
    private Button backButton;
    private PaginatedTable detailTable;

    private static final int SIDEBAR_WIDTH = 80;

    public BuyTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void init(Consumer<AbstractWidget> addWidget) {
        if (mode == Mode.BROWSE) {
            initBrowse(addWidget);
        } else {
            initDetail(addWidget);
        }
    }

    private void initBrowse(Consumer<AbstractWidget> addWidget) {
        Font font = Minecraft.getInstance().font;

        // Category sidebar buttons
        categoryButtons.clear();
        String[] categories = {"Tout", "Armes", "Armures", "Outils", "Potions", "Blocs", "Nourrit.", "Enchant.", "Divers"};
        int btnY = y + 2;
        for (int i = 0; i < categories.length; i++) {
            final int catIndex = i;
            Button btn;
            if (catIndex == getCategoryIndex()) {
                btn = Button.builder(Component.literal(categories[i]), () -> onCategoryClicked(catIndex))
                        .theme(THEME).bounds(x + 2, btnY, SIDEBAR_WIDTH - 6, 14)
                        .bgColor(THEME.accentBg).borderColor(THEME.borderAccent)
                        .textColor(THEME.accent).hoverBg(THEME.accentBgDim).build();
            } else {
                btn = Button.builder(Component.literal(categories[i]), () -> onCategoryClicked(catIndex))
                        .theme(THEME).bounds(x + 2, btnY, SIDEBAR_WIDTH - 6, 14)
                        .bgColor(THEME.bgMedium).borderColor(THEME.borderLight)
                        .textColor(THEME.textGrey).hoverBg(THEME.bgLight).build();
            }
            categoryButtons.add(btn);
            addWidget.accept(btn);
            btnY += 16;
        }

        // Content area (right of sidebar with 2px gap)
        int contentX = x + SIDEBAR_WIDTH + 2;
        int contentW = w - SIDEBAR_WIDTH - 2;

        // Search bar
        searchBar = new TextInput(font, contentX + 2, y + 2, contentW - 4, 12,
                Component.literal("Rechercher..."), THEME);
        searchBar.responder(this::onSearchChanged);
        searchBar.setValue(searchText);
        addWidget.accept(searchBar);

        // Browse table
        int tableY = y + 20;
        int tableH = h - 38;
        List<TableColumn> columns = List.of(
                TableColumn.left(Component.literal("Objet"), 3f),
                TableColumn.right(Component.literal("Meilleur prix"), 2f),
                TableColumn.center(Component.literal("Offres"), 1f),
                TableColumn.center(Component.literal("Dispo."), 1f)
        );
        browseTable = new PaginatedTable(contentX, tableY, contentW, tableH, columns);
        addWidget.accept(browseTable);

        // Pagination buttons
        int paginationY = y + h - 16;
        prevPageBtn = Button.builder(Component.literal("< Pr\u00e9c"), this::onPrevPage)
                .theme(THEME).bounds(contentX, paginationY, 40, 14)
                .bgColor(THEME.accentBg).borderColor(THEME.borderAccent)
                .textColor(THEME.accent).hoverBg(THEME.accentBgDim).build();
        nextPageBtn = Button.builder(Component.literal("Suiv >"), this::onNextPage)
                .theme(THEME).bounds(contentX + contentW - 40, paginationY, 40, 14)
                .bgColor(THEME.accentBg).borderColor(THEME.borderAccent)
                .textColor(THEME.accent).hoverBg(THEME.accentBgDim).build();
        addWidget.accept(prevPageBtn);
        addWidget.accept(nextPageBtn);

        // Populate table with current data
        updateBrowseTable();

        // Request initial data
        requestListings();
    }

    private void initDetail(Consumer<AbstractWidget> addWidget) {
        Font font = Minecraft.getInstance().font;

        // Back button
        backButton = Button.builder(Component.literal("\u25C0 Retour"), this::onBackToBrowse)
                .theme(THEME).bounds(x, y, 60, 14)
                .bgColor(THEME.accentBg).borderColor(THEME.borderAccent)
                .textColor(THEME.accent).hoverBg(THEME.accentBgDim).build();
        addWidget.accept(backButton);

        // Detail table
        int tableY = y + 30;
        int tableH = h - 54; // leave room for price history below
        int tableW = w - 4;
        List<TableColumn> columns = List.of(
                TableColumn.left(Component.literal("Vendeur"), 2f),
                TableColumn.center(Component.literal("Qt\u00e9"), 1f),
                TableColumn.right(Component.literal("Prix unit."), 2f),
                TableColumn.center(Component.literal("Type"), 1f),
                TableColumn.center(Component.literal("Expire"), 1.5f),
                TableColumn.center(Component.literal("Action"), 1.5f)
        );
        detailTable = new PaginatedTable(x, tableY, tableW, tableH, columns);
        addWidget.accept(detailTable);

        updateDetailTable();
    }

    // --- Rendering ---

    /** Called BEFORE widgets render — draw background panels here. */
    public void renderBackground(GuiGraphics graphics) {
        if (mode == Mode.BROWSE) {
            DrawUtils.drawPanel(graphics, x, y, SIDEBAR_WIDTH - 4, h, THEME);
        }
    }

    /** Called AFTER widgets render — draw text overlays here. */
    public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        if (mode == Mode.BROWSE) {
            int contentX = x + SIDEBAR_WIDTH + 2;
            int contentW = w - SIDEBAR_WIDTH - 2;
            int paginationY = y + h - 16;
            String pageInfo = "Page " + (currentPage + 1) + "/" + Math.max(1, totalPages);
            int pageInfoWidth = font.width(pageInfo);
            graphics.drawString(font, pageInfo,
                    contentX + (contentW - pageInfoWidth) / 2,
                    paginationY + 3, THEME.textGrey, false);
        } else {
            renderDetailForeground(graphics, font, mouseX, mouseY);
        }
    }

    /** Keep old render for backward compat — delegates to background+foreground. */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // no-op: AuctionHouseScreen now calls renderBackground + renderForeground separately
    }

    private void renderDetailForeground(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        // Item header
        int headerY = y + 18;
        int maxNameWidth = w - 170; // leave room for price info on the right
        String truncatedName = DrawUtils.truncateText(font, detailItemName, maxNameWidth);
        graphics.drawString(font, Component.literal(truncatedName), x + 64, headerY,
                detailRarityColor, false);

        // Price info panel (right side)
        if (detailPriceInfo != null) {
            int infoX = x + w - 120;
            graphics.drawString(font, "Moy: " + formatPrice(detailPriceInfo.avgPrice()),
                    infoX, y + 2, THEME.textGrey, false);
            graphics.drawString(font, "Min: " + formatPrice(detailPriceInfo.minPrice()),
                    infoX, y + 12, THEME.success, false);
            graphics.drawString(font, "Max: " + formatPrice(detailPriceInfo.maxPrice()),
                    infoX, y + 22, THEME.danger, false);
        }

        // Price history summary below the table
        int historyY = y + h - 18;
        if (detailPriceInfo != null) {
            String historyLine = "Moy: " + formatPrice(detailPriceInfo.avgPrice())
                    + " | Min: " + formatPrice(detailPriceInfo.minPrice())
                    + " | Max: " + formatPrice(detailPriceInfo.maxPrice())
                    + " | Ventes 7j: " + detailPriceInfo.volume7d();
            int historyW = font.width(historyLine);
            graphics.drawString(font, historyLine, x + (w - historyW) / 2, historyY, THEME.textGrey, false);
        } else {
            String noData = "Aucune donn\u00e9e de prix disponible";
            int noDataW = font.width(noData);
            graphics.drawString(font, noData, x + (w - noDataW) / 2, historyY, THEME.textDim, false);
        }
    }

    // --- Input handling ---

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false; // Widgets handle clicks via Screen
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (mode == Mode.BROWSE && searchBar != null && searchBar.isFocused()) {
            return searchBar.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (mode == Mode.BROWSE && searchBar != null && searchBar.isFocused()) {
            return searchBar.charTyped(codePoint, modifiers);
        }
        return false;
    }

    // --- Event handlers ---

    private void onSearchChanged(String text) {
        this.searchText = text;
        this.currentPage = 0;
        requestListings();
    }

    private void onCategoryClicked(int catIndex) {
        String[] cats = {"", "WEAPONS", "ARMOR", "TOOLS", "POTIONS", "BLOCKS", "FOOD", "ENCHANTMENTS", "MISC"};
        selectedCategory = cats[catIndex];
        currentPage = 0;
        // Re-init to update button styles
        parent.rebuildCurrentTab();
    }

    private int getCategoryIndex() {
        if (selectedCategory.isEmpty()) return 0;
        try {
            ItemCategory cat = ItemCategory.valueOf(selectedCategory);
            return cat.ordinal() + 1;
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private void onPrevPage() {
        if (currentPage > 0) {
            currentPage--;
            requestListings();
        }
    }

    private void onNextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            requestListings();
        }
    }

    private void onRowClicked(String itemId) {
        detailItemId = itemId;
        mode = Mode.DETAIL;
        parent.rebuildCurrentTab();
        PacketDistributor.sendToServer(new RequestListingDetailPayload(itemId));
    }

    private void onBackToBrowse() {
        mode = Mode.BROWSE;
        parent.rebuildCurrentTab();
    }

    private void onBuyClicked(String listingId) {
        PacketDistributor.sendToServer(new BuyListingPayload(listingId));
    }

    private void onBidClicked(String listingId) {
        // For now just place a bid at 0 (placeholder - real UI will have an input dialog)
        PacketDistributor.sendToServer(new PlaceBidPayload(listingId, 0));
    }

    // --- Network ---

    private void requestListings() {
        PacketDistributor.sendToServer(new RequestListingsPayload(searchText, selectedCategory, currentPage));
    }

    public void onReceiveListings(ListingsResponsePayload payload) {
        this.currentItems = payload.items();
        this.currentPage = payload.page();
        this.totalPages = payload.totalPages();
        if (mode == Mode.BROWSE) {
            updateBrowseTable();
        }
    }

    public void onReceiveListingDetail(ListingDetailResponsePayload payload) {
        this.detailItemId = payload.itemId();
        this.detailItemName = payload.itemName();
        this.detailRarityColor = payload.rarityColor();
        this.detailEntries = payload.entries();
        this.detailPriceInfo = payload.priceInfo();
        if (mode == Mode.DETAIL) {
            updateDetailTable();
        }
    }

    // --- Table population ---

    private void updateBrowseTable() {
        if (browseTable == null) return;

        List<TableRow> rows = new ArrayList<>();
        for (var item : currentItems) {
            ItemStack icon = AuctionHouseScreen.itemFromId(item.itemId());
            rows.add(TableRow.withIcon(icon, item.rarityColor(), List.of(
                    TableRow.Cell.of(Component.literal(item.itemName()), item.rarityColor()),
                    TableRow.Cell.of(Component.literal(formatPrice(item.bestPrice())), THEME.accent),
                    TableRow.Cell.of(Component.literal(String.valueOf(item.listingCount())), THEME.textLight),
                    TableRow.Cell.of(Component.literal(String.valueOf(item.totalAvailable())), THEME.textLight)
            ), () -> onRowClicked(item.itemId())));
        }
        browseTable.setRows(rows);
    }

    private void updateDetailTable() {
        if (detailTable == null) return;

        List<TableRow> rows = new ArrayList<>();
        for (var entry : detailEntries) {
            boolean isAuction = "AUCTION".equals(entry.type());

            // Resolve the ItemStack: prefer full NBT for enchantments/components
            ItemStack icon = ItemStack.EMPTY;
            String nbt = entry.itemNbt();
            if (nbt != null && !nbt.isEmpty()) {
                var level = Minecraft.getInstance().level;
                if (level != null) {
                    ItemStack deserialized = ItemStackSerializer.deserialize(nbt, level.registryAccess());
                    if (!deserialized.isEmpty()) {
                        icon = deserialized;
                    }
                }
            }
            if (icon.isEmpty()) {
                icon = AuctionHouseScreen.itemFromId(detailItemId);
            }

            rows.add(TableRow.withIcon(icon, detailRarityColor, List.of(
                    TableRow.Cell.of(Component.literal(entry.sellerName()), THEME.textLight),
                    TableRow.Cell.of(Component.literal(String.valueOf(entry.quantity())), THEME.textLight),
                    TableRow.Cell.of(Component.literal(formatPrice(entry.unitPrice())), THEME.accent),
                    TableRow.Cell.of(Component.literal(isAuction ? "Ench\u00e8re" : "Achat"),
                            isAuction ? THEME.warning : THEME.success),
                    TableRow.Cell.of(Component.literal(formatTimeRemaining(entry.expiresInMs())), THEME.textGrey),
                    TableRow.Cell.of(Component.literal(isAuction ? "Ench\u00e9rir" : "Acheter"),
                            isAuction ? THEME.warning : THEME.success)
            ), isAuction ? () -> onBidClicked(entry.listingId()) : () -> onBuyClicked(entry.listingId())));
        }
        detailTable.setRows(rows);
    }

    // --- Formatting helpers ---

    static String formatPrice(long price) {
        if (price <= 0) return "0 G";
        return String.format("%,d", price).replace(',', ' ') + " G";
    }

    static String formatTimeRemaining(long expiresInMs) {
        if (expiresInMs <= 0) return "Expir\u00e9";
        long hours = expiresInMs / 3_600_000;
        long minutes = (expiresInMs % 3_600_000) / 60_000;
        if (hours > 24) return (hours / 24) + "j";
        if (hours > 0) return hours + "h" + (minutes > 0 ? String.format("%02d", minutes) : "");
        return minutes + "min";
    }
}

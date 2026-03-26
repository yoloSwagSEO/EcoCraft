package net.ecocraft.ah.screen;

import net.ecocraft.ah.data.ItemCategory;
import net.ecocraft.ah.network.payload.*;
import net.ecocraft.gui.table.EcoPaginatedTable;
import net.ecocraft.gui.table.TableColumn;
import net.ecocraft.gui.table.TableRow;
import net.ecocraft.gui.theme.EcoColors;
import net.ecocraft.gui.theme.EcoTheme;
import net.ecocraft.gui.widget.EcoButton;
import net.ecocraft.gui.widget.EcoSearchBar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Buy tab with two modes: BROWSE and DETAIL.
 */
public class BuyTab {

    private enum Mode { BROWSE, DETAIL }

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
    private EcoSearchBar searchBar;
    private EcoPaginatedTable browseTable;
    private EcoButton prevPageBtn;
    private EcoButton nextPageBtn;
    private final List<EcoButton> categoryButtons = new ArrayList<>();

    // Detail mode state
    private String detailItemId = "";
    private String detailItemName = "";
    private int detailRarityColor = EcoColors.RARITY_COMMON;
    private List<ListingDetailResponsePayload.ListingEntry> detailEntries = List.of();
    private ListingDetailResponsePayload.PriceInfo detailPriceInfo;

    // Detail mode widgets
    private EcoButton backButton;
    private EcoPaginatedTable detailTable;

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
        String[] categories = {"Tout", "Armes", "Armures", "Outils", "Potions", "Blocs", "Nourriture", "Enchant.", "Divers"};
        int btnY = y + 2;
        for (int i = 0; i < categories.length; i++) {
            final int catIndex = i;
            EcoButton btn = new EcoButton(
                    x, btnY, SIDEBAR_WIDTH - 4, 14,
                    Component.literal(categories[i]),
                    catIndex == getCategoryIndex() ? EcoButton.Style.PRIMARY : EcoButton.Style.GHOST,
                    () -> onCategoryClicked(catIndex)
            );
            categoryButtons.add(btn);
            addWidget.accept(btn);
            btnY += 16;
        }

        // Content area (right of sidebar)
        int contentX = x + SIDEBAR_WIDTH;
        int contentW = w - SIDEBAR_WIDTH;

        // Search bar
        searchBar = new EcoSearchBar(font, contentX + 2, y + 2, contentW - 4, 12,
                Component.literal("Rechercher..."), this::onSearchChanged);
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
        browseTable = new EcoPaginatedTable(contentX, tableY, contentW, tableH, columns);
        addWidget.accept(browseTable);

        // Pagination buttons
        int paginationY = y + h - 16;
        prevPageBtn = EcoButton.primary(contentX, paginationY, 40, 14,
                Component.literal("< Pr\u00e9c"), this::onPrevPage);
        nextPageBtn = EcoButton.primary(contentX + contentW - 40, paginationY, 40, 14,
                Component.literal("Suiv >"), this::onNextPage);
        addWidget.accept(prevPageBtn);
        addWidget.accept(nextPageBtn);

        // Populate table with current data
        updateBrowseTable();

        // Request initial data
        requestListings();
    }

    private void initDetail(Consumer<AbstractWidget> addWidget) {
        // Back button
        backButton = EcoButton.primary(x, y, 60, 14, Component.literal("\u25C0 Retour"), this::onBackToBrowse);
        addWidget.accept(backButton);

        // Detail table
        int tableY = y + 30;
        int tableH = h - 34;
        int tableW = w - 4;
        List<TableColumn> columns = List.of(
                TableColumn.left(Component.literal("Vendeur"), 2f),
                TableColumn.center(Component.literal("Qt\u00e9"), 1f),
                TableColumn.right(Component.literal("Prix unit."), 2f),
                TableColumn.center(Component.literal("Type"), 1f),
                TableColumn.center(Component.literal("Expire"), 1.5f),
                TableColumn.center(Component.literal("Action"), 1.5f)
        );
        detailTable = new EcoPaginatedTable(x, tableY, tableW, tableH, columns);
        addWidget.accept(detailTable);

        updateDetailTable();
    }

    // --- Rendering ---

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        if (mode == Mode.BROWSE) {
            renderBrowse(graphics, font, mouseX, mouseY);
        } else {
            renderDetail(graphics, font, mouseX, mouseY);
        }
    }

    private void renderBrowse(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        // Sidebar background
        EcoTheme.drawPanel(graphics, x, y, SIDEBAR_WIDTH - 4, h);

        // Page info
        int contentX = x + SIDEBAR_WIDTH;
        int contentW = w - SIDEBAR_WIDTH;
        int paginationY = y + h - 16;
        String pageInfo = "Page " + (currentPage + 1) + "/" + Math.max(1, totalPages);
        int pageInfoWidth = font.width(pageInfo);
        graphics.drawString(font, pageInfo,
                contentX + (contentW - pageInfoWidth) / 2,
                paginationY + 3, EcoColors.TEXT_GREY, false);
    }

    private void renderDetail(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        // Item header
        int headerY = y + 18;
        graphics.drawString(font, Component.literal(detailItemName), x + 64, headerY,
                detailRarityColor, false);

        // Price info panel (right side)
        if (detailPriceInfo != null && detailPriceInfo.volume7d() > 0) {
            int infoX = x + w - 100;
            graphics.drawString(font, "Moy: " + formatPrice(detailPriceInfo.avgPrice()),
                    infoX, y + 2, EcoColors.TEXT_GREY, false);
            graphics.drawString(font, "Min: " + formatPrice(detailPriceInfo.minPrice()),
                    infoX, y + 12, EcoColors.SUCCESS, false);
            graphics.drawString(font, "Max: " + formatPrice(detailPriceInfo.maxPrice()),
                    infoX, y + 22, EcoColors.DANGER, false);
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
            rows.add(TableRow.of(List.of(
                    TableRow.Cell.of(Component.literal(item.itemName()), item.rarityColor()),
                    TableRow.Cell.of(Component.literal(formatPrice(item.bestPrice())), EcoColors.GOLD),
                    TableRow.Cell.of(Component.literal(String.valueOf(item.listingCount())), EcoColors.TEXT_LIGHT),
                    TableRow.Cell.of(Component.literal(String.valueOf(item.totalAvailable())), EcoColors.TEXT_LIGHT)
            ), () -> onRowClicked(item.itemId())));
        }
        browseTable.setRows(rows);
    }

    private void updateDetailTable() {
        if (detailTable == null) return;

        List<TableRow> rows = new ArrayList<>();
        for (var entry : detailEntries) {
            boolean isAuction = "AUCTION".equals(entry.type());
            rows.add(TableRow.of(List.of(
                    TableRow.Cell.of(Component.literal(entry.sellerName()), EcoColors.TEXT_LIGHT),
                    TableRow.Cell.of(Component.literal(String.valueOf(entry.quantity())), EcoColors.TEXT_LIGHT),
                    TableRow.Cell.of(Component.literal(formatPrice(entry.unitPrice())), EcoColors.GOLD),
                    TableRow.Cell.of(Component.literal(isAuction ? "Ench\u00e8re" : "Achat"),
                            isAuction ? EcoColors.WARNING : EcoColors.SUCCESS),
                    TableRow.Cell.of(Component.literal(formatTimeRemaining(entry.expiresInMs())), EcoColors.TEXT_GREY),
                    TableRow.Cell.of(Component.literal(isAuction ? "Ench\u00e9rir" : "Acheter"),
                            isAuction ? EcoColors.WARNING : EcoColors.SUCCESS)
            ), isAuction ? () -> onBidClicked(entry.listingId()) : () -> onBuyClicked(entry.listingId())));
        }
        detailTable.setRows(rows);
    }

    // --- Formatting helpers ---

    static String formatPrice(long price) {
        if (price <= 0) return "N/A";
        long whole = price / 100;
        long cents = price % 100;
        if (cents == 0) return whole + " G";
        return whole + "." + String.format("%02d", cents) + " G";
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

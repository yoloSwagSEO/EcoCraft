package net.ecocraft.ah.screen;

import net.ecocraft.ah.data.ItemCategory;
import net.ecocraft.ah.data.ItemStackSerializer;
import net.ecocraft.ah.network.payload.*;
import net.ecocraft.gui.table.Table;
import net.ecocraft.gui.table.TableColumn;
import net.ecocraft.gui.table.TableRow;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.gui.widget.Button;
import net.ecocraft.gui.widget.FilterTags;
import net.ecocraft.gui.widget.ItemSlot;
import net.ecocraft.gui.widget.NumberInput;
import net.ecocraft.gui.widget.TextInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
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
    private Table browseTable;
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
    private Table detailTable;

    // Detail panel state
    private int selectedEntryIndex = 0;
    private ItemSlot panelItemSlot;
    private NumberInput panelQuantityInput;
    private NumberInput panelBidInput;
    private Button panelActionButton;
    private static final int PANEL_WIDTH_RATIO = 35;

    // Detail mode filter state
    private List<ItemStack> detailStacks = new ArrayList<>();
    private Set<String> selectedEnchantFilters = new LinkedHashSet<>();
    private int selectedDurabilityFilter = 0;
    private FilterTags enchantFilterTags;
    private FilterTags durabilityFilterTags;
    private List<String> availableEnchantments = new ArrayList<>();
    private boolean showDurabilityFilter = false;

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
                TableColumn.sortableLeft(Component.literal("Objet"), 3f),
                TableColumn.sortableRight(Component.literal("Meilleur prix"), 2f),
                TableColumn.sortableCenter(Component.literal("Offres"), 1f),
                TableColumn.sortableCenter(Component.literal("Dispo."), 1f)
        );
        browseTable = Table.builder()
                .columns(columns)
                .theme(THEME)
                .navigation(Table.Navigation.PAGINATED)
                .tooltips(false) // tooltips only in detail view
                .build(contentX, tableY, contentW, tableH);
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

        // Dynamic filter area starts below back button + item name header
        int filterY = y + 34;
        enchantFilterTags = null;
        durabilityFilterTags = null;

        // Enchantment filter (only if enchantments found) — multi-select mode
        if (!availableEnchantments.isEmpty()) {
            List<Component> enchantLabels = new ArrayList<>();
            for (String ench : availableEnchantments) {
                enchantLabels.add(Component.literal(ench));
            }
            enchantFilterTags = new FilterTags(x, filterY, enchantLabels,
                    this::onEnchantFilterChanged, THEME, true);
            // Restore selection
            Set<Integer> restoredSelection = new LinkedHashSet<>();
            for (String selected : selectedEnchantFilters) {
                int idx = availableEnchantments.indexOf(selected);
                if (idx >= 0) restoredSelection.add(idx);
            }
            enchantFilterTags.setSelectedTags(restoredSelection);
            addWidget.accept(enchantFilterTags);
            filterY += 22;
        }

        // Durability filter (only if items have durability variation)
        if (showDurabilityFilter) {
            List<Component> durLabels = List.of(
                    Component.literal("Tout"),
                    Component.literal("100%"),
                    Component.literal("75%+"),
                    Component.literal("50%+"),
                    Component.literal("25%+")
            );
            durabilityFilterTags = new FilterTags(x, filterY, durLabels, this::onDurabilityFilterChanged, THEME);
            // Restore selection
            int activeIdx = switch (selectedDurabilityFilter) {
                case 100 -> 1;
                case 75 -> 2;
                case 50 -> 3;
                case 25 -> 4;
                default -> 0;
            };
            durabilityFilterTags.setActiveTag(activeIdx);
            addWidget.accept(durabilityFilterTags);
            filterY += 22;
        }

        // Two-column layout
        int panelW = (int) (w * PANEL_WIDTH_RATIO / 100.0);
        int tableW = w - panelW - 6; // 6px gap

        // Detail table (left column) — no "Action" column
        int tableY = filterY;
        int tableH = y + h - 24 - tableY;
        List<TableColumn> columns = List.of(
                TableColumn.sortableLeft(Component.literal("Vendeur"), 2f),
                TableColumn.sortableCenter(Component.literal("Qté"), 1f),
                TableColumn.sortableRight(Component.literal("Prix unit."), 2f),
                TableColumn.center(Component.literal("Type"), 1f),
                TableColumn.sortableCenter(Component.literal("Expire"), 1.5f)
        );
        detailTable = Table.builder()
                .columns(columns)
                .theme(THEME)
                .navigation(Table.Navigation.SCROLL)
                .showScrollbar(true)
                .scrollLines(1)
                .build(x, tableY, tableW, tableH);
        detailTable.setSelectionListener(this::onDetailRowSelected);
        addWidget.accept(detailTable);

        // Right panel widgets
        int panelX = x + tableW + 6;
        int panelY = y + 16;
        initPurchasePanel(addWidget, panelX, panelY, panelW);

        updateDetailTable();
    }

    private void onEnchantFilterChanged(Set<Integer> selectedIndices) {
        // Convert indices to enchantment display names
        selectedEnchantFilters.clear();
        for (int idx : selectedIndices) {
            if (idx >= 0 && idx < availableEnchantments.size()) {
                selectedEnchantFilters.add(availableEnchantments.get(idx));
            }
        }
        // Send server request with the selected enchantment filters
        requestListingDetail();
    }

    private void onDurabilityFilterChanged(int index) {
        selectedDurabilityFilter = switch (index) {
            case 1 -> 100;
            case 2 -> 75;
            case 3 -> 50;
            case 4 -> 25;
            default -> 0;
        };
        updateDetailTable();
    }

    // --- Rendering ---

    /** Called BEFORE widgets render -- draw background panels here. */
    public void renderBackground(GuiGraphics graphics) {
        if (mode == Mode.BROWSE) {
            DrawUtils.drawPanel(graphics, x, y, SIDEBAR_WIDTH - 4, h, THEME);
        }
    }

    /** Called AFTER widgets render -- draw text overlays here. */
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

    /** Keep old render for backward compat -- delegates to background+foreground. */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // no-op: AuctionHouseScreen now calls renderBackground + renderForeground separately
    }

    private void renderDetailForeground(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        // Item header (left aligned)
        int headerY = y + 18;
        int tableW = w - (int)(w * PANEL_WIDTH_RATIO / 100.0) - 6;
        String truncatedName = DrawUtils.truncateText(font, detailItemName, tableW - 70);
        graphics.drawString(font, Component.literal(truncatedName), x + 64, headerY, detailRarityColor, false);

        // Right panel background
        int panelW = (int) (w * PANEL_WIDTH_RATIO / 100.0);
        int panelX = x + tableW + 6;
        int panelY = y + 16;
        int panelH = h - 40;
        DrawUtils.drawPanel(graphics, panelX, panelY, panelW, panelH, THEME);

        // Panel title
        String title = "Offre sélectionnée";
        int titleW = font.width(title);
        graphics.drawString(font, title, panelX + (panelW - titleW) / 2, panelY + 2, THEME.accent, false);
        DrawUtils.drawAccentSeparator(graphics, panelX + 4, panelY + 12, panelW - 8, THEME);

        // Panel content (based on selected entry)
        var filtered = getFilteredEntries();
        if (selectedEntryIndex >= 0 && selectedEntryIndex < filtered.size()) {
            var entry = filtered.get(selectedEntryIndex);
            boolean isAuction = "AUCTION".equals(entry.type());
            int labelX = panelX + 8;
            int valueX = panelX + panelW - 8;
            int lineY = panelY + 42;

            // Item name
            String itemName = DrawUtils.truncateText(font, detailItemName, panelW - 16);
            int nameW = font.width(itemName);
            graphics.drawString(font, itemName, panelX + (panelW - nameW) / 2, panelY + 38, detailRarityColor, false);

            // Seller
            lineY += 14;
            graphics.drawString(font, "Vendeur:", labelX, lineY, THEME.textGrey, false);
            String seller = DrawUtils.truncateText(font, entry.sellerName(), panelW / 2);
            graphics.drawString(font, seller, valueX - font.width(seller), lineY, THEME.textLight, false);

            if (isAuction) {
                lineY += 12;
                graphics.drawString(font, "Enchère actuelle:", labelX, lineY, THEME.textGrey, false);
                String bid = entry.unitPrice() > 0 ? formatPrice(entry.unitPrice()) : "Aucune";
                graphics.drawString(font, bid, valueX - font.width(bid), lineY, THEME.warning, false);

                lineY += 12;
                long minBid = entry.unitPrice() > 0 ? entry.unitPrice() + 1 : 1;
                graphics.drawString(font, "Enchère min:", labelX, lineY, THEME.textGrey, false);
                String minStr = formatPrice(minBid);
                graphics.drawString(font, minStr, valueX - font.width(minStr), lineY, THEME.textLight, false);

                lineY += 14;
                graphics.drawString(font, "Montant:", labelX, lineY + 4, THEME.textGrey, false);

                lineY += 22;
                graphics.drawString(font, "Expire:", labelX, lineY, THEME.textGrey, false);
                String expire = formatTimeRemaining(entry.expiresInMs());
                graphics.drawString(font, expire, valueX - font.width(expire), lineY, THEME.textGrey, false);
            } else {
                lineY += 12;
                graphics.drawString(font, "Prix unitaire:", labelX, lineY, THEME.textGrey, false);
                String price = formatPrice(entry.unitPrice());
                graphics.drawString(font, price, valueX - font.width(price), lineY, THEME.accent, false);

                lineY += 14;
                graphics.drawString(font, "Quantité:", labelX, lineY + 4, THEME.textGrey, false);

                lineY += 22;
                long qty = panelQuantityInput != null ? panelQuantityInput.getValue() : entry.quantity();
                long total = entry.unitPrice() * qty;
                graphics.drawString(font, "Prix total:", labelX, lineY, THEME.textLight, false);
                String totalStr = formatPrice(total);
                graphics.drawString(font, totalStr, valueX - font.width(totalStr), lineY, THEME.accent, false);
            }
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
            String noData = "Aucune donnée de prix disponible";
            int noDataW = font.width(noData);
            graphics.drawString(font, noData, x + (w - noDataW) / 2, historyY, THEME.textDim, false);
        }

        // Price info top-right (on the table side)
        if (detailPriceInfo != null) {
            int infoX = x + tableW - 120;
            graphics.drawString(font, "Moy: " + formatPrice(detailPriceInfo.avgPrice()),
                    infoX, y + 2, THEME.textGrey, false);
            graphics.drawString(font, "Min: " + formatPrice(detailPriceInfo.minPrice()),
                    infoX, y + 12, THEME.success, false);
            graphics.drawString(font, "Max: " + formatPrice(detailPriceInfo.maxPrice()),
                    infoX, y + 22, THEME.danger, false);
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
        selectedEnchantFilters.clear();
        selectedDurabilityFilter = 0;
        availableEnchantments = new ArrayList<>();
        showDurabilityFilter = false;
        detailStacks = new ArrayList<>();
        parent.rebuildCurrentTab();
    }

    private void onPanelAction() {
        var filtered = getFilteredEntries();
        if (selectedEntryIndex < 0 || selectedEntryIndex >= filtered.size()) return;
        var entry = filtered.get(selectedEntryIndex);
        boolean isAuction = "AUCTION".equals(entry.type());

        if (isAuction) {
            long bidAmount = panelBidInput.getValue();
            PacketDistributor.sendToServer(new PlaceBidPayload(entry.listingId(), bidAmount));
        } else {
            PacketDistributor.sendToServer(new BuyListingPayload(entry.listingId()));
        }
    }

    // --- Network ---

    private void requestListings() {
        PacketDistributor.sendToServer(new RequestListingsPayload(searchText, selectedCategory, currentPage));
    }

    /**
     * Sends a detail request to the server with the current enchantment filters.
     */
    private void requestListingDetail() {
        List<String> filters = new ArrayList<>(selectedEnchantFilters);
        PacketDistributor.sendToServer(new RequestListingDetailPayload(detailItemId, filters));
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

        // Read available enchantments from server response
        this.availableEnchantments = new ArrayList<>(payload.availableEnchantments());

        // Parse durability info from received listings (stays client-side)
        parseDurabilityFilter();

        if (mode == Mode.DETAIL) {
            // Rebuild the entire detail view so filter widgets are created
            parent.rebuildCurrentTab();
        }
    }

    private void parseDurabilityFilter() {
        detailStacks = new ArrayList<>();
        boolean hasDamageable = false;
        Set<Integer> durabilityLevels = new HashSet<>();

        var level = Minecraft.getInstance().level;

        for (var entry : detailEntries) {
            ItemStack stack = ItemStack.EMPTY;
            String nbt = entry.itemNbt();
            if (nbt != null && !nbt.isEmpty() && level != null) {
                stack = ItemStackSerializer.deserialize(nbt, level.registryAccess());
            }
            detailStacks.add(stack);

            if (!stack.isEmpty()) {
                // Extract durability
                if (stack.isDamageableItem()) {
                    hasDamageable = true;
                    int maxDmg = stack.getMaxDamage();
                    int currentDmg = stack.getDamageValue();
                    int pct = (int) ((float) (maxDmg - currentDmg) / maxDmg * 100f);
                    durabilityLevels.add(pct);
                }
            }
        }

        // Show durability filter only if items are damageable and there is variation
        this.showDurabilityFilter = hasDamageable && durabilityLevels.size() > 1;
    }

    // --- Table population ---

    private void updateBrowseTable() {
        if (browseTable == null) return;

        List<TableRow> rows = new ArrayList<>();
        for (var item : currentItems) {
            ItemStack icon = AuctionHouseScreen.itemFromId(item.itemId());
            rows.add(TableRow.withIcon(icon, item.rarityColor(), List.of(
                    TableRow.Cell.of(Component.literal(item.itemName()), item.rarityColor(), item.itemName()),
                    TableRow.Cell.of(Component.literal(formatPrice(item.bestPrice())), THEME.accent, item.bestPrice()),
                    TableRow.Cell.of(Component.literal(String.valueOf(item.listingCount())), THEME.textLight, item.listingCount()),
                    TableRow.Cell.of(Component.literal(String.valueOf(item.totalAvailable())), THEME.textLight, item.totalAvailable())
            ), () -> onRowClicked(item.itemId())));
        }
        browseTable.setRows(rows);
    }

    private void updateDetailTable() {
        if (detailTable == null) return;

        var filtered = getFilteredEntries();
        List<TableRow> rows = new ArrayList<>();
        for (int i = 0; i < filtered.size(); i++) {
            var entry = filtered.get(i);
            int stackIdx = detailEntries.indexOf(entry);
            ItemStack stack = stackIdx >= 0 && stackIdx < detailStacks.size() ? detailStacks.get(stackIdx) : ItemStack.EMPTY;

            boolean isAuction = "AUCTION".equals(entry.type());
            ItemStack icon = stack.isEmpty() ? AuctionHouseScreen.itemFromId(detailItemId) : stack;

            rows.add(TableRow.withIcon(icon, detailRarityColor, List.of(
                    TableRow.Cell.of(Component.literal(entry.sellerName()), THEME.textLight, entry.sellerName()),
                    TableRow.Cell.of(Component.literal(String.valueOf(entry.quantity())), THEME.textLight, entry.quantity()),
                    TableRow.Cell.of(Component.literal(formatPrice(entry.unitPrice())), THEME.accent, entry.unitPrice()),
                    TableRow.Cell.of(Component.literal(isAuction ? "Enchère" : "Achat"),
                            isAuction ? THEME.warning : THEME.success),
                    TableRow.Cell.of(Component.literal(formatTimeRemaining(entry.expiresInMs())), THEME.textGrey, entry.expiresInMs())
            ), null));
        }
        detailTable.setRows(rows);

        // Pre-select first row (best price)
        if (!rows.isEmpty()) {
            selectedEntryIndex = 0;
            detailTable.setSelectedRow(0);
            updatePurchasePanel();
        }
    }

    private void initPurchasePanel(Consumer<AbstractWidget> addWidget, int px, int py, int pw) {
        Font font = Minecraft.getInstance().font;

        // Item slot (centered)
        int slotSize = 32;
        int slotX = px + (pw - slotSize) / 2;
        panelItemSlot = new ItemSlot(slotX, py + 4, slotSize, THEME);
        addWidget.accept(panelItemSlot);

        // Quantity input (for BUYOUT)
        int inputY = py + 100;
        panelQuantityInput = new NumberInput(font, px + 4, inputY, pw - 8, 16, THEME);
        panelQuantityInput.min(1).max(1).step(1);
        panelQuantityInput.setValue(1);
        panelQuantityInput.responder(val -> updatePanelTotal());
        addWidget.accept(panelQuantityInput);

        // Bid input (for AUCTION — overlaps quantity, only one visible at a time)
        panelBidInput = new NumberInput(font, px + 4, inputY, pw - 8, 16, THEME);
        panelBidInput.min(1).max(Long.MAX_VALUE).step(1);
        panelBidInput.setValue(1);
        panelBidInput.visible = false;
        addWidget.accept(panelBidInput);

        // Action button
        int btnY = inputY + 40;
        panelActionButton = Button.success(THEME, Component.literal("Acheter"), () -> onPanelAction());
        panelActionButton.setX(px + 4);
        panelActionButton.setY(btnY);
        panelActionButton.setWidth(pw - 8);
        panelActionButton.setHeight(20);
        addWidget.accept(panelActionButton);

        // Apply initial selection
        updatePurchasePanel();
    }

    private void onDetailRowSelected(int displayIndex) {
        this.selectedEntryIndex = displayIndex;
        updatePurchasePanel();
    }

    private void updatePurchasePanel() {
        var filtered = getFilteredEntries();
        if (selectedEntryIndex < 0 || selectedEntryIndex >= filtered.size()) {
            selectedEntryIndex = 0;
        }
        if (filtered.isEmpty()) return;

        var entry = filtered.get(selectedEntryIndex);
        boolean isAuction = "AUCTION".equals(entry.type());

        // Update item slot
        ItemStack icon;
        int stackIdx = detailEntries.indexOf(entry);
        if (stackIdx >= 0 && stackIdx < detailStacks.size() && !detailStacks.get(stackIdx).isEmpty()) {
            icon = detailStacks.get(stackIdx);
        } else {
            icon = AuctionHouseScreen.itemFromId(detailItemId);
        }
        panelItemSlot.setItem(icon, detailRarityColor);

        // Quantity vs Bid
        if (isAuction) {
            panelQuantityInput.visible = false;
            panelBidInput.visible = true;
            long minBid = entry.unitPrice() > 0 ? entry.unitPrice() + 1 : 1;
            panelBidInput.min(minBid).setValue(minBid);
            panelActionButton.setMessage(Component.literal("Enchérir"));
        } else {
            panelQuantityInput.visible = true;
            panelBidInput.visible = false;
            panelQuantityInput.max(entry.quantity()).setValue(entry.quantity());
            panelQuantityInput.setEnabled(entry.quantity() > 1);
            panelActionButton.setMessage(Component.literal("Acheter"));
        }
    }

    private void updatePanelTotal() {
        // Total is recalculated and drawn in renderDetailForeground
    }

    private List<ListingDetailResponsePayload.ListingEntry> getFilteredEntries() {
        List<ListingDetailResponsePayload.ListingEntry> filtered = new ArrayList<>();
        for (int i = 0; i < detailEntries.size(); i++) {
            ItemStack stack = i < detailStacks.size() ? detailStacks.get(i) : ItemStack.EMPTY;
            if (matchesDurabilityFilter(stack)) {
                filtered.add(detailEntries.get(i));
            }
        }
        return filtered;
    }

    private boolean matchesDurabilityFilter(ItemStack stack) {
        // Durability filter (client-side)
        if (selectedDurabilityFilter > 0) {
            if (stack.isEmpty() || !stack.isDamageableItem()) return false;
            float pct = (float) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage() * 100f;
            if (selectedDurabilityFilter == 100) {
                if (pct < 100f) return false;
            } else {
                if (pct < selectedDurabilityFilter) return false;
            }
        }

        return true;
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

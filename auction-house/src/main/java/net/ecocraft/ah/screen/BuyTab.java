package net.ecocraft.ah.screen;

import net.ecocraft.ah.data.ItemCategory;
import net.ecocraft.ah.data.ItemStackSerializer;
import net.ecocraft.ah.network.payload.*;
import net.ecocraft.gui.core.*;
import net.ecocraft.gui.table.TableColumn;
import net.ecocraft.gui.table.TableRow;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Buy tab with two modes: BROWSE and DETAIL.
 */
public class BuyTab extends BaseWidget {

    private enum Mode { BROWSE, DETAIL }

    private static final Theme THEME = Theme.dark();

    private final AuctionHouseScreen parent;
    private final int tabX, tabY, tabW, tabH;

    private Mode mode = Mode.BROWSE;

    // Browse mode state
    private String searchText = "";
    private String selectedCategory = "";
    private int currentPage = 0;
    private int totalPages = 0;
    private int browseRowsPerPage = 14;
    private List<ListingsResponsePayload.ListingSummary> currentItems = List.of();

    // Browse mode widgets
    private EcoTextInput searchBar;
    private EcoTable browseTable;
    private EcoButton prevPageBtn;
    private EcoButton nextPageBtn;
    private final List<EcoButton> categoryButtons = new ArrayList<>();
    private Label pageLabel;

    // Detail mode state
    private String detailItemId = "";
    private String detailItemName = "";
    private int detailRarityColor = THEME.rarityCommon;
    private List<ListingDetailResponsePayload.ListingEntry> detailEntries = List.of();
    private ListingDetailResponsePayload.PriceInfo detailPriceInfo;

    // Detail mode widgets
    private EcoButton backButton;
    private EcoTable detailTable;

    // Detail panel state
    private int selectedEntryIndex = 0;
    private EcoItemSlot panelItemSlot;
    private EcoNumberInput panelQuantityInput;
    private EcoNumberInput panelBidInput;
    private EcoButton panelBuyButton;
    private EcoButton panelBidButton;
    private static final int PANEL_WIDTH_RATIO = 35;

    // Detail mode filter state
    private List<ItemStack> detailStacks = new ArrayList<>();
    private Set<String> selectedEnchantFilters = new LinkedHashSet<>();
    private int selectedDurabilityFilter = 0;
    private EcoFilterTags enchantFilterTags;
    private EcoFilterTags durabilityFilterTags;
    private List<String> availableEnchantments = new ArrayList<>();
    private boolean showDurabilityFilter = false;

    private static final int SIDEBAR_WIDTH = 80;

    public BuyTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.parent = parent;
        this.tabX = x;
        this.tabY = y;
        this.tabW = w;
        this.tabH = h;
        buildWidgets();
    }

    private String getAhId() {
        return parent.getCurrentAhId();
    }

    private void buildWidgets() {
        // Remove old children
        for (WidgetNode child : new ArrayList<>(getChildren())) {
            removeChild(child);
        }
        categoryButtons.clear();

        if (mode == Mode.BROWSE) {
            buildBrowse();
        } else {
            buildDetail();
        }
    }

    private void buildBrowse() {
        Font font = Minecraft.getInstance().font;

        // Category sidebar buttons
        String[] categories = {"Tout", "Armes", "Armures", "Outils", "Potions", "Blocs", "Nourrit.", "Enchant.", "Divers"};
        int btnY = tabY + 2;
        for (int i = 0; i < categories.length; i++) {
            final int catIndex = i;
            EcoButton btn;
            if (catIndex == getCategoryIndex()) {
                btn = EcoButton.builder(Component.literal(categories[i]), () -> onCategoryClicked(catIndex))
                        .theme(THEME).bounds(tabX + 2, btnY, SIDEBAR_WIDTH - 6, 14)
                        .bgColor(THEME.accentBg).borderColor(THEME.borderAccent)
                        .textColor(THEME.accent).hoverBg(THEME.accentBgDim).build();
            } else {
                btn = EcoButton.builder(Component.literal(categories[i]), () -> onCategoryClicked(catIndex))
                        .theme(THEME).bounds(tabX + 2, btnY, SIDEBAR_WIDTH - 6, 14)
                        .bgColor(THEME.bgMedium).borderColor(THEME.borderLight)
                        .textColor(THEME.textGrey).hoverBg(THEME.bgLight).build();
            }
            categoryButtons.add(btn);
            addChild(btn);
            btnY += 16;
        }

        // Content area (right of sidebar with 2px gap)
        int contentX = tabX + SIDEBAR_WIDTH + 2;
        int contentW = tabW - SIDEBAR_WIDTH - 2;

        // Search bar
        searchBar = new EcoTextInput(font, contentX + 2, tabY + 1, contentW - 4, 16,
                Component.literal("Rechercher..."), THEME);
        searchBar.responder(this::onSearchChanged);
        searchBar.setValue(searchText);
        addChild(searchBar);

        // Browse table
        int tableY = tabY + 20;
        int tableH = tabH - 38;
        browseRowsPerPage = Math.max(1, (tableH - 22) / 24);
        List<TableColumn> columns = List.of(
                TableColumn.sortableLeft(Component.literal("Objet"), 3f),
                TableColumn.sortableRight(Component.literal("Meilleur prix"), 2f),
                TableColumn.sortableCenter(Component.literal("Offres"), 1f),
                TableColumn.sortableCenter(Component.literal("Dispo."), 1f)
        );
        browseTable = EcoTable.builder()
                .columns(columns)
                .theme(THEME)
                .navigation(EcoTable.Navigation.NONE)
                .tooltips(false)
                .build(contentX, tableY, contentW, tableH);
        addChild(browseTable);

        // Pagination: label FIRST (so buttons are on top in hit test)
        int paginationY = tabY + tabH - 16;
        String pageInfo = "Page " + (currentPage + 1) + "/" + Math.max(1, totalPages);
        pageLabel = new Label(font, contentX + 44, paginationY + 3, contentW - 88, Component.literal(pageInfo), THEME);
        pageLabel.setColor(THEME.textGrey).setAlignment(Label.Align.CENTER);
        addChild(pageLabel);

        // Pagination buttons AFTER label (on top in hit test)
        prevPageBtn = EcoButton.builder(Component.literal("< Pr\u00e9c"), this::onPrevPage)
                .theme(THEME).bounds(contentX, paginationY, 40, 14)
                .bgColor(THEME.accentBg).borderColor(THEME.borderAccent)
                .textColor(THEME.accent).hoverBg(THEME.accentBgDim).build();
        nextPageBtn = EcoButton.builder(Component.literal("Suiv >"), this::onNextPage)
                .theme(THEME).bounds(contentX + contentW - 40, paginationY, 40, 14)
                .bgColor(THEME.accentBg).borderColor(THEME.borderAccent)
                .textColor(THEME.accent).hoverBg(THEME.accentBgDim).build();
        addChild(prevPageBtn);
        addChild(nextPageBtn);

        // Populate table with current data
        updateBrowseTable();
    }

    private void buildDetail() {
        Font font = Minecraft.getInstance().font;

        // Back button
        backButton = EcoButton.builder(Component.literal("\u25C0 Retour"), this::onBackToBrowse)
                .theme(THEME).bounds(tabX, tabY, 60, 14)
                .bgColor(THEME.accentBg).borderColor(THEME.borderAccent)
                .textColor(THEME.accent).hoverBg(THEME.accentBgDim).build();
        addChild(backButton);

        // Dynamic filter area starts below back button + item name header
        int filterY = tabY + 34;
        enchantFilterTags = null;
        durabilityFilterTags = null;

        // Enchantment filter (only if enchantments found) - multi-select mode
        if (!availableEnchantments.isEmpty()) {
            List<Component> enchantLabels = new ArrayList<>();
            for (String ench : availableEnchantments) {
                enchantLabels.add(Component.literal(ench));
            }
            enchantFilterTags = new EcoFilterTags(tabX, filterY, enchantLabels,
                    this::onEnchantFilterChanged, THEME, true);
            // Restore selection
            Set<Integer> restoredSelection = new LinkedHashSet<>();
            for (String selected : selectedEnchantFilters) {
                int idx = availableEnchantments.indexOf(selected);
                if (idx >= 0) restoredSelection.add(idx);
            }
            enchantFilterTags.setSelectedTags(restoredSelection);
            addChild(enchantFilterTags);
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
            durabilityFilterTags = new EcoFilterTags(tabX, filterY, durLabels, this::onDurabilityFilterChanged, THEME);
            // Restore selection
            int activeIdx = switch (selectedDurabilityFilter) {
                case 100 -> 1;
                case 75 -> 2;
                case 50 -> 3;
                case 25 -> 4;
                default -> 0;
            };
            durabilityFilterTags.setActiveTag(activeIdx);
            addChild(durabilityFilterTags);
            filterY += 22;
        }

        // Two-column layout
        int panelW = (int) (tabW * PANEL_WIDTH_RATIO / 100.0);
        int tableW = tabW - panelW - 6;

        // Detail table (left column)
        int tableY = filterY;
        int tableH = tabY + tabH - 24 - tableY;
        List<TableColumn> columns = List.of(
                TableColumn.sortableLeft(Component.literal("Vendeur"), 2f),
                TableColumn.sortableCenter(Component.literal("Qté"), 1f),
                TableColumn.sortableRight(Component.literal("Prix unit."), 2f),
                TableColumn.center(Component.literal("Type"), 1f),
                TableColumn.sortableCenter(Component.literal("Expire"), 1.5f)
        );
        detailTable = EcoTable.builder()
                .columns(columns)
                .theme(THEME)
                .navigation(EcoTable.Navigation.SCROLL)
                .showScrollbar(true)
                .scrollLines(1)
                .build(tabX, tableY, tableW, tableH);
        detailTable.setSelectionListener(this::onDetailRowSelected);
        addChild(detailTable);

        // Right panel widgets
        int panelX = tabX + tableW + 6;
        int panelY = tabY + 16;
        buildPurchasePanel(panelX, panelY, panelW);

        updateDetailTable();
    }

    private void onEnchantFilterChanged(Set<Integer> selectedIndices) {
        selectedEnchantFilters.clear();
        for (int idx : selectedIndices) {
            if (idx >= 0 && idx < availableEnchantments.size()) {
                selectedEnchantFilters.add(availableEnchantments.get(idx));
            }
        }
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (mode == Mode.BROWSE) {
            // Draw sidebar panel background
            DrawUtils.drawPanel(graphics, tabX, tabY, SIDEBAR_WIDTH - 4, tabH, THEME);
        } else {
            // Detail mode backgrounds
            int panelW = (int) (tabW * PANEL_WIDTH_RATIO / 100.0);
            int tableW = tabW - panelW - 6;
            int panelX = tabX + tableW + 6;
            int panelY = tabY + 16;
            int panelH = tabH - 40;
            DrawUtils.drawPanel(graphics, panelX, panelY, panelW, panelH, THEME);

            renderDetailForeground(graphics, Minecraft.getInstance().font, mouseX, mouseY);
        }
    }

    private void renderDetailForeground(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        // Item header (left aligned)
        int headerY = tabY + 18;
        int tableW = tabW - (int)(tabW * PANEL_WIDTH_RATIO / 100.0) - 6;
        String truncatedName = DrawUtils.truncateText(font, detailItemName, tableW - 70);
        graphics.drawString(font, Component.literal(truncatedName), tabX + 64, headerY, detailRarityColor, false);

        // Right panel labels
        int panelW = (int) (tabW * PANEL_WIDTH_RATIO / 100.0);
        int panelX = tabX + tableW + 6;
        int panelY = tabY + 16;

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

            // Item name (below item slot)
            String itemName = DrawUtils.truncateText(font, detailItemName, panelW - 16);
            int nameW = font.width(itemName);
            graphics.drawString(font, itemName, panelX + (panelW - nameW) / 2, panelY + 52, detailRarityColor, false);

            // Separator
            DrawUtils.drawAccentSeparator(graphics, panelX + 8, panelY + 62, panelW - 16, THEME);

            // Seller
            graphics.drawString(font, "Vendeur:", labelX, panelY + 66, THEME.textGrey, false);
            String seller = DrawUtils.truncateText(font, entry.sellerName(), panelW / 2);
            graphics.drawString(font, seller, valueX - font.width(seller), panelY + 66, THEME.textLight, false);

            if (isAuction) {
                graphics.drawString(font, "Enchère actuelle:", labelX, panelY + 80, THEME.textGrey, false);
                String bid = entry.unitPrice() > 0 ? formatPrice(entry.unitPrice()) : "Aucune";
                graphics.drawString(font, bid, valueX - font.width(bid), panelY + 80, THEME.warning, false);

                long minBid = entry.unitPrice() > 0 ? entry.unitPrice() + 1 : 1;
                graphics.drawString(font, "Enchère min:", labelX, panelY + 94, THEME.textGrey, false);
                String minStr = formatPrice(minBid);
                graphics.drawString(font, minStr, valueX - font.width(minStr), panelY + 94, THEME.textLight, false);

                graphics.drawString(font, "Montant:", labelX, panelY + 112, THEME.textGrey, false);

                graphics.drawString(font, "Expire:", labelX, panelY + 134, THEME.textGrey, false);
                String expire = formatTimeRemaining(entry.expiresInMs());
                graphics.drawString(font, expire, valueX - font.width(expire), panelY + 134, THEME.textGrey, false);
            } else {
                graphics.drawString(font, "Prix unitaire:", labelX, panelY + 80, THEME.textGrey, false);
                String price = formatPrice(entry.unitPrice());
                graphics.drawString(font, price, valueX - font.width(price), panelY + 80, THEME.accent, false);

                graphics.drawString(font, "Quantité:", labelX, panelY + 96, THEME.textGrey, false);

                long qty = panelQuantityInput != null ? panelQuantityInput.getValue() : entry.quantity();
                long total = entry.unitPrice() * qty;
                graphics.drawString(font, "Prix total:", labelX, panelY + 132, THEME.textLight, false);
                String totalStr = formatPrice(total);
                graphics.drawString(font, totalStr, valueX - font.width(totalStr), panelY + 132, THEME.accent, false);
            }
        }

        // Price history summary below the table
        int historyY = tabY + tabH - 18;
        if (detailPriceInfo != null) {
            String historyLine = "Moy: " + formatPrice(detailPriceInfo.avgPrice())
                    + " | Min: " + formatPrice(detailPriceInfo.minPrice())
                    + " | Max: " + formatPrice(detailPriceInfo.maxPrice())
                    + " | Ventes 7j: " + detailPriceInfo.volume7d();
            int historyW = font.width(historyLine);
            graphics.drawString(font, historyLine, tabX + (tabW - historyW) / 2, historyY, THEME.textGrey, false);
        } else {
            String noData = "Aucune donnée de prix disponible";
            int noDataW = font.width(noData);
            graphics.drawString(font, noData, tabX + (tabW - noDataW) / 2, historyY, THEME.textDim, false);
        }

        // Price info top-right (on the table side)
        if (detailPriceInfo != null) {
            int infoX = tabX + tableW - 120;
            graphics.drawString(font, "Moy: " + formatPrice(detailPriceInfo.avgPrice()),
                    infoX, tabY + 2, THEME.textGrey, false);
            graphics.drawString(font, "Min: " + formatPrice(detailPriceInfo.minPrice()),
                    infoX, tabY + 12, THEME.success, false);
            graphics.drawString(font, "Max: " + formatPrice(detailPriceInfo.maxPrice()),
                    infoX, tabY + 22, THEME.danger, false);
        }
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
        buildWidgets();
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
        com.mojang.logging.LogUtils.getLogger().info("[AH] onPrevPage called, currentPage={}", currentPage);
        if (currentPage > 0) {
            currentPage--;
            requestListings();
        }
    }

    private void onNextPage() {
        com.mojang.logging.LogUtils.getLogger().info("[AH] onNextPage called, currentPage={} totalPages={}", currentPage, totalPages);
        if (currentPage < totalPages - 1) {
            currentPage++;
            requestListings();
        }
    }

    private void onRowClicked(String itemId) {
        detailItemId = itemId;
        mode = Mode.DETAIL;
        buildWidgets();
        PacketDistributor.sendToServer(new RequestListingDetailPayload(getAhId(), itemId));
    }

    private void onBackToBrowse() {
        mode = Mode.BROWSE;
        selectedEnchantFilters.clear();
        selectedDurabilityFilter = 0;
        availableEnchantments = new ArrayList<>();
        showDurabilityFilter = false;
        detailStacks = new ArrayList<>();
        buildWidgets();
    }

    private void onPanelAction() {
        var filtered = getFilteredEntries();
        if (selectedEntryIndex < 0 || selectedEntryIndex >= filtered.size()) return;
        var entry = filtered.get(selectedEntryIndex);
        boolean isAuction = "AUCTION".equals(entry.type());

        if (isAuction) {
            long bidAmount = panelBidInput.getValue();
            PacketDistributor.sendToServer(new PlaceBidPayload(getAhId(), entry.listingId(), bidAmount));
        } else {
            int qty = (int) panelQuantityInput.getValue();
            PacketDistributor.sendToServer(new BuyListingPayload(getAhId(), entry.listingId(), qty));
        }
    }

    // --- Network ---

    private void requestListings() {
        com.mojang.logging.LogUtils.getLogger().info("[AH Client] BuyTab.requestListings ahId={}", getAhId());
        PacketDistributor.sendToServer(new RequestListingsPayload(getAhId(), searchText, selectedCategory, currentPage, browseRowsPerPage));
    }

    private void requestListingDetail() {
        List<String> filters = new ArrayList<>(selectedEnchantFilters);
        PacketDistributor.sendToServer(new RequestListingDetailPayload(getAhId(), detailItemId, filters));
    }

    /** Called when tab becomes visible. */
    public void onActivated() {
        if (mode == Mode.BROWSE) {
            requestListings();
        }
    }

    public void onActionResult(AHActionResultPayload payload) {
        if (mode == Mode.DETAIL) {
            requestListingDetail();
        }
    }

    public void onReceiveListings(ListingsResponsePayload payload) {
        this.currentItems = payload.items();
        this.currentPage = payload.page();
        this.totalPages = payload.totalPages();
        if (mode == Mode.BROWSE) {
            updateBrowseTable();
            updatePageLabel();
        }
    }

    public void onReceiveListingDetail(ListingDetailResponsePayload payload) {
        this.detailItemId = payload.itemId();
        this.detailItemName = payload.itemName();
        this.detailRarityColor = payload.rarityColor();
        this.detailEntries = payload.entries();
        this.detailPriceInfo = payload.priceInfo();

        this.availableEnchantments = new ArrayList<>(payload.availableEnchantments());

        parseDurabilityFilter();

        if (mode == Mode.DETAIL) {
            buildWidgets();
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
                if (stack.isDamageableItem()) {
                    hasDamageable = true;
                    int maxDmg = stack.getMaxDamage();
                    int currentDmg = stack.getDamageValue();
                    int pct = (int) ((float) (maxDmg - currentDmg) / maxDmg * 100f);
                    durabilityLevels.add(pct);
                }
            }
        }

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

    private void updatePageLabel() {
        if (pageLabel != null) {
            String pageInfo = "Page " + (currentPage + 1) + "/" + Math.max(1, totalPages);
            pageLabel.setText(Component.literal(pageInfo));
        }
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

        if (!rows.isEmpty()) {
            selectedEntryIndex = 0;
            detailTable.setSelectedRow(0);
            updatePurchasePanel();
        }
    }

    private void buildPurchasePanel(int px, int py, int pw) {
        int slotSize = 32;
        int slotX = px + (pw - slotSize) / 2;
        panelItemSlot = new EcoItemSlot(slotX, py + 16, slotSize, THEME);
        addChild(panelItemSlot);

        Font font = Minecraft.getInstance().font;

        // Quantity input (for BUYOUT)
        int inputY = py + 108;
        panelQuantityInput = new EcoNumberInput(font, px + 8, inputY, pw - 16, 18, THEME);
        panelQuantityInput.min(1).max(1).step(1);
        panelQuantityInput.setValue(1);
        panelQuantityInput.responder(val -> updatePanelTotal());
        addChild(panelQuantityInput);

        // Bid input (for AUCTION -- overlaps quantity, only one visible at a time)
        panelBidInput = new EcoNumberInput(font, px + 8, inputY, pw - 16, 18, THEME);
        panelBidInput.min(1).max(Long.MAX_VALUE).step(1);
        panelBidInput.setValue(1);
        panelBidInput.setVisible(false);
        addChild(panelBidInput);

        // Action button
        int btnY = py + 152;
        panelBuyButton = EcoButton.success(THEME, Component.literal("Acheter"), () -> onPanelAction());
        panelBuyButton.setPosition(px + 8, btnY);
        panelBuyButton.setSize(pw - 16, 20);
        addChild(panelBuyButton);

        panelBidButton = EcoButton.warning(THEME, Component.literal("Enchérir"), () -> onPanelAction());
        panelBidButton.setPosition(px + 8, btnY);
        panelBidButton.setSize(pw - 16, 20);
        panelBidButton.setVisible(false);
        addChild(panelBidButton);

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
        if (panelItemSlot != null) panelItemSlot.setItem(icon, detailRarityColor);

        // Quantity vs Bid
        if (isAuction) {
            if (panelQuantityInput != null) panelQuantityInput.setVisible(false);
            if (panelBidInput != null) {
                panelBidInput.setVisible(true);
                long minBid = entry.unitPrice() > 0 ? entry.unitPrice() + 1 : 1;
                panelBidInput.min(minBid).setValue(minBid);
            }
            if (panelBuyButton != null) panelBuyButton.setVisible(false);
            if (panelBidButton != null) panelBidButton.setVisible(true);
        } else {
            if (panelQuantityInput != null) {
                panelQuantityInput.setVisible(true);
                panelQuantityInput.max(entry.quantity()).setValue(1);
                panelQuantityInput.setEnabled(entry.quantity() > 1);
            }
            if (panelBidInput != null) panelBidInput.setVisible(false);
            if (panelBuyButton != null) panelBuyButton.setVisible(true);
            if (panelBidButton != null) panelBidButton.setVisible(false);
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

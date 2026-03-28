package net.ecocraft.ah.screen;

import net.ecocraft.ah.network.payload.LedgerResponsePayload;
import net.ecocraft.ah.network.payload.RequestLedgerPayload;
import net.ecocraft.gui.table.Table;
import net.ecocraft.gui.table.TableColumn;
import net.ecocraft.gui.table.TableRow;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.gui.widget.Button;
import net.ecocraft.gui.widget.FilterTags;
import net.ecocraft.gui.widget.StatCard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Consumer;

/**
 * Ledger tab: transaction history with filters and stats.
 * When entries span multiple AH instances, an extra "AH" column and filter appear.
 */
public class LedgerTab {

    private static final Theme THEME = Theme.dark();
    private static final String[] PERIODS = {"24h", "7j", "30j", "all"};
    private static final String[] PERIOD_LABELS = {"24h", "7j", "30j", "Tout"};
    private static final String[] TYPE_FILTERS = {"all", "purchases", "sales", "auctions", "expired"};
    private static final String[] TYPE_FILTER_LABELS = {"Tout", "Achats", "Ventes", "Ench\u00e8res", "Expirations"};

    private final AuctionHouseScreen parent;
    private final int x, y, w, h;

    // State
    private int activePeriod = 1; // default 7j
    private int activeTypeFilter = 0; // default all
    private List<LedgerResponsePayload.LedgerEntry> entries = List.of();
    private long netProfit = 0;
    private long totalSales = 0;
    private long totalPurchases = 0;
    private long taxesPaid = 0;

    // Multi-AH state
    private boolean multiAH = false;
    private List<String> ahIds = List.of();
    private List<String> ahNamesList = List.of();
    private int activeAHFilter = 0; // 0=Tout, 1..N = specific AH

    // Widgets
    private FilterTags periodTags;
    private FilterTags typeTags;
    private FilterTags ahFilterTags;
    private Table table;
    private StatCard profitCard;
    private StatCard salesCard;
    private StatCard purchasesCard;
    private StatCard taxCard;

    public LedgerTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void init(Consumer<AbstractWidget> addWidget) {
        Font font = Minecraft.getInstance().font;

        // Period filter
        List<Component> periodLabels = new ArrayList<>();
        for (String label : PERIOD_LABELS) periodLabels.add(Component.literal(label));
        periodTags = new FilterTags(x, y, periodLabels, this::onPeriodChanged);
        periodTags.setActiveTag(activePeriod);
        addWidget.accept(periodTags);

        // Type filter
        List<Component> typeLabels = new ArrayList<>();
        for (String label : TYPE_FILTER_LABELS) typeLabels.add(Component.literal(label));
        typeTags = new FilterTags(x, y + 22, typeLabels, this::onTypeFilterChanged);
        typeTags.setActiveTag(activeTypeFilter);
        addWidget.accept(typeTags);

        int filterY = y + 44;

        // AH filter (only when multiAH)
        ahFilterTags = null;
        if (multiAH && !ahNamesList.isEmpty()) {
            List<Component> ahLabels = new ArrayList<>();
            ahLabels.add(Component.literal("Tout"));
            for (String name : ahNamesList) {
                ahLabels.add(Component.literal(name));
            }
            ahFilterTags = new FilterTags(x, filterY, ahLabels, this::onAHFilterChanged);
            ahFilterTags.setActiveTag(activeAHFilter);
            addWidget.accept(ahFilterTags);
            filterY += 22;
        }

        // Stats row
        int statsY = filterY + 2;
        int cardW = (w - 16) / 4;
        int cardH = 38;

        profitCard = new StatCard(x, statsY, cardW, cardH,
                Component.literal("Profit net"),
                Component.literal(formatStatPrice(netProfit)),
                netProfit >= 0 ? THEME.success : THEME.danger, THEME);
        addWidget.accept(profitCard);

        salesCard = new StatCard(x + cardW + 4, statsY, cardW, cardH,
                Component.literal("Ventes"),
                Component.literal(formatStatPrice(totalSales)),
                THEME.success, THEME);
        addWidget.accept(salesCard);

        purchasesCard = new StatCard(x + (cardW + 4) * 2, statsY, cardW, cardH,
                Component.literal("Achats"),
                Component.literal(formatStatPrice(totalPurchases)),
                THEME.info, THEME);
        addWidget.accept(purchasesCard);

        taxCard = new StatCard(x + (cardW + 4) * 3, statsY, cardW, cardH,
                Component.literal("Taxes"),
                Component.literal(formatStatPrice(taxesPaid)),
                THEME.danger, THEME);
        addWidget.accept(taxCard);

        // Table
        int tableY = statsY + cardH + 4;
        int tableH = h - (tableY - y) - 18;
        List<TableColumn> columns = new ArrayList<>();
        columns.add(TableColumn.sortableLeft(Component.literal("Objet"), 2.5f));
        columns.add(TableColumn.center(Component.literal("Type"), 1f));
        columns.add(TableColumn.sortableRight(Component.literal("Montant"), 1.5f));
        columns.add(TableColumn.left(Component.literal("Avec"), 1.5f));
        if (multiAH) {
            columns.add(TableColumn.center(Component.literal("AH"), 1f));
        }
        columns.add(TableColumn.sortableCenter(Component.literal("Date"), 1.5f));

        table = Table.builder()
                .columns(columns)
                .theme(THEME)
                .navigation(Table.Navigation.SCROLL)
                .showScrollbar(true)
                .scrollLines(1)
                .build(x, tableY, w, tableH);
        addWidget.accept(table);
        updateTable();

        // Request data
        requestData();
    }

    public void renderBackground(GuiGraphics graphics) {}

    public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    // --- Event handlers ---

    private void onPeriodChanged(int idx) {
        activePeriod = idx;
        activeAHFilter = 0;
        requestData();
    }

    private void onTypeFilterChanged(int idx) {
        activeTypeFilter = idx;
        requestData();
    }

    private void onAHFilterChanged(int idx) {
        activeAHFilter = idx;
        updateTable();
    }

    // --- Network ---

    private void requestData() {
        PacketDistributor.sendToServer(new RequestLedgerPayload(
                PERIODS[activePeriod],
                TYPE_FILTERS[activeTypeFilter],
                0
        ));
    }

    public void onReceiveLedger(LedgerResponsePayload payload) {
        this.entries = payload.entries();
        this.netProfit = payload.netProfit();
        this.totalSales = payload.totalSales();
        this.totalPurchases = payload.totalPurchases();
        this.taxesPaid = payload.taxesPaid();

        // Detect multi-AH
        boolean wasMultiAH = this.multiAH;
        Set<String> seenAhIds = new LinkedHashSet<>();
        Map<String, String> ahIdToName = new LinkedHashMap<>();
        for (var entry : entries) {
            String ahId = entry.ahId() != null && !entry.ahId().isEmpty() ? entry.ahId() : "";
            if (!ahId.isEmpty()) {
                seenAhIds.add(ahId);
                String name = entry.ahName() != null && !entry.ahName().isEmpty() ? entry.ahName() : ahId.substring(0, Math.min(8, ahId.length()));
                ahIdToName.put(ahId, name);
            }
        }
        this.multiAH = seenAhIds.size() > 1;
        this.ahIds = new ArrayList<>(seenAhIds);
        this.ahNamesList = new ArrayList<>();
        for (String id : ahIds) {
            ahNamesList.add(ahIdToName.getOrDefault(id, id));
        }

        if (this.multiAH != wasMultiAH) {
            activeAHFilter = 0;
            parent.rebuildCurrentTab();
        } else {
            updateTable();
            updateStats();
        }
    }

    // --- Table population ---

    private void updateTable() {
        if (table == null) return;

        // Determine AH filter
        String ahIdFilter = null;
        if (multiAH && activeAHFilter > 0 && activeAHFilter <= ahIds.size()) {
            ahIdFilter = ahIds.get(activeAHFilter - 1);
        }

        List<TableRow> rows = new ArrayList<>();
        for (var entry : entries) {
            // Apply AH filter
            if (ahIdFilter != null) {
                String entryAhId = entry.ahId() != null ? entry.ahId() : "";
                if (!ahIdFilter.equals(entryAhId)) continue;
            }

            int typeColor = getTypeColor(entry.type());
            boolean isIncome = (entry.type().contains("SALE") || entry.type().contains("OUTBID"))
                    && !entry.type().contains("LISTING_FEE");

            ItemStack icon = AuctionHouseScreen.itemFromId(entry.itemId());

            List<TableRow.Cell> cells = new ArrayList<>();
            cells.add(TableRow.Cell.of(Component.literal(entry.itemName()), entry.rarityColor(), entry.itemName()));
            cells.add(TableRow.Cell.of(Component.literal(translateType(entry.type())), typeColor));
            cells.add(TableRow.Cell.of(Component.literal((isIncome ? "+" : "-") + BuyTab.formatPrice(entry.amount())),
                    isIncome ? THEME.success : THEME.danger, isIncome ? entry.amount() : -entry.amount()));
            cells.add(TableRow.Cell.of(Component.literal(entry.counterparty()), THEME.textLight));
            if (multiAH) {
                String ahDisplay = entry.ahName() != null && !entry.ahName().isEmpty()
                        ? entry.ahName() : "\u2014";
                cells.add(TableRow.Cell.of(Component.literal(ahDisplay), THEME.textLight));
            }
            cells.add(TableRow.Cell.of(Component.literal(formatDate(entry.timestamp())), THEME.textDim, entry.timestamp()));

            rows.add(TableRow.withIcon(icon, entry.rarityColor(), cells, null));
        }
        table.setRows(rows);
    }

    private void updateStats() {
        if (profitCard != null) {
            profitCard.setValue(Component.literal(formatStatPrice(netProfit)),
                    netProfit >= 0 ? THEME.success : THEME.danger);
        }
        if (salesCard != null) {
            salesCard.setValue(Component.literal(formatStatPrice(totalSales)), THEME.success);
        }
        if (purchasesCard != null) {
            purchasesCard.setValue(Component.literal(formatStatPrice(totalPurchases)), THEME.info);
        }
        if (taxCard != null) {
            taxCard.setValue(Component.literal(formatStatPrice(taxesPaid)), THEME.danger);
        }
    }

    // --- Helpers ---

    /**
     * Format price for stat cards: show "0 G" instead of "N/A" when value is 0.
     */
    private static String formatStatPrice(long price) {
        if (price == 0) return "0 G";
        return BuyTab.formatPrice(price);
    }

    private int getTypeColor(String type) {
        return switch (type) {
            case "PURCHASE", "HDV_PURCHASE" -> THEME.info;
            case "SALE", "HDV_SALE" -> THEME.success;
            case "EXPIRED", "HDV_EXPIRED" -> THEME.textDim;
            case "OUTBID", "HDV_OUTBID" -> THEME.warning;
            case "TAX" -> THEME.danger;
            case "LISTING_FEE", "HDV_LISTING_FEE" -> THEME.warning;
            default -> THEME.textGrey;
        };
    }

    private static String translateType(String type) {
        return switch (type) {
            case "PURCHASE", "HDV_PURCHASE" -> "Achat";
            case "SALE", "HDV_SALE" -> "Vente";
            case "EXPIRED", "HDV_EXPIRED" -> "Expiration";
            case "OUTBID", "HDV_OUTBID" -> "Surench.";
            case "TAX" -> "Taxe";
            case "LISTING_FEE", "HDV_LISTING_FEE" -> "D\u00e9p\u00f4t";
            default -> type;
        };
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM HH:mm");

    private static String formatDate(long timestamp) {
        if (timestamp <= 0) return "-";
        return DATE_FORMAT.format(new Date(timestamp));
    }
}

package net.ecocraft.ah.screen;

import net.ecocraft.ah.network.payload.LedgerResponsePayload;
import net.ecocraft.ah.network.payload.RequestLedgerPayload;
import net.ecocraft.gui.table.EcoPaginatedTable;
import net.ecocraft.gui.table.TableColumn;
import net.ecocraft.gui.table.TableRow;
import net.ecocraft.gui.theme.EcoColors;
import net.ecocraft.gui.widget.EcoButton;
import net.ecocraft.gui.widget.EcoFilterTags;
import net.ecocraft.gui.widget.EcoStatCard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ledger tab: transaction history with filters and stats.
 */
public class LedgerTab {

    private static final String[] PERIODS = {"24h", "7j", "30j", "all"};
    private static final String[] PERIOD_LABELS = {"24h", "7j", "30j", "Tout"};
    private static final String[] TYPE_FILTERS = {"all", "purchases", "sales", "auctions", "expired"};
    private static final String[] TYPE_FILTER_LABELS = {"Tout", "Achats", "Ventes", "Ench\u00e8res", "Expirations"};

    private final AuctionHouseScreen parent;
    private final int x, y, w, h;

    // State
    private int activePeriod = 1; // default 7j
    private int activeTypeFilter = 0; // default all
    private int currentPage = 0;
    private int totalPages = 0;

    private List<LedgerResponsePayload.LedgerEntry> entries = List.of();
    private long netProfit = 0;
    private long totalSales = 0;
    private long totalPurchases = 0;
    private long taxesPaid = 0;

    // Widgets
    private EcoFilterTags periodTags;
    private EcoFilterTags typeTags;
    private EcoPaginatedTable table;
    private EcoButton prevPageBtn;
    private EcoButton nextPageBtn;
    private EcoStatCard profitCard;
    private EcoStatCard salesCard;
    private EcoStatCard purchasesCard;
    private EcoStatCard taxCard;

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
        periodTags = new EcoFilterTags(x, y, periodLabels, this::onPeriodChanged);
        periodTags.setActiveTag(activePeriod);
        addWidget.accept(periodTags);

        // Type filter
        List<Component> typeLabels = new ArrayList<>();
        for (String label : TYPE_FILTER_LABELS) typeLabels.add(Component.literal(label));
        typeTags = new EcoFilterTags(x, y + 22, typeLabels, this::onTypeFilterChanged);
        typeTags.setActiveTag(activeTypeFilter);
        addWidget.accept(typeTags);

        // Stats row
        int statsY = y + 46;
        int cardW = (w - 16) / 4;
        int cardH = 32;

        profitCard = new EcoStatCard(x, statsY, cardW, cardH,
                Component.literal("Profit net"),
                Component.literal(BuyTab.formatPrice(netProfit)),
                netProfit >= 0 ? EcoColors.SUCCESS : EcoColors.DANGER);
        addWidget.accept(profitCard);

        salesCard = new EcoStatCard(x + cardW + 4, statsY, cardW, cardH,
                Component.literal("Ventes"),
                Component.literal(BuyTab.formatPrice(totalSales)),
                EcoColors.SUCCESS);
        addWidget.accept(salesCard);

        purchasesCard = new EcoStatCard(x + (cardW + 4) * 2, statsY, cardW, cardH,
                Component.literal("Achats"),
                Component.literal(BuyTab.formatPrice(totalPurchases)),
                EcoColors.INFO);
        addWidget.accept(purchasesCard);

        taxCard = new EcoStatCard(x + (cardW + 4) * 3, statsY, cardW, cardH,
                Component.literal("Taxes"),
                Component.literal(BuyTab.formatPrice(taxesPaid)),
                EcoColors.DANGER);
        addWidget.accept(taxCard);

        // Table
        int tableY = statsY + cardH + 4;
        int tableH = h - (tableY - y) - 18;
        List<TableColumn> columns = List.of(
                TableColumn.left(Component.literal("Objet"), 2.5f),
                TableColumn.center(Component.literal("Type"), 1f),
                TableColumn.right(Component.literal("Montant"), 1.5f),
                TableColumn.left(Component.literal("Avec"), 1.5f),
                TableColumn.center(Component.literal("Date"), 1.5f)
        );
        table = new EcoPaginatedTable(x, tableY, w, tableH, columns);
        addWidget.accept(table);
        updateTable();

        // Pagination
        int paginationY = y + h - 16;
        prevPageBtn = EcoButton.primary(x, paginationY, 40, 14,
                Component.literal("< Pr\u00e9c"), this::onPrevPage);
        nextPageBtn = EcoButton.primary(x + w - 40, paginationY, 40, 14,
                Component.literal("Suiv >"), this::onNextPage);
        addWidget.accept(prevPageBtn);
        addWidget.accept(nextPageBtn);

        // Request data
        requestData();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        // Page info
        int paginationY = y + h - 16;
        String pageInfo = "Page " + (currentPage + 1) + "/" + Math.max(1, totalPages);
        int pageInfoWidth = font.width(pageInfo);
        graphics.drawString(font, pageInfo,
                x + (w - pageInfoWidth) / 2,
                paginationY + 3, EcoColors.TEXT_GREY, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    // --- Event handlers ---

    private void onPeriodChanged(int idx) {
        activePeriod = idx;
        currentPage = 0;
        requestData();
    }

    private void onTypeFilterChanged(int idx) {
        activeTypeFilter = idx;
        currentPage = 0;
        requestData();
    }

    private void onPrevPage() {
        if (currentPage > 0) {
            currentPage--;
            requestData();
        }
    }

    private void onNextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            requestData();
        }
    }

    // --- Network ---

    private void requestData() {
        PacketDistributor.sendToServer(new RequestLedgerPayload(
                PERIODS[activePeriod],
                TYPE_FILTERS[activeTypeFilter],
                currentPage
        ));
    }

    public void onReceiveLedger(LedgerResponsePayload payload) {
        this.entries = payload.entries();
        this.netProfit = payload.netProfit();
        this.totalSales = payload.totalSales();
        this.totalPurchases = payload.totalPurchases();
        this.taxesPaid = payload.taxesPaid();
        this.currentPage = payload.page();
        this.totalPages = payload.totalPages();
        updateTable();
        updateStats();
    }

    // --- Table population ---

    private void updateTable() {
        if (table == null) return;

        List<TableRow> rows = new ArrayList<>();
        for (var entry : entries) {
            int typeColor = getTypeColor(entry.type());
            boolean isIncome = "SALE".equals(entry.type()) || "OUTBID".equals(entry.type());

            rows.add(TableRow.of(List.of(
                    TableRow.Cell.of(Component.literal(entry.itemName()), entry.rarityColor()),
                    TableRow.Cell.of(Component.literal(translateType(entry.type())), typeColor),
                    TableRow.Cell.of(Component.literal((isIncome ? "+" : "-") + BuyTab.formatPrice(entry.amount())),
                            isIncome ? EcoColors.SUCCESS : EcoColors.DANGER),
                    TableRow.Cell.of(Component.literal(entry.counterparty()), EcoColors.TEXT_LIGHT),
                    TableRow.Cell.of(Component.literal(formatDate(entry.timestamp())), EcoColors.TEXT_DIM)
            ), null));
        }
        table.setRows(rows);
    }

    private void updateStats() {
        if (profitCard != null) {
            profitCard.setValue(Component.literal(BuyTab.formatPrice(netProfit)),
                    netProfit >= 0 ? EcoColors.SUCCESS : EcoColors.DANGER);
        }
        if (salesCard != null) {
            salesCard.setValue(Component.literal(BuyTab.formatPrice(totalSales)), EcoColors.SUCCESS);
        }
        if (purchasesCard != null) {
            purchasesCard.setValue(Component.literal(BuyTab.formatPrice(totalPurchases)), EcoColors.INFO);
        }
        if (taxCard != null) {
            taxCard.setValue(Component.literal(BuyTab.formatPrice(taxesPaid)), EcoColors.DANGER);
        }
    }

    // --- Helpers ---

    private static int getTypeColor(String type) {
        return switch (type) {
            case "PURCHASE" -> EcoColors.INFO;
            case "SALE" -> EcoColors.SUCCESS;
            case "EXPIRED" -> EcoColors.TEXT_DIM;
            case "OUTBID" -> EcoColors.WARNING;
            default -> EcoColors.TEXT_GREY;
        };
    }

    private static String translateType(String type) {
        return switch (type) {
            case "PURCHASE" -> "Achat";
            case "SALE" -> "Vente";
            case "EXPIRED" -> "Expir\u00e9";
            case "OUTBID" -> "Surench.";
            default -> type;
        };
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM HH:mm");

    private static String formatDate(long timestamp) {
        if (timestamp <= 0) return "-";
        return DATE_FORMAT.format(new Date(timestamp));
    }
}

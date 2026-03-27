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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ledger tab: transaction history with filters and stats.
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

    // Widgets
    private FilterTags periodTags;
    private FilterTags typeTags;
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

        // Stats row
        int statsY = y + 46;
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
        List<TableColumn> columns = List.of(
                TableColumn.sortableLeft(Component.literal("Objet"), 2.5f),
                TableColumn.center(Component.literal("Type"), 1f),
                TableColumn.sortableRight(Component.literal("Montant"), 1.5f),
                TableColumn.left(Component.literal("Avec"), 1.5f),
                TableColumn.sortableCenter(Component.literal("Date"), 1.5f)
        );
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
        requestData();
    }

    private void onTypeFilterChanged(int idx) {
        activeTypeFilter = idx;
        requestData();
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
        updateTable();
        updateStats();
    }

    // --- Table population ---

    private void updateTable() {
        if (table == null) return;

        List<TableRow> rows = new ArrayList<>();
        for (var entry : entries) {
            int typeColor = getTypeColor(entry.type());
            boolean isIncome = (entry.type().contains("SALE") || entry.type().contains("OUTBID"))
                    && !entry.type().contains("LISTING_FEE");

            ItemStack icon = AuctionHouseScreen.itemFromId(entry.itemId());
            rows.add(TableRow.withIcon(icon, entry.rarityColor(), List.of(
                    TableRow.Cell.of(Component.literal(entry.itemName()), entry.rarityColor(), entry.itemName()),
                    TableRow.Cell.of(Component.literal(translateType(entry.type())), typeColor),
                    TableRow.Cell.of(Component.literal((isIncome ? "+" : "-") + BuyTab.formatPrice(entry.amount())),
                            isIncome ? THEME.success : THEME.danger, isIncome ? entry.amount() : -entry.amount()),
                    TableRow.Cell.of(Component.literal(entry.counterparty()), THEME.textLight),
                    TableRow.Cell.of(Component.literal(formatDate(entry.timestamp())), THEME.textDim, entry.timestamp())
            ), null));
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

package net.ecocraft.ah.screen;

import net.ecocraft.ah.network.payload.LedgerResponsePayload;
import net.ecocraft.ah.network.payload.RequestLedgerPayload;
import net.ecocraft.gui.core.*;
import net.ecocraft.gui.table.TableColumn;
import net.ecocraft.gui.table.TableRow;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Ledger tab: transaction history with filters and stats.
 * When entries span multiple AH instances, an extra "AH" column and filter appear.
 */
public class LedgerTab extends BaseWidget {

    private static final Theme THEME = Theme.dark();
    private static final String[] PERIODS = {"24h", "7j", "30j", "all"};
    private static final String[] PERIOD_LABELS = {"24h", "7j", "30j", "Tout"};
    private static final String[] TYPE_FILTERS = {"all", "purchases", "sales", "auctions", "expired"};
    private static final String[] TYPE_FILTER_LABELS = {"Tout", "Achats", "Ventes", "Ench\u00e8res", "Expirations"};

    private final AuctionHouseScreen parent;
    private final int tabX, tabY, tabW, tabH;

    // State
    private int activePeriod = 1;
    private int activeTypeFilter = 0;
    private List<LedgerResponsePayload.LedgerEntry> entries = List.of();
    private long netProfit = 0;
    private long totalSales = 0;
    private long totalPurchases = 0;
    private long taxesPaid = 0;

    // Multi-AH state
    private boolean multiAH = false;
    private List<String> ahIds = List.of();
    private List<String> ahNamesList = List.of();
    private int activeAHFilter = 0;

    // Widgets
    private EcoFilterTags periodTags;
    private EcoFilterTags typeTags;
    private EcoFilterTags ahFilterTags;
    private EcoTable table;
    private EcoStatCard profitCard;
    private EcoStatCard salesCard;
    private EcoStatCard purchasesCard;
    private EcoStatCard taxCard;

    public LedgerTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.parent = parent;
        this.tabX = x;
        this.tabY = y;
        this.tabW = w;
        this.tabH = h;
        buildWidgets();
    }

    private void buildWidgets() {
        // Remove old children
        for (WidgetNode child : new ArrayList<>(getChildren())) {
            removeChild(child);
        }

        Font font = Minecraft.getInstance().font;

        // Period filter
        List<Component> periodLabels = new ArrayList<>();
        for (String label : PERIOD_LABELS) periodLabels.add(Component.literal(label));
        periodTags = new EcoFilterTags(tabX, tabY, periodLabels, this::onPeriodChanged, THEME);
        periodTags.setActiveTag(activePeriod);
        addChild(periodTags);

        // Type filter
        List<Component> typeLabels = new ArrayList<>();
        for (String label : TYPE_FILTER_LABELS) typeLabels.add(Component.literal(label));
        typeTags = new EcoFilterTags(tabX, tabY + 22, typeLabels, this::onTypeFilterChanged, THEME);
        typeTags.setActiveTag(activeTypeFilter);
        addChild(typeTags);

        int filterY = tabY + 44;

        // AH filter (only when multiAH)
        ahFilterTags = null;
        if (multiAH && !ahNamesList.isEmpty()) {
            List<Component> ahLabels = new ArrayList<>();
            ahLabels.add(Component.literal("Tout"));
            for (String name : ahNamesList) {
                ahLabels.add(Component.literal(name));
            }
            ahFilterTags = new EcoFilterTags(tabX, filterY, ahLabels, this::onAHFilterChanged, THEME);
            ahFilterTags.setActiveTag(activeAHFilter);
            addChild(ahFilterTags);
            filterY += 22;
        }

        // Stats row
        int statsY = filterY + 2;
        int cardW = (tabW - 16) / 4;
        int cardH = 38;

        profitCard = new EcoStatCard(tabX, statsY, cardW, cardH,
                Component.literal("Profit net"),
                Component.literal(formatStatPrice(netProfit)),
                netProfit >= 0 ? THEME.success : THEME.danger, THEME);
        addChild(profitCard);

        salesCard = new EcoStatCard(tabX + cardW + 4, statsY, cardW, cardH,
                Component.literal("Ventes"),
                Component.literal(formatStatPrice(totalSales)),
                THEME.success, THEME);
        addChild(salesCard);

        purchasesCard = new EcoStatCard(tabX + (cardW + 4) * 2, statsY, cardW, cardH,
                Component.literal("Achats"),
                Component.literal(formatStatPrice(totalPurchases)),
                THEME.info, THEME);
        addChild(purchasesCard);

        taxCard = new EcoStatCard(tabX + (cardW + 4) * 3, statsY, cardW, cardH,
                Component.literal("Taxes"),
                Component.literal(formatStatPrice(taxesPaid)),
                THEME.danger, THEME);
        addChild(taxCard);

        // Table
        int tableY = statsY + cardH + 4;
        int tableH = tabH - (tableY - tabY) - 18;
        List<TableColumn> columns = new ArrayList<>();
        columns.add(TableColumn.sortableLeft(Component.literal("Objet"), 2.5f));
        columns.add(TableColumn.center(Component.literal("Type"), 1f));
        columns.add(TableColumn.sortableRight(Component.literal("Montant"), 1.5f));
        columns.add(TableColumn.left(Component.literal("Avec"), 1.5f));
        if (multiAH) {
            columns.add(TableColumn.center(Component.literal("AH"), 1f));
        }
        columns.add(TableColumn.sortableCenter(Component.literal("Date"), 1.5f));

        table = EcoTable.builder()
                .columns(columns)
                .theme(THEME)
                .navigation(EcoTable.Navigation.SCROLL)
                .showScrollbar(true)
                .scrollLines(1)
                .build(tabX, tableY, tabW, tableH);
        addChild(table);
        updateTable();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No custom rendering needed; children render via tree
    }

    /** Called when tab becomes visible. */
    public void onActivated() {
        requestData();
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
            buildWidgets();
        } else {
            updateTable();
            updateStats();
        }
    }

    // --- Table population ---

    private void updateTable() {
        if (table == null) return;

        String ahIdFilter = null;
        if (multiAH && activeAHFilter > 0 && activeAHFilter <= ahIds.size()) {
            ahIdFilter = ahIds.get(activeAHFilter - 1);
        }

        List<TableRow> rows = new ArrayList<>();
        for (var entry : entries) {
            if (ahIdFilter != null) {
                String entryAhId = entry.ahId() != null ? entry.ahId() : "";
                if (!ahIdFilter.equals(entryAhId)) continue;
            }

            int typeColor = getTypeColor(entry.type());
            boolean isIncome = (entry.type().contains("SALE") || entry.type().contains("OUTBID"))
                    && !entry.type().contains("LISTING_FEE");

            ItemStack icon = ItemStack.EMPTY;
            String nbt = entry.itemNbt();
            if (nbt != null && !nbt.isEmpty()) {
                var level = net.minecraft.client.Minecraft.getInstance().level;
                if (level != null) {
                    icon = net.ecocraft.ah.data.ItemStackSerializer.deserialize(nbt, level.registryAccess());
                }
            }
            if (icon.isEmpty()) {
                icon = AuctionHouseScreen.itemFromId(entry.itemId());
            }

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

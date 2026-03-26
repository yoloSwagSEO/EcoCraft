package net.ecocraft.ah.screen;

import net.ecocraft.ah.network.payload.AHActionResultPayload;
import net.ecocraft.ah.network.payload.CancelListingPayload;
import net.ecocraft.ah.network.payload.CollectParcelsPayload;
import net.ecocraft.ah.network.payload.MyListingsResponsePayload;
import net.ecocraft.ah.network.payload.RequestMyListingsPayload;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * My Auctions tab with sub-tabs for sales, purchases, and bids.
 */
public class MyAuctionsTab {

    private static final String[] SUB_TABS = {"sales", "purchases", "bids"};

    private final AuctionHouseScreen parent;
    private final int x, y, w, h;

    // State
    private int activeSubTab = 0;
    private List<MyListingsResponsePayload.MyListingEntry> entries = List.of();
    private long revenue7d = 0;
    private long taxesPaid7d = 0;
    private int parcelsToCollect = 0;

    // Widgets
    private EcoFilterTags subTabTags;
    private EcoPaginatedTable table;
    private EcoButton collectBtn;
    private EcoStatCard revenueCard;
    private EcoStatCard taxCard;
    private EcoStatCard parcelsCard;

    public MyAuctionsTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void init(Consumer<AbstractWidget> addWidget) {
        Font font = Minecraft.getInstance().font;

        // Sub-tab selector
        List<Component> subTabLabels = List.of(
                Component.literal("Mes ventes"),
                Component.literal("Mes achats"),
                Component.literal("Ench\u00e8res en cours")
        );
        subTabTags = new EcoFilterTags(x, y, subTabLabels, this::onSubTabChanged);
        subTabTags.setActiveTag(activeSubTab);
        addWidget.accept(subTabTags);

        // Collect parcels button
        collectBtn = EcoButton.success(x + w - 80, y, 78, 18,
                Component.literal("R\u00e9cup\u00e9rer (" + parcelsToCollect + ")"),
                this::onCollectClicked);
        addWidget.accept(collectBtn);

        // Table
        int tableY = y + 24;
        int tableH = h - 62;
        List<TableColumn> columns = List.of(
                TableColumn.left(Component.literal("Objet"), 2.5f),
                TableColumn.right(Component.literal("Prix"), 1.5f),
                TableColumn.center(Component.literal("Type"), 1f),
                TableColumn.center(Component.literal("Statut"), 1f),
                TableColumn.center(Component.literal("Expire"), 1f),
                TableColumn.center(Component.literal("Action"), 1.5f)
        );
        table = new EcoPaginatedTable(x, tableY, w, tableH, columns);
        addWidget.accept(table);
        updateTable();

        // Footer stat cards
        int footerY = y + h - 34;
        int cardW = (w - 12) / 3;

        revenueCard = new EcoStatCard(x, footerY, cardW, 32,
                Component.literal("Revenus 7j"),
                Component.literal(BuyTab.formatPrice(revenue7d)),
                EcoColors.SUCCESS);
        addWidget.accept(revenueCard);

        taxCard = new EcoStatCard(x + cardW + 4, footerY, cardW, 32,
                Component.literal("Taxes 7j"),
                Component.literal(BuyTab.formatPrice(taxesPaid7d)),
                EcoColors.DANGER);
        addWidget.accept(taxCard);

        parcelsCard = new EcoStatCard(x + (cardW + 4) * 2, footerY, cardW, 32,
                Component.literal("Colis"),
                Component.literal(String.valueOf(parcelsToCollect)),
                EcoColors.INFO);
        addWidget.accept(parcelsCard);

        // Request data
        requestData();
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Widgets handle their own rendering
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    // --- Event handlers ---

    private void onSubTabChanged(int idx) {
        activeSubTab = idx;
        requestData();
    }

    private void onCollectClicked() {
        PacketDistributor.sendToServer(new CollectParcelsPayload());
    }

    private void onCancelClicked(String listingId) {
        PacketDistributor.sendToServer(new CancelListingPayload(listingId));
    }

    // --- Network ---

    private void requestData() {
        PacketDistributor.sendToServer(new RequestMyListingsPayload(SUB_TABS[activeSubTab]));
    }

    public void onReceiveMyListings(MyListingsResponsePayload payload) {
        this.entries = payload.entries();
        this.revenue7d = payload.revenue7d();
        this.taxesPaid7d = payload.taxesPaid7d();
        this.parcelsToCollect = payload.parcelsToCollect();
        updateTable();
        updateStats();
    }

    public void onActionResult(AHActionResultPayload payload) {
        // Refresh data after action
        requestData();
    }

    // --- Table population ---

    private void updateTable() {
        if (table == null) return;

        List<TableRow> rows = new ArrayList<>();
        for (var entry : entries) {
            int statusColor = getStatusColor(entry.status());
            String actionLabel = getActionLabel(entry);
            int actionColor = entry.canCollect() ? EcoColors.SUCCESS : EcoColors.DANGER;

            Runnable action = null;
            if (entry.canCollect()) {
                action = this::onCollectClicked;
            } else if ("ACTIVE".equals(entry.status())) {
                final String lid = entry.listingId();
                action = () -> onCancelClicked(lid);
            }

            rows.add(TableRow.of(List.of(
                    TableRow.Cell.of(Component.literal(entry.itemName()), entry.rarityColor()),
                    TableRow.Cell.of(Component.literal(BuyTab.formatPrice(entry.price())), EcoColors.GOLD),
                    TableRow.Cell.of(Component.literal("AUCTION".equals(entry.type()) ? "Ench\u00e8re" : "Achat"),
                            "AUCTION".equals(entry.type()) ? EcoColors.WARNING : EcoColors.SUCCESS),
                    TableRow.Cell.of(Component.literal(translateStatus(entry.status())), statusColor),
                    TableRow.Cell.of(Component.literal(BuyTab.formatTimeRemaining(entry.expiresInMs())), EcoColors.TEXT_GREY),
                    TableRow.Cell.of(Component.literal(actionLabel), actionColor)
            ), action));
        }
        table.setRows(rows);
    }

    private void updateStats() {
        if (revenueCard != null) {
            revenueCard.setValue(Component.literal(BuyTab.formatPrice(revenue7d)), EcoColors.SUCCESS);
        }
        if (taxCard != null) {
            taxCard.setValue(Component.literal(BuyTab.formatPrice(taxesPaid7d)), EcoColors.DANGER);
        }
        if (parcelsCard != null) {
            parcelsCard.setValue(Component.literal(String.valueOf(parcelsToCollect)), EcoColors.INFO);
        }
    }

    // --- Helpers ---

    private static int getStatusColor(String status) {
        return switch (status) {
            case "ACTIVE" -> EcoColors.SUCCESS;
            case "SOLD" -> EcoColors.GOLD;
            case "EXPIRED" -> EcoColors.TEXT_DIM;
            case "CANCELLED" -> EcoColors.DANGER;
            default -> EcoColors.TEXT_GREY;
        };
    }

    private static String translateStatus(String status) {
        return switch (status) {
            case "ACTIVE" -> "Actif";
            case "SOLD" -> "Vendu";
            case "EXPIRED" -> "Expir\u00e9";
            case "CANCELLED" -> "Annul\u00e9";
            default -> status;
        };
    }

    private static String getActionLabel(MyListingsResponsePayload.MyListingEntry entry) {
        if (entry.canCollect()) return "R\u00e9cup\u00e9rer";
        if ("ACTIVE".equals(entry.status())) return "Annuler";
        return "-";
    }
}

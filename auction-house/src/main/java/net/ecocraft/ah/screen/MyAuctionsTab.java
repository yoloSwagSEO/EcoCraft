package net.ecocraft.ah.screen;

import net.ecocraft.ah.data.ItemStackSerializer;
import net.ecocraft.ah.network.payload.AHActionResultPayload;
import net.ecocraft.ah.network.payload.CancelListingPayload;
import net.ecocraft.ah.network.payload.CollectParcelsPayload;
import net.ecocraft.ah.network.payload.MyListingsResponsePayload;
import net.ecocraft.ah.network.payload.RequestMyListingsPayload;
import net.ecocraft.gui.dialog.Dialog;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * My Auctions tab with sub-tabs for sales, purchases, and bids.
 */
public class MyAuctionsTab {

    private static final Theme THEME = Theme.dark();
    private static final String[] SUB_TABS = {"sales", "purchases", "bids"};

    private final AuctionHouseScreen parent;
    private final int x, y, w, h;

    // State
    private int activeSubTab = 0;
    private int activeStatusFilter = 0; // 0=Tout, 1=Actif, 2=Vendu, 3=Expiré, 4=Annulé
    private static final String[] STATUS_FILTERS = {"", "ACTIVE", "SOLD", "EXPIRED", "CANCELLED"};
    private List<MyListingsResponsePayload.MyListingEntry> entries = List.of();
    private long revenue7d = 0;
    private long taxesPaid7d = 0;
    private int parcelsToCollect = 0;

    // Widgets
    private FilterTags subTabTags;
    private FilterTags statusFilterTags;
    private Table table;
    private Button collectBtn;
    private StatCard revenueCard;
    private StatCard taxCard;
    private StatCard parcelsCard;

    // Dialog overlay
    private Consumer<AbstractWidget> widgetAdder;

    public MyAuctionsTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void init(Consumer<AbstractWidget> addWidget) {
        this.widgetAdder = addWidget;
        Font font = Minecraft.getInstance().font;

        // Sub-tab selector
        List<Component> subTabLabels = List.of(
                Component.literal("Mes ventes"),
                Component.literal("Mes achats"),
                Component.literal("Ench\u00e8res en cours")
        );
        subTabTags = new FilterTags(x, y, subTabLabels, this::onSubTabChanged);
        subTabTags.setActiveTag(activeSubTab);
        addWidget.accept(subTabTags);

        // Collect parcels button
        collectBtn = Button.success(THEME, Component.literal("R\u00e9cup\u00e9rer (" + parcelsToCollect + ")"),
                this::onCollectClicked);
        collectBtn.setX(x + w - 80);
        collectBtn.setY(y);
        collectBtn.setWidth(78);
        collectBtn.setHeight(18);
        addWidget.accept(collectBtn);

        // Footer stat cards - calculate first to know how much space table gets
        int cardH = 38;
        int footerY = y + h - cardH - 4;
        int cardW = (w - 12) / 3;

        revenueCard = new StatCard(x, footerY, cardW, cardH,
                Component.literal("Revenus 7j"),
                Component.literal(BuyTab.formatPrice(revenue7d)),
                THEME.success, THEME);
        addWidget.accept(revenueCard);

        taxCard = new StatCard(x + cardW + 4, footerY, cardW, cardH,
                Component.literal("Taxes 7j"),
                Component.literal(BuyTab.formatPrice(taxesPaid7d)),
                THEME.danger, THEME);
        addWidget.accept(taxCard);

        parcelsCard = new StatCard(x + (cardW + 4) * 2, footerY, cardW, cardH,
                Component.literal("Colis"),
                Component.literal(String.valueOf(parcelsToCollect)),
                THEME.info, THEME);
        addWidget.accept(parcelsCard);

        // Status filter (only on "Mes ventes" sub-tab)
        int filterY = y + 24;
        statusFilterTags = null;
        if (activeSubTab == 0) {
            List<Component> statusLabels = List.of(
                    Component.literal("Tout"),
                    Component.literal("Actif"),
                    Component.literal("Vendu"),
                    Component.literal("Expiré"),
                    Component.literal("Annulé")
            );
            statusFilterTags = new FilterTags(x, filterY, statusLabels, this::onStatusFilterChanged);
            statusFilterTags.setActiveTag(activeStatusFilter);
            addWidget.accept(statusFilterTags);
            filterY += 22;
        }

        // Table
        int tableY = filterY;
        int tableH = footerY - tableY - 4;
        List<TableColumn> columns = List.of(
                TableColumn.sortableLeft(Component.literal("Objet"), 2.5f),
                TableColumn.sortableRight(Component.literal("Prix"), 1.5f),
                TableColumn.center(Component.literal("Type"), 1f),
                TableColumn.center(Component.literal("Statut"), 1f),
                TableColumn.sortableCenter(Component.literal("Expire"), 1f),
                TableColumn.center(Component.literal("Action"), 1.5f)
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
        // PaginatedTable handles tooltips automatically
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    // --- Event handlers ---

    private void onSubTabChanged(int idx) {
        activeSubTab = idx;
        activeStatusFilter = 0;
        requestData();
        parent.rebuildCurrentTab();
    }

    private void onStatusFilterChanged(int idx) {
        activeStatusFilter = idx;
        updateTable();
    }

    private void onCollectClicked() {
        PacketDistributor.sendToServer(new CollectParcelsPayload());
    }

    private void onCancelClicked(String listingId) {
        Dialog dialog = Dialog.confirm(
                THEME,
                Component.literal("Annuler l'annonce"),
                Component.literal("\u00cates-vous s\u00fbr de vouloir annuler cette annonce ?"),
                Component.literal("Confirmer"),
                Component.literal("Retour"),
                () -> PacketDistributor.sendToServer(new CancelListingPayload(listingId)),
                () -> { /* cancelled, do nothing */ }
        );
        parent.openDialog(dialog);
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

        String statusFilter = STATUS_FILTERS[activeStatusFilter];
        List<TableRow> rows = new ArrayList<>();
        for (var entry : entries) {
            // Apply status filter
            if (!statusFilter.isEmpty() && !statusFilter.equals(entry.status())) continue;
            int statusColor = getStatusColor(entry.status());
            String actionLabel = getActionLabel(entry);
            int actionColor = entry.canCollect() ? THEME.success : THEME.danger;

            Runnable action = null;
            if (entry.canCollect()) {
                action = this::onCollectClicked;
            } else if ("ACTIVE".equals(entry.status())) {
                final String lid = entry.listingId();
                action = () -> onCancelClicked(lid);
            }

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
                icon = AuctionHouseScreen.itemFromId(entry.itemId());
            }

            rows.add(TableRow.withIcon(icon, entry.rarityColor(), List.of(
                    TableRow.Cell.of(Component.literal(entry.itemName()), entry.rarityColor(), entry.itemName()),
                    TableRow.Cell.of(Component.literal(BuyTab.formatPrice(entry.price())), THEME.accent, entry.price()),
                    TableRow.Cell.of(Component.literal("AUCTION".equals(entry.type()) ? "Ench\u00e8re" : "Achat"),
                            "AUCTION".equals(entry.type()) ? THEME.warning : THEME.success),
                    TableRow.Cell.of(Component.literal(translateStatus(entry.status())), statusColor),
                    TableRow.Cell.of(Component.literal(BuyTab.formatTimeRemaining(entry.expiresInMs())), THEME.textGrey, entry.expiresInMs()),
                    TableRow.Cell.of(Component.literal(actionLabel), actionColor)
            ), action));
        }
        table.setRows(rows);
    }

    private void updateStats() {
        if (revenueCard != null) {
            revenueCard.setValue(Component.literal(BuyTab.formatPrice(revenue7d)), THEME.success);
        }
        if (taxCard != null) {
            taxCard.setValue(Component.literal(BuyTab.formatPrice(taxesPaid7d)), THEME.danger);
        }
        if (parcelsCard != null) {
            parcelsCard.setValue(Component.literal(String.valueOf(parcelsToCollect)), THEME.info);
        }
        if (collectBtn != null) {
            collectBtn.setMessage(Component.literal("R\u00e9cup\u00e9rer (" + parcelsToCollect + ")"));
        }
    }

    // --- Helpers ---

    private int getStatusColor(String status) {
        return switch (status) {
            case "ACTIVE" -> THEME.success;
            case "SOLD" -> THEME.accent;
            case "EXPIRED" -> THEME.textDim;
            case "CANCELLED" -> THEME.danger;
            default -> THEME.textGrey;
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

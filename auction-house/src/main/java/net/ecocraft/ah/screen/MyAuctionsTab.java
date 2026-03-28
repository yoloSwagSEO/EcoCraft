package net.ecocraft.ah.screen;

import net.ecocraft.ah.data.ItemStackSerializer;
import net.ecocraft.ah.network.payload.AHActionResultPayload;
import net.ecocraft.ah.network.payload.CancelListingPayload;
import net.ecocraft.ah.network.payload.CollectParcelsPayload;
import net.ecocraft.ah.network.payload.MyListingsResponsePayload;
import net.ecocraft.ah.network.payload.RequestMyListingsPayload;
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

import java.util.*;

/**
 * My Auctions tab with sub-tabs for sales, purchases, and bids.
 * When entries span multiple AH instances, an extra "AH" column and filter appear.
 */
public class MyAuctionsTab extends BaseWidget {

    private static final Theme THEME = Theme.dark();
    private static final String[] SUB_TABS = {"sales", "purchases", "bids"};

    private final AuctionHouseScreen parent;
    private final int tabX, tabY, tabW, tabH;

    // State
    private int activeSubTab = 0;
    private int activeStatusFilter = 0;
    private static final String[] STATUS_FILTERS = {"", "ACTIVE", "SOLD", "EXPIRED", "CANCELLED"};
    private List<MyListingsResponsePayload.MyListingEntry> entries = List.of();
    private long revenue7d = 0;
    private long taxesPaid7d = 0;
    private int parcelsToCollect = 0;

    // Multi-AH state
    private boolean multiAH = false;
    private List<String> ahIds = List.of();
    private List<String> ahNamesList = List.of();
    private int activeAHFilter = 0;

    // Widgets
    private EcoFilterTags subTabTags;
    private EcoFilterTags statusFilterTags;
    private EcoFilterTags ahFilterTags;
    private EcoTable table;
    private EcoButton collectBtn;
    private EcoStatCard revenueCard;
    private EcoStatCard taxCard;
    private EcoStatCard parcelsCard;

    public MyAuctionsTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
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

        // Sub-tab selector
        List<Component> subTabLabels = List.of(
                Component.literal("Mes ventes"),
                Component.literal("Mes achats"),
                Component.literal("Ench\u00e8res en cours")
        );
        subTabTags = new EcoFilterTags(tabX, tabY, subTabLabels, this::onSubTabChanged, THEME);
        subTabTags.setActiveTag(activeSubTab);
        addChild(subTabTags);

        // Collect parcels button
        collectBtn = EcoButton.success(THEME, Component.literal("R\u00e9cup\u00e9rer (" + parcelsToCollect + ")"),
                this::onCollectClicked);
        collectBtn.setPosition(tabX + tabW - 80, tabY);
        collectBtn.setSize(78, 18);
        addChild(collectBtn);

        // Footer stat cards
        int cardH = 38;
        int footerY = tabY + tabH - cardH - 4;
        int cardW = (tabW - 12) / 3;

        revenueCard = new EcoStatCard(tabX, footerY, cardW, cardH,
                Component.literal("Revenus 7j"),
                Component.literal(BuyTab.formatPrice(revenue7d)),
                THEME.success, THEME);
        addChild(revenueCard);

        taxCard = new EcoStatCard(tabX + cardW + 4, footerY, cardW, cardH,
                Component.literal("Taxes 7j"),
                Component.literal(BuyTab.formatPrice(taxesPaid7d)),
                THEME.danger, THEME);
        addChild(taxCard);

        parcelsCard = new EcoStatCard(tabX + (cardW + 4) * 2, footerY, cardW, cardH,
                Component.literal("Colis"),
                Component.literal(String.valueOf(parcelsToCollect)),
                THEME.info, THEME);
        addChild(parcelsCard);

        // Status filter (only on "Mes ventes" sub-tab)
        int filterY = tabY + 24;
        statusFilterTags = null;
        if (activeSubTab == 0) {
            List<Component> statusLabels = List.of(
                    Component.literal("Tout"),
                    Component.literal("Actif"),
                    Component.literal("Vendu"),
                    Component.literal("Expir\u00e9"),
                    Component.literal("Annul\u00e9")
            );
            statusFilterTags = new EcoFilterTags(tabX, filterY, statusLabels, this::onStatusFilterChanged, THEME);
            statusFilterTags.setActiveTag(activeStatusFilter);
            addChild(statusFilterTags);
            filterY += 22;
        }

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

        // Table
        int tableY = filterY;
        int tableH = footerY - tableY - 4;
        List<TableColumn> columns = new ArrayList<>();
        columns.add(TableColumn.sortableLeft(Component.literal("Objet"), 2.5f));
        columns.add(TableColumn.sortableRight(Component.literal("Prix"), 1.5f));
        columns.add(TableColumn.center(Component.literal("Type"), 1f));
        columns.add(TableColumn.center(Component.literal("Statut"), 1f));
        if (multiAH) {
            columns.add(TableColumn.center(Component.literal("AH"), 1f));
        }
        columns.add(TableColumn.sortableCenter(Component.literal("Expire"), 1f));
        columns.add(TableColumn.center(Component.literal("Action"), 1.5f));

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

    private void onSubTabChanged(int idx) {
        activeSubTab = idx;
        activeStatusFilter = 0;
        activeAHFilter = 0;
        requestData();
        buildWidgets();
    }

    private void onStatusFilterChanged(int idx) {
        activeStatusFilter = idx;
        updateTable();
    }

    private void onAHFilterChanged(int idx) {
        activeAHFilter = idx;
        updateTable();
    }

    private void onCollectClicked() {
        PacketDistributor.sendToServer(new CollectParcelsPayload());
    }

    private void onCancelClicked(String listingId) {
        EcoDialog dialog = EcoDialog.confirm(
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

    public void onActionResult(AHActionResultPayload payload) {
        requestData();
    }

    // --- Table population ---

    private void updateTable() {
        if (table == null) return;

        String statusFilter = STATUS_FILTERS[activeStatusFilter];
        String ahIdFilter = null;
        if (multiAH && activeAHFilter > 0 && activeAHFilter <= ahIds.size()) {
            ahIdFilter = ahIds.get(activeAHFilter - 1);
        }

        List<TableRow> rows = new ArrayList<>();
        for (var entry : entries) {
            if (!statusFilter.isEmpty() && !statusFilter.equals(entry.status())) continue;
            if (ahIdFilter != null) {
                String entryAhId = entry.ahId() != null ? entry.ahId() : "";
                if (!ahIdFilter.equals(entryAhId)) continue;
            }

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

            // Resolve the ItemStack
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

            List<TableRow.Cell> cells = new ArrayList<>();
            cells.add(TableRow.Cell.of(Component.literal(entry.itemName()), entry.rarityColor(), entry.itemName()));
            cells.add(TableRow.Cell.of(Component.literal(BuyTab.formatPrice(entry.price())), THEME.accent, entry.price()));
            cells.add(TableRow.Cell.of(Component.literal("AUCTION".equals(entry.type()) ? "Ench\u00e8re" : "Achat"),
                    "AUCTION".equals(entry.type()) ? THEME.warning : THEME.success));
            cells.add(TableRow.Cell.of(Component.literal(translateStatus(entry.status())), statusColor));
            if (multiAH) {
                String ahDisplay = entry.ahName() != null && !entry.ahName().isEmpty()
                        ? entry.ahName() : "\u2014";
                cells.add(TableRow.Cell.of(Component.literal(ahDisplay), THEME.textLight));
            }
            cells.add(TableRow.Cell.of(Component.literal(BuyTab.formatTimeRemaining(entry.expiresInMs())), THEME.textGrey, entry.expiresInMs()));
            cells.add(TableRow.Cell.of(Component.literal(actionLabel), actionColor));

            rows.add(TableRow.withIcon(icon, entry.rarityColor(), cells, action));
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
            collectBtn.setLabel(Component.literal("R\u00e9cup\u00e9rer (" + parcelsToCollect + ")"));
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

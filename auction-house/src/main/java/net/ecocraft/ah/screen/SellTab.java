package net.ecocraft.ah.screen;

import net.ecocraft.ah.network.payload.AHActionResultPayload;
import net.ecocraft.ah.network.payload.CreateListingPayload;
import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Sell tab: two-column layout.
 * Left column: item slot, type toggle, price input, duration, tax summary, sell button.
 * Right column: full 9x4 inventory grid.
 */
public class SellTab extends BaseWidget {

    private static final Theme THEME = Theme.dark();
    private static final int INV_COLS = 9;

    // Set by AuctionHouseScreen when settings are received from server (instance, not static)
    int[] activeDurations = {12, 24, 48};
    double activeTaxRate = 0.05;
    double activeDepositRate = 0.02;
    boolean activeAllowBuyout = true;
    boolean activeAllowAuction = true;

    private final AuctionHouseScreen parent;
    private final int tabX, tabY, tabW, tabH;

    // State
    private boolean isBuyout = true;
    private int selectedDuration = 1; // default 24h
    private long priceValue = 0;
    private String lastMessage = "";
    private boolean lastSuccess = false;
    private int selectedInventorySlot = -1;

    // Widgets
    private EcoItemSlot itemSlot;
    private EcoButton buyoutBtn;
    private EcoButton auctionBtn;
    private EcoNumberInput priceInput;
    private EcoFilterTags durationTags;
    private EcoButton sellButton;
    private EcoInventoryGrid inventoryGrid;

    // Layout positions computed in buildWidgets
    private int leftColX, leftColW;
    private int rightColX, rightColW;
    private int summaryY;

    public SellTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
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

        Font font = Minecraft.getInstance().font;

        // Two-column layout: 60% left, 40% right
        leftColX = tabX + 4;
        leftColW = (int) (tabW * 0.6) - 8;
        rightColX = tabX + (int) (tabW * 0.6) + 4;
        rightColW = tabW - (int) (tabW * 0.6) - 8;

        int leftCenterX = leftColX + leftColW / 2;
        int currentY = tabY + 4;

        // 1. Item slot (32x32) + item name
        itemSlot = new EcoItemSlot(leftCenterX - 16, currentY, 32);
        updateSelectedItem();
        addChild(itemSlot);
        currentY += 36;

        // Item name label space (rendered in render())
        currentY += 12;
        currentY += 4;

        // 2. Type toggle: depends on AH config
        // Force type if only one mode is allowed
        if (activeAllowBuyout && !activeAllowAuction) isBuyout = true;
        if (!activeAllowBuyout && activeAllowAuction) isBuyout = false;

        // Only show toggle if both modes are allowed
        if (activeAllowBuyout && activeAllowAuction) {
            if (isBuyout) {
                buyoutBtn = EcoButton.success(THEME, Component.translatable("ecocraft_ah.type.buyout"), () -> { isBuyout = true; buildWidgets(); });
            } else {
                buyoutBtn = EcoButton.ghost(THEME, Component.translatable("ecocraft_ah.type.buyout"), () -> { isBuyout = true; buildWidgets(); });
            }
            buyoutBtn.setPosition(leftCenterX - 82, currentY);
            buyoutBtn.setSize(80, 16);

            if (!isBuyout) {
                auctionBtn = EcoButton.warning(THEME, Component.translatable("ecocraft_ah.type.auction"), () -> { isBuyout = false; buildWidgets(); });
            } else {
                auctionBtn = EcoButton.ghost(THEME, Component.translatable("ecocraft_ah.type.auction"), () -> { isBuyout = false; buildWidgets(); });
            }
            auctionBtn.setPosition(leftCenterX + 2, currentY);
            auctionBtn.setSize(80, 16);

            addChild(buyoutBtn);
            addChild(auctionBtn);
            currentY += 20;
        }
        currentY += 4;

        // 3. "Prix unitaire:" label + price input
        priceInput = new EcoNumberInput(font, leftCenterX - 60, currentY, 120, 18, THEME);
        priceInput.min(0).max(999_999_999_999L).step(1).showButtons(false);
        priceInput.responder(this::onPriceChanged);
        if (priceValue > 0) {
            priceInput.setValue(priceValue);
        }
        addChild(priceInput);
        currentY += 18;
        currentY += 4;

        // 4. Duration selector
        List<Component> durationLabels = new java.util.ArrayList<>();
        for (int d : activeDurations) {
            durationLabels.add(Component.literal(d + "h"));
        }
        int totalTagsW = 0;
        for (var label : durationLabels) {
            totalTagsW += Minecraft.getInstance().font.width(label) + 24 + 6;
        }
        totalTagsW -= 6;
        int tagsX = leftCenterX - totalTagsW / 2;
        if (selectedDuration >= activeDurations.length) selectedDuration = 0;
        durationTags = new EcoFilterTags(tagsX, currentY, durationLabels, idx -> selectedDuration = idx, THEME);
        durationTags.setActiveTag(selectedDuration);
        addChild(durationTags);
        currentY += 18;
        currentY += 4;

        // 5. Quantity info (rendered in render())
        currentY += 12;
        currentY += 4;

        // 6. Tax summary panel (rendered in render())
        summaryY = currentY;
        int summaryH = 68;
        currentY += summaryH + 4;
        currentY += 4;

        // 7. "Mettre en vente" button
        sellButton = EcoButton.success(THEME, Component.translatable("ecocraft_ah.button.sell"), this::onSellClicked);
        sellButton.setPosition(leftCenterX - 60, currentY);
        sellButton.setSize(120, 18);
        addChild(sellButton);
        currentY += 22;

        // --- Right column: Inventory grid ---
        buildInventoryGrid();
    }

    private void buildInventoryGrid() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        inventoryGrid = EcoInventoryGrid.builder()
                .inventory(player.getInventory())
                .columns(INV_COLS)
                .slotSize(EcoInventoryGrid.SlotSize.MEDIUM)
                .scrollable(true)
                .showMain(true)
                .showHotbar(true)
                .showArmor(false)
                .showOffhand(false)
                .showOther(false)
                .onSlotClicked(this::onInventorySlotClicked)
                .theme(THEME)
                .build();

        int gridY = tabY + 4;
        int gridH = tabH - 8;
        inventoryGrid.setBounds(rightColX, gridY, rightColW, gridH);
        inventoryGrid.setSelectedSlot(selectedInventorySlot);
        addChild(inventoryGrid);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int leftCenterX = leftColX + leftColW / 2;

        // Item name (below the slot)
        int nameY = tabY + 40;
        ItemStack selected = getSelectedItem();
        if (selected.isEmpty()) {
            Component msg = Component.translatable("ecocraft_ah.sell.select_item");
            int msgW = font.width(msg);
            graphics.drawString(font, msg, leftCenterX - msgW / 2, nameY, THEME.textGrey, false);
        } else {
            String itemName = selected.getHoverName().getString();
            int maxNameWidth = leftColW - 20;
            String truncatedName = DrawUtils.truncateText(font, itemName, maxNameWidth);
            int nameW = font.width(truncatedName);
            graphics.drawString(font, truncatedName, leftCenterX - nameW / 2, nameY, THEME.textLight, false);
        }

        // "Prix unitaire:" label left of the price input
        if (priceInput != null) {
            int priceInputY = priceInput.getY();
            Component unitPriceLabel = Component.translatable("ecocraft_ah.sell.unit_price_label");
            graphics.drawString(font, unitPriceLabel, priceInput.getX() - font.width(unitPriceLabel) - 4, priceInputY + 3, THEME.textGrey, false);
        }

        // Quantity info below duration
        int quantityY = (durationTags != null) ? durationTags.getY() + 22 : tabY + 130;
        int quantity = selected.isEmpty() ? 0 : selected.getCount();
        Component qtyText = Component.translatable("ecocraft_ah.sell.quantity_label", quantity);
        int qtyW = font.width(qtyText);
        graphics.drawString(font, qtyText, leftCenterX - qtyW / 2, quantityY, THEME.textLight, false);

        // Tax summary panel
        int panelX = leftColX + 10;
        int panelW = leftColW - 20;
        int panelY = summaryY;
        int panelH = 68;

        if (priceValue > 0 && !selected.isEmpty()) {
            DrawUtils.drawPanel(graphics, panelX, panelY, panelW, panelH, THEME);

            long unitPrice = priceValue;
            long totalPrice = unitPrice * quantity;
            long tax = Math.max(1, (long) (totalPrice * activeTaxRate));
            long deposit = Math.max(1, (long) (totalPrice * activeDepositRate));
            long net = totalPrice - tax;

            int labelX = panelX + 8;
            int valueX = panelX + panelW - 8;

            drawSummaryLine(graphics, font, Component.translatable("ecocraft_ah.sell.unit_price_label").getString(), BuyTab.formatPrice(unitPrice), labelX, valueX, panelY + 4, THEME.textGrey, THEME.textLight);
            drawSummaryLine(graphics, font, Component.translatable("ecocraft_ah.sell.total_price_label").getString(), BuyTab.formatPrice(totalPrice), labelX, valueX, panelY + 16, THEME.textLight, THEME.accent);
            drawSummaryLine(graphics, font, Component.translatable("ecocraft_ah.sell.tax_label", Math.round(activeTaxRate * 100)).getString(), "-" + BuyTab.formatPrice(tax), labelX, valueX, panelY + 28, THEME.textGrey, THEME.danger);
            drawSummaryLine(graphics, font, Component.translatable("ecocraft_ah.sell.deposit_label", Math.round(activeDepositRate * 100)).getString(), "-" + BuyTab.formatPrice(deposit), labelX, valueX, panelY + 40, THEME.textGrey, THEME.warning);
            DrawUtils.drawAccentSeparator(graphics, panelX + 4, panelY + 51, panelW - 8, THEME);
            drawSummaryLine(graphics, font, Component.translatable("ecocraft_ah.sell.net_label").getString(), BuyTab.formatPrice(net), labelX, valueX, panelY + 56, THEME.textLight, THEME.success);
        } else {
            DrawUtils.drawPanel(graphics, panelX, panelY, panelW, panelH, THEME);
            Component placeholder = Component.translatable("ecocraft_ah.sell.enter_price_summary");
            int phW = font.width(placeholder);
            graphics.drawString(font, placeholder, panelX + (panelW - phW) / 2, panelY + 28, THEME.textDim, false);
        }

        // Status message at bottom left
        if (!lastMessage.isEmpty()) {
            int msgColor = lastSuccess ? THEME.success : THEME.danger;
            int msgW = font.width(lastMessage);
            graphics.drawString(font, lastMessage, leftCenterX - msgW / 2, tabY + tabH - 10, msgColor, false);
        }
    }

    private void drawSummaryLine(GuiGraphics graphics, Font font, String label, String value,
                                  int labelX, int rightEdge, int y, int labelColor, int valueColor) {
        graphics.drawString(font, label, labelX, y, labelColor, false);
        int valW = font.width(value);
        graphics.drawString(font, value, rightEdge - valW, y, valueColor, false);
    }

    /** Called when tab becomes visible. */
    public void onActivated() {
        // Rebuild widgets to pick up any config changes (durations, rates)
        buildWidgets();
        // Refresh inventory grid
        if (inventoryGrid != null) {
            inventoryGrid.refresh();
        }
    }

    // --- Event handlers ---

    private void onPriceChanged(long value) {
        priceValue = value;
    }

    private void onInventorySlotClicked(int inventoryIndex) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack stack = player.getInventory().getItem(inventoryIndex);
            if (stack.isEmpty()) {
                selectedInventorySlot = -1;
            } else {
                selectedInventorySlot = inventoryIndex;
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).toString();
                String fingerprint = net.ecocraft.ah.data.ItemFingerprint.compute(stack);
                PacketDistributor.sendToServer(
                        new net.ecocraft.ah.network.payload.RequestBestPricePayload(getAhId(), fingerprint, itemId));
            }
        }
        updateSelectedItem();
        buildWidgets();
    }

    public void onReceiveBestPrice(net.ecocraft.ah.network.payload.BestPriceResponsePayload payload) {
        if (payload.bestPrice() > 1) {
            priceValue = payload.bestPrice() - 1;
        } else if (payload.bestPrice() == 1) {
            priceValue = 1;
        } else {
            priceValue = 0;
        }
        if (priceInput != null && priceValue > 0) {
            priceInput.setValue(priceValue);
        }
    }

    private void onSellClicked() {
        ItemStack selected = getSelectedItem();
        if (selected.isEmpty()) {
            lastMessage = Component.translatable("ecocraft_ah.sell.no_item_selected").getString();
            lastSuccess = false;
            return;
        }
        if (priceValue <= 0) {
            lastMessage = Component.translatable("ecocraft_ah.sell.enter_valid_price").getString();
            lastSuccess = false;
            return;
        }

        String listingType = isBuyout ? "BUYOUT" : "AUCTION";
        int hours = activeDurations[selectedDuration];
        int slotToSend = selectedInventorySlot >= 0 ? selectedInventorySlot : -1;
        com.mojang.logging.LogUtils.getLogger().info("[AH Client] SellTab.createListing ahId={} price={}", getAhId(), priceValue);
        PacketDistributor.sendToServer(new CreateListingPayload(getAhId(), listingType, priceValue, hours, slotToSend));
        lastMessage = Component.translatable("ecocraft_ah.sell.sending").getString();
        lastSuccess = true;
    }

    public void onActionResult(AHActionResultPayload payload) {
        String msg = payload.message();
        lastMessage = msg;
        lastSuccess = payload.success();

        if (payload.success()) {
            selectedInventorySlot = -1;
            priceValue = 0;
            if (itemSlot != null) {
                itemSlot.setItem(ItemStack.EMPTY);
            }
            if (inventoryGrid != null) {
                inventoryGrid.setSelectedSlot(-1);
                inventoryGrid.refresh();
            }
            buildWidgets();
        }
    }

    // --- Helpers ---

    private void updateSelectedItem() {
        ItemStack selected = getSelectedItem();
        if (itemSlot != null) {
            itemSlot.setItem(selected.isEmpty() ? ItemStack.EMPTY : selected);
        }
    }

    private ItemStack getSelectedItem() {
        var player = Minecraft.getInstance().player;
        if (player == null) return ItemStack.EMPTY;
        if (selectedInventorySlot >= 0) {
            ItemStack stack = player.getInventory().getItem(selectedInventorySlot);
            if (!stack.isEmpty()) return stack;
            selectedInventorySlot = -1;
        }
        return ItemStack.EMPTY;
    }
}

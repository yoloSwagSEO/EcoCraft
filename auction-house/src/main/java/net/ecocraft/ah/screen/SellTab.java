package net.ecocraft.ah.screen;

import net.ecocraft.ah.network.payload.AHActionResultPayload;
import net.ecocraft.ah.network.payload.CreateListingPayload;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.gui.widget.Button;
import net.ecocraft.gui.widget.FilterTags;
import net.ecocraft.gui.widget.InventoryGrid;
import net.ecocraft.gui.widget.ItemSlot;
import net.ecocraft.gui.widget.NumberInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sell tab: two-column layout.
 * Left column: item slot, type toggle, price input, duration, tax summary, sell button.
 * Right column: full 9x4 inventory grid.
 */
public class SellTab {

    private static final Theme THEME = Theme.dark();
    private static final double TAX_RATE = 0.05;
    private static final double DEPOSIT_RATE = 0.02;
    private static final int[] DURATIONS = {12, 24, 48};
    private static final String[] DURATION_LABELS = {"12h", "24h", "48h"};
    private static final int INV_COLS = 9;

    private final AuctionHouseScreen parent;
    private final int x, y, w, h;

    // State
    private boolean isBuyout = true;
    private int selectedDuration = 1; // default 24h
    private long priceValue = 0;
    private String lastMessage = "";
    private boolean lastSuccess = false;
    private int selectedInventorySlot = -1; // -1 means nothing selected

    // Widgets
    private ItemSlot itemSlot;
    private Button buyoutBtn;
    private Button auctionBtn;
    private NumberInput priceInput;
    private FilterTags durationTags;
    private Button sellButton;
    private InventoryGrid inventoryGrid;

    // Layout positions computed in init
    private int leftColX, leftColW;
    private int rightColX, rightColW;
    private int summaryY;

    public SellTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void init(Consumer<AbstractWidget> addWidget) {
        Font font = Minecraft.getInstance().font;

        // Two-column layout: 60% left, 40% right
        leftColX = x + 4;
        leftColW = (int) (w * 0.6) - 8;
        rightColX = x + (int) (w * 0.6) + 4;
        rightColW = w - (int) (w * 0.6) - 8;

        int leftCenterX = leftColX + leftColW / 2;
        int currentY = y + 4;

        // 1. Item slot (32x32) + item name
        itemSlot = new ItemSlot(leftCenterX - 16, currentY, 32);
        updateSelectedItem();
        addWidget.accept(itemSlot);
        currentY += 36; // 32 slot + 4 gap

        // Item name label space (rendered in render())
        currentY += 12; // text height + gap

        currentY += 4;

        // 2. Type toggle: Achat immediat / Enchere
        if (isBuyout) {
            buyoutBtn = Button.success(THEME, Component.literal("Achat imm\u00e9diat"), () -> { isBuyout = true; parent.rebuildCurrentTab(); });
        } else {
            buyoutBtn = Button.ghost(THEME, Component.literal("Achat imm\u00e9diat"), () -> { isBuyout = true; parent.rebuildCurrentTab(); });
        }
        buyoutBtn.setX(leftCenterX - 82);
        buyoutBtn.setY(currentY);
        buyoutBtn.setWidth(80);
        buyoutBtn.setHeight(16);

        if (!isBuyout) {
            auctionBtn = Button.warning(THEME, Component.literal("Ench\u00e8re"), () -> { isBuyout = false; parent.rebuildCurrentTab(); });
        } else {
            auctionBtn = Button.ghost(THEME, Component.literal("Ench\u00e8re"), () -> { isBuyout = false; parent.rebuildCurrentTab(); });
        }
        auctionBtn.setX(leftCenterX + 2);
        auctionBtn.setY(currentY);
        auctionBtn.setWidth(80);
        auctionBtn.setHeight(16);

        addWidget.accept(buyoutBtn);
        addWidget.accept(auctionBtn);
        currentY += 20;

        currentY += 4;

        // 3. "Prix unitaire:" label + price input (NumberInput replaces EditBox)
        priceInput = new NumberInput(font, leftCenterX - 60, currentY, 120, 14, THEME);
        priceInput.min(0).max(999_999_999_999L).step(1).showButtons(false);
        priceInput.responder(this::onPriceChanged);
        if (priceValue > 0) {
            priceInput.setValue(priceValue);
        }
        addWidget.accept(priceInput);
        currentY += 18;

        currentY += 4;

        // 4. Duration selector
        List<Component> durationLabels = List.of(
                Component.literal(DURATION_LABELS[0]),
                Component.literal(DURATION_LABELS[1]),
                Component.literal(DURATION_LABELS[2])
        );
        durationTags = new FilterTags(leftCenterX - 60, currentY, durationLabels, idx -> selectedDuration = idx);
        durationTags.setActiveTag(selectedDuration);
        addWidget.accept(durationTags);
        currentY += 18;

        currentY += 4;

        // 5. Quantity info (rendered in render())
        currentY += 12; // "Quantite: X" line
        currentY += 4;

        // 6. Tax summary panel (rendered in render()) - reserve space
        summaryY = currentY;
        int summaryH = 68; // taller to include unit price + total price + tax + deposit + net
        currentY += summaryH + 4;

        currentY += 4;

        // 7. "Mettre en vente" button
        sellButton = Button.success(THEME, Component.literal("Mettre en vente"), this::onSellClicked);
        sellButton.setX(leftCenterX - 60);
        sellButton.setY(currentY);
        sellButton.setWidth(120);
        sellButton.setHeight(18);
        addWidget.accept(sellButton);
        currentY += 22;

        // 8. Status message space (rendered in render())

        // --- Right column: Inventory grid ---
        buildInventoryGrid(addWidget);
    }

    private void buildInventoryGrid(Consumer<AbstractWidget> addWidget) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        inventoryGrid = InventoryGrid.builder()
                .inventory(player.getInventory())
                .columns(INV_COLS)
                .slotSize(InventoryGrid.SlotSize.MEDIUM)
                .scrollable(true)
                .onSlotClicked(this::onInventorySlotClicked)
                .theme(THEME)
                .build();

        int gridY = y + 18; // leave room for "Inventaire" title
        int gridH = h - 22; // remaining height
        inventoryGrid.setBounds(rightColX, gridY, rightColW, gridH);
        inventoryGrid.setSelectedSlot(selectedInventorySlot);
        addWidget.accept(inventoryGrid);
    }

    public void renderBackground(GuiGraphics graphics) {
        // No background panels needed before widgets
    }

    public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int leftCenterX = leftColX + leftColW / 2;

        // Item name (below the slot)
        int nameY = y + 40;
        ItemStack selected = getSelectedItem();
        if (selected.isEmpty()) {
            String msg = "S\u00e9lectionnez un objet";
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
            graphics.drawString(font, "Prix unitaire:", priceInput.getX() - font.width("Prix unitaire: ") - 2, priceInputY + 3, THEME.textGrey, false);
        }

        // Quantity info below duration
        int quantityY = (durationTags != null) ? durationTags.getY() + 22 : y + 130;
        int quantity = selected.isEmpty() ? 0 : selected.getCount();
        String qtyText = "Quantit\u00e9: " + quantity;
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
            long tax = (long) (totalPrice * TAX_RATE);
            long deposit = (long) (totalPrice * DEPOSIT_RATE);
            long net = totalPrice - tax;

            int labelX = panelX + 8;
            int valueX = panelX + panelW - 8;

            drawSummaryLine(graphics, font, "Prix unitaire:", BuyTab.formatPrice(unitPrice), labelX, valueX, panelY + 4, THEME.textGrey, THEME.textLight);
            drawSummaryLine(graphics, font, "Prix total:", BuyTab.formatPrice(totalPrice), labelX, valueX, panelY + 16, THEME.textLight, THEME.accent);
            drawSummaryLine(graphics, font, "Taxe (5%):", "-" + BuyTab.formatPrice(tax), labelX, valueX, panelY + 28, THEME.textGrey, THEME.danger);
            drawSummaryLine(graphics, font, "D\u00e9p\u00f4t (2%):", "-" + BuyTab.formatPrice(deposit), labelX, valueX, panelY + 40, THEME.textGrey, THEME.warning);
            DrawUtils.drawAccentSeparator(graphics, panelX + 4, panelY + 51, panelW - 8, THEME);
            drawSummaryLine(graphics, font, "Net:", BuyTab.formatPrice(net), labelX, valueX, panelY + 56, THEME.textLight, THEME.success);
        } else {
            // Draw empty summary placeholder
            DrawUtils.drawPanel(graphics, panelX, panelY, panelW, panelH, THEME);
            String placeholder = "Entrez un prix pour voir le r\u00e9sum\u00e9";
            int phW = font.width(placeholder);
            graphics.drawString(font, placeholder, panelX + (panelW - phW) / 2, panelY + 28, THEME.textDim, false);
        }

        // Right column: Inventory title
        graphics.drawString(font, "Inventaire", rightColX + 4, y + 6, THEME.textGrey, false);

        // Status message at bottom left
        if (!lastMessage.isEmpty()) {
            int msgColor = lastSuccess ? THEME.success : THEME.danger;
            int msgW = font.width(lastMessage);
            graphics.drawString(font, lastMessage, leftCenterX - msgW / 2, y + h - 10, msgColor, false);
        }
    }

    private void drawSummaryLine(GuiGraphics graphics, Font font, String label, String value,
                                  int labelX, int rightEdge, int y, int labelColor, int valueColor) {
        graphics.drawString(font, label, labelX, y, labelColor, false);
        int valW = font.width(value);
        graphics.drawString(font, value, rightEdge - valW, y, valueColor, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (priceInput != null && priceInput.isFocused()) {
            return priceInput.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (priceInput != null && priceInput.isFocused()) {
            return priceInput.charTyped(codePoint, modifiers);
        }
        return false;
    }

    // --- Event handlers ---

    private void onPriceChanged(long value) {
        priceValue = value;
    }

    private void onInventorySlotClicked(int inventoryIndex) {
        // If clicking an empty slot, deselect
        var player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack stack = player.getInventory().getItem(inventoryIndex);
            if (stack.isEmpty()) {
                selectedInventorySlot = -1;
            } else {
                selectedInventorySlot = inventoryIndex;
            }
        }
        updateSelectedItem();
        parent.rebuildCurrentTab();
    }

    private void onSellClicked() {
        ItemStack selected = getSelectedItem();
        if (selected.isEmpty()) {
            lastMessage = "Aucun objet s\u00e9lectionn\u00e9 !";
            lastSuccess = false;
            return;
        }
        if (priceValue <= 0) {
            lastMessage = "Entrez un prix valide !";
            lastSuccess = false;
            return;
        }

        String listingType = isBuyout ? "BUYOUT" : "AUCTION";
        int hours = DURATIONS[selectedDuration];
        int slotToSend = selectedInventorySlot >= 0 ? selectedInventorySlot : -1;
        PacketDistributor.sendToServer(new CreateListingPayload(listingType, priceValue, hours, slotToSend));
        lastMessage = "Envoi en cours...";
        lastSuccess = true;
    }

    public void onActionResult(AHActionResultPayload payload) {
        // Translate known English messages to French
        String msg = payload.message();
        if ("Listing created successfully.".equals(msg)) {
            msg = "Objet mis en vente !";
        } else if ("You must hold an item to sell.".equals(msg)) {
            msg = "Aucun objet s\u00e9lectionn\u00e9 !";
        } else if ("Price must be positive.".equals(msg)) {
            msg = "Le prix doit \u00eatre positif !";
        } else if ("Invalid item.".equals(msg)) {
            msg = "Objet invalide !";
        } else if ("Not enough funds.".equals(msg)) {
            msg = "Fonds insuffisants !";
        } else if ("Listing not found.".equals(msg)) {
            msg = "Annonce introuvable !";
        }
        lastMessage = msg;
        lastSuccess = payload.success();

        // Clear selection and reset after successful sale
        if (payload.success()) {
            selectedInventorySlot = -1;
            priceValue = 0;
            if (itemSlot != null) {
                itemSlot.setItem(ItemStack.EMPTY);
            }
            // Refresh the inventory grid to reflect updated inventory state
            if (inventoryGrid != null) {
                inventoryGrid.setSelectedSlot(-1);
                inventoryGrid.refresh();
            }
            parent.rebuildCurrentTab();
        }
    }

    // --- Helpers ---

    private void updateSelectedItem() {
        ItemStack selected = getSelectedItem();
        if (!selected.isEmpty()) {
            itemSlot.setItem(selected);
        } else {
            itemSlot.setItem(ItemStack.EMPTY);
        }
    }

    private ItemStack getSelectedItem() {
        var player = Minecraft.getInstance().player;
        if (player == null) return ItemStack.EMPTY;
        if (selectedInventorySlot >= 0) {
            ItemStack stack = player.getInventory().getItem(selectedInventorySlot);
            if (!stack.isEmpty()) return stack;
            // Selection is now empty, fall back
            selectedInventorySlot = -1;
        }
        return ItemStack.EMPTY; // No default to main hand; require explicit selection
    }

}

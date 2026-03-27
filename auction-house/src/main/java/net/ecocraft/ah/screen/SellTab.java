package net.ecocraft.ah.screen;

import net.ecocraft.ah.network.payload.AHActionResultPayload;
import net.ecocraft.ah.network.payload.CreateListingPayload;
import net.ecocraft.gui.theme.EcoColors;
import net.ecocraft.gui.theme.EcoTheme;
import net.ecocraft.gui.widget.EcoButton;
import net.ecocraft.gui.widget.EcoFilterTags;
import net.ecocraft.gui.widget.EcoItemSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sell tab: two-column layout.
 * Left column: item slot, type toggle, price input, duration, tax summary, sell button.
 * Right column: full 9x4 inventory grid.
 */
public class SellTab {

    private static final double TAX_RATE = 0.05;
    private static final double DEPOSIT_RATE = 0.02;
    private static final int[] DURATIONS = {12, 24, 48};
    private static final String[] DURATION_LABELS = {"12h", "24h", "48h"};
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_PADDING = 2;
    private static final int INV_COLS = 9;
    private static final int INV_ROWS = 4;

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
    private EcoItemSlot itemSlot;
    private EcoButton buyoutBtn;
    private EcoButton auctionBtn;
    private EditBox priceInput;
    private EcoFilterTags durationTags;
    private EcoButton sellButton;
    private final List<InventorySlotWidget> inventorySlots = new ArrayList<>();

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
        itemSlot = new EcoItemSlot(leftCenterX - 16, currentY, 32);
        updateSelectedItem();
        addWidget.accept(itemSlot);
        currentY += 36; // 32 slot + 4 gap

        // Item name label space (rendered in render())
        currentY += 12; // text height + gap

        currentY += 4;

        // 2. Type toggle: Achat immediat / Enchere
        buyoutBtn = new EcoButton(leftCenterX - 82, currentY, 80, 16,
                Component.literal("Achat imm\u00e9diat"),
                isBuyout ? EcoButton.Style.SUCCESS : EcoButton.Style.GHOST,
                () -> { isBuyout = true; parent.rebuildCurrentTab(); });
        auctionBtn = new EcoButton(leftCenterX + 2, currentY, 80, 16,
                Component.literal("Ench\u00e8re"),
                !isBuyout ? EcoButton.Style.AUCTION : EcoButton.Style.GHOST,
                () -> { isBuyout = false; parent.rebuildCurrentTab(); });
        addWidget.accept(buyoutBtn);
        addWidget.accept(auctionBtn);
        currentY += 20;

        currentY += 4;

        // 3. "Prix unitaire:" label + price input
        priceInput = new EditBox(font, leftCenterX - 60, currentY, 120, 14, Component.literal("Prix"));
        priceInput.setHint(Component.literal("Prix unitaire (G)"));
        priceInput.setMaxLength(12);
        priceInput.setFilter(SellTab::isValidPriceChar);
        priceInput.setResponder(this::onPriceChanged);
        if (priceValue > 0) {
            priceInput.setValue(String.valueOf(priceValue));
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
        durationTags = new EcoFilterTags(leftCenterX - 60, currentY, durationLabels, idx -> selectedDuration = idx);
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
        sellButton = EcoButton.success(leftCenterX - 60, currentY, 120, 18,
                Component.literal("Mettre en vente"), this::onSellClicked);
        addWidget.accept(sellButton);
        currentY += 22;

        // 8. Status message space (rendered in render())

        // --- Right column: Inventory grid ---
        buildInventoryGrid(addWidget);
    }

    private void buildInventoryGrid(Consumer<AbstractWidget> addWidget) {
        inventorySlots.clear();
        var player = Minecraft.getInstance().player;
        if (player == null) return;

        Inventory inv = player.getInventory();
        int cellSize = SLOT_SIZE + SLOT_PADDING;
        int totalGridWidth = INV_COLS * cellSize - SLOT_PADDING;
        int gridX = rightColX + (rightColW - totalGridWidth) / 2;
        int gridY = y + 18; // leave room for "Inventaire" title

        // Show ALL 36 slots (9 hotbar + 27 main) as a proper 9x4 grid
        for (int i = 0; i < INV_ROWS * INV_COLS; i++) {
            int col = i % INV_COLS;
            int row = i / INV_COLS;
            int slotX = gridX + col * cellSize;
            int slotY = gridY + row * cellSize;

            ItemStack stack = inv.getItem(i);
            boolean isSelected = (selectedInventorySlot == i);
            final int inventoryIndex = i;

            InventorySlotWidget slotWidget = new InventorySlotWidget(
                    slotX, slotY, SLOT_SIZE, stack, isSelected,
                    () -> onInventorySlotClicked(inventoryIndex));
            inventorySlots.add(slotWidget);
            addWidget.accept(slotWidget);
        }
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
            graphics.drawString(font, msg, leftCenterX - msgW / 2, nameY, EcoColors.TEXT_GREY, false);
        } else {
            String itemName = selected.getHoverName().getString();
            int maxNameWidth = leftColW - 20;
            String truncatedName = AuctionHouseScreen.truncateText(font, itemName, maxNameWidth);
            int nameW = font.width(truncatedName);
            graphics.drawString(font, truncatedName, leftCenterX - nameW / 2, nameY, EcoColors.TEXT_LIGHT, false);
        }

        // "Prix unitaire:" label left of the price input
        if (priceInput != null) {
            int priceInputY = priceInput.getY();
            graphics.drawString(font, "Prix unitaire:", priceInput.getX() - font.width("Prix unitaire: ") - 2, priceInputY + 3, EcoColors.TEXT_GREY, false);
        }

        // Quantity info below duration
        int quantityY = (durationTags != null) ? durationTags.getY() + 22 : y + 130;
        int quantity = selected.isEmpty() ? 0 : selected.getCount();
        String qtyText = "Quantit\u00e9: " + quantity;
        int qtyW = font.width(qtyText);
        graphics.drawString(font, qtyText, leftCenterX - qtyW / 2, quantityY, EcoColors.TEXT_LIGHT, false);

        // Tax summary panel
        int panelX = leftColX + 10;
        int panelW = leftColW - 20;
        int panelY = summaryY;
        int panelH = 68;

        if (priceValue > 0 && !selected.isEmpty()) {
            EcoTheme.drawPanel(graphics, panelX, panelY, panelW, panelH);

            long unitPrice = priceValue;
            long totalPrice = unitPrice * quantity;
            long tax = (long) (totalPrice * TAX_RATE);
            long deposit = (long) (totalPrice * DEPOSIT_RATE);
            long net = totalPrice - tax;

            int labelX = panelX + 8;
            int valueX = panelX + panelW - 8;

            drawSummaryLine(graphics, font, "Prix unitaire:", BuyTab.formatPrice(unitPrice), labelX, valueX, panelY + 4, EcoColors.TEXT_GREY, EcoColors.TEXT_LIGHT);
            drawSummaryLine(graphics, font, "Prix total:", BuyTab.formatPrice(totalPrice), labelX, valueX, panelY + 16, EcoColors.TEXT_LIGHT, EcoColors.GOLD);
            drawSummaryLine(graphics, font, "Taxe (5%):", "-" + BuyTab.formatPrice(tax), labelX, valueX, panelY + 28, EcoColors.TEXT_GREY, EcoColors.DANGER);
            drawSummaryLine(graphics, font, "D\u00e9p\u00f4t (2%):", "-" + BuyTab.formatPrice(deposit), labelX, valueX, panelY + 40, EcoColors.TEXT_GREY, EcoColors.WARNING);
            EcoTheme.drawGoldSeparator(graphics, panelX + 4, panelY + 51, panelW - 8);
            drawSummaryLine(graphics, font, "Net:", BuyTab.formatPrice(net), labelX, valueX, panelY + 56, EcoColors.TEXT_LIGHT, EcoColors.SUCCESS);
        } else {
            // Draw empty summary placeholder
            EcoTheme.drawPanel(graphics, panelX, panelY, panelW, panelH);
            String placeholder = "Entrez un prix pour voir le r\u00e9sum\u00e9";
            int phW = font.width(placeholder);
            graphics.drawString(font, placeholder, panelX + (panelW - phW) / 2, panelY + 28, EcoColors.TEXT_DIM, false);
        }

        // Right column: Inventory title
        graphics.drawString(font, "Inventaire", rightColX + 4, y + 6, EcoColors.TEXT_GREY, false);

        // Status message at bottom left
        if (!lastMessage.isEmpty()) {
            int msgColor = lastSuccess ? EcoColors.SUCCESS : EcoColors.DANGER;
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

    private void onPriceChanged(String text) {
        try {
            priceValue = text.isEmpty() ? 0 : Long.parseLong(text);
        } catch (NumberFormatException e) {
            priceValue = 0;
        }
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

    private static boolean isValidPriceChar(String text) {
        return text.isEmpty() || text.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    // --- Inner widget for inventory slots ---

    private static class InventorySlotWidget extends AbstractWidget {
        private final ItemStack stack;
        private final boolean selected;
        private final Runnable onClick;

        public InventorySlotWidget(int x, int y, int size, ItemStack stack, boolean selected, Runnable onClick) {
            super(x, y, size, size, stack.isEmpty() ? Component.literal("Vide") : stack.getHoverName());
            this.stack = stack;
            this.selected = selected;
            this.onClick = onClick;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int borderColor = selected ? EcoColors.GOLD : (isHovered() ? EcoColors.TEXT_LIGHT : EcoColors.BG_LIGHT);
            graphics.fill(getX(), getY(), getX() + width, getY() + height, EcoColors.BG_DARK);
            // Border
            graphics.fill(getX(), getY(), getX() + width, getY() + 1, borderColor);
            graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, borderColor);
            graphics.fill(getX(), getY(), getX() + 1, getY() + height, borderColor);
            graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor);

            // Item (only if not empty)
            if (!stack.isEmpty()) {
                int itemX = getX() + (width - 16) / 2;
                int itemY = getY() + (height - 16) / 2;
                graphics.renderItem(stack, itemX, itemY);
                graphics.renderItemDecorations(Minecraft.getInstance().font, stack, itemX, itemY);

                // Tooltip on hover
                if (isHovered()) {
                    graphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
                }
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            onClick.run();
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        }
    }
}

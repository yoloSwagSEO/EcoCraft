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
 * Sell tab: allows the player to select an item from their inventory and list it.
 */
public class SellTab {

    private static final double TAX_RATE = 0.05;
    private static final double DEPOSIT_RATE = 0.02;
    private static final int[] DURATIONS = {12, 24, 48};
    private static final String[] DURATION_LABELS = {"12h", "24h", "48h"};
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_PADDING = 2;

    private final AuctionHouseScreen parent;
    private final int x, y, w, h;

    // State
    private boolean isBuyout = true;
    private int selectedDuration = 1; // default 24h
    private long priceValue = 0;
    private String lastMessage = "";
    private boolean lastSuccess = false;
    private int selectedInventorySlot = -1; // -1 means use main hand

    // Widgets
    private EcoItemSlot itemSlot;
    private EcoButton buyoutBtn;
    private EcoButton auctionBtn;
    private EditBox priceInput;
    private EcoFilterTags durationTags;
    private EcoButton sellButton;
    private final List<InventorySlotWidget> inventorySlots = new ArrayList<>();

    // Inventory grid layout
    private int invGridX;
    private int invGridY;
    private int invGridCols;

    public SellTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void init(Consumer<AbstractWidget> addWidget) {
        Font font = Minecraft.getInstance().font;
        int centerX = x + w / 2;

        // Item slot showing selected item
        itemSlot = new EcoItemSlot(centerX - 16, y + 4, 32);
        updateSelectedItem();
        addWidget.accept(itemSlot);

        // Type toggle: Buyout / Auction
        int toggleY = y + 42;
        buyoutBtn = new EcoButton(centerX - 82, toggleY, 80, 16,
                Component.literal("Achat imm\u00e9diat"),
                isBuyout ? EcoButton.Style.SUCCESS : EcoButton.Style.GHOST,
                () -> { isBuyout = true; parent.rebuildCurrentTab(); });
        auctionBtn = new EcoButton(centerX + 2, toggleY, 80, 16,
                Component.literal("Ench\u00e8re"),
                !isBuyout ? EcoButton.Style.AUCTION : EcoButton.Style.GHOST,
                () -> { isBuyout = false; parent.rebuildCurrentTab(); });
        addWidget.accept(buyoutBtn);
        addWidget.accept(auctionBtn);

        // Price input
        int priceY = toggleY + 22;
        priceInput = new EditBox(font, centerX - 60, priceY, 120, 14, Component.literal("Prix"));
        priceInput.setHint(Component.literal("Prix (en G)"));
        priceInput.setMaxLength(12);
        priceInput.setFilter(SellTab::isValidPriceChar);
        priceInput.setResponder(this::onPriceChanged);
        if (priceValue > 0) {
            priceInput.setValue(String.valueOf(priceValue));
        }
        addWidget.accept(priceInput);

        // Duration selector
        int durationY = priceY + 22;
        List<Component> durationLabels = List.of(
                Component.literal(DURATION_LABELS[0]),
                Component.literal(DURATION_LABELS[1]),
                Component.literal(DURATION_LABELS[2])
        );
        durationTags = new EcoFilterTags(centerX - 60, durationY, durationLabels, idx -> selectedDuration = idx);
        durationTags.setActiveTag(selectedDuration);
        addWidget.accept(durationTags);

        // Sell button
        int sellY = durationY + 22;
        sellButton = EcoButton.success(centerX - 60, sellY, 120, 18,
                Component.literal("Mettre en vente"), this::onSellClicked);
        addWidget.accept(sellButton);

        // Inventory grid
        int invStartY = sellY + 24;
        int availableHeight = (y + h) - invStartY;

        // Calculate grid layout
        int cellSize = SLOT_SIZE + SLOT_PADDING;
        invGridCols = Math.min(9, (w - 8) / cellSize);
        int totalGridWidth = invGridCols * cellSize - SLOT_PADDING;
        invGridX = x + (w - totalGridWidth) / 2;
        invGridY = invStartY;

        // Build inventory slot widgets from player inventory
        inventorySlots.clear();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            Inventory inv = player.getInventory();
            int slotIndex = 0;
            int maxRows = Math.max(1, availableHeight / cellSize);
            int maxSlots = maxRows * invGridCols;

            // Show main inventory (slots 0-35: 9 hotbar + 27 main)
            for (int i = 0; i < Math.min(36, maxSlots); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.isEmpty()) continue;

                int col = slotIndex % invGridCols;
                int row = slotIndex / invGridCols;
                if (row >= maxRows) break;

                int slotX = invGridX + col * cellSize;
                int slotY = invGridY + row * cellSize;
                final int inventoryIndex = i;
                boolean isSelected = (selectedInventorySlot == i);

                InventorySlotWidget slotWidget = new InventorySlotWidget(
                        slotX, slotY, SLOT_SIZE, stack, isSelected,
                        () -> onInventorySlotClicked(inventoryIndex));
                inventorySlots.add(slotWidget);
                addWidget.accept(slotWidget);
                slotIndex++;
            }
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int centerX = x + w / 2;

        // Item name
        ItemStack selected = getSelectedItem();
        if (selected.isEmpty()) {
            String msg = "Cliquez sur un objet ci-dessous";
            int msgW = font.width(msg);
            graphics.drawString(font, msg, centerX - msgW / 2, y + 38, EcoColors.TEXT_GREY, false);
        } else {
            String itemName = selected.getHoverName().getString();
            int nameW = font.width(itemName);
            graphics.drawString(font, itemName, centerX - nameW / 2, y + 38, EcoColors.TEXT_LIGHT, false);
        }

        // Summary section (only if price > 0)
        if (priceValue > 0 && !selected.isEmpty()) {
            int summaryX = x + 20;
            int summaryW = w - 40;
            int summaryY = y + 108;

            // Only draw summary if it fits above the inventory grid
            if (summaryY + 52 <= invGridY) {
                EcoTheme.drawPanel(graphics, summaryX, summaryY, summaryW, 52);

                long price = priceValue;
                long tax = (long) (price * TAX_RATE);
                long deposit = (long) (price * DEPOSIT_RATE);
                long net = price - tax;

                int labelX = summaryX + 10;
                int valueX = summaryX + summaryW - 10;

                drawSummaryLine(graphics, font, "Prix de vente:", BuyTab.formatPrice(price), labelX, valueX, summaryY + 4, EcoColors.TEXT_LIGHT, EcoColors.GOLD);
                drawSummaryLine(graphics, font, "Taxe (5%):", "-" + BuyTab.formatPrice(tax), labelX, valueX, summaryY + 16, EcoColors.TEXT_GREY, EcoColors.DANGER);
                drawSummaryLine(graphics, font, "D\u00e9p\u00f4t (2%):", BuyTab.formatPrice(deposit), labelX, valueX, summaryY + 28, EcoColors.TEXT_GREY, EcoColors.WARNING);
                EcoTheme.drawGoldSeparator(graphics, summaryX + 4, summaryY + 39, summaryW - 8);
                drawSummaryLine(graphics, font, "Net:", BuyTab.formatPrice(net), labelX, valueX, summaryY + 42, EcoColors.TEXT_LIGHT, EcoColors.SUCCESS);
            }
        }

        // Inventory label
        String invLabel = "Inventaire:";
        graphics.drawString(font, invLabel, invGridX, invGridY - 10, EcoColors.TEXT_GREY, false);

        // Status message
        if (!lastMessage.isEmpty()) {
            int msgColor = lastSuccess ? EcoColors.SUCCESS : EcoColors.DANGER;
            int msgW = font.width(lastMessage);
            graphics.drawString(font, lastMessage, centerX - msgW / 2, y + h - 10, msgColor, false);
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
        selectedInventorySlot = inventoryIndex;
        updateSelectedItem();
        parent.rebuildCurrentTab();
    }

    private void onSellClicked() {
        ItemStack selected = getSelectedItem();
        if (selected.isEmpty()) {
            lastMessage = "Aucun objet s\u00e9lectionn\u00e9!";
            lastSuccess = false;
            return;
        }
        if (priceValue <= 0) {
            lastMessage = "Entrez un prix valide!";
            lastSuccess = false;
            return;
        }

        String listingType = isBuyout ? "BUYOUT" : "AUCTION";
        int hours = DURATIONS[selectedDuration];
        PacketDistributor.sendToServer(new CreateListingPayload(listingType, priceValue, hours));
        lastMessage = "Envoi en cours...";
        lastSuccess = true;
    }

    public void onActionResult(AHActionResultPayload payload) {
        lastMessage = payload.message();
        lastSuccess = payload.success();
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
        return player.getMainHandItem();
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
            super(x, y, size, size, stack.getHoverName());
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

            // Item
            int itemX = getX() + (width - 16) / 2;
            int itemY = getY() + (height - 16) / 2;
            graphics.renderItem(stack, itemX, itemY);
            graphics.renderItemDecorations(Minecraft.getInstance().font, stack, itemX, itemY);

            // Tooltip on hover
            if (isHovered()) {
                graphics.renderTooltip(Minecraft.getInstance().font, stack, mouseX, mouseY);
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

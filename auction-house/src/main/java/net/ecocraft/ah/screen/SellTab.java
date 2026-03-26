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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sell tab: allows the player to list the item in their main hand.
 */
public class SellTab {

    private static final double TAX_RATE = 0.05;
    private static final double DEPOSIT_RATE = 0.02;
    private static final int[] DURATIONS = {12, 24, 48};
    private static final String[] DURATION_LABELS = {"12h", "24h", "48h"};

    private final AuctionHouseScreen parent;
    private final int x, y, w, h;

    // State
    private boolean isBuyout = true;
    private int selectedDuration = 1; // default 24h
    private long priceValue = 0;
    private String lastMessage = "";
    private boolean lastSuccess = false;

    // Widgets
    private EcoItemSlot itemSlot;
    private EcoButton buyoutBtn;
    private EcoButton auctionBtn;
    private EditBox priceInput;
    private EcoFilterTags durationTags;
    private EcoButton sellButton;

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

        // Item slot showing held item
        itemSlot = new EcoItemSlot(centerX - 16, y + 4, 32);
        updateHeldItem();
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
        int sellY = y + h - 20;
        sellButton = EcoButton.success(centerX - 60, sellY, 120, 18,
                Component.literal("Mettre en vente"), this::onSellClicked);
        addWidget.accept(sellButton);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int centerX = x + w / 2;

        // Item name
        ItemStack held = getHeldItem();
        if (held.isEmpty()) {
            String msg = "Tenez un objet pour le vendre";
            int msgW = font.width(msg);
            graphics.drawString(font, msg, centerX - msgW / 2, y + 38, EcoColors.TEXT_GREY, false);
            return;
        }

        String itemName = held.getHoverName().getString();
        int nameW = font.width(itemName);
        graphics.drawString(font, itemName, centerX - nameW / 2, y + 38, EcoColors.TEXT_LIGHT, false);

        // Summary section
        int summaryY = y + 108;
        EcoTheme.drawPanel(graphics, x + 20, summaryY, w - 40, 52);

        long price = priceValue;
        long tax = (long) (price * TAX_RATE);
        long deposit = (long) (price * DEPOSIT_RATE);
        long net = price - tax;

        int labelX = x + 30;
        int valueX = x + w - 30;

        drawSummaryLine(graphics, font, "Prix de vente:", BuyTab.formatPrice(price), labelX, valueX, summaryY + 4, EcoColors.TEXT_LIGHT, EcoColors.GOLD);
        drawSummaryLine(graphics, font, "Taxe (5%):", "-" + BuyTab.formatPrice(tax), labelX, valueX, summaryY + 16, EcoColors.TEXT_GREY, EcoColors.DANGER);
        drawSummaryLine(graphics, font, "D\u00e9p\u00f4t (2%):", BuyTab.formatPrice(deposit), labelX, valueX, summaryY + 28, EcoColors.TEXT_GREY, EcoColors.WARNING);
        EcoTheme.drawGoldSeparator(graphics, x + 24, summaryY + 39, w - 48);
        drawSummaryLine(graphics, font, "Net:", BuyTab.formatPrice(net), labelX, valueX, summaryY + 42, EcoColors.TEXT_LIGHT, EcoColors.SUCCESS);

        // Status message
        if (!lastMessage.isEmpty()) {
            int msgColor = lastSuccess ? EcoColors.SUCCESS : EcoColors.DANGER;
            int msgW = font.width(lastMessage);
            graphics.drawString(font, lastMessage, centerX - msgW / 2, y + h - 34, msgColor, false);
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

    private void onSellClicked() {
        ItemStack held = getHeldItem();
        if (held.isEmpty()) {
            lastMessage = "Aucun objet en main!";
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

    private void updateHeldItem() {
        ItemStack held = getHeldItem();
        if (!held.isEmpty()) {
            itemSlot.setItem(held);
        }
    }

    private static ItemStack getHeldItem() {
        var player = Minecraft.getInstance().player;
        if (player == null) return ItemStack.EMPTY;
        return player.getMainHandItem();
    }

    private static boolean isValidPriceChar(String text) {
        return text.isEmpty() || text.chars().allMatch(c -> c >= '0' && c <= '9');
    }
}

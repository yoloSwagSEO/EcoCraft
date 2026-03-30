package net.ecocraft.core.screen;

import com.mojang.logging.LogUtils;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyFormatter;
import net.ecocraft.core.network.payload.ExchangeDataPayload;
import net.ecocraft.core.network.payload.ExchangeRequestPayload;
import net.ecocraft.core.network.payload.ExchangeResultPayload;
import net.ecocraft.core.network.payload.ExchangerSkinPayload;
import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Bureau de Change screen — allows players to convert between currencies.
 * Compact centered panel with dropdowns, amount input, preview, and footer buttons.
 */
public class ExchangeScreen extends EcoScreen {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final Theme THEME = Theme.dark();

    private final int npcEntityId;

    private int guiWidth;
    private int guiHeight;
    private int guiLeft;
    private int guiTop;

    // Data from server
    private List<ExchangeDataPayload.CurrencyData> currencies = new ArrayList<>();
    private double feePercent = 2.0;

    // Skin data (for admin config, passed to settings screen)
    String exchangerSkinName = "";

    // Widgets
    private EcoDropdown fromDropdown;
    private EcoDropdown toDropdown;
    private EcoCurrencyInput amountInput;
    private Label previewLabel;
    private Label feeLabel;
    private Label fromBalanceLabel;
    private Label toBalanceLabel;
    private Label statusLabel;
    private EcoButton convertButton;
    private Label noCurrenciesLabel;

    // Current input amount
    private long currentAmount = 0;

    public ExchangeScreen(int npcEntityId) {
        super(Component.translatable("ecocraft_core.exchange.title"));
        this.npcEntityId = npcEntityId;
    }

    int getNpcEntityId() {
        return npcEntityId;
    }

    @Override
    protected void init() {
        super.init();

        guiWidth = (int) (this.width * 0.50);
        guiHeight = (int) (this.height * 0.70);
        guiLeft = (this.width - guiWidth) / 2;
        guiTop = (this.height - guiHeight) / 2;

        buildUI();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight, THEME.bgDarkest, THEME.borderAccent);

        // Title
        Font font = Minecraft.getInstance().font;
        String titleText = Component.translatable("ecocraft_core.exchange.title").getString();
        int titleWidth = font.width(titleText);
        int titleX = guiLeft + (guiWidth - titleWidth) / 2;
        graphics.drawString(font, titleText, titleX, guiTop + 10, THEME.accent, false);

        // Accent separator below title
        DrawUtils.drawAccentSeparator(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20, THEME);
    }

    private void buildUI() {
        Font font = Minecraft.getInstance().font;
        int contentLeft = guiLeft + 16;
        int contentWidth = guiWidth - 32;
        int y = guiTop + 30;

        // Gear button (admin only) — top-right corner
        if (npcEntityId > 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.hasPermissions(2)) {
                EcoButton gearButton = EcoButton.ghost(THEME, Component.literal("\u2699"), this::openSettings);
                gearButton.setBounds(guiLeft + guiWidth - 20, guiTop + 6, 14, 14);
                getTree().addChild(gearButton);
            }
        }

        // "De" (From) label + dropdown
        Label fromLabel = new Label(font, contentLeft, y,
                Component.translatable("ecocraft_core.exchange.from"), THEME);
        fromLabel.setColor(THEME.accent);
        getTree().addChild(fromLabel);

        y += 12;
        fromDropdown = new EcoDropdown(contentLeft, y, contentWidth, 18, THEME);
        fromDropdown.options(getCurrencyNames());
        if (!currencies.isEmpty()) fromDropdown.selectedIndex(0);
        fromDropdown.responder(idx -> onCurrencyChanged());
        getTree().addChild(fromDropdown);

        y += 22;

        // From balance
        fromBalanceLabel = new Label(font, contentLeft, y,
                Component.translatable("ecocraft_core.exchange.balance", "---"), THEME);
        fromBalanceLabel.setColor(THEME.textGrey);
        getTree().addChild(fromBalanceLabel);

        y += 16;

        // "Montant" (Amount) label + input
        Label amountLabel = new Label(font, contentLeft, y,
                Component.translatable("ecocraft_core.exchange.amount"), THEME);
        amountLabel.setColor(THEME.accent);
        getTree().addChild(amountLabel);

        y += 12;
        Currency fromCurrency = getSelectedFromCurrency();
        if (fromCurrency != null) {
            amountInput = new EcoCurrencyInput(font, contentLeft, y, contentWidth, fromCurrency, THEME);
            amountInput.min(0).responder(val -> {
                currentAmount = val;
                updatePreview();
                updateConvertButtonState();
            });
            getTree().addChild(amountInput);
            y += amountInput.getHeight() + 8;
        } else {
            y += 24;
        }

        // Arrow separator
        Label arrowLabel = new Label(font, guiLeft + guiWidth / 2, y,
                Component.literal("\u2193"), THEME);
        arrowLabel.setColor(THEME.textDim);
        arrowLabel.setAlignment(Label.Align.CENTER);
        getTree().addChild(arrowLabel);

        y += 14;

        // "Vers" (To) label + dropdown
        Label toLabel = new Label(font, contentLeft, y,
                Component.translatable("ecocraft_core.exchange.to"), THEME);
        toLabel.setColor(THEME.accent);
        getTree().addChild(toLabel);

        y += 12;
        toDropdown = new EcoDropdown(contentLeft, y, contentWidth, 18, THEME);
        toDropdown.options(getCurrencyNames());
        if (currencies.size() > 1) toDropdown.selectedIndex(1);
        else if (!currencies.isEmpty()) toDropdown.selectedIndex(0);
        toDropdown.responder(idx -> onCurrencyChanged());
        getTree().addChild(toDropdown);

        y += 22;

        // To balance
        toBalanceLabel = new Label(font, contentLeft, y,
                Component.translatable("ecocraft_core.exchange.balance", "---"), THEME);
        toBalanceLabel.setColor(THEME.textGrey);
        getTree().addChild(toBalanceLabel);

        y += 20;

        // Preview: "Vous recevrez : X"
        previewLabel = new Label(font, contentLeft, y,
                Component.translatable("ecocraft_core.exchange.preview", "---"), THEME);
        previewLabel.setColor(THEME.accent);
        getTree().addChild(previewLabel);

        y += 14;

        // Fee: "Frais : X%"
        feeLabel = new Label(font, contentLeft, y,
                Component.translatable("ecocraft_core.exchange.fee", String.format("%.1f", feePercent)), THEME);
        feeLabel.setColor(THEME.textDim);
        getTree().addChild(feeLabel);

        y += 18;

        // Status message (success/error)
        statusLabel = new Label(font, contentLeft, y, Component.empty(), THEME);
        statusLabel.setColor(THEME.textLight);
        getTree().addChild(statusLabel);

        // Footer: centered buttons at bottom of panel
        int footerY = guiTop + guiHeight - 30;
        int btnW = 100;
        int gap = 10;
        int totalBtnW = btnW * 2 + gap;
        int btnStartX = guiLeft + (guiWidth - totalBtnW) / 2;

        convertButton = EcoButton.success(THEME,
                Component.translatable("ecocraft_core.exchange.convert"),
                this::onConvert);
        convertButton.setPosition(btnStartX, footerY);
        convertButton.setSize(btnW, 20);
        getTree().addChild(convertButton);

        EcoButton closeButton = EcoButton.ghost(THEME,
                Component.translatable("ecocraft_core.exchange.close"),
                this::doClose);
        closeButton.setPosition(btnStartX + btnW + gap, footerY);
        closeButton.setSize(btnW, 20);
        getTree().addChild(closeButton);

        // No currencies label (hidden by default, centered)
        noCurrenciesLabel = new Label(font, guiLeft + guiWidth / 2, guiTop + guiHeight / 2,
                Component.translatable("ecocraft_core.exchange.no_currencies"), THEME);
        noCurrenciesLabel.setColor(THEME.textDim);
        noCurrenciesLabel.setAlignment(Label.Align.CENTER);
        noCurrenciesLabel.setVisible(false);
        getTree().addChild(noCurrenciesLabel);

        // Update initial state
        updateBalances();
        updatePreview();
        updateConvertButtonState();

        if (currencies.size() < 2) {
            setExchangeWidgetsVisible(false);
            noCurrenciesLabel.setVisible(true);
        }
    }

    private void setExchangeWidgetsVisible(boolean visible) {
        if (fromDropdown != null) fromDropdown.setVisible(visible);
        if (toDropdown != null) toDropdown.setVisible(visible);
        if (amountInput != null) amountInput.setVisible(visible);
        if (previewLabel != null) previewLabel.setVisible(visible);
        if (feeLabel != null) feeLabel.setVisible(visible);
        if (fromBalanceLabel != null) fromBalanceLabel.setVisible(visible);
        if (toBalanceLabel != null) toBalanceLabel.setVisible(visible);
        if (convertButton != null) convertButton.setVisible(visible);
    }

    // --- Settings ---

    private void openSettings() {
        boolean isAdmin = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            isAdmin = mc.player.hasPermissions(2);
        }
        mc.setScreen(new ExchangeSettingsScreen(this, isAdmin, npcEntityId, exchangerSkinName));
    }

    // --- Data reception from server ---

    public void receiveExchangeData(ExchangeDataPayload payload) {
        this.currencies = new ArrayList<>(payload.currencies());
        this.feePercent = payload.feePercent();
        init();
    }

    public void receiveExchangeResult(ExchangeResultPayload payload) {
        if (statusLabel == null) return;
        if (payload.success()) {
            statusLabel.setText(Component.translatable("ecocraft_core.exchange.success"));
            statusLabel.setColor(0xFF00FF00);
        } else {
            statusLabel.setText(Component.translatable("ecocraft_core.exchange.error", payload.message()));
            statusLabel.setColor(THEME.danger);
        }
    }

    public void receiveExchangerSkin(ExchangerSkinPayload payload) {
        this.exchangerSkinName = payload.skinPlayerName();
    }

    // --- Helpers ---

    private List<String> getCurrencyNames() {
        List<String> names = new ArrayList<>();
        for (var c : currencies) {
            names.add(c.name() + " (" + c.symbol() + ")");
        }
        return names;
    }

    private ExchangeDataPayload.CurrencyData getSelectedFrom() {
        int idx = fromDropdown != null ? fromDropdown.getSelectedIndex() : -1;
        if (idx >= 0 && idx < currencies.size()) return currencies.get(idx);
        return null;
    }

    private ExchangeDataPayload.CurrencyData getSelectedTo() {
        int idx = toDropdown != null ? toDropdown.getSelectedIndex() : -1;
        if (idx >= 0 && idx < currencies.size()) return currencies.get(idx);
        return null;
    }

    private Currency getSelectedFromCurrency() {
        var data = getSelectedFrom();
        if (data == null) return null;
        return buildCurrency(data);
    }

    private void onCurrencyChanged() {
        updateBalances();
        updatePreview();
        updateConvertButtonState();
    }

    private void updateBalances() {
        var from = getSelectedFrom();
        var to = getSelectedTo();
        if (from != null && fromBalanceLabel != null) {
            Currency fromC = buildCurrency(from);
            fromBalanceLabel.setText(Component.translatable("ecocraft_core.exchange.balance",
                    CurrencyFormatter.format(from.balance(), fromC) + " " + from.symbol()));
        }
        if (to != null && toBalanceLabel != null) {
            Currency toC = buildCurrency(to);
            toBalanceLabel.setText(Component.translatable("ecocraft_core.exchange.balance",
                    CurrencyFormatter.format(to.balance(), toC) + " " + to.symbol()));
        }
    }

    private void updatePreview() {
        if (previewLabel == null || feeLabel == null) return;

        var from = getSelectedFrom();
        var to = getSelectedTo();

        if (from == null || to == null || currentAmount <= 0 || from.id().equals(to.id())) {
            previewLabel.setText(Component.translatable("ecocraft_core.exchange.preview", "---"));
            return;
        }

        // Calculate preview using reference rates
        if (from.referenceRate() > 0 && to.referenceRate() > 0) {
            BigDecimal crossRate = BigDecimal.valueOf(from.referenceRate())
                    .divide(BigDecimal.valueOf(to.referenceRate()), 10, RoundingMode.HALF_UP);
            BigDecimal converted = BigDecimal.valueOf(currentAmount).multiply(crossRate);
            // Apply fee
            BigDecimal fee = BigDecimal.valueOf(feePercent).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            converted = converted.subtract(converted.multiply(fee));
            converted = converted.setScale(to.decimals(), RoundingMode.HALF_UP);

            Currency toC = buildCurrency(to);
            String formatted = CurrencyFormatter.format(converted.longValue(), toC);
            previewLabel.setText(Component.translatable("ecocraft_core.exchange.preview",
                    formatted + " " + to.symbol()));
        } else {
            previewLabel.setText(Component.translatable("ecocraft_core.exchange.preview", "---"));
        }

        feeLabel.setText(Component.translatable("ecocraft_core.exchange.fee",
                String.format("%.1f", feePercent)));
    }

    private void updateConvertButtonState() {
        var from = getSelectedFrom();
        var to = getSelectedTo();
        boolean canConvert = from != null && to != null && !from.id().equals(to.id()) && currentAmount > 0;
        if (convertButton != null) {
            convertButton.setEnabled(canConvert);
        }
    }

    private Currency buildCurrency(ExchangeDataPayload.CurrencyData data) {
        return Currency.builder(data.id(), data.name(), data.symbol())
                .decimals(data.decimals())
                .exchangeable(true)
                .referenceRate(data.referenceRate())
                .build();
    }

    // --- Actions ---

    private void onConvert() {
        var from = getSelectedFrom();
        var to = getSelectedTo();
        if (from == null || to == null || from.id().equals(to.id()) || currentAmount <= 0) return;

        if (statusLabel != null) statusLabel.setText(Component.empty());
        PacketDistributor.sendToServer(new ExchangeRequestPayload(currentAmount, from.id(), to.id()));
    }

    private void doClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

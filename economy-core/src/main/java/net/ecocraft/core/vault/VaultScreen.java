package net.ecocraft.core.vault;

import com.mojang.logging.LogUtils;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyFormatter;
import net.ecocraft.api.currency.SubUnit;
import net.ecocraft.core.network.payload.VaultDataPayload;
import net.ecocraft.core.network.payload.VaultDataPayload.VaultCurrencyData;
import net.ecocraft.core.network.payload.VaultDataPayload.VaultSubUnitData;
import net.ecocraft.core.network.payload.VaultResultPayload;
import net.ecocraft.core.network.payload.VaultWithdrawPayload;
import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Vault screen — shows all currency balances, allows withdraw of physical currencies.
 */
public class VaultScreen extends EcoScreen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Theme THEME = Theme.dark();

    private int guiWidth;
    private int guiHeight;
    private int guiLeft;
    private int guiTop;

    // Data from server
    private List<VaultCurrencyData> currencies = new ArrayList<>();

    // Widgets
    private Label statusLabel;
    private int selectedCurrencyIndex = 0;

    // Withdraw input
    private EcoCurrencyInput withdrawInput;
    private long withdrawAmount = 0;

    public VaultScreen() {
        super(Component.translatable("ecocraft.vault.title"));
    }

    @Override
    protected void init() {
        super.init();

        guiWidth = (int) (this.width * 0.65);
        guiHeight = (int) (this.height * 0.8);
        guiLeft = (this.width - guiWidth) / 2;
        guiTop = (this.height - guiHeight) / 2;

        buildUI();
    }

    private void buildUI() {
        Font font = Minecraft.getInstance().font;
        int contentLeft = guiLeft + 16;
        int contentWidth = guiWidth - 32;

        // --- Left panel: currency list ---
        int leftPanelWidth = (int) (contentWidth * 0.45);
        int rightPanelLeft = contentLeft + leftPanelWidth + 16;
        int rightPanelWidth = contentWidth - leftPanelWidth - 16;

        int y = guiTop + 34;

        // Currency list heading
        Label currencyListHeading = new Label(font, contentLeft, y,
                Component.translatable("ecocraft.vault.currencies"), THEME);
        currencyListHeading.setColor(THEME.accent);
        getTree().addChild(currencyListHeading);
        y += 14;

        // Currency balance rows
        for (int i = 0; i < currencies.size(); i++) {
            VaultCurrencyData c = currencies.get(i);
            Currency currency = buildCurrency(c);
            String formatted = CurrencyFormatter.format(c.balance(), currency);

            int rowY = y;
            final int idx = i;

            // Currency name + balance as a clickable button
            boolean isSelected = (i == selectedCurrencyIndex);
            String rowText = c.name() + " : " + formatted;

            EcoButton row;
            if (isSelected) {
                row = EcoButton.primary(THEME, Component.literal(rowText), () -> selectCurrency(idx));
            } else {
                row = EcoButton.ghost(THEME, Component.literal(rowText), () -> selectCurrency(idx));
            }
            row.setPosition(contentLeft, rowY);
            row.setSize(leftPanelWidth, 18);
            getTree().addChild(row);

            // Physical indicator
            if (c.physical() && !c.subUnits().isEmpty()) {
                // Small indicator next to the row (rendered in render())
            }

            y += 22;
        }

        if (currencies.isEmpty()) {
            Label noData = new Label(font, contentLeft, y,
                    Component.translatable("ecocraft.vault.no_currencies"), THEME);
            noData.setColor(THEME.textDim);
            getTree().addChild(noData);
        }

        // --- Right panel: selected currency details + withdraw ---
        int ry = guiTop + 34;

        if (selectedCurrencyIndex >= 0 && selectedCurrencyIndex < currencies.size()) {
            VaultCurrencyData selected = currencies.get(selectedCurrencyIndex);
            Currency currency = buildCurrency(selected);
            String formattedBalance = CurrencyFormatter.format(selected.balance(), currency);

            // Currency name
            Label nameLabel = new Label(font, rightPanelLeft, ry,
                    Component.literal(selected.name() + " (" + selected.symbol() + ")"), THEME);
            nameLabel.setColor(THEME.accent);
            getTree().addChild(nameLabel);
            ry += 14;

            // Balance
            Label balanceLabel = new Label(font, rightPanelLeft, ry,
                    Component.translatable("ecocraft.vault.balance", formattedBalance), THEME);
            balanceLabel.setColor(THEME.textWhite);
            getTree().addChild(balanceLabel);
            ry += 20;

            // Physical currency: show withdraw section
            boolean hasPhysical = selected.physical() && !selected.subUnits().isEmpty();

            if (hasPhysical) {
                // Separator
                ry += 4;

                Label withdrawHeading = new Label(font, rightPanelLeft, ry,
                        Component.translatable("ecocraft.vault.withdraw_amount"), THEME);
                withdrawHeading.setColor(THEME.accent);
                getTree().addChild(withdrawHeading);
                ry += 14;

                // Withdraw input
                withdrawInput = new EcoCurrencyInput(font, rightPanelLeft, ry, rightPanelWidth, currency, THEME);
                withdrawInput.min(0).max(selected.balance()).responder(val -> withdrawAmount = val);
                getTree().addChild(withdrawInput);
                ry += withdrawInput.getHeight() + 10;

                // Withdraw button
                EcoButton withdrawButton = EcoButton.warning(THEME,
                        Component.translatable("ecocraft.vault.withdraw"),
                        this::onWithdraw);
                withdrawButton.setPosition(rightPanelLeft, ry);
                withdrawButton.setSize(rightPanelWidth, 20);
                getTree().addChild(withdrawButton);
                ry += 28;
            } else {
                // Virtual-only currency
                ry += 8;
                Label virtualLabel = new Label(font, rightPanelLeft, ry,
                        Component.translatable("ecocraft.vault.no_physical"), THEME);
                virtualLabel.setColor(THEME.textDim);
                getTree().addChild(virtualLabel);
                ry += 20;
            }
        }

        // --- Status label ---
        statusLabel = new Label(font, contentLeft, guiTop + guiHeight - 36,
                Component.empty(), THEME);
        statusLabel.setColor(THEME.textLight);
        getTree().addChild(statusLabel);

        // --- Close button ---
        int closeWidth = 80;
        EcoButton closeButton = EcoButton.ghost(THEME,
                Component.translatable("ecocraft.vault.close"),
                this::doClose);
        closeButton.setPosition(guiLeft + (guiWidth - closeWidth) / 2, guiTop + guiHeight - 30);
        closeButton.setSize(closeWidth, 20);
        getTree().addChild(closeButton);
    }

    // --- Data reception from server ---

    public void receiveVaultData(VaultDataPayload payload) {
        this.currencies = new ArrayList<>(payload.currencies());
        // Clamp selection index
        if (selectedCurrencyIndex >= currencies.size()) {
            selectedCurrencyIndex = currencies.isEmpty() ? 0 : currencies.size() - 1;
        }
        withdrawAmount = 0;
        init();
    }

    public void receiveVaultResult(VaultResultPayload payload) {
        if (payload.success()) {
            statusLabel.setText(Component.literal(payload.message()));
            statusLabel.setColor(0xFF00FF00);
        } else {
            statusLabel.setText(Component.literal(payload.message()));
            statusLabel.setColor(THEME.danger);
        }
    }

    // --- Actions ---

    private void selectCurrency(int index) {
        selectedCurrencyIndex = index;
        withdrawAmount = 0;
        init();
    }

    private void onWithdraw() {
        if (selectedCurrencyIndex < 0 || selectedCurrencyIndex >= currencies.size()) return;
        VaultCurrencyData selected = currencies.get(selectedCurrencyIndex);
        if (withdrawAmount <= 0) return;

        statusLabel.setText(Component.empty());
        PacketDistributor.sendToServer(new VaultWithdrawPayload(selected.id(), withdrawAmount));
    }

    private void doClose() {
        Minecraft.getInstance().setScreen(null);
    }

    // --- Helpers ---

    private Currency buildCurrency(VaultCurrencyData data) {
        Currency.Builder builder = Currency.builder(data.id(), data.name(), data.symbol())
                .decimals(data.decimals());

        if (data.physical() && !data.subUnits().isEmpty()) {
            // Use the first sub-unit's itemId as the currency itemId
            builder.physical(data.subUnits().get(0).itemId());
            List<SubUnit> subUnits = new ArrayList<>();
            for (VaultSubUnitData su : data.subUnits()) {
                subUnits.add(new SubUnit(su.code(), su.name(), su.multiplier(), su.itemId(), null));
            }
            builder.subUnits(subUnits);
        }

        return builder.build();
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Background panel
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight, THEME.bgDark, THEME.border);

        // Title
        Font font = Minecraft.getInstance().font;
        String titleText = Component.translatable("ecocraft.vault.title").getString();
        int titleWidth = font.width(titleText);
        int titleX = guiLeft + (guiWidth - titleWidth) / 2;
        graphics.drawString(font, titleText, titleX, guiTop + 10, THEME.accent, false);

        // Accent separator below title
        DrawUtils.drawAccentSeparator(graphics, guiLeft + 10, guiTop + 22, guiWidth - 20, THEME);

        // Vertical separator between left and right panels
        int contentLeft = guiLeft + 16;
        int contentWidth = guiWidth - 32;
        int leftPanelWidth = (int) (contentWidth * 0.45);
        int sepX = contentLeft + leftPanelWidth + 8;
        graphics.fill(sepX, guiTop + 30, sepX + 1, guiTop + guiHeight - 42, THEME.border);

        // Re-render tree on top of background
        getTree().render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

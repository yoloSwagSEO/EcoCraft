package net.ecocraft.ah.screen;

import net.ecocraft.ah.network.payload.UpdateAHSettingsPayload;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.gui.widget.Button;
import net.ecocraft.gui.widget.NumberInput;
import net.ecocraft.gui.widget.Repeater;
import net.ecocraft.gui.widget.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class AHSettingsScreen extends Screen {

    private static final Theme THEME = Theme.dark();

    private int guiWidth, guiHeight, guiLeft, guiTop;

    private int saleRate;
    private int depositRate;
    private List<Integer> durations;

    private Slider saleRateSlider;
    private Slider depositRateSlider;
    private Repeater<Integer> durationsRepeater;

    public AHSettingsScreen(int saleRate, int depositRate, List<Integer> durations) {
        super(Component.literal("AH Settings"));
        this.saleRate = saleRate;
        this.depositRate = depositRate;
        this.durations = new ArrayList<>(durations);
    }

    @Override
    protected void init() {
        guiWidth = (int) (width * 0.9);
        guiHeight = (int) (height * 0.9);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        Font font = Minecraft.getInstance().font;
        int contentX = guiLeft + 20;
        int contentW = guiWidth - 40;

        // Back button
        addRenderableWidget(Button.builder(Component.literal("\u25C0 Retour"), this::onCancel)
                .theme(THEME).bounds(guiLeft + 4, guiTop + 4, 60, 14)
                .bgColor(THEME.accentBg).borderColor(THEME.borderAccent)
                .textColor(THEME.accent).hoverBg(THEME.accentBgDim).build());

        // Calculate Y positions
        // Title at guiTop + 8
        // Section "Taxes" title at guiTop + 30
        // Sale rate label at guiTop + 46
        int y = guiTop + 58;

        // Sale rate slider
        saleRateSlider = new Slider(font, contentX, y, contentW, 16, THEME);
        saleRateSlider.min(0).max(50).step(1).value(saleRate).suffix("%")
                .labelPosition(Slider.LabelPosition.AFTER);
        saleRateSlider.responder(val -> saleRate = val.intValue());
        addRenderableWidget(saleRateSlider);
        y += 30;

        // Deposit rate label at y
        y += 14;

        // Deposit rate slider
        depositRateSlider = new Slider(font, contentX, y, contentW, 16, THEME);
        depositRateSlider.min(0).max(20).step(1).value(depositRate).suffix("%")
                .labelPosition(Slider.LabelPosition.AFTER);
        depositRateSlider.responder(val -> depositRate = val.intValue());
        addRenderableWidget(depositRateSlider);
        y += 36;

        // Section "Durees" title at y
        y += 16;

        // Durations repeater
        int repeaterH = Math.min(160, guiTop + guiHeight - y - 40);
        durationsRepeater = new Repeater<>(contentX, y, contentW, repeaterH, THEME);
        durationsRepeater.itemFactory(() -> 24);
        durationsRepeater.rowHeight(22);
        durationsRepeater.maxItems(10);
        durationsRepeater.rowRenderer((value, ctx) -> {
            NumberInput input = new NumberInput(ctx.font(), ctx.x(), ctx.y() + 2, ctx.width(), 18, ctx.theme());
            input.min(1).max(168).step(1).showButtons(true);
            input.setValue(value);
            input.responder(newVal -> ctx.setValue(newVal.intValue()));
            ctx.addWidget(input);
        });
        durationsRepeater.values(durations);
        durationsRepeater.responder(vals -> durations = new ArrayList<>(vals));
        addRenderableWidget(durationsRepeater);

        // Footer buttons
        int footerY = guiTop + guiHeight - 30;
        int btnW = 100;
        int gap = 10;
        int totalBtnW = btnW * 2 + gap;
        int btnStartX = guiLeft + (guiWidth - totalBtnW) / 2;

        Button cancelBtn = Button.ghost(THEME, Component.literal("Annuler"), this::onCancel);
        cancelBtn.setX(btnStartX);
        cancelBtn.setY(footerY);
        cancelBtn.setWidth(btnW);
        cancelBtn.setHeight(20);
        addRenderableWidget(cancelBtn);

        Button saveBtn = Button.success(THEME, Component.literal("Sauvegarder"), this::onSave);
        saveBtn.setX(btnStartX + btnW + gap);
        saveBtn.setY(footerY);
        saveBtn.setWidth(btnW);
        saveBtn.setHeight(20);
        addRenderableWidget(saveBtn);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight, THEME.bgDarkest, THEME.borderAccent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        Font font = Minecraft.getInstance().font;
        int contentX = guiLeft + 20;
        int contentW = guiWidth - 40;

        // Title
        String title = "Configuration de l'H\u00f4tel des Ventes";
        int titleW = font.width(title);
        graphics.drawString(font, title, guiLeft + (guiWidth - titleW) / 2, guiTop + 8, THEME.accent, false);

        // Section: Taxes
        int y = guiTop + 30;
        graphics.drawString(font, "Taxes", contentX, y, THEME.textWhite, false);
        DrawUtils.drawAccentSeparator(graphics, contentX, y + 10, contentW, THEME);

        // Sale rate label
        graphics.drawString(font, "Taxe sur les ventes:", contentX, guiTop + 46, THEME.textGrey, false);

        // Deposit rate label
        int depositLabelY = guiTop + 58 + 30;
        graphics.drawString(font, "D\u00e9p\u00f4t de mise en vente:", contentX, depositLabelY, THEME.textGrey, false);

        // Section: Durees
        int durSectionY = depositLabelY + 14 + 16 + 20;
        graphics.drawString(font, "Dur\u00e9es de listing (heures)", contentX, durSectionY, THEME.textWhite, false);
        DrawUtils.drawAccentSeparator(graphics, contentX, durSectionY + 10, contentW, THEME);
    }

    private void onSave() {
        PacketDistributor.sendToServer(new UpdateAHSettingsPayload(saleRate, depositRate, durations));
        Minecraft.getInstance().setScreen(new AuctionHouseScreen());
    }

    private void onCancel() {
        Minecraft.getInstance().setScreen(new AuctionHouseScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

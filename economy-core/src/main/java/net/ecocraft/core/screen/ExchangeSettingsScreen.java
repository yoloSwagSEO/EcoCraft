package net.ecocraft.core.screen;

import net.ecocraft.core.network.payload.UpdateExchangerSkinPayload;
import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Exchange settings screen with sidebar tabs (like MailSettingsScreen / AHSettingsScreen).
 * Tab 0: General — skin config for NPC exchanger.
 */
public class ExchangeSettingsScreen extends EcoScreen {

    private static final Theme THEME = Theme.dark();
    private static final int PANEL_PADDING = 8;

    private int guiWidth, guiHeight, guiLeft, guiTop;
    private int sidebarWidth;

    private final Screen parentScreen;
    private final boolean isAdmin;
    private final int npcEntityId;
    private String skinPlayerName;
    private int selectedTab = 0;
    private final List<EcoButton> sidebarButtons = new ArrayList<>();

    public ExchangeSettingsScreen(Screen parent, boolean isAdmin, int npcEntityId, String skinPlayerName) {
        super(Component.translatable("ecocraft_core.exchange.settings_title"));
        this.parentScreen = parent;
        this.isAdmin = isAdmin;
        this.npcEntityId = npcEntityId;
        this.skinPlayerName = skinPlayerName != null ? skinPlayerName : "";
    }

    @Override
    protected void init() {
        super.init();

        guiWidth = (int) (width * 0.9);
        guiHeight = (int) (height * 0.9);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        sidebarWidth = (int) (guiWidth * 0.20);

        rebuildScreen();
    }

    private void rebuildScreen() {
        getTree().clear();
        sidebarButtons.clear();

        initSidebar();
        initRightPanel();
        initFooter();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight, THEME.bgDarkest, THEME.borderAccent);

        // Sidebar background
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, sidebarWidth, guiHeight, THEME.bgDark, THEME.border);
    }

    // --- Sidebar ---

    private void initSidebar() {
        int btnX = guiLeft + 4;
        int btnW = sidebarWidth - 8;
        int btnH = 18;
        int y = guiTop + 8;

        // Tab 0: General (always visible)
        EcoButton generalBtn = createSidebarButton(
                Component.translatable("ecocraft_core.exchange.settings_tab_general").getString(),
                0, btnX, y, btnW, btnH);
        getTree().addChild(generalBtn);
        sidebarButtons.add(generalBtn);
    }

    private EcoButton createSidebarButton(String label, int tabIndex, int x, int y, int w, int h) {
        boolean selected = tabIndex == selectedTab;
        return EcoButton.builder(Component.literal(label), () -> onTabClicked(tabIndex))
                .theme(THEME).bounds(x, y, w, h)
                .bgColor(selected ? THEME.accentBg : THEME.bgMedium)
                .borderColor(selected ? THEME.borderAccent : THEME.borderLight)
                .textColor(selected ? THEME.accent : THEME.textLight)
                .hoverBg(selected ? THEME.accentBg : THEME.bgLight)
                .build();
    }

    private void onTabClicked(int tabIndex) {
        selectedTab = tabIndex;
        rebuildScreen();
    }

    // --- Right panel ---

    private void initRightPanel() {
        int panelX = guiLeft + sidebarWidth + 4;
        int panelY = guiTop + 4;
        int panelW = guiWidth - sidebarWidth - 8;
        int panelH = guiHeight - 40; // leave room for footer

        if (selectedTab == 0) {
            buildGeneralTab(panelX, panelY, panelW, panelH);
        }
    }

    // --- General tab ---

    private void buildGeneralTab(int x, int y, int w, int h) {
        Font font = Minecraft.getInstance().font;

        boolean hasNpc = npcEntityId > 0;
        int titleBlockH = font.lineHeight + 2 + 6;
        int skinRowH = font.lineHeight + 4 + 16 + 8;

        int skinPanelH = hasNpc ? PANEL_PADDING * 2 + titleBlockH + skinRowH : 0;
        int totalContentH = skinPanelH + 20;

        ScrollPane scrollPane = new ScrollPane(x, y, w, h, THEME);
        scrollPane.setContentHeight(totalContentH);
        getTree().addChild(scrollPane);

        if (hasNpc) {
            Panel skinPanel = new Panel(0, 0, 0, 0, THEME);
            skinPanel.padding(PANEL_PADDING).titleUppercase(true).titleMarginBottom(6)
                    .separatorStyle(Panel.SeparatorStyle.NONE)
                    .title(Component.literal("\u263A Changeur"), font);

            EcoGrid grid = new EcoGrid(x, y, w, totalContentH, 0);
            grid.rowGap(6);
            scrollPane.addChild(grid);

            grid.addRow(skinPanelH).addCol(12).addChild(skinPanel);
            grid.relayout();

            // Skin input
            int pcx = skinPanel.getContentX();
            int pcy = skinPanel.getContentY();
            int pcw = skinPanel.getContentWidth();

            Label skinLabel = new Label(font, pcx, pcy,
                    Component.translatable("ecocraft_core.exchange.skin_label"), THEME);
            skinLabel.setColor(THEME.textGrey);
            scrollPane.addChild(skinLabel);

            EcoTextInput skinInput = new EcoTextInput(font, pcx, pcy + font.lineHeight + 4, (pcw - 8) / 2, 16,
                    Component.translatable("ecocraft_core.exchange.skin_placeholder"), THEME);
            skinInput.setValue(skinPlayerName);
            skinInput.responder(val -> skinPlayerName = val);
            scrollPane.addChild(skinInput);
        }
    }

    // --- Footer ---

    private void initFooter() {
        int footerY = guiTop + guiHeight - 30;
        int btnW = 100;
        int gap = 10;
        int totalBtnW = btnW * 2 + gap;
        int btnStartX = guiLeft + (guiWidth - totalBtnW) / 2;

        EcoButton closeBtn = EcoButton.ghost(THEME,
                Component.translatable("ecocraft_core.exchange.close"), this::onClose);
        closeBtn.setPosition(btnStartX, footerY);
        closeBtn.setSize(btnW, 20);
        getTree().addChild(closeBtn);

        EcoButton saveBtn = EcoButton.success(THEME,
                Component.translatable("ecocraft_core.exchange.skin_save"), this::onSave);
        saveBtn.setPosition(btnStartX + btnW + gap, footerY);
        saveBtn.setSize(btnW, 20);
        getTree().addChild(saveBtn);
    }

    private void onSave() {
        if (npcEntityId > 0) {
            String name = skinPlayerName != null ? skinPlayerName.trim() : "";
            PacketDistributor.sendToServer(new UpdateExchangerSkinPayload(npcEntityId, name));
        }
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parentScreen);
    }
}

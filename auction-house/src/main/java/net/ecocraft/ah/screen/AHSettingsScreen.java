package net.ecocraft.ah.screen;

import net.ecocraft.ah.data.AHInstance;
import net.ecocraft.ah.network.payload.*;
import net.ecocraft.gui.core.*;
import net.ecocraft.ah.screen.NotificationsTab;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Settings screen with a left sidebar (tab buttons) and right panel using EcoGrid.
 * Tab 0 = "Notifications" (all players), Tab 1 = "General" (admin, NPC config),
 * Tab 2+ = per-AH instance settings (admin only).
 */
public class AHSettingsScreen extends EcoScreen {

    private static final Theme THEME = Theme.dark();
    private static final int SECTION_GAP = 6;
    private static final int PANEL_PADDING = 8;

    private int guiWidth, guiHeight, guiLeft, guiTop;
    private int sidebarWidth;

    private final Screen parentScreen;
    private int selectedTab = 0;
    private final boolean isAdmin;
    private List<AHInstancesPayload.AHInstanceData> ahInstances;
    private final Map<String, EditedAH> editedAHs = new HashMap<>();

    // NPC state
    private final int npcEntityId;
    private String skinPlayerName;
    private String linkedAhId;

    // Delete confirmation state
    private String deleteAhId = null;
    private String deleteAhName = null;
    private boolean showDeleteConfirm = false;

    // Sidebar buttons (for label updates)
    private final List<EcoButton> sidebarButtons = new ArrayList<>();

    public AHSettingsScreen(Screen parent, int npcEntityId, String skinPlayerName,
                            String currentAhId, List<AHInstancesPayload.AHInstanceData> ahInstances,
                            boolean isAdmin) {
        super(Component.translatable("ecocraft_ah.settings.title"));
        this.parentScreen = parent;
        this.npcEntityId = npcEntityId;
        this.skinPlayerName = skinPlayerName != null ? skinPlayerName : "";
        this.linkedAhId = currentAhId != null ? currentAhId : AHInstance.DEFAULT_ID;
        this.ahInstances = new ArrayList<>(ahInstances);
        this.isAdmin = isAdmin;
    }

    // --- EditedAH inner class ---

    private static class EditedAH {
        String name;
        int saleRate;
        int depositRate;
        List<Integer> durations;
        boolean allowBuyout;
        boolean allowAuction;
        String taxRecipient;

        EditedAH(AHInstancesPayload.AHInstanceData data) {
            this.name = data.name();
            this.saleRate = data.saleRate();
            this.depositRate = data.depositRate();
            this.durations = new ArrayList<>(data.durations());
            this.allowBuyout = data.allowBuyout();
            this.allowAuction = data.allowAuction();
            this.taxRecipient = data.taxRecipient() != null ? data.taxRecipient() : "";
        }
    }

    // --- Helpers ---

    private String getAHDisplayName(AHInstancesPayload.AHInstanceData data) {
        EditedAH edited = editedAHs.get(data.id());
        return edited != null ? edited.name : data.name();
    }

    private EditedAH getOrCreateEdited(AHInstancesPayload.AHInstanceData data) {
        return editedAHs.computeIfAbsent(data.id(), k -> new EditedAH(data));
    }

    // --- Screen lifecycle ---

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

    /** Full rebuild: sidebar + right panel. */
    private void rebuildScreen() {
        getTree().clear();
        sidebarButtons.clear();

        initSidebar();
        initRightPanel();
        initFooter();
    }

    // --- Sidebar ---

    private void initSidebar() {
        int btnX = guiLeft + 4;
        int btnW = sidebarWidth - 8;
        int btnH = 18;
        int y = guiTop + 8;

        // Tab 0: "Notifications" (always visible)
        EcoButton notifBtn = createSidebarButton(
                Component.translatable("ecocraft_ah.settings.notifications").getString(), 0, btnX, y, btnW, btnH);
        getTree().addChild(notifBtn);
        sidebarButtons.add(notifBtn);
        y += btnH + 4;
        y += 4;

        if (isAdmin) {
            // Tab 1: "General" (admin only)
            EcoButton generalBtn = createSidebarButton(
                    Component.translatable("ecocraft_ah.settings.general").getString(), 1, btnX, y, btnW, btnH);
            getTree().addChild(generalBtn);
            sidebarButtons.add(generalBtn);
            y += btnH + 4;
            y += 4;

            // Tab 2+: One button per AH instance (admin only)
            for (int i = 0; i < ahInstances.size(); i++) {
                AHInstancesPayload.AHInstanceData data = ahInstances.get(i);
                String label = getAHDisplayName(data);
                EcoButton ahBtn = createSidebarButton(label, i + 2, btnX, y, btnW, btnH);
                getTree().addChild(ahBtn);
                sidebarButtons.add(ahBtn);
                y += btnH + 2;
            }

            // "+ Creer un AH" button at bottom (admin only)
            int createY = guiTop + guiHeight - 30 - 30;
            EcoButton createBtn = EcoButton.builder(
                    Component.translatable("ecocraft_ah.settings.create_ah"), this::onCreateAH)
                    .theme(THEME).bounds(btnX, createY, btnW, btnH)
                    .bgColor(THEME.successBg).borderColor(THEME.success)
                    .textColor(THEME.success).hoverBg(0xFF2A4A2A).build();
            getTree().addChild(createBtn);
        }
    }

    private EcoButton createSidebarButton(String label, int tabIndex, int x, int y, int w, int h) {
        boolean selected = tabIndex == selectedTab;
        Font font = Minecraft.getInstance().font;
        String displayLabel = DrawUtils.truncateText(font, label, w - 8);

        return EcoButton.builder(Component.literal(displayLabel), () -> onTabClicked(tabIndex))
                .theme(THEME).bounds(x, y, w, h)
                .bgColor(selected ? THEME.accentBg : THEME.bgMedium)
                .borderColor(selected ? THEME.borderAccent : THEME.borderLight)
                .textColor(selected ? THEME.accent : THEME.textLight)
                .hoverBg(selected ? THEME.accentBg : THEME.bgLight)
                .build();
    }

    private void onTabClicked(int tabIndex) {
        if (tabIndex == selectedTab) return;
        selectedTab = tabIndex;
        rebuildScreen();
    }

    // --- Right Panel ---

    private void initRightPanel() {
        if (showDeleteConfirm) {
            initDeleteConfirmPanel();
            return;
        }
        if (selectedTab == 0) {
            initNotificationsPanel();
        } else if (selectedTab == 1 && isAdmin) {
            initGeneralPanel();
        } else if (selectedTab >= 2 && isAdmin) {
            int ahIndex = selectedTab - 2;
            if (ahIndex >= 0 && ahIndex < ahInstances.size()) {
                initAHPanel(ahInstances.get(ahIndex));
            }
        }
    }

    private void initDeleteConfirmPanel() {
        int panelX = guiLeft + sidebarWidth + 10;
        int panelW = guiWidth - sidebarWidth - 20;
        int y = guiTop + 60;
        int btnW = panelW - 20;

        EcoButton transferBtn = EcoButton.builder(
                Component.translatable("ecocraft_ah.settings.delete_transfer"),
                () -> executeDeleteAH("TRANSFER_TO_DEFAULT"))
                .theme(THEME).bounds(panelX + 10, y, btnW, 22)
                .bgColor(THEME.warningBg).borderColor(THEME.warning)
                .textColor(THEME.textWhite).hoverBg(THEME.bgLight).build();
        getTree().addChild(transferBtn);
        y += 30;

        EcoButton deleteListingsBtn = EcoButton.builder(
                Component.translatable("ecocraft_ah.settings.delete_all"),
                () -> executeDeleteAH("DELETE_LISTINGS"))
                .theme(THEME).bounds(panelX + 10, y, btnW, 22)
                .bgColor(THEME.dangerBg).borderColor(THEME.danger)
                .textColor(THEME.textWhite).hoverBg(THEME.bgLight).build();
        getTree().addChild(deleteListingsBtn);
        y += 30;

        EcoButton returnItemsBtn = EcoButton.builder(
                Component.translatable("ecocraft_ah.settings.delete_return"),
                () -> executeDeleteAH("RETURN_ITEMS"))
                .theme(THEME).bounds(panelX + 10, y, btnW, 22)
                .bgColor(THEME.successBg).borderColor(THEME.success)
                .textColor(THEME.textWhite).hoverBg(THEME.bgLight).build();
        getTree().addChild(returnItemsBtn);
        y += 40;

        EcoButton cancelBtn = EcoButton.ghost(THEME,
                Component.translatable("ecocraft_ah.button.cancel"), this::cancelDelete);
        cancelBtn.setPosition(panelX + 10, y);
        cancelBtn.setSize(btnW, 20);
        getTree().addChild(cancelBtn);
    }

    // --- Notifications tab ---

    private void initNotificationsPanel() {
        Font font = Minecraft.getInstance().font;
        int panelX = guiLeft + sidebarWidth + 10;
        int panelY = guiTop + 28;
        int panelW = guiWidth - sidebarWidth - 20;
        int panelH = guiHeight - 28 - 40;

        NotificationsTab notifTab = new NotificationsTab(font, panelX, panelY, panelW, panelH);
        getTree().addChild(notifTab);
    }

    // --- General tab ---

    private void initGeneralPanel() {
        Font font = Minecraft.getInstance().font;
        int panelX = guiLeft + sidebarWidth + 10;
        int panelW = guiWidth - sidebarWidth - 20;

        if (npcEntityId == -1) {
            return;
        }

        int y = guiTop + 30;

        // Skin pseudo input
        Label skinLabel = new Label(font, panelX, y, Component.translatable("ecocraft_ah.settings.skin_label"), THEME);
        skinLabel.setColor(THEME.textGrey);
        getTree().addChild(skinLabel);
        y += font.lineHeight + 4;

        EcoTextInput skinInput = new EcoTextInput(font, panelX, y, panelW / 2, 16,
                Component.translatable("ecocraft_ah.settings.skin_placeholder"), THEME);
        skinInput.setValue(skinPlayerName);
        skinInput.responder(val -> skinPlayerName = val);
        getTree().addChild(skinInput);
        y += 26;

        // AH linking dropdown
        Label ahLabel = new Label(font, panelX, y, Component.translatable("ecocraft_ah.settings.ah_label"), THEME);
        ahLabel.setColor(THEME.textGrey);
        getTree().addChild(ahLabel);
        y += font.lineHeight + 4;

        List<String> ahNames = new ArrayList<>();
        int selectedAhIndex = 0;
        for (int i = 0; i < ahInstances.size(); i++) {
            ahNames.add(getAHDisplayName(ahInstances.get(i)));
            if (ahInstances.get(i).id().equals(linkedAhId)) {
                selectedAhIndex = i;
            }
        }

        EcoDropdown ahDropdown = new EcoDropdown(panelX, y, panelW / 2, 16, THEME);
        ahDropdown.options(ahNames).selectedIndex(selectedAhIndex);
        ahDropdown.responder(idx -> {
            if (idx >= 0 && idx < ahInstances.size()) {
                linkedAhId = ahInstances.get(idx).id();
            }
        });
        getTree().addChild(ahDropdown);
    }

    // --- AH instance tab (EcoGrid layout) ---

    private void initAHPanel(AHInstancesPayload.AHInstanceData data) {
        Font font = Minecraft.getInstance().font;
        int panelX = guiLeft + sidebarWidth + 10;
        int panelW = guiWidth - sidebarWidth - 20;
        int contentTop = guiTop + 28;
        int availableH = guiHeight - 28 - 40; // minus title and footer

        EditedAH edited = getOrCreateEdited(data);

        // --- Phase 1: Build grid structure with panels ---

        int titleBlockH = font.lineHeight + 2 + 6; // title text + separator + titleMarginBottom
        int identityH = PANEL_PADDING * 2 + titleBlockH + font.lineHeight + 4 + 16 + 2;
        int modesH = PANEL_PADDING * 2 + titleBlockH + 20 * 2 + 4;
        int taxesH = PANEL_PADDING * 2 + titleBlockH +
                (font.lineHeight + 4 + 16 + 10) + // slider row
                (font.lineHeight + 4 + 16 + 2);   // recipient row
        // Durations panel gets all remaining vertical space
        int usedH = identityH + modesH + taxesH + SECTION_GAP * 4;
        int durationsH = Math.max(availableH - usedH, PANEL_PADDING * 2 + titleBlockH + 80);
        int repeaterH = durationsH - PANEL_PADDING * 2 - titleBlockH;

        // Create panels — EcoCol auto-fills width/height, so (0,0,0,0) is fine
        Panel identityPanel = new Panel(0, 0, 0, 0, THEME);
        identityPanel.padding(PANEL_PADDING).titleUppercase(true).titleMarginBottom(6)
                .separatorStyle(Panel.SeparatorStyle.NONE)
                .title(Component.literal("\u2699 Identité"), font);

        Panel modesPanel = new Panel(0, 0, 0, 0, THEME);
        modesPanel.padding(PANEL_PADDING).titleUppercase(true).titleMarginBottom(6)
                .separatorStyle(Panel.SeparatorStyle.NONE)
                .title(Component.literal("\u2302 Modes de vente"), font);

        Panel taxesPanel = new Panel(0, 0, 0, 0, THEME);
        taxesPanel.padding(PANEL_PADDING).titleUppercase(true).titleMarginBottom(6)
                .separatorStyle(Panel.SeparatorStyle.NONE)
                .title(Component.literal("\u272A Taxes"), font);

        Panel durationsPanel = new Panel(0, 0, 0, 0, THEME);
        durationsPanel.padding(PANEL_PADDING).titleUppercase(true).titleMarginBottom(6)
                .separatorStyle(Panel.SeparatorStyle.NONE)
                .title(Component.literal("\u29D7 Durées de listing"), font);

        EcoGrid grid = new EcoGrid(panelX, contentTop, panelW, availableH, 0);
        grid.rowGap(SECTION_GAP);
        getTree().addChild(grid);

        grid.addRow(identityH).addCol(12).addChild(identityPanel);
        grid.addRow(modesH).addCol(12).addChild(modesPanel);
        grid.addRow(taxesH).addCol(12).addChild(taxesPanel);
        grid.addRow(durationsH).addCol(12).addChild(durationsPanel);
        grid.relayout();

        // --- Phase 2: Position inner widgets using panel content areas ---

        // -- Identité: name input on col-6 (50%) --
        int cx = identityPanel.getContentX();
        int cy = identityPanel.getContentY();
        int cw = identityPanel.getContentWidth();
        int halfW = cw / 2;

        Label nameLabel = new Label(font, cx, cy,
                Component.translatable("ecocraft_ah.settings.ah_name_label"), THEME);
        nameLabel.setColor(THEME.textGrey);
        getTree().addChild(nameLabel);

        EcoTextInput nameInput = new EcoTextInput(font, cx, cy + font.lineHeight + 2, halfW, 16,
                Component.translatable("ecocraft_ah.settings.ah_name_placeholder"), THEME);
        nameInput.setValue(edited.name);
        nameInput.responder(val -> {
            edited.name = val;
            rebuildSidebarLabels();
        });
        getTree().addChild(nameInput);

        // -- Modes de vente: label left + toggle right-aligned in col-4 area --
        cx = modesPanel.getContentX();
        cy = modesPanel.getContentY();
        int toggleAreaW = modesPanel.getContentWidth() / 3; // col-4 equivalent
        int toggleW = 40;

        Label buyoutLabel = new Label(font, cx, cy + 3,
                Component.translatable("ecocraft_ah.settings.allow_buyout"), THEME);
        buyoutLabel.setColor(THEME.textLight);
        getTree().addChild(buyoutLabel);

        EcoToggle buyoutToggle = new EcoToggle(cx + toggleAreaW - toggleW, cy, toggleW, 14, THEME);
        buyoutToggle.value(edited.allowBuyout);
        buyoutToggle.responder(val -> edited.allowBuyout = val);
        getTree().addChild(buyoutToggle);

        cy += 20;

        Label auctionLabel = new Label(font, cx, cy + 3,
                Component.translatable("ecocraft_ah.settings.allow_auction"), THEME);
        auctionLabel.setColor(THEME.textLight);
        getTree().addChild(auctionLabel);

        EcoToggle auctionToggle = new EcoToggle(cx + toggleAreaW - toggleW, cy, toggleW, 14, THEME);
        auctionToggle.value(edited.allowAuction);
        auctionToggle.responder(val -> edited.allowAuction = val);
        getTree().addChild(auctionToggle);

        // -- Taxes: sliders side-by-side (50/50) + recipient below --
        cx = taxesPanel.getContentX();
        cy = taxesPanel.getContentY();
        cw = taxesPanel.getContentWidth();
        halfW = (cw - 8) / 2;

        // Sale rate slider (left)
        Label saleLabel = new Label(font, cx, cy,
                Component.translatable("ecocraft_ah.settings.sale_tax_label"), THEME);
        saleLabel.setColor(THEME.textGrey);
        getTree().addChild(saleLabel);

        EcoSlider saleSlider = new EcoSlider(font, cx, cy + font.lineHeight + 4, halfW, 16, THEME);
        saleSlider.min(0).max(100).step(1).value(edited.saleRate).suffix("%")
                .labelPosition(EcoSlider.LabelPosition.AFTER);
        saleSlider.responder(val -> edited.saleRate = val.intValue());
        getTree().addChild(saleSlider);

        // Deposit slider (right)
        int rightX = cx + halfW + 8;
        Label depositLabel = new Label(font, rightX, cy,
                Component.translatable("ecocraft_ah.settings.deposit_label"), THEME);
        depositLabel.setColor(THEME.textGrey);
        getTree().addChild(depositLabel);

        EcoSlider depositSlider = new EcoSlider(font, rightX, cy + font.lineHeight + 4, halfW, 16, THEME);
        depositSlider.min(0).max(100).step(1).value(edited.depositRate).suffix("%")
                .labelPosition(EcoSlider.LabelPosition.AFTER);
        depositSlider.responder(val -> edited.depositRate = val.intValue());
        getTree().addChild(depositSlider);

        // Tax recipient (below sliders, col-6)
        cy += font.lineHeight + 4 + 16 + 8;
        Label recipientLabel = new Label(font, cx, cy,
                Component.translatable("ecocraft_ah.settings.tax_recipient_label"), THEME);
        recipientLabel.setColor(THEME.textGrey);
        getTree().addChild(recipientLabel);

        EcoTextInput taxRecipientInput = new EcoTextInput(font,
                cx, cy + font.lineHeight + 4, halfW, 16,
                Component.translatable("ecocraft_ah.settings.tax_recipient_placeholder"), THEME);
        taxRecipientInput.setValue(edited.taxRecipient);
        taxRecipientInput.responder(val -> edited.taxRecipient = val);
        getTree().addChild(taxRecipientInput);

        // -- Durées: repeater centered (col-6 offset-3) --
        cx = durationsPanel.getContentX();
        cy = durationsPanel.getContentY();
        cw = durationsPanel.getContentWidth();
        int repeaterW = cw / 2;
        int repeaterX = cx + (cw - repeaterW) / 2;

        EcoRepeater<Integer> durationsRepeater = new EcoRepeater<>(
                repeaterX, cy, repeaterW, repeaterH, THEME);
        durationsRepeater.itemFactory(() -> 24);
        durationsRepeater.rowHeight(22);
        durationsRepeater.maxItems(10);
        durationsRepeater.rowRenderer((value, ctx) -> {
            EcoNumberInput input = new EcoNumberInput(ctx.font(), ctx.x(), ctx.y() + 2, ctx.width(), 18, ctx.theme());
            input.min(1).max(168).step(1).showButtons(true);
            input.setValue(value);
            input.responder(newVal -> ctx.setValue(newVal.intValue()));
            ctx.addWidget(input);
        });
        durationsRepeater.responder(vals -> edited.durations = new ArrayList<>(vals));
        getTree().addChild(durationsRepeater);
        durationsRepeater.values(edited.durations);

        // --- Delete button (hidden for default AH) ---
        if (!data.id().equals(AHInstance.DEFAULT_ID)) {
            int deleteY = durationsPanel.getY() + durationsPanel.getHeight() + SECTION_GAP;
            int deleteW = 120;
            int deleteX = panelX + (panelW - deleteW) / 2;
            EcoButton deleteBtn = EcoButton.danger(THEME,
                    Component.translatable("ecocraft_ah.settings.delete_ah"),
                    () -> onDeleteAH(data.id(), edited.name));
            deleteBtn.setPosition(deleteX, deleteY);
            deleteBtn.setSize(deleteW, 20);
            getTree().addChild(deleteBtn);
        }
    }

    /** Update sidebar button labels without rebuilding anything. */
    private void rebuildSidebarLabels() {
        // EcoButton doesn't have setMessage, so we rebuild on tab switch
    }

    // --- Footer ---

    private void initFooter() {
        int footerY = guiTop + guiHeight - 30;
        int btnW = 100;
        int gap = 10;
        int totalBtnW = btnW * 2 + gap;
        int btnStartX = guiLeft + (guiWidth - totalBtnW) / 2;

        EcoButton footerCancelBtn = EcoButton.ghost(THEME,
                Component.translatable("ecocraft_ah.button.cancel"), this::onCancel);
        footerCancelBtn.setPosition(btnStartX, footerY);
        footerCancelBtn.setSize(btnW, 20);
        getTree().addChild(footerCancelBtn);

        EcoButton saveBtn = EcoButton.success(THEME,
                Component.translatable("ecocraft_ah.button.save"), this::onSave);
        saveBtn.setPosition(btnStartX + btnW + gap, footerY);
        saveBtn.setSize(btnW, 20);
        getTree().addChild(saveBtn);
    }

    // --- Rendering ---

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        // Main outer panel
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight, THEME.bgDarkest, THEME.borderAccent);
        // Sidebar background
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, sidebarWidth, guiHeight, THEME.bgDark, THEME.border);
        // Separator between sidebar title area and AH buttons
        int sepY = guiTop + 8 + 18 + 4;
        DrawUtils.drawSeparator(graphics, guiLeft + 4, sepY, sidebarWidth - 8, THEME.borderLight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        Font font = Minecraft.getInstance().font;
        int panelX = guiLeft + sidebarWidth + 10;
        int panelW = guiWidth - sidebarWidth - 20;

        if (showDeleteConfirm) {
            Component deleteTitle = Component.translatable("ecocraft_ah.settings.delete_title",
                    deleteAhName != null ? deleteAhName : "");
            graphics.drawString(font, deleteTitle, panelX, guiTop + 10, THEME.danger, false);
            DrawUtils.drawAccentSeparator(graphics, panelX, guiTop + 22, panelW, THEME);
            graphics.drawString(font, Component.translatable("ecocraft_ah.settings.delete_question"),
                    panelX, guiTop + 36, THEME.textLight, false);
        } else if (selectedTab == 0) {
            renderNotificationsTitle(graphics, font, panelX, panelW);
        } else if (selectedTab == 1 && isAdmin) {
            renderGeneralTitle(graphics, font, panelX, panelW);
        } else if (selectedTab >= 2 && isAdmin) {
            int ahIndex = selectedTab - 2;
            if (ahIndex >= 0 && ahIndex < ahInstances.size()) {
                renderAHTitle(graphics, font, panelX, panelW, ahInstances.get(ahIndex));
            }
        }
    }

    private void renderNotificationsTitle(GuiGraphics graphics, Font font, int panelX, int panelW) {
        int y = guiTop + 10;
        Component notifTitle = Component.translatable("ecocraft_ah.settings.notifications");
        graphics.drawString(font, notifTitle, panelX, y, THEME.accent, false);
        DrawUtils.drawAccentSeparator(graphics, panelX, y + 12, panelW, THEME);
    }

    private void renderGeneralTitle(GuiGraphics graphics, Font font, int panelX, int panelW) {
        int y = guiTop + 10;
        Component genTitle = Component.translatable("ecocraft_ah.settings.general");
        graphics.drawString(font, genTitle, panelX, y, THEME.accent, false);
        DrawUtils.drawAccentSeparator(graphics, panelX, y + 12, panelW, THEME);

        if (npcEntityId == -1) {
            Component msg = Component.translatable("ecocraft_ah.settings.npc_hint");
            int msgW = font.width(msg);
            int msgX = panelX + (panelW - msgW) / 2;
            int msgY = guiTop + guiHeight / 2 - font.lineHeight / 2;
            graphics.drawString(font, msg, msgX, msgY, THEME.textDim, false);
        }
    }

    private void renderAHTitle(GuiGraphics graphics, Font font, int panelX, int panelW,
                               AHInstancesPayload.AHInstanceData data) {
        int y = guiTop + 10;
        String title = getAHDisplayName(data);
        graphics.drawString(font, title, panelX, y, THEME.accent, false);
        DrawUtils.drawAccentSeparator(graphics, panelX, y + 12, panelW, THEME);
    }

    // --- Actions ---

    private void onCreateAH() {
        String newName = Component.translatable("ecocraft_ah.settings.new_ah_name").getString();
        String newId = UUID.randomUUID().toString();
        AHInstancesPayload.AHInstanceData newData = new AHInstancesPayload.AHInstanceData(
                newId, AHInstance.slugify(newName), newName,
                AHInstance.DEFAULT_SALE_RATE, AHInstance.DEFAULT_DEPOSIT_RATE,
                new ArrayList<>(AHInstance.DEFAULT_DURATIONS), true, true, "");
        ahInstances.add(newData);
        PacketDistributor.sendToServer(new CreateAHPayload(newName));

        selectedTab = ahInstances.size() + 1; // +1 for Notifications tab offset
        rebuildScreen();
    }

    private void onDeleteAH(String ahId, String ahName) {
        deleteAhId = ahId;
        deleteAhName = ahName;
        showDeleteConfirm = true;
        rebuildScreen();
    }

    private void executeDeleteAH(String mode) {
        if (deleteAhId == null) return;
        PacketDistributor.sendToServer(new DeleteAHPayload(deleteAhId, mode));
        ahInstances.removeIf(a -> a.id().equals(deleteAhId));
        editedAHs.remove(deleteAhId);
        deleteAhId = null;
        deleteAhName = null;
        showDeleteConfirm = false;
        selectedTab = 0;
        rebuildScreen();
    }

    private void cancelDelete() {
        deleteAhId = null;
        deleteAhName = null;
        showDeleteConfirm = false;
        rebuildScreen();
    }

    private void onSave() {
        for (var entry : editedAHs.entrySet()) {
            var edit = entry.getValue();
            PacketDistributor.sendToServer(new UpdateAHInstancePayload(
                    entry.getKey(), edit.name, edit.saleRate, edit.depositRate, edit.durations,
                    edit.allowBuyout, edit.allowAuction, edit.taxRecipient));
        }
        if (npcEntityId != -1) {
            PacketDistributor.sendToServer(new UpdateNPCSkinPayload(npcEntityId, skinPlayerName, linkedAhId));
            if (parentScreen instanceof AuctionHouseScreen ahScreen) {
                ahScreen.setCurrentAhId(linkedAhId);
                for (var inst : ahInstances) {
                    if (inst.id().equals(linkedAhId)) {
                        ahScreen.setCurrentAhName(inst.name());
                        break;
                    }
                }
                ahScreen.rebuildCurrentTab();
            }
        }
        Minecraft.getInstance().setScreen(parentScreen);
    }

    private void onCancel() {
        Minecraft.getInstance().setScreen(parentScreen);
    }
}

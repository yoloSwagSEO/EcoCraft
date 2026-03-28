package net.ecocraft.ah.screen;

import net.ecocraft.ah.data.AHInstance;
import net.ecocraft.ah.network.payload.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.gui.widget.Button;
import net.ecocraft.gui.widget.Dropdown;
import net.ecocraft.gui.widget.NumberInput;
import net.ecocraft.gui.widget.Repeater;
import net.ecocraft.gui.widget.Slider;
import net.ecocraft.gui.widget.TextInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Settings screen with a left sidebar (tab buttons) and right panel.
 * Tab 0 = "General" (NPC config), Tab 1+ = per-AH instance settings.
 */
public class AHSettingsScreen extends Screen {

    private static final Theme THEME = Theme.dark();

    private int guiWidth, guiHeight, guiLeft, guiTop;
    private int sidebarWidth;

    private final Screen parentScreen;
    private int selectedTab = 0;
    private List<AHInstancesPayload.AHInstanceData> ahInstances;
    private final Map<String, EditedAH> editedAHs = new HashMap<>();

    // NPC state
    private final int npcEntityId;
    private String skinPlayerName;
    private String linkedAhId;

    // Right panel widgets (rebuilt on tab change)
    private final List<AbstractWidget> rightPanelWidgets = new ArrayList<>();

    // Delete confirmation state
    private String deleteAhId = null;
    private String deleteAhName = null;
    private boolean showDeleteConfirm = false;

    // Sidebar buttons (persist across tab changes)
    private final List<Button> sidebarButtons = new ArrayList<>();

    public AHSettingsScreen(Screen parent, int npcEntityId, String skinPlayerName,
                            String currentAhId, List<AHInstancesPayload.AHInstanceData> ahInstances) {
        super(Component.literal("AH Settings"));
        this.parentScreen = parent;
        this.npcEntityId = npcEntityId;
        this.skinPlayerName = skinPlayerName != null ? skinPlayerName : "";
        this.linkedAhId = currentAhId != null ? currentAhId : AHInstance.DEFAULT_ID;
        this.ahInstances = new ArrayList<>(ahInstances);
    }

    // --- EditedAH inner class ---

    private static class EditedAH {
        String name;
        int saleRate;
        int depositRate;
        List<Integer> durations;

        EditedAH(AHInstancesPayload.AHInstanceData data) {
            this.name = data.name();
            this.saleRate = data.saleRate();
            this.depositRate = data.depositRate();
            this.durations = new ArrayList<>(data.durations());
        }
    }

    // --- Helpers ---

    /** Get the display name for an AH: edited name if modified, otherwise original. */
    private String getAHDisplayName(AHInstancesPayload.AHInstanceData data) {
        EditedAH edited = editedAHs.get(data.id());
        return edited != null ? edited.name : data.name();
    }

    /** Get or create EditedAH for tracking modifications. */
    private EditedAH getOrCreateEdited(AHInstancesPayload.AHInstanceData data) {
        return editedAHs.computeIfAbsent(data.id(), k -> new EditedAH(data));
    }

    // --- Screen lifecycle ---

    @Override
    protected void init() {
        guiWidth = (int) (width * 0.9);
        guiHeight = (int) (height * 0.9);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        sidebarWidth = (int) (guiWidth * 0.20);

        rebuildScreen();
    }

    /** Full rebuild: sidebar + right panel. */
    private void rebuildScreen() {
        clearWidgets();
        sidebarButtons.clear();
        rightPanelWidgets.clear();

        initSidebar();
        initRightPanel();
        initFooter();
    }

    // --- Sidebar ---

    private void initSidebar() {
        Font font = Minecraft.getInstance().font;
        int btnX = guiLeft + 4;
        int btnW = sidebarWidth - 8;
        int btnH = 18;
        int y = guiTop + 8;

        // Tab 0: "General"
        Button generalBtn = createSidebarButton("G\u00e9n\u00e9ral", 0, btnX, y, btnW, btnH);
        addRenderableWidget(generalBtn);
        sidebarButtons.add(generalBtn);
        y += btnH + 4;

        // Separator
        y += 4;

        // One button per AH instance
        for (int i = 0; i < ahInstances.size(); i++) {
            AHInstancesPayload.AHInstanceData data = ahInstances.get(i);
            String label = getAHDisplayName(data);
            Button ahBtn = createSidebarButton(label, i + 1, btnX, y, btnW, btnH);
            addRenderableWidget(ahBtn);
            sidebarButtons.add(ahBtn);
            y += btnH + 2;
        }

        // "+ Creer un AH" button at bottom
        int createY = guiTop + guiHeight - 30 - 30; // above footer
        Button createBtn = Button.builder(Component.literal("+ Cr\u00e9er un AH"), this::onCreateAH)
                .theme(THEME).bounds(btnX, createY, btnW, btnH)
                .bgColor(THEME.successBg).borderColor(THEME.success)
                .textColor(THEME.success).hoverBg(0xFF2A4A2A).build();
        addRenderableWidget(createBtn);
    }

    private Button createSidebarButton(String label, int tabIndex, int x, int y, int w, int h) {
        boolean selected = tabIndex == selectedTab;
        Font font = Minecraft.getInstance().font;
        String displayLabel = DrawUtils.truncateText(font, label, w - 8);

        return Button.builder(Component.literal(displayLabel), () -> onTabClicked(tabIndex))
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
        // Remove right panel widgets only, then rebuild everything
        rebuildScreen();
    }

    // --- Right Panel ---

    private void initRightPanel() {
        if (showDeleteConfirm) {
            initDeleteConfirmPanel();
            return;
        }
        if (selectedTab == 0) {
            initGeneralPanel();
        } else {
            int ahIndex = selectedTab - 1;
            if (ahIndex >= 0 && ahIndex < ahInstances.size()) {
                initAHPanel(ahInstances.get(ahIndex));
            }
        }
    }

    private void initDeleteConfirmPanel() {
        Consumer<AbstractWidget> adder = panelAdder();
        int panelX = guiLeft + sidebarWidth + 10;
        int panelW = guiWidth - sidebarWidth - 20;
        int y = guiTop + 60;
        int btnW = panelW - 20;

        // 3 delete mode buttons
        Button transferBtn = Button.builder(Component.literal("Transférer les listings à l'AH par défaut"),
                () -> executeDeleteAH("TRANSFER_TO_DEFAULT"))
                .theme(THEME).bounds(panelX + 10, y, btnW, 22)
                .bgColor(THEME.warningBg).borderColor(THEME.warning)
                .textColor(THEME.textWhite).hoverBg(THEME.bgLight).build();
        adder.accept(transferBtn);
        y += 30;

        Button deleteListingsBtn = Button.builder(Component.literal("Supprimer toutes les listings"),
                () -> executeDeleteAH("DELETE_LISTINGS"))
                .theme(THEME).bounds(panelX + 10, y, btnW, 22)
                .bgColor(THEME.dangerBg).borderColor(THEME.danger)
                .textColor(THEME.textWhite).hoverBg(THEME.bgLight).build();
        adder.accept(deleteListingsBtn);
        y += 30;

        Button returnItemsBtn = Button.builder(Component.literal("Rendre les items aux joueurs (parcels)"),
                () -> executeDeleteAH("RETURN_ITEMS"))
                .theme(THEME).bounds(panelX + 10, y, btnW, 22)
                .bgColor(THEME.successBg).borderColor(THEME.success)
                .textColor(THEME.textWhite).hoverBg(THEME.bgLight).build();
        adder.accept(returnItemsBtn);
        y += 40;

        Button cancelBtn = Button.ghost(THEME, Component.literal("Annuler"), this::cancelDelete);
        cancelBtn.setX(panelX + 10);
        cancelBtn.setY(y);
        cancelBtn.setWidth(btnW);
        cancelBtn.setHeight(20);
        adder.accept(cancelBtn);
    }

    private Consumer<AbstractWidget> panelAdder() {
        return w -> { addRenderableWidget(w); rightPanelWidgets.add(w); };
    }

    private Consumer<AbstractWidget> panelRemover() {
        return w -> { removeWidget(w); rightPanelWidgets.remove(w); };
    }

    // --- General tab ---

    private void initGeneralPanel() {
        Font font = Minecraft.getInstance().font;
        int panelX = guiLeft + sidebarWidth + 10;
        int panelW = guiWidth - sidebarWidth - 20;
        int y = guiTop + 30;

        if (npcEntityId == -1) {
            // No NPC context — show message
            // (message is rendered in render() method)
            return;
        }

        Consumer<AbstractWidget> adder = panelAdder();

        // Skin pseudo input
        // Label rendered in render()
        y += 14;
        TextInput skinInput = new TextInput(font, panelX, y, panelW, 16,
                Component.literal("Pseudo Minecraft..."), THEME);
        skinInput.setValue(skinPlayerName);
        skinInput.responder(val -> skinPlayerName = val);
        adder.accept(skinInput);
        y += 26;

        // AH linking dropdown
        // Label rendered in render()
        y += 14;

        List<String> ahNames = new ArrayList<>();
        int selectedAhIndex = 0;
        for (int i = 0; i < ahInstances.size(); i++) {
            ahNames.add(getAHDisplayName(ahInstances.get(i)));
            if (ahInstances.get(i).id().equals(linkedAhId)) {
                selectedAhIndex = i;
            }
        }

        Dropdown ahDropdown = new Dropdown(font, panelX, y, panelW, 16, THEME);
        ahDropdown.options(ahNames).selectedIndex(selectedAhIndex);
        ahDropdown.responder(idx -> {
            if (idx >= 0 && idx < ahInstances.size()) {
                linkedAhId = ahInstances.get(idx).id();
            }
        });
        adder.accept(ahDropdown);
    }

    // --- AH instance tab ---

    private void initAHPanel(AHInstancesPayload.AHInstanceData data) {
        Font font = Minecraft.getInstance().font;
        int panelX = guiLeft + sidebarWidth + 10;
        int panelW = guiWidth - sidebarWidth - 20;
        int y = guiTop + 30;

        Consumer<AbstractWidget> adder = panelAdder();
        Consumer<AbstractWidget> remover = panelRemover();
        EditedAH edited = getOrCreateEdited(data);

        // AH name input
        // Label rendered in render()
        y += 14;
        TextInput nameInput = new TextInput(font, panelX, y, panelW, 16,
                Component.literal("Nom..."), THEME);
        nameInput.setValue(edited.name);
        nameInput.responder(val -> {
            edited.name = val;
            // Update sidebar button label
            rebuildSidebarLabels();
        });
        adder.accept(nameInput);
        y += 30;

        // Sale rate slider
        // Label rendered in render()
        y += 14;
        Slider saleSlider = new Slider(font, panelX, y, panelW, 16, THEME);
        saleSlider.min(0).max(100).step(1).value(edited.saleRate).suffix("%")
                .labelPosition(Slider.LabelPosition.AFTER);
        saleSlider.responder(val -> edited.saleRate = val.intValue());
        adder.accept(saleSlider);
        y += 30;

        // Deposit rate slider
        // Label rendered in render()
        y += 14;
        Slider depositSlider = new Slider(font, panelX, y, panelW, 16, THEME);
        depositSlider.min(0).max(100).step(1).value(edited.depositRate).suffix("%")
                .labelPosition(Slider.LabelPosition.AFTER);
        depositSlider.responder(val -> edited.depositRate = val.intValue());
        adder.accept(depositSlider);
        y += 36;

        // Durations repeater
        // Label rendered in render()
        y += 16;

        int repeaterH = Math.min(140, guiTop + guiHeight - y - 70);
        Repeater<Integer> durationsRepeater = new Repeater<>(panelX, y, panelW, repeaterH, THEME);
        durationsRepeater.widgetAdder(adder);
        durationsRepeater.widgetRemover(remover);
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
        durationsRepeater.responder(vals -> edited.durations = new ArrayList<>(vals));
        adder.accept(durationsRepeater);
        durationsRepeater.values(edited.durations);

        y += repeaterH + 10;

        // Delete button (hidden for default AH)
        if (!data.id().equals(AHInstance.DEFAULT_ID)) {
            Button deleteBtn = Button.danger(THEME, Component.literal("Supprimer cet AH"),
                    () -> onDeleteAH(data.id(), edited.name));
            deleteBtn.setX(panelX);
            deleteBtn.setY(y);
            deleteBtn.setWidth(panelW);
            deleteBtn.setHeight(20);
            adder.accept(deleteBtn);
        }
    }

    /** Update sidebar button labels without rebuilding anything. */
    private void rebuildSidebarLabels() {
        // Just update button text — don't rebuild (would kill focus)
        for (int i = 0; i < sidebarButtons.size(); i++) {
            if (i == 0) continue; // "Général" doesn't change
            int ahIndex = i - 1;
            if (ahIndex >= 0 && ahIndex < ahInstances.size()) {
                var data = ahInstances.get(ahIndex);
                EditedAH edited = editedAHs.get(data.id());
                String label = edited != null ? edited.name : data.name();
                sidebarButtons.get(i).setMessage(Component.literal(label));
            }
        }
    }

    // --- Footer ---

    private void initFooter() {
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
            // Delete confirmation title
            String title = "Supprimer: " + (deleteAhName != null ? deleteAhName : "");
            graphics.drawString(font, title, panelX, guiTop + 10, THEME.danger, false);
            DrawUtils.drawAccentSeparator(graphics, panelX, guiTop + 22, panelW, THEME);
            graphics.drawString(font, "Que faire des listings actives ?", panelX, guiTop + 36, THEME.textLight, false);
        } else if (selectedTab == 0) {
            renderGeneralLabels(graphics, font, panelX, panelW);
        } else {
            int ahIndex = selectedTab - 1;
            if (ahIndex >= 0 && ahIndex < ahInstances.size()) {
                renderAHLabels(graphics, font, panelX, panelW, ahInstances.get(ahIndex));
            }
        }
    }

    private void renderGeneralLabels(GuiGraphics graphics, Font font, int panelX, int panelW) {
        int y = guiTop + 10;

        // Title
        String title = "G\u00e9n\u00e9ral";
        graphics.drawString(font, title, panelX, y, THEME.accent, false);
        DrawUtils.drawAccentSeparator(graphics, panelX, y + 12, panelW, THEME);

        if (npcEntityId == -1) {
            String msg = "Ouvrez les param\u00e8tres depuis un PNJ pour configurer ses options.";
            int msgW = font.width(msg);
            int msgX = panelX + (panelW - msgW) / 2;
            int msgY = guiTop + guiHeight / 2 - font.lineHeight / 2;
            graphics.drawString(font, msg, msgX, msgY, THEME.textDim, false);
            return;
        }

        y = guiTop + 30;
        graphics.drawString(font, "Pseudo du skin:", panelX, y, THEME.textGrey, false);
        y += 40;
        graphics.drawString(font, "H\u00f4tel des ventes:", panelX, y, THEME.textGrey, false);
    }

    private void renderAHLabels(GuiGraphics graphics, Font font, int panelX, int panelW,
                                 AHInstancesPayload.AHInstanceData data) {
        int y = guiTop + 10;

        // Title
        String title = getAHDisplayName(data);
        graphics.drawString(font, title, panelX, y, THEME.accent, false);
        DrawUtils.drawAccentSeparator(graphics, panelX, y + 12, panelW, THEME);

        y = guiTop + 30;
        graphics.drawString(font, "Nom de l'AH:", panelX, y, THEME.textGrey, false);

        y += 44;
        graphics.drawString(font, "Taxe sur les ventes:", panelX, y, THEME.textGrey, false);

        y += 44;
        graphics.drawString(font, "D\u00e9p\u00f4t:", panelX, y, THEME.textGrey, false);

        y += 50;
        graphics.drawString(font, "Dur\u00e9es (heures):", panelX, y, THEME.textGrey, false);
    }

    // --- Actions ---

    private void onCreateAH() {
        String newName = "Nouvel AH";
        String newId = UUID.randomUUID().toString();
        AHInstancesPayload.AHInstanceData newData = new AHInstancesPayload.AHInstanceData(
                newId, AHInstance.slugify(newName), newName,
                AHInstance.DEFAULT_SALE_RATE, AHInstance.DEFAULT_DEPOSIT_RATE,
                new ArrayList<>(AHInstance.DEFAULT_DURATIONS));
        ahInstances.add(newData);
        PacketDistributor.sendToServer(new CreateAHPayload(newName));

        // Select the new tab
        selectedTab = ahInstances.size(); // index = last AH + 1 (0 is General)
        rebuildScreen();
    }

    private void onDeleteAH(String ahId, String ahName) {
        // Show confirmation with delete mode choice
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
        // Send updates for each modified AH
        for (var entry : editedAHs.entrySet()) {
            var edit = entry.getValue();
            PacketDistributor.sendToServer(new UpdateAHInstancePayload(
                    entry.getKey(), edit.name, edit.saleRate, edit.depositRate, edit.durations));
        }
        // Send NPC skin update if NPC context
        if (npcEntityId != -1) {
            PacketDistributor.sendToServer(new UpdateNPCSkinPayload(npcEntityId, skinPlayerName, linkedAhId));
            // Update parent screen's AH context so it uses the new linked AH
            if (parentScreen instanceof AuctionHouseScreen ahScreen) {
                ahScreen.setCurrentAhId(linkedAhId);
                for (var inst : ahInstances) {
                    if (inst.id().equals(linkedAhId)) {
                        ahScreen.setCurrentAhName(inst.name());
                        break;
                    }
                }
                // Rebuild tabs to reload data with new AH
                ahScreen.rebuildCurrentTab();
            }
        }
        Minecraft.getInstance().setScreen(parentScreen);
    }

    private void onCancel() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

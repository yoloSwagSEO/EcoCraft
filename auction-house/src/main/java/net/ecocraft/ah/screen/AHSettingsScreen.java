package net.ecocraft.ah.screen;

import net.ecocraft.ah.data.AHInstance;
import net.ecocraft.ah.network.payload.*;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Settings screen with a left sidebar (tab buttons) and right panel.
 * Tab 0 = "General" (NPC config), Tab 1+ = per-AH instance settings.
 */
public class AHSettingsScreen extends EcoScreen {

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

    // Delete confirmation state
    private String deleteAhId = null;
    private String deleteAhName = null;
    private boolean showDeleteConfirm = false;

    // Sidebar buttons (for label updates)
    private final List<EcoButton> sidebarButtons = new ArrayList<>();

    public AHSettingsScreen(Screen parent, int npcEntityId, String skinPlayerName,
                            String currentAhId, List<AHInstancesPayload.AHInstanceData> ahInstances) {
        super(Component.translatable("ecocraft_ah.settings.title"));
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
        Font font = Minecraft.getInstance().font;
        int btnX = guiLeft + 4;
        int btnW = sidebarWidth - 8;
        int btnH = 18;
        int y = guiTop + 8;

        // Tab 0: "General"
        EcoButton generalBtn = createSidebarButton(Component.translatable("ecocraft_ah.settings.general").getString(), 0, btnX, y, btnW, btnH);
        getTree().addChild(generalBtn);
        sidebarButtons.add(generalBtn);
        y += btnH + 4;

        // Separator
        y += 4;

        // One button per AH instance
        for (int i = 0; i < ahInstances.size(); i++) {
            AHInstancesPayload.AHInstanceData data = ahInstances.get(i);
            String label = getAHDisplayName(data);
            EcoButton ahBtn = createSidebarButton(label, i + 1, btnX, y, btnW, btnH);
            getTree().addChild(ahBtn);
            sidebarButtons.add(ahBtn);
            y += btnH + 2;
        }

        // "+ Creer un AH" button at bottom
        int createY = guiTop + guiHeight - 30 - 30;
        EcoButton createBtn = EcoButton.builder(Component.translatable("ecocraft_ah.settings.create_ah"), this::onCreateAH)
                .theme(THEME).bounds(btnX, createY, btnW, btnH)
                .bgColor(THEME.successBg).borderColor(THEME.success)
                .textColor(THEME.success).hoverBg(0xFF2A4A2A).build();
        getTree().addChild(createBtn);
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
            initGeneralPanel();
        } else {
            int ahIndex = selectedTab - 1;
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

        EcoButton transferBtn = EcoButton.builder(Component.translatable("ecocraft_ah.settings.delete_transfer"),
                () -> executeDeleteAH("TRANSFER_TO_DEFAULT"))
                .theme(THEME).bounds(panelX + 10, y, btnW, 22)
                .bgColor(THEME.warningBg).borderColor(THEME.warning)
                .textColor(THEME.textWhite).hoverBg(THEME.bgLight).build();
        getTree().addChild(transferBtn);
        y += 30;

        EcoButton deleteListingsBtn = EcoButton.builder(Component.translatable("ecocraft_ah.settings.delete_all"),
                () -> executeDeleteAH("DELETE_LISTINGS"))
                .theme(THEME).bounds(panelX + 10, y, btnW, 22)
                .bgColor(THEME.dangerBg).borderColor(THEME.danger)
                .textColor(THEME.textWhite).hoverBg(THEME.bgLight).build();
        getTree().addChild(deleteListingsBtn);
        y += 30;

        EcoButton returnItemsBtn = EcoButton.builder(Component.translatable("ecocraft_ah.settings.delete_return"),
                () -> executeDeleteAH("RETURN_ITEMS"))
                .theme(THEME).bounds(panelX + 10, y, btnW, 22)
                .bgColor(THEME.successBg).borderColor(THEME.success)
                .textColor(THEME.textWhite).hoverBg(THEME.bgLight).build();
        getTree().addChild(returnItemsBtn);
        y += 40;

        EcoButton cancelBtn = EcoButton.ghost(THEME, Component.translatable("ecocraft_ah.button.cancel"), this::cancelDelete);
        cancelBtn.setPosition(panelX + 10, y);
        cancelBtn.setSize(btnW, 20);
        getTree().addChild(cancelBtn);
    }

    // --- General tab ---

    private void initGeneralPanel() {
        Font font = Minecraft.getInstance().font;
        int panelX = guiLeft + sidebarWidth + 10;
        int panelW = guiWidth - sidebarWidth - 20;
        int y = guiTop + 30;

        if (npcEntityId == -1) {
            return;
        }

        // Skin pseudo input
        y += 14;
        EcoTextInput skinInput = new EcoTextInput(font, panelX, y, panelW, 16,
                Component.translatable("ecocraft_ah.settings.skin_placeholder"), THEME);
        skinInput.setValue(skinPlayerName);
        skinInput.responder(val -> skinPlayerName = val);
        getTree().addChild(skinInput);
        y += 26;

        // AH linking dropdown
        y += 14;

        List<String> ahNames = new ArrayList<>();
        int selectedAhIndex = 0;
        for (int i = 0; i < ahInstances.size(); i++) {
            ahNames.add(getAHDisplayName(ahInstances.get(i)));
            if (ahInstances.get(i).id().equals(linkedAhId)) {
                selectedAhIndex = i;
            }
        }

        EcoDropdown ahDropdown = new EcoDropdown(panelX, y, panelW, 16, THEME);
        ahDropdown.options(ahNames).selectedIndex(selectedAhIndex);
        ahDropdown.responder(idx -> {
            if (idx >= 0 && idx < ahInstances.size()) {
                linkedAhId = ahInstances.get(idx).id();
            }
        });
        getTree().addChild(ahDropdown);
    }

    // --- AH instance tab ---

    private void initAHPanel(AHInstancesPayload.AHInstanceData data) {
        Font font = Minecraft.getInstance().font;
        int panelX = guiLeft + sidebarWidth + 10;
        int panelW = guiWidth - sidebarWidth - 20;
        int y = guiTop + 30;

        EditedAH edited = getOrCreateEdited(data);

        // AH name input
        y += 14;
        EcoTextInput nameInput = new EcoTextInput(font, panelX, y, panelW, 16,
                Component.translatable("ecocraft_ah.settings.ah_name_placeholder"), THEME);
        nameInput.setValue(edited.name);
        nameInput.responder(val -> {
            edited.name = val;
            rebuildSidebarLabels();
        });
        getTree().addChild(nameInput);
        y += 30;

        // Listing type toggles
        y += 14;
        // "Achat immédiat" toggle
        EcoToggle buyoutToggle = new EcoToggle(panelX + panelW - 44, y, 40, 14, THEME);
        buyoutToggle.value(edited.allowBuyout);
        buyoutToggle.responder(val -> edited.allowBuyout = val);
        getTree().addChild(buyoutToggle);
        y += 20;

        // "Enchères" toggle
        EcoToggle auctionToggle = new EcoToggle(panelX + panelW - 44, y, 40, 14, THEME);
        auctionToggle.value(edited.allowAuction);
        auctionToggle.responder(val -> edited.allowAuction = val);
        getTree().addChild(auctionToggle);
        y += 24;

        // Tax recipient input
        y += 14;
        EcoTextInput taxRecipientInput = new EcoTextInput(font, panelX, y, panelW, 16,
                Component.translatable("ecocraft_ah.settings.tax_recipient_placeholder"), THEME);
        taxRecipientInput.setValue(edited.taxRecipient);
        taxRecipientInput.responder(val -> edited.taxRecipient = val);
        getTree().addChild(taxRecipientInput);
        y += 24;

        // Sale rate slider
        y += 14;
        EcoSlider saleSlider = new EcoSlider(font, panelX, y, panelW, 16, THEME);
        saleSlider.min(0).max(100).step(1).value(edited.saleRate).suffix("%")
                .labelPosition(EcoSlider.LabelPosition.AFTER);
        saleSlider.responder(val -> edited.saleRate = val.intValue());
        getTree().addChild(saleSlider);
        y += 30;

        // Deposit rate slider
        y += 14;
        EcoSlider depositSlider = new EcoSlider(font, panelX, y, panelW, 16, THEME);
        depositSlider.min(0).max(100).step(1).value(edited.depositRate).suffix("%")
                .labelPosition(EcoSlider.LabelPosition.AFTER);
        depositSlider.responder(val -> edited.depositRate = val.intValue());
        getTree().addChild(depositSlider);
        y += 36;

        // Durations repeater
        y += 16;

        int repeaterH = Math.min(140, guiTop + guiHeight - y - 70);
        EcoRepeater<Integer> durationsRepeater = new EcoRepeater<>(panelX, y, panelW, repeaterH, THEME);
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

        y += repeaterH + 10;

        // Delete button (hidden for default AH)
        if (!data.id().equals(AHInstance.DEFAULT_ID)) {
            EcoButton deleteBtn = EcoButton.danger(THEME, Component.translatable("ecocraft_ah.settings.delete_ah"),
                    () -> onDeleteAH(data.id(), edited.name));
            deleteBtn.setPosition(panelX, y);
            deleteBtn.setSize(panelW, 20);
            getTree().addChild(deleteBtn);
        }
    }

    /** Update sidebar button labels without rebuilding anything. */
    private void rebuildSidebarLabels() {
        for (int i = 0; i < sidebarButtons.size(); i++) {
            if (i == 0) continue; // "General" doesn't change
            int ahIndex = i - 1;
            if (ahIndex >= 0 && ahIndex < ahInstances.size()) {
                var data = ahInstances.get(ahIndex);
                EditedAH edited = editedAHs.get(data.id());
                // EcoButton doesn't have setMessage, so we rebuild on tab switch
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

        EcoButton footerCancelBtn = EcoButton.ghost(THEME, Component.translatable("ecocraft_ah.button.cancel"), this::onCancel);
        footerCancelBtn.setPosition(btnStartX, footerY);
        footerCancelBtn.setSize(btnW, 20);
        getTree().addChild(footerCancelBtn);

        EcoButton saveBtn = EcoButton.success(THEME, Component.translatable("ecocraft_ah.button.save"), this::onSave);
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
            Component deleteTitle = Component.translatable("ecocraft_ah.settings.delete_title", deleteAhName != null ? deleteAhName : "");
            graphics.drawString(font, deleteTitle, panelX, guiTop + 10, THEME.danger, false);
            DrawUtils.drawAccentSeparator(graphics, panelX, guiTop + 22, panelW, THEME);
            graphics.drawString(font, Component.translatable("ecocraft_ah.settings.delete_question"), panelX, guiTop + 36, THEME.textLight, false);
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

        Component genTitle = Component.translatable("ecocraft_ah.settings.general");
        graphics.drawString(font, genTitle, panelX, y, THEME.accent, false);
        DrawUtils.drawAccentSeparator(graphics, panelX, y + 12, panelW, THEME);

        if (npcEntityId == -1) {
            Component msg = Component.translatable("ecocraft_ah.settings.npc_hint");
            int msgW = font.width(msg);
            int msgX = panelX + (panelW - msgW) / 2;
            int msgY = guiTop + guiHeight / 2 - font.lineHeight / 2;
            graphics.drawString(font, msg, msgX, msgY, THEME.textDim, false);
            return;
        }

        y = guiTop + 30;
        graphics.drawString(font, Component.translatable("ecocraft_ah.settings.skin_label"), panelX, y, THEME.textGrey, false);
        y += 40;
        graphics.drawString(font, Component.translatable("ecocraft_ah.settings.ah_label"), panelX, y, THEME.textGrey, false);
    }

    private void renderAHLabels(GuiGraphics graphics, Font font, int panelX, int panelW,
                                 AHInstancesPayload.AHInstanceData data) {
        int y = guiTop + 10;

        String title = getAHDisplayName(data);
        graphics.drawString(font, title, panelX, y, THEME.accent, false);
        DrawUtils.drawAccentSeparator(graphics, panelX, y + 12, panelW, THEME);

        y = guiTop + 30;
        graphics.drawString(font, Component.translatable("ecocraft_ah.settings.ah_name_label"), panelX, y, THEME.textGrey, false);

        y += 44;
        graphics.drawString(font, Component.translatable("ecocraft_ah.settings.allow_buyout"), panelX, y + 3, THEME.textGrey, false);

        y += 20;
        graphics.drawString(font, Component.translatable("ecocraft_ah.settings.allow_auction"), panelX, y + 3, THEME.textGrey, false);

        y += 24;
        graphics.drawString(font, Component.translatable("ecocraft_ah.settings.tax_recipient_label"), panelX, y + 3, THEME.textGrey, false);

        y += 52;
        graphics.drawString(font, Component.translatable("ecocraft_ah.settings.sale_tax_label"), panelX, y, THEME.textGrey, false);

        y += 44;
        graphics.drawString(font, Component.translatable("ecocraft_ah.settings.deposit_label"), panelX, y, THEME.textGrey, false);

        y += 50;
        graphics.drawString(font, Component.translatable("ecocraft_ah.settings.durations_label"), panelX, y, THEME.textGrey, false);
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

        selectedTab = ahInstances.size();
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

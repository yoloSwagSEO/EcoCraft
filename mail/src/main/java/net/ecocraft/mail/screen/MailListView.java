package net.ecocraft.mail.screen;

import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.mail.network.payload.CollectMailPayload;
import net.ecocraft.mail.network.payload.DeleteDraftPayload;
import net.ecocraft.mail.network.payload.DeleteMailPayload;
import net.ecocraft.mail.network.payload.DraftsResponsePayload;
import net.ecocraft.mail.network.payload.MailListResponsePayload.MailSummary;
import net.ecocraft.mail.network.payload.MarkReadPayload;
import net.ecocraft.mail.network.payload.RequestDraftsPayload;
import net.ecocraft.mail.network.payload.RequestSentMailsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Mail list view: compact header with inline stats, action buttons, and scrollable mail rows.
 */
public class MailListView extends BaseWidget {

    private static final Theme THEME = MailboxScreen.THEME;
    private static final int HEADER_HEIGHT = 68;
    private static final int TAB_HEIGHT = 18;
    private static final int ROW_HEIGHT = 34;
    private static final int ROW_GAP = 1;
    private static final int INDICATOR_WIDTH = 3;

    private final MailboxScreen screen;

    // Tab system
    enum Tab { INBOX, SENT, DRAFTS }
    private Tab activeTab = Tab.INBOX;
    private EcoButton tabInboxButton;
    private EcoButton tabSentButton;
    private EcoButton tabDraftsButton;

    // Action buttons
    private EcoButton collectAllButton;
    private EcoButton newMailButton;

    // Filter buttons
    private EcoButton filterAllButton;
    private EcoButton filterUnreadButton;
    private EcoButton filterAttachmentsButton;
    private EcoButton filterCodButton;

    // Search input
    private EcoTextInput searchInput;
    private String searchText = "";

    // Active filter
    private enum Filter { ALL, UNREAD, ATTACHMENTS, COD }
    private Filter activeFilter = Filter.ALL;

    // Mail list scroll
    private ScrollPane scrollPane;

    // Current mail data
    private List<MailSummary> mails = List.of();

    // Drafts data
    private List<DraftsResponsePayload.DraftEntry> draftsList = List.of();

    // Stats line (rendered as text, not cards)
    private String statsLine = "";

    public MailListView(MailboxScreen screen, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.screen = screen;
        buildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();

        // Header separator line
        int sepY = y + HEADER_HEIGHT - 1;
        graphics.fill(x, sepY, x + w, sepY + 1, THEME.border);

        // Stats line (below tabs)
        if (!statsLine.isEmpty()) {
            graphics.drawString(font, statsLine, x + 4, y + 24, THEME.textDim, false);
        }
    }

    private void buildWidgets() {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();

        // --- Tab buttons (top row) ---
        int tabY = y + 4;
        int tabBtnH = 16;
        int tabX = x + 4;
        int tabGap = 3;

        tabInboxButton = createTabButton(font, Component.translatable("ecocraft_mail.tab.inbox"), Tab.INBOX, tabX, tabY, tabBtnH);
        addChild(tabInboxButton);
        tabX += tabInboxButton.getWidth() + tabGap;

        tabSentButton = createTabButton(font, Component.translatable("ecocraft_mail.tab.sent"), Tab.SENT, tabX, tabY, tabBtnH);
        addChild(tabSentButton);
        tabX += tabSentButton.getWidth() + tabGap;

        tabDraftsButton = createTabButton(font, Component.translatable("ecocraft_mail.tab.drafts"), Tab.DRAFTS, tabX, tabY, tabBtnH);
        addChild(tabDraftsButton);

        // --- Header: Action buttons (right-aligned, always) ---
        int btnH = 16;
        int btnY = y + 4;
        int rightEdge = x + w - 4;

        // New mail button (always rightmost)
        int newMailW = font.width(Component.translatable("ecocraft_mail.button.new_mail").getString()) + 12;
        newMailButton = EcoButton.primary(THEME, Component.translatable("ecocraft_mail.button.new_mail"), () -> screen.showComposeView());
        newMailButton.setBounds(rightEdge - newMailW, btnY, newMailW, btnH);
        addChild(newMailButton);
        rightEdge -= (newMailW + 4);

        // Collect all button (before new mail)
        int collectW = font.width(Component.translatable("ecocraft_mail.button.collect_all").getString()) + 12;
        collectAllButton = EcoButton.success(THEME, Component.translatable("ecocraft_mail.button.collect_all"), this::onCollectAll);
        collectAllButton.setBounds(rightEdge - collectW, btnY, collectW, btnH);
        addChild(collectAllButton);

        // --- Stats line + Search + Filters (row 2-3) ---
        int row2Y = y + 24;

        // Search input
        int searchY = row2Y + font.lineHeight + 4;
        int searchW = (int) (w * 0.35);
        searchInput = new EcoTextInput(font, x + 4, searchY, searchW, 14,
                Component.translatable("ecocraft_mail.search.placeholder"), THEME);
        searchInput.setMaxLength(64);
        searchInput.responder(text -> {
            this.searchText = text;
            rebuildMailRows();
        });
        addChild(searchInput);

        // Filter buttons (right of search)
        int filterBtnH = 14;
        int filterX = x + 4 + searchW + 6;
        int filterBtnGap = 2;

        filterAllButton = createFilterButton(font, Component.translatable("ecocraft_mail.filter.all"), Filter.ALL, filterX, searchY, filterBtnH);
        addChild(filterAllButton);
        filterX += filterAllButton.getWidth() + filterBtnGap;

        filterUnreadButton = createFilterButton(font, Component.translatable("ecocraft_mail.filter.unread"), Filter.UNREAD, filterX, searchY, filterBtnH);
        addChild(filterUnreadButton);
        filterX += filterUnreadButton.getWidth() + filterBtnGap;

        filterAttachmentsButton = createFilterButton(font, Component.translatable("ecocraft_mail.filter.attachments"), Filter.ATTACHMENTS, filterX, searchY, filterBtnH);
        addChild(filterAttachmentsButton);
        filterX += filterAttachmentsButton.getWidth() + filterBtnGap;

        filterCodButton = createFilterButton(font, Component.translatable("ecocraft_mail.filter.cod"), Filter.COD, filterX, searchY, filterBtnH);
        addChild(filterCodButton);

        // --- ScrollPane for mail rows ---
        int scrollY = y + HEADER_HEIGHT + 2;
        int scrollH = getHeight() - HEADER_HEIGHT - 2;
        scrollPane = new ScrollPane(x, scrollY, w, scrollH, THEME);
        addChild(scrollPane);
    }

    private EcoButton createTabButton(Font font, Component label, Tab tab, int x, int y, int h) {
        boolean selected = activeTab == tab;
        int btnW = font.width(label.getString()) + 12;
        return EcoButton.builder(label, () -> switchTab(tab))
                .theme(THEME).bounds(x, y, btnW, h)
                .bgColor(selected ? THEME.accentBg : THEME.bgMedium)
                .borderColor(selected ? THEME.borderAccent : THEME.borderLight)
                .textColor(selected ? THEME.accent : THEME.textLight)
                .hoverBg(selected ? THEME.accentBg : THEME.bgLight)
                .build();
    }

    private EcoButton createFilterButton(Font font, Component label, Filter filter, int x, int y, int h) {
        boolean selected = activeFilter == filter;
        int btnW = font.width(label.getString()) + 8;
        return EcoButton.builder(label, () -> setFilter(filter))
                .theme(THEME).bounds(x, y, btnW, h)
                .bgColor(selected ? THEME.accentBg : THEME.bgDark)
                .borderColor(selected ? THEME.borderAccent : THEME.border)
                .textColor(selected ? THEME.accent : THEME.textGrey)
                .hoverBg(selected ? THEME.accentBg : THEME.bgMedium)
                .build();
    }

    private void switchTab(Tab tab) {
        this.activeTab = tab;
        if (tab == Tab.DRAFTS) {
            PacketDistributor.sendToServer(new RequestDraftsPayload());
        } else if (tab == Tab.SENT) {
            PacketDistributor.sendToServer(new RequestSentMailsPayload());
        }
        // Rebuild all widgets to update tab button styles
        rebuildAll();
    }

    private void rebuildAll() {
        for (WidgetNode child : new ArrayList<>(getChildren())) {
            removeChild(child);
        }
        buildWidgets();
        if (activeTab == Tab.INBOX) {
            rebuildMailRows();
            updateStats();
        } else {
            statsLine = "";
            rebuildMailRows();
        }
        // Show/hide inbox-specific controls
        boolean isInbox = activeTab == Tab.INBOX;
        collectAllButton.setVisible(isInbox);
        filterAllButton.setVisible(isInbox);
        filterUnreadButton.setVisible(isInbox);
        filterAttachmentsButton.setVisible(isInbox);
        filterCodButton.setVisible(isInbox);
        searchInput.setVisible(isInbox);
    }

    public void onReceiveSentMails(List<MailSummary> sentMails) {
        screen.sentMails = sentMails;
        if (activeTab == Tab.SENT) {
            rebuildMailRows();
        }
    }

    public void onReceiveDrafts(List<DraftsResponsePayload.DraftEntry> drafts) {
        this.draftsList = drafts;
        if (activeTab == Tab.DRAFTS) {
            rebuildMailRows();
        }
    }

    private void onCollectAll() {
        PacketDistributor.sendToServer(new CollectMailPayload("ALL"));
    }

    /**
     * Called when server sends the mail list response.
     */
    public void onReceiveMailList(List<MailSummary> mailList) {
        // Sort: unread first, then by date descending
        List<MailSummary> sorted = new ArrayList<>(mailList);
        sorted.sort(Comparator
                .comparing(MailSummary::read)
                .thenComparing(Comparator.comparingLong(MailSummary::createdAt).reversed()));
        this.mails = sorted;

        rebuildMailRows();
        updateStats();
    }

    private void updateStats() {
        int itemCount = 0;
        long goldTotal = 0;
        for (MailSummary mail : mails) {
            if (!mail.collected() && !mail.hasCOD()) {
                if (mail.hasItems()) itemCount++;
                if (mail.hasCurrency()) goldTotal += mail.currencyAmount();
            }
        }
        int total = mails.size();
        int unread = (int) mails.stream().filter(m -> !m.read()).count();

        StringBuilder sb = new StringBuilder();
        sb.append(total).append(" mail(s)");
        if (unread > 0) {
            sb.append(" \u2022 ").append(unread).append(" non lu(s)");
        }
        if (itemCount > 0) {
            sb.append(" \u2022 ").append(itemCount).append(" item(s)");
        }
        if (goldTotal > 0) {
            sb.append(" \u2022 ").append(goldTotal).append(" ").append(screen.currencySymbol);
        }
        this.statsLine = sb.toString();
    }

    private void setFilter(Filter filter) {
        this.activeFilter = filter;
        rebuildAll();
    }

    private List<MailSummary> getFilteredMails() {
        String search = searchText.toLowerCase();
        List<MailSummary> filtered = new ArrayList<>();
        for (MailSummary mail : mails) {
            // Apply category filter
            boolean passesFilter = switch (activeFilter) {
                case ALL -> true;
                case UNREAD -> !mail.read();
                case ATTACHMENTS -> mail.hasItems() || mail.hasCurrency();
                case COD -> mail.hasCOD();
            };
            if (!passesFilter) continue;

            // Apply search filter
            if (!search.isEmpty()) {
                boolean matchesSearch = mail.subject().toLowerCase().contains(search)
                        || mail.senderName().toLowerCase().contains(search);
                if (!matchesSearch) continue;
            }

            filtered.add(mail);
        }
        return filtered;
    }

    private void rebuildMailRows() {
        for (WidgetNode child : new ArrayList<>(scrollPane.getChildren())) {
            scrollPane.removeChild(child);
        }

        Font font = Minecraft.getInstance().font;
        int rowX = scrollPane.getX();
        int rowW = scrollPane.getWidth() - 8; // scrollbar space
        int currentY = scrollPane.getY();

        switch (activeTab) {
            case INBOX -> {
                List<MailSummary> filtered = getFilteredMails();
                for (MailSummary mail : filtered) {
                    MailRowWidget row = new MailRowWidget(mail, rowX, currentY, rowW, ROW_HEIGHT, font, false);
                    scrollPane.addChild(row);
                    currentY += ROW_HEIGHT + ROW_GAP;
                }
                scrollPane.setContentHeight(filtered.size() * (ROW_HEIGHT + ROW_GAP));
            }
            case SENT -> {
                List<MailSummary> sentMails = screen.sentMails;
                for (MailSummary mail : sentMails) {
                    MailRowWidget row = new MailRowWidget(mail, rowX, currentY, rowW, ROW_HEIGHT, font, true);
                    scrollPane.addChild(row);
                    currentY += ROW_HEIGHT + ROW_GAP;
                }
                scrollPane.setContentHeight(sentMails.size() * (ROW_HEIGHT + ROW_GAP));
            }
            case DRAFTS -> {
                for (DraftsResponsePayload.DraftEntry draft : draftsList) {
                    DraftRowWidget row = new DraftRowWidget(draft, rowX, currentY, rowW, ROW_HEIGHT, font);
                    scrollPane.addChild(row);
                    currentY += ROW_HEIGHT + ROW_GAP;
                }
                scrollPane.setContentHeight(draftsList.size() * (ROW_HEIGHT + ROW_GAP));
            }
        }
    }

    // --- Mail Row Widget ---

    private class MailRowWidget extends BaseWidget {

        private final MailSummary mail;
        private final Font font;
        private final boolean sentView;

        private EcoButton collectButton;
        private EcoButton deleteButton;
        private int buttonsWidth; // total width of buttons area

        MailRowWidget(MailSummary mail, int x, int y, int w, int h, Font font, boolean sentView) {
            super(x, y, w, h);
            this.mail = mail;
            this.font = font;
            this.sentView = sentView;

            int btnW = 55;
            int btnH = 13;
            int btnY = y + h - btnH - 4; // bottom-right aligned
            int rightEdge = x + w;
            buttonsWidth = 0;

            if (!sentView) {
                boolean hasAttachments = mail.hasItems() || mail.hasCurrency();
                boolean canCollect = hasAttachments && !mail.collected() && !mail.hasCOD();
                boolean canDelete = mail.read() && (!hasAttachments || mail.collected() || mail.returned());

                if (canDelete) {
                    deleteButton = EcoButton.danger(THEME, Component.translatable("ecocraft_mail.button.delete"), () -> onDelete(mail.id()));
                    deleteButton.setBounds(rightEdge - btnW - 2, btnY, btnW, btnH);
                    addChild(deleteButton);
                    rightEdge -= (btnW + 4);
                    buttonsWidth += btnW + 4;
                }

                if (canCollect) {
                    collectButton = EcoButton.success(THEME, Component.translatable("ecocraft_mail.button.collect"), () -> onCollect(mail.id()));
                    collectButton.setBounds(rightEdge - btnW - 2, btnY, btnW, btnH);
                    addChild(collectButton);
                    buttonsWidth += btnW + 4;
                }
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) return;

            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            // Row background
            boolean hovered = containsPoint(mouseX, mouseY);
            int bgColor;
            if (hovered) {
                bgColor = THEME.bgLight;
            } else if (!mail.read()) {
                bgColor = 0xFF1A2A3A; // subtle blue-ish for unread
            } else {
                bgColor = THEME.bgMedium;
            }
            graphics.fill(x, y, x + w, y + h, bgColor);

            // Left color indicator (thicker, full height)
            int indicatorColor = mail.read() ? THEME.textDim : THEME.success;
            graphics.fill(x, y, x + INDICATOR_WIDTH, y + h, indicatorColor);

            int textX = x + INDICATOR_WIDTH + 6;
            int availW = w - INDICATOR_WIDTH - 6 - buttonsWidth - 8;

            // --- Line 1: Subject (left) + Date (right) ---
            int lineY1 = y + 4;
            int subjectColor = mail.read() ? THEME.textGrey : THEME.textWhite;

            String dateStr = formatDate(mail.createdAt());
            int dateW = font.width(dateStr);
            int subjectMaxW = availW - dateW - 8;

            String subject = DrawUtils.truncateText(font, mail.subject(), subjectMaxW);
            graphics.drawString(font, subject, textX, lineY1, subjectColor, false);
            graphics.drawString(font, dateStr, x + w - dateW - buttonsWidth - 6, lineY1, THEME.textDim, false);

            // --- Line 2: Sender/Recipient (left) + Tags (right, before buttons) ---
            int lineY2 = y + 4 + font.lineHeight + 3;
            String personLabel = sentView
                    ? Component.translatable("ecocraft_mail.list.to", mail.senderName()).getString()
                    : Component.translatable("ecocraft_mail.list.from", mail.senderName()).getString();
            graphics.drawString(font, personLabel, textX, lineY2, THEME.textDim, false);

            // Tags right-aligned on line 2, before buttons
            int tagRightEdge = x + w - buttonsWidth - 6;
            if (mail.hasCOD()) {
                String codTag = Component.translatable("ecocraft_mail.list.tag_cod").getString();
                int codW = font.width(codTag);
                tagRightEdge -= codW;
                graphics.drawString(font, codTag, tagRightEdge, lineY2, THEME.warning, false);
                tagRightEdge -= 4;
            }
            if (mail.hasCurrency()) {
                String goldTag = Component.translatable("ecocraft_mail.list.tag_gold", mail.currencyAmount(), screen.currencySymbol).getString();
                int goldW = font.width(goldTag);
                tagRightEdge -= goldW;
                graphics.drawString(font, goldTag, tagRightEdge, lineY2, THEME.accent, false);
                tagRightEdge -= 4;
            }
            if (mail.hasItems()) {
                String itemsTag = Component.translatable("ecocraft_mail.list.tag_items").getString();
                int itemsW = font.width(itemsTag);
                tagRightEdge -= itemsW;
                graphics.drawString(font, itemsTag, tagRightEdge, lineY2, THEME.info, false);
            }
        }

        @Override
        public boolean onMouseClicked(double mouseX, double mouseY, int button) {
            if (!containsPoint(mouseX, mouseY)) return false;

            // Dispatch to child buttons first
            if (collectButton != null && collectButton.containsPoint(mouseX, mouseY)) {
                return collectButton.onMouseClicked(mouseX, mouseY, button);
            }
            if (deleteButton != null && deleteButton.containsPoint(mouseX, mouseY)) {
                return deleteButton.onMouseClicked(mouseX, mouseY, button);
            }

            // Shift+Click: quick collect if possible, otherwise just mark as read
            if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                boolean hasAttachments = mail.hasItems() || mail.hasCurrency();
                boolean canCollect = hasAttachments && !mail.collected() && !mail.hasCOD();
                if (canCollect) {
                    onCollect(mail.id());
                } else {
                    onMarkRead(mail.id());
                }
                return true;
            }

            // Normal click -> show detail
            screen.showDetailView(mail.id());
            return true;
        }
    }

    // --- Draft Row Widget ---

    private class DraftRowWidget extends BaseWidget {

        private final DraftsResponsePayload.DraftEntry draft;
        private final Font font;
        private EcoButton deleteButton;
        private int buttonsWidth;

        DraftRowWidget(DraftsResponsePayload.DraftEntry draft, int x, int y, int w, int h, Font font) {
            super(x, y, w, h);
            this.draft = draft;
            this.font = font;

            int btnW = 55;
            int btnH = 13;
            int btnY = y + h - btnH - 4;
            int rightEdge = x + w;
            buttonsWidth = 0;

            deleteButton = EcoButton.danger(THEME, Component.translatable("ecocraft_mail.button.delete"), () -> onDeleteDraft(draft.id()));
            deleteButton.setBounds(rightEdge - btnW - 2, btnY, btnW, btnH);
            addChild(deleteButton);
            buttonsWidth = btnW + 4;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) return;

            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            boolean hovered = containsPoint(mouseX, mouseY);
            int bgColor = hovered ? THEME.bgLight : THEME.bgMedium;
            graphics.fill(x, y, x + w, y + h, bgColor);

            // Left indicator (draft = warning/orange)
            graphics.fill(x, y, x + INDICATOR_WIDTH, y + h, THEME.warning);

            int textX = x + INDICATOR_WIDTH + 6;
            int availW = w - INDICATOR_WIDTH - 6 - buttonsWidth - 8;

            // Line 1: Subject + Date
            int lineY1 = y + 4;
            String dateStr = formatDate(draft.createdAt());
            int dateW = font.width(dateStr);
            int subjectMaxW = availW - dateW - 8;

            String subject = draft.subject().isEmpty() ? "(sans objet)" : DrawUtils.truncateText(font, draft.subject(), subjectMaxW);
            graphics.drawString(font, subject, textX, lineY1, THEME.textWhite, false);
            graphics.drawString(font, dateStr, x + w - dateW - buttonsWidth - 6, lineY1, THEME.textDim, false);

            // Line 2: Recipient
            int lineY2 = y + 4 + font.lineHeight + 3;
            String recipientText = draft.recipient().isEmpty() ? "(pas de destinataire)" :
                    Component.translatable("ecocraft_mail.list.to", draft.recipient()).getString();
            graphics.drawString(font, recipientText, textX, lineY2, THEME.textDim, false);
        }

        @Override
        public boolean onMouseClicked(double mouseX, double mouseY, int button) {
            if (!containsPoint(mouseX, mouseY)) return false;

            if (deleteButton != null && deleteButton.containsPoint(mouseX, mouseY)) {
                return deleteButton.onMouseClicked(mouseX, mouseY, button);
            }

            // Click on draft -> open compose with draft data
            screen.showComposeViewFromDraft(draft);
            return true;
        }
    }

    private void onDeleteDraft(String draftId) {
        PacketDistributor.sendToServer(new DeleteDraftPayload(draftId));
        draftsList = draftsList.stream().filter(d -> !d.id().equals(draftId)).toList();
        rebuildMailRows();
    }

    private void onCollect(String mailId) {
        PacketDistributor.sendToServer(new CollectMailPayload(mailId));
    }

    private void onMarkRead(String mailId) {
        PacketDistributor.sendToServer(new MarkReadPayload(mailId));
    }

    private void onDelete(String mailId) {
        PacketDistributor.sendToServer(new DeleteMailPayload(mailId));
        mails = mails.stream().filter(m -> !m.id().equals(mailId)).toList();
        rebuildMailRows();
        updateStats();
    }

    private static String formatDate(long epochMs) {
        if (epochMs <= 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM HH:mm");
        return sdf.format(new Date(epochMs));
    }
}

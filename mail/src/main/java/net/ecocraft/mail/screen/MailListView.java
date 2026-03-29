package net.ecocraft.mail.screen;

import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.mail.network.payload.CollectMailPayload;
import net.ecocraft.mail.network.payload.DeleteMailPayload;
import net.ecocraft.mail.network.payload.MailListResponsePayload.MailSummary;
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
 * Mail list view: header with stats, action buttons, and scrollable mail rows.
 */
public class MailListView extends BaseWidget {

    private static final Theme THEME = MailboxScreen.THEME;
    private static final int HEADER_HEIGHT = 50;
    private static final int ROW_HEIGHT = 36;
    private static final int ROW_GAP = 2;

    private final MailboxScreen screen;

    // Header widgets
    private EcoStatCard itemsToCollectCard;
    private EcoStatCard goldToCollectCard;

    // Action buttons
    private EcoButton collectAllButton;
    private EcoButton newMailButton;

    // Mail list scroll
    private ScrollPane scrollPane;

    // Current mail data
    private List<MailSummary> mails = List.of();

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        // Background drawn by parent panel; nothing extra needed at this level
    }

    public MailListView(MailboxScreen screen, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.screen = screen;
        buildWidgets();
    }

    private void buildWidgets() {
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();

        // --- Header panel ---
        Panel headerPanel = new Panel(x, y, w, HEADER_HEIGHT, THEME);
        headerPanel.padding(6);
        headerPanel.title(Component.translatable("ecocraft_mail.screen.header"), font);
        headerPanel.titleUppercase(false);
        addChild(headerPanel);

        // Stat cards
        int cardW = 120;
        int cardH = 28;
        int cardY = y + 22;

        itemsToCollectCard = new EcoStatCard(x + 8, cardY, cardW, cardH,
                Component.translatable("ecocraft_mail.stat.items"), Component.literal("0"), THEME.textLight, THEME);
        headerPanel.addChild(itemsToCollectCard);

        goldToCollectCard = new EcoStatCard(x + 8 + cardW + 8, cardY, cardW, cardH,
                Component.translatable("ecocraft_mail.stat.gold"), Component.literal("0 G"), THEME.accent, THEME);
        headerPanel.addChild(goldToCollectCard);

        // Buttons (top-right of header)
        int btnY = y + 8;
        int btnH = 18;

        newMailButton = EcoButton.primary(THEME, Component.translatable("ecocraft_mail.button.new_mail"), () -> screen.showComposeView());
        newMailButton.setBounds(x + w - 200, btnY, 90, btnH);
        headerPanel.addChild(newMailButton);

        collectAllButton = EcoButton.success(THEME, Component.translatable("ecocraft_mail.button.collect_all"), this::onCollectAll);
        collectAllButton.setBounds(x + w - 105, btnY, 95, btnH);
        headerPanel.addChild(collectAllButton);

        // --- ScrollPane for mail rows ---
        int scrollY = y + HEADER_HEIGHT + 4;
        int scrollH = getHeight() - HEADER_HEIGHT - 4;
        scrollPane = new ScrollPane(x, scrollY, w, scrollH, THEME);
        addChild(scrollPane);
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
        itemsToCollectCard.setValue(Component.translatable("ecocraft_mail.stat.items_value", itemCount), THEME.textLight);
        goldToCollectCard.setValue(Component.translatable("ecocraft_mail.stat.gold_value", goldTotal), THEME.accent);
    }

    private void rebuildMailRows() {
        // Clear existing rows
        for (WidgetNode child : new ArrayList<>(scrollPane.getChildren())) {
            scrollPane.removeChild(child);
        }

        Font font = Minecraft.getInstance().font;
        int rowX = scrollPane.getX();
        int rowW = scrollPane.getWidth() - 10; // scrollbar space
        int currentY = scrollPane.getY();

        for (MailSummary mail : mails) {
            MailRowWidget row = new MailRowWidget(mail, rowX, currentY, rowW, ROW_HEIGHT, font);
            scrollPane.addChild(row);
            currentY += ROW_HEIGHT + ROW_GAP;
        }

        scrollPane.setContentHeight(mails.size() * (ROW_HEIGHT + ROW_GAP));
        scrollPane.setScrollOffset(0);
    }

    // --- Mail Row Widget ---

    private class MailRowWidget extends BaseWidget {

        private final MailSummary mail;
        private final Font font;
        private double lastMouseX, lastMouseY;

        // Buttons inside the row
        private EcoButton collectButton;
        private EcoButton deleteButton;

        MailRowWidget(MailSummary mail, int x, int y, int w, int h, Font font) {
            super(x, y, w, h);
            this.mail = mail;
            this.font = font;

            int btnW = 60;
            int btnH = 14;
            int btnY = y + (h - btnH) / 2;
            int rightEdge = x + w;

            boolean hasAttachments = mail.hasItems() || mail.hasCurrency();
            boolean canCollect = hasAttachments && !mail.collected() && !mail.hasCOD();
            boolean canDelete = mail.read() && (!hasAttachments || mail.collected());

            if (canDelete) {
                deleteButton = EcoButton.danger(THEME, Component.translatable("ecocraft_mail.button.delete"), () -> onDelete(mail.id()));
                deleteButton.setBounds(rightEdge - btnW - 4, btnY, btnW, btnH);
                addChild(deleteButton);
                rightEdge -= (btnW + 8);
            }

            if (canCollect) {
                collectButton = EcoButton.success(THEME, Component.translatable("ecocraft_mail.button.collect"), () -> onCollect(mail.id()));
                collectButton.setBounds(rightEdge - btnW - 4, btnY, btnW, btnH);
                addChild(collectButton);
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!isVisible()) return;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;

            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();

            // Row background
            boolean hovered = containsPoint(mouseX, mouseY);
            int bgColor = hovered ? THEME.bgLight : (mail.read() ? THEME.bgMedium : THEME.bgDark);
            DrawUtils.drawPanel(graphics, x, y, w, h, bgColor, THEME.border);

            // Envelope indicator (left side)
            int indicatorColor = mail.read() ? THEME.textDim : THEME.success;
            graphics.fill(x + 2, y + 2, x + 6, y + h - 2, indicatorColor);

            // Subject
            int textX = x + 12;
            int subjectColor = mail.read() ? THEME.textGrey : THEME.textWhite;
            String subject = DrawUtils.truncateText(font, mail.subject(), w - 180);
            graphics.drawString(font, subject, textX, y + 5, subjectColor, false);

            // Sender name (below subject)
            String sender = Component.translatable("ecocraft_mail.list.from", mail.senderName()).getString();
            graphics.drawString(font, sender, textX, y + 17, THEME.textDim, false);

            // Date (right side, above buttons area)
            String dateStr = formatDate(mail.createdAt());
            int dateW = font.width(dateStr);
            graphics.drawString(font, dateStr, x + w - dateW - 70, y + 5, THEME.textDim, false);

            // Attachment tags (right side)
            int tagX = x + w - 200;
            int tagY = y + 17;
            if (mail.hasItems()) {
                String itemsTag = Component.translatable("ecocraft_mail.list.tag_items").getString();
                graphics.drawString(font, itemsTag, tagX, tagY, THEME.info, false);
                tagX += font.width(itemsTag) + 4;
            }
            if (mail.hasCurrency()) {
                String goldTag = Component.translatable("ecocraft_mail.list.tag_gold", mail.currencyAmount()).getString();
                graphics.drawString(font, goldTag, tagX, tagY, THEME.accent, false);
                tagX += font.width(goldTag) + 4;
            }
            if (mail.hasCOD()) {
                String codTag = Component.translatable("ecocraft_mail.list.tag_cod").getString();
                graphics.drawString(font, codTag, tagX, tagY, THEME.warning, false);
            }
        }

        @Override
        public boolean onMouseClicked(double mouseX, double mouseY, int button) {
            if (!containsPoint(mouseX, mouseY)) return false;

            // Check if click is on a button first (buttons handle themselves via tree)
            if (collectButton != null && collectButton.containsPoint(mouseX, mouseY)) {
                return false; // let button handle it
            }
            if (deleteButton != null && deleteButton.containsPoint(mouseX, mouseY)) {
                return false; // let button handle it
            }

            // Click on the row itself -> show detail
            screen.showDetailView(mail.id());
            return true;
        }
    }

    private void onCollect(String mailId) {
        PacketDistributor.sendToServer(new CollectMailPayload(mailId));
    }

    private void onDelete(String mailId) {
        PacketDistributor.sendToServer(new DeleteMailPayload(mailId));
        // Optimistically remove from list
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

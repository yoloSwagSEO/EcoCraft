package net.ecocraft.mail.screen;

import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.mail.network.payload.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Detail view for a single mail: subject, sender, body, attachments, COD, action buttons.
 */
public class MailDetailView extends BaseWidget {

    private static final Theme THEME = MailboxScreen.THEME;

    private final MailboxScreen screen;
    private String currentMailId = "";

    // Cached detail data
    private MailDetailResponsePayload detail;

    public MailDetailView(MailboxScreen screen, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.screen = screen;
    }

    /**
     * Request mail detail from server.
     */
    public void requestDetail(String mailId) {
        this.currentMailId = mailId;
        PacketDistributor.sendToServer(new RequestMailDetailPayload(mailId));
    }

    /**
     * Called when server responds with mail detail.
     */
    public void onReceiveMailDetail(MailDetailResponsePayload payload) {
        this.detail = payload;
        this.currentMailId = payload.id();
        rebuildContent();
    }

    private void rebuildContent() {
        // Clear existing children
        for (WidgetNode child : new ArrayList<>(getChildren())) {
            removeChild(child);
        }

        if (detail == null) return;

        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int padding = 8;
        int innerX = x + padding;
        int innerW = w - padding * 2;

        int currentY = y + padding;

        // --- Back button (top-left) ---
        EcoButton backButton = EcoButton.ghost(THEME, Component.translatable("ecocraft_mail.button.back"), () -> screen.showListView());
        backButton.setBounds(innerX, currentY, 70, 16);
        addChild(backButton);

        // --- Subject (large text, top-center) ---
        Label subjectLabel = new Label(font, innerX + 80, currentY, innerW - 80,
                Component.literal(detail.subject()), THEME);
        subjectLabel.setColor(THEME.textWhite);
        addChild(subjectLabel);

        currentY += 22;

        // --- Sender + date ---
        Label senderLabel = new Label(font, innerX, currentY, Component.translatable("ecocraft_mail.detail.from", detail.senderName()), THEME);
        senderLabel.setColor(THEME.textGrey);
        addChild(senderLabel);

        String dateText = formatDate(detail.createdAt());
        Label dateLabel = new Label(font, innerX + innerW - font.width(dateText), currentY,
                Component.literal(dateText), THEME);
        dateLabel.setColor(THEME.textDim);
        addChild(dateLabel);

        currentY += 16;

        // --- Separator ---
        // We draw it in render() instead of adding a widget

        currentY += 4;

        // --- Body ---
        String body = detail.body();
        int bodyAreaHeight;
        boolean hasAttachments = !detail.items().isEmpty() || detail.currencyAmount() > 0;
        boolean hasCOD = detail.codAmount() > 0;
        int attachmentAreaHeight = hasAttachments ? 60 : 0;
        int codAreaHeight = hasCOD ? 40 : 0;
        int buttonAreaHeight = 24;
        int bottomReserved = attachmentAreaHeight + codAreaHeight + buttonAreaHeight + padding * 2;

        bodyAreaHeight = h - (currentY - y) - bottomReserved;
        if (bodyAreaHeight < 30) bodyAreaHeight = 30;

        if (body != null && !body.isEmpty()) {
            ScrollPane bodyScroll = new ScrollPane(innerX, currentY, innerW, bodyAreaHeight, THEME);

            // Split body into lines and create labels
            String[] lines = body.split("\n");
            int lineY = currentY;
            for (String line : lines) {
                // Word-wrap long lines
                List<String> wrapped = wrapText(font, line, innerW - 12);
                for (String wrappedLine : wrapped) {
                    Label lineLabel = new Label(font, innerX + 4, lineY,
                            Component.literal(wrappedLine), THEME);
                    lineLabel.setColor(THEME.textLight);
                    bodyScroll.addChild(lineLabel);
                    lineY += font.lineHeight + 2;
                }
            }
            bodyScroll.setContentHeight(lineY - currentY + 4);
            addChild(bodyScroll);
        }

        currentY += bodyAreaHeight + 4;

        // --- Attachment panel ---
        if (hasAttachments) {
            Panel attachPanel = new Panel(innerX, currentY, innerW, attachmentAreaHeight - 4, THEME);
            attachPanel.padding(4);
            attachPanel.bgColor(THEME.bgMedium);
            addChild(attachPanel);

            int slotX = innerX + 8;
            int slotY = currentY + 6;
            int slotSize = 24;

            // Item slots
            for (MailDetailResponsePayload.ItemEntry item : detail.items()) {
                EcoItemSlot slot = new EcoItemSlot(slotX, slotY, slotSize, THEME);
                ItemStack stack = itemFromId(item.itemId(), item.itemNbt());
                if (!stack.isEmpty()) {
                    stack.setCount(item.quantity());
                }
                slot.setItem(stack);
                attachPanel.addChild(slot);
                slotX += slotSize + 4;
            }

            // Currency label
            if (detail.currencyAmount() > 0) {
                Label currLabel = new Label(font, slotX + 8, slotY + 6,
                        Component.translatable("ecocraft_mail.detail.currency", detail.currencyAmount(), screen.currencySymbol), THEME);
                currLabel.setColor(THEME.accent);
                attachPanel.addChild(currLabel);
            }

            currentY += attachmentAreaHeight;
        }

        // --- COD banner ---
        if (hasCOD) {
            Panel codPanel = new Panel(innerX, currentY, innerW, codAreaHeight - 4, THEME);
            codPanel.padding(4);
            codPanel.bgColor(THEME.warningBg);
            codPanel.borderColor(THEME.warning);
            addChild(codPanel);

            Label codLabel = new Label(font, innerX + 8, currentY + 6,
                    Component.translatable("ecocraft_mail.detail.cod_banner", detail.codAmount(), screen.currencySymbol), THEME);
            codLabel.setColor(THEME.warning);
            codPanel.addChild(codLabel);

            if (!detail.collected() && !detail.returned()) {
                // Pay & Collect button
                EcoButton payButton = EcoButton.success(THEME,
                        Component.translatable("ecocraft_mail.button.pay_collect"), this::onPayCOD);
                payButton.setBounds(innerX + innerW - 230, currentY + 6, 110, 18);
                codPanel.addChild(payButton);

                // Return button
                EcoButton returnButton = EcoButton.warning(THEME,
                        Component.translatable("ecocraft_mail.button.return_cod"), this::onReturnCOD);
                returnButton.setBounds(innerX + innerW - 110, currentY + 6, 80, 18);
                codPanel.addChild(returnButton);
            }

            currentY += codAreaHeight;
        }

        // --- Action buttons (bottom) ---
        int btnY = currentY + 4;
        int btnH = 18;
        int btnX = innerX;

        // Collect button (non-COD attachments, not yet collected)
        if (hasAttachments && !detail.collected() && !hasCOD) {
            EcoButton collectBtn = EcoButton.success(THEME,
                    Component.translatable("ecocraft_mail.button.collect"), this::onCollect);
            collectBtn.setBounds(btnX, btnY, 80, btnH);
            addChild(collectBtn);
            btnX += 88;
        }

        // Delete button (if eligible)
        boolean canDelete = detail.read() && (!hasAttachments || detail.collected() || detail.returned());
        if (canDelete) {
            EcoButton deleteBtn = EcoButton.danger(THEME,
                    Component.translatable("ecocraft_mail.button.delete"), this::onDelete);
            deleteBtn.setBounds(btnX, btnY, 80, btnH);
            addChild(deleteBtn);
            btnX += 88;
        }

        // Reply button
        EcoButton replyBtn = EcoButton.primary(THEME,
                Component.translatable("ecocraft_mail.button.reply"), this::onReply);
        replyBtn.setBounds(btnX, btnY, 80, btnH);
        addChild(replyBtn);
        btnX += 88;

        // Forward button
        EcoButton forwardBtn = EcoButton.ghost(THEME,
                Component.translatable("ecocraft_mail.button.forward"), this::onForward);
        forwardBtn.setBounds(btnX, btnY, 80, btnH);
        addChild(forwardBtn);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        // Draw background
        DrawUtils.drawPanel(graphics, getX(), getY(), getWidth(), getHeight(), THEME.bgDark, THEME.border);

        // Draw separator under sender/date line
        if (detail != null) {
            int sepY = getY() + 8 + 22 + 16;
            DrawUtils.drawSeparator(graphics, getX() + 8, sepY, getWidth() - 16, THEME.borderLight);
        }
    }

    // --- Actions ---

    private void onCollect() {
        PacketDistributor.sendToServer(new CollectMailPayload(currentMailId));
    }

    private void onDelete() {
        PacketDistributor.sendToServer(new DeleteMailPayload(currentMailId));
        screen.showListView();
    }

    private void onPayCOD() {
        PacketDistributor.sendToServer(new PayCODPayload(currentMailId));
    }

    private void onReturnCOD() {
        PacketDistributor.sendToServer(new ReturnCODPayload(currentMailId));
        screen.showListView();
    }

    private void onReply() {
        if (detail != null) {
            screen.showComposeViewWithReply(detail.senderName(), detail.subject());
        }
    }

    private void onForward() {
        if (detail != null) {
            screen.showComposeViewWithForward(detail.subject(), detail.body());
        }
    }

    // --- Helpers ---

    private static String formatDate(long epochMs) {
        if (epochMs <= 0) return "";
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        return sdf.format(new Date(epochMs));
    }

    static ItemStack itemFromId(String itemId, String itemNbt) {
        // If NBT data is available, try to deserialize the full ItemStack from it
        if (itemNbt != null && !itemNbt.isEmpty()) {
            try {
                var mc = Minecraft.getInstance();
                if (mc.level != null) {
                    var tag = net.minecraft.nbt.TagParser.parseTag(itemNbt);
                    var result = ItemStack.OPTIONAL_CODEC.parse(
                            mc.level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag
                    );
                    var stack = result.result();
                    if (stack.isPresent() && !stack.get().isEmpty()) {
                        return stack.get();
                    }
                }
            } catch (Exception e) { /* fall through to simple lookup */ }
        }
        try {
            ResourceLocation rl = ResourceLocation.parse(itemId);
            var item = BuiltInRegistries.ITEM.get(rl);
            if (item != null) return new ItemStack(item);
        } catch (Exception e) { /* ignore */ }
        return ItemStack.EMPTY;
    }

    /**
     * Word-wrap text to fit within maxWidth pixels.
     */
    static List<String> wrapText(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }

        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.isEmpty()) {
                current.append(word);
            } else {
                String test = current + " " + word;
                if (font.width(test) > maxWidth) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current.append(" ").append(word);
                }
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }

        return lines;
    }
}

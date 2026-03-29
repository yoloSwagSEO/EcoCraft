package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record MailDetailResponsePayload(
        String id,
        String senderUuid,
        String senderName,
        String subject,
        String body,
        List<ItemEntry> items,
        long currencyAmount,
        String currencyId,
        long codAmount,
        String codCurrencyId,
        boolean read,
        boolean collected,
        boolean indestructible,
        boolean returned,
        long createdAt,
        long availableAt,
        long expiresAt
) implements CustomPacketPayload {

    public record ItemEntry(String itemId, String itemName, String itemNbt, int quantity) {}

    public static final StreamCodec<ByteBuf, ItemEntry> ITEM_ENTRY_CODEC = new StreamCodec<>() {
        @Override
        public ItemEntry decode(ByteBuf buf) {
            String itemId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String itemName = ByteBufCodecs.STRING_UTF8.decode(buf);
            String itemNbt = ByteBufCodecs.STRING_UTF8.decode(buf);
            int quantity = ByteBufCodecs.VAR_INT.decode(buf);
            return new ItemEntry(itemId, itemName, itemNbt, quantity);
        }

        @Override
        public void encode(ByteBuf buf, ItemEntry e) {
            ByteBufCodecs.STRING_UTF8.encode(buf, e.itemId());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.itemName());
            ByteBufCodecs.STRING_UTF8.encode(buf, e.itemNbt() != null ? e.itemNbt() : "");
            ByteBufCodecs.VAR_INT.encode(buf, e.quantity());
        }
    };

    public static final CustomPacketPayload.Type<MailDetailResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "mail_detail_response"));

    public static final StreamCodec<ByteBuf, MailDetailResponsePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MailDetailResponsePayload decode(ByteBuf buf) {
            String id = ByteBufCodecs.STRING_UTF8.decode(buf);
            String senderUuid = ByteBufCodecs.STRING_UTF8.decode(buf);
            String senderName = ByteBufCodecs.STRING_UTF8.decode(buf);
            String subject = ByteBufCodecs.STRING_UTF8.decode(buf);
            String body = ByteBufCodecs.STRING_UTF8.decode(buf);
            List<ItemEntry> items = ITEM_ENTRY_CODEC.apply(ByteBufCodecs.list()).decode(buf);
            long currencyAmount = ByteBufCodecs.VAR_LONG.decode(buf);
            String currencyId = ByteBufCodecs.STRING_UTF8.decode(buf);
            long codAmount = ByteBufCodecs.VAR_LONG.decode(buf);
            String codCurrencyId = ByteBufCodecs.STRING_UTF8.decode(buf);
            boolean read = ByteBufCodecs.BOOL.decode(buf);
            boolean collected = ByteBufCodecs.BOOL.decode(buf);
            boolean indestructible = ByteBufCodecs.BOOL.decode(buf);
            boolean returned = ByteBufCodecs.BOOL.decode(buf);
            long createdAt = ByteBufCodecs.VAR_LONG.decode(buf);
            long availableAt = ByteBufCodecs.VAR_LONG.decode(buf);
            long expiresAt = ByteBufCodecs.VAR_LONG.decode(buf);
            return new MailDetailResponsePayload(id, senderUuid, senderName, subject, body, items,
                    currencyAmount, currencyId, codAmount, codCurrencyId,
                    read, collected, indestructible, returned, createdAt, availableAt, expiresAt);
        }

        @Override
        public void encode(ByteBuf buf, MailDetailResponsePayload p) {
            ByteBufCodecs.STRING_UTF8.encode(buf, p.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.senderUuid() != null ? p.senderUuid() : "");
            ByteBufCodecs.STRING_UTF8.encode(buf, p.senderName());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.subject());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.body());
            ITEM_ENTRY_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.items());
            ByteBufCodecs.VAR_LONG.encode(buf, p.currencyAmount());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.currencyId() != null ? p.currencyId() : "");
            ByteBufCodecs.VAR_LONG.encode(buf, p.codAmount());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.codCurrencyId() != null ? p.codCurrencyId() : "");
            ByteBufCodecs.BOOL.encode(buf, p.read());
            ByteBufCodecs.BOOL.encode(buf, p.collected());
            ByteBufCodecs.BOOL.encode(buf, p.indestructible());
            ByteBufCodecs.BOOL.encode(buf, p.returned());
            ByteBufCodecs.VAR_LONG.encode(buf, p.createdAt());
            ByteBufCodecs.VAR_LONG.encode(buf, p.availableAt());
            ByteBufCodecs.VAR_LONG.encode(buf, p.expiresAt());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

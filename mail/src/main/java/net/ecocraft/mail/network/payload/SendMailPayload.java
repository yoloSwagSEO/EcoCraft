package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * C->S: send a new mail with optional item/currency/COD attachments.
 * Items are specified by inventory slot indices.
 */
public record SendMailPayload(
        String recipientName,
        String subject,
        String body,
        List<Integer> inventorySlots,
        long currencyAmount,
        long codAmount,
        boolean readReceipt
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SendMailPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "send_mail"));

    public static final StreamCodec<ByteBuf, SendMailPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SendMailPayload decode(ByteBuf buf) {
            String recipientName = ByteBufCodecs.STRING_UTF8.decode(buf);
            String subject = ByteBufCodecs.STRING_UTF8.decode(buf);
            String body = ByteBufCodecs.STRING_UTF8.decode(buf);
            List<Integer> inventorySlots = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(buf);
            long currencyAmount = ByteBufCodecs.VAR_LONG.decode(buf);
            long codAmount = ByteBufCodecs.VAR_LONG.decode(buf);
            boolean readReceipt = ByteBufCodecs.BOOL.decode(buf);
            return new SendMailPayload(recipientName, subject, body, inventorySlots, currencyAmount, codAmount, readReceipt);
        }

        @Override
        public void encode(ByteBuf buf, SendMailPayload p) {
            ByteBufCodecs.STRING_UTF8.encode(buf, p.recipientName());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.subject());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.body());
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buf, p.inventorySlots());
            ByteBufCodecs.VAR_LONG.encode(buf, p.currencyAmount());
            ByteBufCodecs.VAR_LONG.encode(buf, p.codAmount());
            ByteBufCodecs.BOOL.encode(buf, p.readReceipt());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

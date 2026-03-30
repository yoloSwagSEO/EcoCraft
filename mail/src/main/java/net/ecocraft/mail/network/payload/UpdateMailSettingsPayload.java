package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server payload to update mail settings (admin only).
 */
public record UpdateMailSettingsPayload(
        boolean allowPlayerMail,
        boolean allowItemAttachments,
        boolean allowCurrencyAttachments,
        boolean allowCOD,
        boolean allowMailboxCraft,
        int maxItemAttachments,
        int mailExpiryDays,
        long sendCost,
        int codFeePercent,
        boolean allowReadReceipt,
        long readReceiptCost,
        long sendCostPerItem
) implements CustomPacketPayload {

    public static final Type<UpdateMailSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "update_mail_settings"));

    public static final StreamCodec<ByteBuf, UpdateMailSettingsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public UpdateMailSettingsPayload decode(ByteBuf buf) {
            boolean allowPlayerMail = ByteBufCodecs.BOOL.decode(buf);
            boolean allowItemAttachments = ByteBufCodecs.BOOL.decode(buf);
            boolean allowCurrencyAttachments = ByteBufCodecs.BOOL.decode(buf);
            boolean allowCOD = ByteBufCodecs.BOOL.decode(buf);
            boolean allowMailboxCraft = ByteBufCodecs.BOOL.decode(buf);
            int maxItemAttachments = ByteBufCodecs.VAR_INT.decode(buf);
            int mailExpiryDays = ByteBufCodecs.VAR_INT.decode(buf);
            long sendCost = ByteBufCodecs.VAR_LONG.decode(buf);
            int codFeePercent = ByteBufCodecs.VAR_INT.decode(buf);
            boolean allowReadReceipt = ByteBufCodecs.BOOL.decode(buf);
            long readReceiptCost = ByteBufCodecs.VAR_LONG.decode(buf);
            long sendCostPerItem = ByteBufCodecs.VAR_LONG.decode(buf);
            return new UpdateMailSettingsPayload(allowPlayerMail, allowItemAttachments,
                    allowCurrencyAttachments, allowCOD, allowMailboxCraft,
                    maxItemAttachments, mailExpiryDays, sendCost, codFeePercent,
                    allowReadReceipt, readReceiptCost, sendCostPerItem);
        }

        @Override
        public void encode(ByteBuf buf, UpdateMailSettingsPayload payload) {
            ByteBufCodecs.BOOL.encode(buf, payload.allowPlayerMail());
            ByteBufCodecs.BOOL.encode(buf, payload.allowItemAttachments());
            ByteBufCodecs.BOOL.encode(buf, payload.allowCurrencyAttachments());
            ByteBufCodecs.BOOL.encode(buf, payload.allowCOD());
            ByteBufCodecs.BOOL.encode(buf, payload.allowMailboxCraft());
            ByteBufCodecs.VAR_INT.encode(buf, payload.maxItemAttachments());
            ByteBufCodecs.VAR_INT.encode(buf, payload.mailExpiryDays());
            ByteBufCodecs.VAR_LONG.encode(buf, payload.sendCost());
            ByteBufCodecs.VAR_INT.encode(buf, payload.codFeePercent());
            ByteBufCodecs.BOOL.encode(buf, payload.allowReadReceipt());
            ByteBufCodecs.VAR_LONG.encode(buf, payload.readReceiptCost());
            ByteBufCodecs.VAR_LONG.encode(buf, payload.sendCostPerItem());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

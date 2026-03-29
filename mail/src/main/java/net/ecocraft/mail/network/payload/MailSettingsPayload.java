package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client payload carrying current mail settings (for admin UI).
 */
public record MailSettingsPayload(
        boolean allowPlayerMail,
        boolean allowItemAttachments,
        boolean allowCurrencyAttachments,
        boolean allowCOD,
        boolean allowMailboxCraft,
        int maxItemAttachments,
        int mailExpiryDays,
        long sendCost,
        int codFeePercent
) implements CustomPacketPayload {

    public static final Type<MailSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "mail_settings"));

    public static final StreamCodec<ByteBuf, MailSettingsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MailSettingsPayload decode(ByteBuf buf) {
            boolean allowPlayerMail = ByteBufCodecs.BOOL.decode(buf);
            boolean allowItemAttachments = ByteBufCodecs.BOOL.decode(buf);
            boolean allowCurrencyAttachments = ByteBufCodecs.BOOL.decode(buf);
            boolean allowCOD = ByteBufCodecs.BOOL.decode(buf);
            boolean allowMailboxCraft = ByteBufCodecs.BOOL.decode(buf);
            int maxItemAttachments = ByteBufCodecs.VAR_INT.decode(buf);
            int mailExpiryDays = ByteBufCodecs.VAR_INT.decode(buf);
            long sendCost = ByteBufCodecs.VAR_LONG.decode(buf);
            int codFeePercent = ByteBufCodecs.VAR_INT.decode(buf);
            return new MailSettingsPayload(allowPlayerMail, allowItemAttachments,
                    allowCurrencyAttachments, allowCOD, allowMailboxCraft,
                    maxItemAttachments, mailExpiryDays, sendCost, codFeePercent);
        }

        @Override
        public void encode(ByteBuf buf, MailSettingsPayload payload) {
            ByteBufCodecs.BOOL.encode(buf, payload.allowPlayerMail());
            ByteBufCodecs.BOOL.encode(buf, payload.allowItemAttachments());
            ByteBufCodecs.BOOL.encode(buf, payload.allowCurrencyAttachments());
            ByteBufCodecs.BOOL.encode(buf, payload.allowCOD());
            ByteBufCodecs.BOOL.encode(buf, payload.allowMailboxCraft());
            ByteBufCodecs.VAR_INT.encode(buf, payload.maxItemAttachments());
            ByteBufCodecs.VAR_INT.encode(buf, payload.mailExpiryDays());
            ByteBufCodecs.VAR_LONG.encode(buf, payload.sendCost());
            ByteBufCodecs.VAR_INT.encode(buf, payload.codFeePercent());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

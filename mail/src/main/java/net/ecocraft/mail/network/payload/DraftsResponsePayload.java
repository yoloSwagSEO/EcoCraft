package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record DraftsResponsePayload(List<DraftEntry> drafts) implements CustomPacketPayload {

    public record DraftEntry(
            String id,
            String recipient,
            String subject,
            String body,
            long currencyAmount,
            long codAmount,
            long createdAt
    ) {}

    public static final StreamCodec<ByteBuf, DraftEntry> DRAFT_CODEC = new StreamCodec<>() {
        @Override
        public DraftEntry decode(ByteBuf buf) {
            String id = ByteBufCodecs.STRING_UTF8.decode(buf);
            String recipient = ByteBufCodecs.STRING_UTF8.decode(buf);
            String subject = ByteBufCodecs.STRING_UTF8.decode(buf);
            String body = ByteBufCodecs.STRING_UTF8.decode(buf);
            long currencyAmount = ByteBufCodecs.VAR_LONG.decode(buf);
            long codAmount = ByteBufCodecs.VAR_LONG.decode(buf);
            long createdAt = ByteBufCodecs.VAR_LONG.decode(buf);
            return new DraftEntry(id, recipient, subject, body, currencyAmount, codAmount, createdAt);
        }

        @Override
        public void encode(ByteBuf buf, DraftEntry d) {
            ByteBufCodecs.STRING_UTF8.encode(buf, d.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, d.recipient());
            ByteBufCodecs.STRING_UTF8.encode(buf, d.subject());
            ByteBufCodecs.STRING_UTF8.encode(buf, d.body());
            ByteBufCodecs.VAR_LONG.encode(buf, d.currencyAmount());
            ByteBufCodecs.VAR_LONG.encode(buf, d.codAmount());
            ByteBufCodecs.VAR_LONG.encode(buf, d.createdAt());
        }
    };

    public static final CustomPacketPayload.Type<DraftsResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "drafts_response"));

    public static final StreamCodec<ByteBuf, DraftsResponsePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DraftsResponsePayload decode(ByteBuf buf) {
            List<DraftEntry> drafts = DRAFT_CODEC.apply(ByteBufCodecs.list()).decode(buf);
            return new DraftsResponsePayload(drafts);
        }

        @Override
        public void encode(ByteBuf buf, DraftsResponsePayload p) {
            DRAFT_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.drafts());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

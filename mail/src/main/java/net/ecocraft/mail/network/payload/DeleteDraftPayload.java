package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DeleteDraftPayload(String draftId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DeleteDraftPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "delete_draft"));

    public static final StreamCodec<ByteBuf, DeleteDraftPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DeleteDraftPayload::draftId,
            DeleteDraftPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

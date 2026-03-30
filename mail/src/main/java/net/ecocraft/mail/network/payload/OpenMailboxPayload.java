package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMailboxPayload(int entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenMailboxPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "open_mailbox"));

    public static final StreamCodec<ByteBuf, OpenMailboxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenMailboxPayload::entityId,
            OpenMailboxPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

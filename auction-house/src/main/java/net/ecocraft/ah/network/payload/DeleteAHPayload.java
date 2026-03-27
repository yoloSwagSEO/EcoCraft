package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server payload to delete an AH instance (admin only).
 * deleteMode is one of: "RETURN_ITEMS", "DELETE_LISTINGS", "TRANSFER_TO_DEFAULT".
 */
public record DeleteAHPayload(String ahId, String deleteMode) implements CustomPacketPayload {

    public static final Type<DeleteAHPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "delete_ah"));

    public static final StreamCodec<ByteBuf, DeleteAHPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DeleteAHPayload decode(ByteBuf buf) {
            String ahId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String deleteMode = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new DeleteAHPayload(ahId, deleteMode);
        }

        @Override
        public void encode(ByteBuf buf, DeleteAHPayload payload) {
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.ahId());
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.deleteMode());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

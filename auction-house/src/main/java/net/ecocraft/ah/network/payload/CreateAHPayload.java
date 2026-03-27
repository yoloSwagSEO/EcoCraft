package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server payload to create a new AH instance (admin only).
 */
public record CreateAHPayload(String name) implements CustomPacketPayload {

    public static final Type<CreateAHPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "create_ah"));

    public static final StreamCodec<ByteBuf, CreateAHPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CreateAHPayload decode(ByteBuf buf) {
            return new CreateAHPayload(ByteBufCodecs.STRING_UTF8.decode(buf));
        }

        @Override
        public void encode(ByteBuf buf, CreateAHPayload payload) {
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.name());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

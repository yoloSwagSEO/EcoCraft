package net.ecocraft.core.network.payload;

import io.netty.buffer.ByteBuf;
import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client: sends the current skin player name for an Exchanger NPC.
 */
public record ExchangerSkinPayload(int entityId, String skinPlayerName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ExchangerSkinPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EcoCraftCoreMod.MOD_ID, "exchanger_skin"));

    public static final StreamCodec<ByteBuf, ExchangerSkinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExchangerSkinPayload::entityId,
            ByteBufCodecs.STRING_UTF8, ExchangerSkinPayload::skinPlayerName,
            ExchangerSkinPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

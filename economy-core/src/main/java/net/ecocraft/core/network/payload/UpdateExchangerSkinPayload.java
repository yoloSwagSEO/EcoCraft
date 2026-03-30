package net.ecocraft.core.network.payload;

import io.netty.buffer.ByteBuf;
import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: admin requests to update an Exchanger NPC's skin.
 */
public record UpdateExchangerSkinPayload(int entityId, String skinPlayerName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateExchangerSkinPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EcoCraftCoreMod.MOD_ID, "update_exchanger_skin"));

    public static final StreamCodec<ByteBuf, UpdateExchangerSkinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UpdateExchangerSkinPayload::entityId,
            ByteBufCodecs.STRING_UTF8, UpdateExchangerSkinPayload::skinPlayerName,
            UpdateExchangerSkinPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

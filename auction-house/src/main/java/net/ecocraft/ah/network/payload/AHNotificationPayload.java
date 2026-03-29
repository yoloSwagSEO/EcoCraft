package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AHNotificationPayload(
        String eventType,
        String itemName,
        String playerName,
        long amount,
        String currencyId
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AHNotificationPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "notification"));

    public static final StreamCodec<ByteBuf, AHNotificationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AHNotificationPayload::eventType,
            ByteBufCodecs.STRING_UTF8, AHNotificationPayload::itemName,
            ByteBufCodecs.STRING_UTF8, AHNotificationPayload::playerName,
            ByteBufCodecs.VAR_LONG, AHNotificationPayload::amount,
            ByteBufCodecs.STRING_UTF8, AHNotificationPayload::currencyId,
            AHNotificationPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

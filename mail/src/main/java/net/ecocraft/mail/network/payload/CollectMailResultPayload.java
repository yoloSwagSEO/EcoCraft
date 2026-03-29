package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CollectMailResultPayload(
        boolean success,
        String message,
        int itemsCollected,
        long currencyCollected
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectMailResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "collect_mail_result"));

    public static final StreamCodec<ByteBuf, CollectMailResultPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CollectMailResultPayload decode(ByteBuf buf) {
            boolean success = ByteBufCodecs.BOOL.decode(buf);
            String message = ByteBufCodecs.STRING_UTF8.decode(buf);
            int itemsCollected = ByteBufCodecs.VAR_INT.decode(buf);
            long currencyCollected = ByteBufCodecs.VAR_LONG.decode(buf);
            return new CollectMailResultPayload(success, message, itemsCollected, currencyCollected);
        }

        @Override
        public void encode(ByteBuf buf, CollectMailResultPayload p) {
            ByteBufCodecs.BOOL.encode(buf, p.success());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.message());
            ByteBufCodecs.VAR_INT.encode(buf, p.itemsCollected());
            ByteBufCodecs.VAR_LONG.encode(buf, p.currencyCollected());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

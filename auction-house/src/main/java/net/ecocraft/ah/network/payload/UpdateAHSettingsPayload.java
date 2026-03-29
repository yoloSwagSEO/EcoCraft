package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> Server payload to update AH settings (admin only).
 */
public record UpdateAHSettingsPayload(int saleRate, int depositRate, List<Integer> durations, String deliveryMode)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateAHSettingsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "update_ah_settings"));

    public static final StreamCodec<ByteBuf, UpdateAHSettingsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public UpdateAHSettingsPayload decode(ByteBuf buf) {
            int saleRate = ByteBufCodecs.VAR_INT.decode(buf);
            int depositRate = ByteBufCodecs.VAR_INT.decode(buf);
            int count = ByteBufCodecs.VAR_INT.decode(buf);
            List<Integer> durations = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                durations.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            String deliveryMode = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new UpdateAHSettingsPayload(saleRate, depositRate, durations, deliveryMode);
        }

        @Override
        public void encode(ByteBuf buf, UpdateAHSettingsPayload payload) {
            ByteBufCodecs.VAR_INT.encode(buf, payload.saleRate());
            ByteBufCodecs.VAR_INT.encode(buf, payload.depositRate());
            ByteBufCodecs.VAR_INT.encode(buf, payload.durations().size());
            for (int d : payload.durations()) {
                ByteBufCodecs.VAR_INT.encode(buf, d);
            }
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.deliveryMode() != null ? payload.deliveryMode() : "DIRECT");
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

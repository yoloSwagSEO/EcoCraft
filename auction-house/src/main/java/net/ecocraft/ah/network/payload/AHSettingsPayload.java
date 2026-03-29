package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client payload carrying current AH settings and admin status.
 */
public record AHSettingsPayload(boolean isAdmin, int saleRate, int depositRate, List<Integer> durations,
                                 String deliveryMode)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AHSettingsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "ah_settings"));

    public static final StreamCodec<ByteBuf, AHSettingsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AHSettingsPayload decode(ByteBuf buf) {
            boolean isAdmin = ByteBufCodecs.BOOL.decode(buf);
            int saleRate = ByteBufCodecs.VAR_INT.decode(buf);
            int depositRate = ByteBufCodecs.VAR_INT.decode(buf);
            int count = ByteBufCodecs.VAR_INT.decode(buf);
            List<Integer> durations = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                durations.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            String deliveryMode = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new AHSettingsPayload(isAdmin, saleRate, depositRate, durations, deliveryMode);
        }

        @Override
        public void encode(ByteBuf buf, AHSettingsPayload payload) {
            ByteBufCodecs.BOOL.encode(buf, payload.isAdmin());
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

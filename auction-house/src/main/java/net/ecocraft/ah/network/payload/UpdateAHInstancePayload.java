package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> Server payload to update an AH instance's configuration (admin only).
 */
public record UpdateAHInstancePayload(String ahId, String name, int saleRate, int depositRate, List<Integer> durations,
                                       boolean allowBuyout, boolean allowAuction, String taxRecipient)
        implements CustomPacketPayload {

    public static final Type<UpdateAHInstancePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "update_ah_instance"));

    public static final StreamCodec<ByteBuf, UpdateAHInstancePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public UpdateAHInstancePayload decode(ByteBuf buf) {
            String ahId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String name = ByteBufCodecs.STRING_UTF8.decode(buf);
            int saleRate = ByteBufCodecs.VAR_INT.decode(buf);
            int depositRate = ByteBufCodecs.VAR_INT.decode(buf);
            int count = ByteBufCodecs.VAR_INT.decode(buf);
            List<Integer> durations = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                durations.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            boolean allowBuyout = ByteBufCodecs.BOOL.decode(buf);
            boolean allowAuction = ByteBufCodecs.BOOL.decode(buf);
            String taxRecipient = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new UpdateAHInstancePayload(ahId, name, saleRate, depositRate, durations, allowBuyout, allowAuction, taxRecipient);
        }

        @Override
        public void encode(ByteBuf buf, UpdateAHInstancePayload payload) {
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.ahId());
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.name());
            ByteBufCodecs.VAR_INT.encode(buf, payload.saleRate());
            ByteBufCodecs.VAR_INT.encode(buf, payload.depositRate());
            ByteBufCodecs.VAR_INT.encode(buf, payload.durations().size());
            for (int d : payload.durations()) {
                ByteBufCodecs.VAR_INT.encode(buf, d);
            }
            ByteBufCodecs.BOOL.encode(buf, payload.allowBuyout());
            ByteBufCodecs.BOOL.encode(buf, payload.allowAuction());
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.taxRecipient() != null ? payload.taxRecipient() : "");
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

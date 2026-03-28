package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> Client payload carrying the list of all AH instances.
 */
public record AHInstancesPayload(List<AHInstanceData> instances) implements CustomPacketPayload {

    public record AHInstanceData(String id, String slug, String name, int saleRate, int depositRate, List<Integer> durations, boolean allowBuyout, boolean allowAuction, String taxRecipient) {}

    public static final Type<AHInstancesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "ah_instances"));

    public static final StreamCodec<ByteBuf, AHInstancesPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AHInstancesPayload decode(ByteBuf buf) {
            int count = ByteBufCodecs.VAR_INT.decode(buf);
            List<AHInstanceData> list = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                String id = ByteBufCodecs.STRING_UTF8.decode(buf);
                String slug = ByteBufCodecs.STRING_UTF8.decode(buf);
                String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                int saleRate = ByteBufCodecs.VAR_INT.decode(buf);
                int depositRate = ByteBufCodecs.VAR_INT.decode(buf);
                int durCount = ByteBufCodecs.VAR_INT.decode(buf);
                List<Integer> durations = new ArrayList<>();
                for (int j = 0; j < durCount; j++) durations.add(ByteBufCodecs.VAR_INT.decode(buf));
                boolean allowBuyout = ByteBufCodecs.BOOL.decode(buf);
                boolean allowAuction = ByteBufCodecs.BOOL.decode(buf);
                String taxRecipient = ByteBufCodecs.STRING_UTF8.decode(buf);
                list.add(new AHInstanceData(id, slug, name, saleRate, depositRate, durations, allowBuyout, allowAuction, taxRecipient));
            }
            return new AHInstancesPayload(list);
        }

        @Override
        public void encode(ByteBuf buf, AHInstancesPayload payload) {
            ByteBufCodecs.VAR_INT.encode(buf, payload.instances().size());
            for (var inst : payload.instances()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, inst.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, inst.slug());
                ByteBufCodecs.STRING_UTF8.encode(buf, inst.name());
                ByteBufCodecs.VAR_INT.encode(buf, inst.saleRate());
                ByteBufCodecs.VAR_INT.encode(buf, inst.depositRate());
                ByteBufCodecs.VAR_INT.encode(buf, inst.durations().size());
                for (int d : inst.durations()) ByteBufCodecs.VAR_INT.encode(buf, d);
                ByteBufCodecs.BOOL.encode(buf, inst.allowBuyout());
                ByteBufCodecs.BOOL.encode(buf, inst.allowAuction());
                ByteBufCodecs.STRING_UTF8.encode(buf, inst.taxRecipient() != null ? inst.taxRecipient() : "");
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record RequestListingDetailPayload(String itemId, List<String> enchantmentFilters) implements CustomPacketPayload {

    /**
     * Backward-compatible constructor: no enchantment filters.
     */
    public RequestListingDetailPayload(String itemId) {
        this(itemId, List.of());
    }

    public static final CustomPacketPayload.Type<RequestListingDetailPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "request_listing_detail"));

    public static final StreamCodec<ByteBuf, RequestListingDetailPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestListingDetailPayload::itemId,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RequestListingDetailPayload::enchantmentFilters,
            RequestListingDetailPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

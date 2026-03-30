package net.ecocraft.mail.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.codec.StreamCodec;

/** Serializes then deserializes a payload to verify codec round-trip fidelity. */
final class PayloadTestHelper {
    private PayloadTestHelper() {}

    public static <T> T roundTrip(T payload, StreamCodec<ByteBuf, T> codec) {
        ByteBuf buf = Unpooled.buffer();
        codec.encode(buf, payload);
        return codec.decode(buf);
    }
}

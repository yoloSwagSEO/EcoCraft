package net.ecocraft.core.network.payload;

import io.netty.buffer.ByteBuf;
import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: deposit a physical money item from inventory into balance.
 * slotIndex = the inventory slot containing the money item to deposit.
 * amount = number of items to deposit from that slot.
 */
public record VaultDepositPayload(int slotIndex, int amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VaultDepositPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EcoCraftCoreMod.MOD_ID, "vault_deposit"));

    public static final StreamCodec<ByteBuf, VaultDepositPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VaultDepositPayload::slotIndex,
            ByteBufCodecs.VAR_INT, VaultDepositPayload::amount,
            VaultDepositPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

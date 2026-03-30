package net.ecocraft.core.vault;

import com.mojang.serialization.MapCodec;
import net.ecocraft.core.network.EcoNetworkHandler;
import net.ecocraft.core.network.payload.OpenVaultPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class VaultBlock extends BaseEntityBlock {

    public static final MapCodec<VaultBlock> CODEC = simpleCodec(VaultBlock::new);

    public VaultBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VaultBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof VaultBlockEntity vault) {
                vault.setOwnerUuid(player.getUUID());
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof VaultBlockEntity vault) {
                // Assign owner on first use if not set
                if (vault.getOwnerUuid() == null) {
                    vault.setOwnerUuid(serverPlayer.getUUID());
                }

                // Only the owner can open
                if (!serverPlayer.getUUID().equals(vault.getOwnerUuid())) {
                    serverPlayer.sendSystemMessage(Component.translatable("ecocraft.vault.not_owner"));
                    return InteractionResult.FAIL;
                }

                // Send open + data payloads
                PacketDistributor.sendToPlayer(serverPlayer, new OpenVaultPayload());
                EcoNetworkHandler.sendVaultData(serverPlayer);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}

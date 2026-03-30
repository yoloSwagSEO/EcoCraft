package net.ecocraft.mail.block;

import com.mojang.serialization.MapCodec;
import net.ecocraft.mail.network.payload.OpenMailboxPayload;
import net.ecocraft.mail.permission.MailPermissions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import org.jetbrains.annotations.Nullable;

/**
 * Mailbox block — public mail terminal.
 * Right-click opens the mailbox UI (for now, sends a chat message placeholder).
 */
public class MailboxBlock extends BaseEntityBlock {

    public static final MapCodec<MailboxBlock> CODEC = simpleCodec(MailboxBlock::new);

    public MailboxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MailboxBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!PermissionAPI.getPermission(serverPlayer, MailPermissions.READ)) {
                serverPlayer.sendSystemMessage(Component.translatable("ecocraft_mail.error.no_permission"));
                return InteractionResult.FAIL;
            }
            PacketDistributor.sendToPlayer(serverPlayer, new OpenMailboxPayload(0));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}

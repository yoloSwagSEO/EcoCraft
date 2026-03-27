package net.ecocraft.ah.block;

import net.ecocraft.ah.network.ServerPayloadHandler;
import net.ecocraft.ah.network.payload.OpenAHPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A simple block that opens the auction house GUI when right-clicked.
 */
public class AuctionTerminalBlock extends Block {

    public AuctionTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(net.minecraft.world.level.block.state.BlockState state,
                                               Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenAHPayload());
            ServerPayloadHandler.sendBalanceUpdate(serverPlayer);
            ServerPayloadHandler.sendAHSettings(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}

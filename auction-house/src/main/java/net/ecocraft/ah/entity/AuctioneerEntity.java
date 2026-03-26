package net.ecocraft.ah.entity;

import net.ecocraft.ah.network.payload.OpenAHPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A stationary NPC that opens the auction house when interacted with.
 */
public class AuctioneerEntity extends PathfinderMob {

    public AuctioneerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !level().isClientSide
                && player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenAHPayload());
            return InteractionResult.SUCCESS;
        }
        // For OFF_HAND, consume the interaction on client side to prevent further processing
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Invulnerable — return false but don't interfere with interaction pipeline
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean skipAttackInteraction(net.minecraft.world.entity.Entity attacker) {
        // Prevent attack interactions from consuming left-click events
        return true;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}

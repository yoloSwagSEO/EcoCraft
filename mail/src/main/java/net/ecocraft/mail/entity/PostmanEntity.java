package net.ecocraft.mail.entity;

import net.ecocraft.mail.permission.MailPermissions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.permission.PermissionAPI;

/**
 * A stationary NPC postman. Uses Mob (not PathfinderMob) to avoid
 * pathfinding-related issues. Invulnerable, no gravity, no collision.
 */
public class PostmanEntity extends Mob {

    public PostmanEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setCustomName(Component.literal("\u00a76Facteur"));
        this.setCustomNameVisible(true);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND
                && player instanceof ServerPlayer serverPlayer) {
            if (!PermissionAPI.getPermission(serverPlayer, MailPermissions.READ)) {
                serverPlayer.sendSystemMessage(Component.literal(
                        "\u00a7cVous n'avez pas la permission d'ouvrir la bo\u00eete aux lettres."));
                return InteractionResult.FAIL;
            }
            // TODO Task 6: send OpenMailboxPayload instead of chat message
            serverPlayer.sendSystemMessage(Component.literal("\u00a7eBo\u00eete aux lettres ouverte"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(net.minecraft.world.entity.Entity attacker) {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }
}

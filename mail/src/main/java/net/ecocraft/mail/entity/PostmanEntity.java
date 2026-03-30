package net.ecocraft.mail.entity;

import com.mojang.authlib.GameProfile;
import net.ecocraft.mail.network.MailServerPayloadHandler;
import net.ecocraft.mail.network.payload.OpenMailboxPayload;
import net.ecocraft.mail.permission.MailPermissions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.permission.PermissionAPI;

import java.util.Optional;

/**
 * A stationary NPC postman. Uses Mob (not PathfinderMob) to avoid
 * pathfinding-related issues. Invulnerable, no gravity, no collision.
 */
public class PostmanEntity extends Mob {

    private static final EntityDataAccessor<CompoundTag> DATA_SKIN_PROFILE =
            SynchedEntityData.defineId(PostmanEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private String skinPlayerName = "";

    public PostmanEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setCustomName(Component.translatable("ecocraft_mail.entity.postman_name"));
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
                serverPlayer.sendSystemMessage(Component.translatable("ecocraft_mail.error.no_permission"));
                return InteractionResult.FAIL;
            }
            PacketDistributor.sendToPlayer(serverPlayer, new OpenMailboxPayload(this.getId()));
            MailServerPayloadHandler.sendPostmanSkin(serverPlayer, this.getId());
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
    public void checkDespawn() {
        // Never despawn — these are permanent world NPCs
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN_PROFILE, new CompoundTag());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinPlayerName", skinPlayerName);
        CompoundTag profileTag = entityData.get(DATA_SKIN_PROFILE);
        if (!profileTag.isEmpty()) {
            tag.put("SkinProfile", profileTag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        skinPlayerName = tag.getString("SkinPlayerName");
        if (tag.contains("SkinProfile")) {
            entityData.set(DATA_SKIN_PROFILE, tag.getCompound("SkinProfile"));
        }
    }

    public String getSkinPlayerName() { return skinPlayerName; }

    public void setSkinPlayerName(String name) { this.skinPlayerName = name; }

    public Optional<GameProfile> getSkinProfile() {
        CompoundTag tag = entityData.get(DATA_SKIN_PROFILE);
        if (tag.isEmpty()) {
            return Optional.empty();
        }
        return ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, tag)
                .result()
                .map(ResolvableProfile::gameProfile);
    }

    public void setSkinProfile(GameProfile profile) {
        if (profile != null) {
            ResolvableProfile resolvable = new ResolvableProfile(profile);
            ResolvableProfile.CODEC.encodeStart(NbtOps.INSTANCE, resolvable)
                    .result()
                    .ifPresent(nbt -> entityData.set(DATA_SKIN_PROFILE, (CompoundTag) nbt));
        } else {
            entityData.set(DATA_SKIN_PROFILE, new CompoundTag());
        }
    }
}

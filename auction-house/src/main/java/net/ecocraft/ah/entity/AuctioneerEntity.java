package net.ecocraft.ah.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import net.ecocraft.ah.data.AHInstance;
import net.ecocraft.ah.network.ServerPayloadHandler;
import net.ecocraft.ah.network.payload.OpenAHPayload;
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

import java.util.Optional;

/**
 * A stationary NPC auctioneer. Uses Mob (not PathfinderMob) to avoid
 * pathfinding-related issues. Invulnerable, no gravity, no collision.
 */
public class AuctioneerEntity extends Mob {

    private static final EntityDataAccessor<CompoundTag> DATA_SKIN_PROFILE =
            SynchedEntityData.defineId(AuctioneerEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private String skinPlayerName = "";
    private String linkedAhId = AHInstance.DEFAULT_ID;

    public AuctioneerEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setCustomName(Component.literal("\u00a76Commissaire-priseur"));
        this.setCustomNameVisible(true);
        // Entity will persist in the world (not despawn)
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND
                && player instanceof ServerPlayer serverPlayer) {
            String ahId = this.getLinkedAhId();
            String ahName = ServerPayloadHandler.resolveAHName(ahId);
            com.mojang.logging.LogUtils.getLogger().info("[AH NPC] Opening AH: linkedAhId={} ahName='{}' skinPlayerName='{}'", ahId, ahName, skinPlayerName);
            PacketDistributor.sendToPlayer(serverPlayer, new OpenAHPayload(this.getId(), ahId, ahName));
            ServerPayloadHandler.sendBalanceUpdate(serverPlayer);
            ServerPayloadHandler.sendAHSettings(serverPlayer);
            ServerPayloadHandler.sendAHInstances(serverPlayer);
            ServerPayloadHandler.sendNPCSkin(serverPlayer, this.getId());
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
        tag.putString("LinkedAhId", linkedAhId);
        CompoundTag profileTag = entityData.get(DATA_SKIN_PROFILE);
        if (!profileTag.isEmpty()) {
            tag.put("SkinProfile", profileTag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        skinPlayerName = tag.getString("SkinPlayerName");
        if (tag.contains("LinkedAhId")) linkedAhId = tag.getString("LinkedAhId");
        if (tag.contains("SkinProfile")) {
            entityData.set(DATA_SKIN_PROFILE, tag.getCompound("SkinProfile"));
        }
    }

    public String getSkinPlayerName() { return skinPlayerName; }

    public void setSkinPlayerName(String name) { this.skinPlayerName = name; }

    public String getLinkedAhId() { return linkedAhId; }

    public void setLinkedAhId(String ahId) { this.linkedAhId = ahId; }

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

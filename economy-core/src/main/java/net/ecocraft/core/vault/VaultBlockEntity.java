package net.ecocraft.core.vault;

import net.ecocraft.core.registry.EcoRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class VaultBlockEntity extends BlockEntity {

    private @Nullable UUID ownerUuid;

    public VaultBlockEntity(BlockPos pos, BlockState state) {
        super(EcoRegistries.VAULT_BLOCK_ENTITY.get(), pos, state);
    }

    public @Nullable UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
    }
}

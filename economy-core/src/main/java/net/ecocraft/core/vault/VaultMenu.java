package net.ecocraft.core.vault;

import net.ecocraft.core.registry.EcoRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class VaultMenu extends AbstractContainerMenu {

    public VaultMenu(int containerId, Inventory playerInventory) {
        super(EcoRegistries.VAULT_MENU.get(), containerId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}

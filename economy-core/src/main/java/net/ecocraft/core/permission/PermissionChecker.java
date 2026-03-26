package net.ecocraft.core.permission;

import net.minecraft.server.level.ServerPlayer;

public interface PermissionChecker {
    boolean hasPermission(ServerPlayer player, String permission);
}

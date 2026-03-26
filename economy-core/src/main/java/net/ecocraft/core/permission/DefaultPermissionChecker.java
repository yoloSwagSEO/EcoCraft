package net.ecocraft.core.permission;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Default permission checker based on vanilla op levels.
 * Maps permission nodes to op levels (0 = everyone, 2 = op, 4 = admin).
 */
public class DefaultPermissionChecker implements PermissionChecker {

    private static final Map<String, Integer> PERMISSION_LEVELS = Map.ofEntries(
        Map.entry("economy.balance", 0),
        Map.entry("economy.balance.others", 1),
        Map.entry("economy.pay", 0),
        Map.entry("economy.exchange", 0),
        Map.entry("economy.bank", 0),
        Map.entry("economy.currency.list", 0),
        Map.entry("economy.admin.give", 2),
        Map.entry("economy.admin.take", 2),
        Map.entry("economy.admin.set", 2),
        Map.entry("ah.use", 0),
        Map.entry("ah.sell", 0),
        Map.entry("ah.stats", 0),
        Map.entry("ah.stats.others", 1),
        Map.entry("ah.admin.reload", 2),
        Map.entry("ah.admin.clear", 3),
        Map.entry("ah.admin.expire", 2)
    );

    @Override
    public boolean hasPermission(ServerPlayer player, String permission) {
        Integer required = PERMISSION_LEVELS.get(permission);
        if (required == null) {
            return player.hasPermissions(2); // unknown perms default to op
        }
        return player.hasPermissions(required);
    }
}

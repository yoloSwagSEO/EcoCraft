package net.ecocraft.core.permission;

import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

public class EcoPermissions {

    public static final PermissionNode<Boolean> BALANCE = new PermissionNode<>(
            "ecocraft", "balance",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> BALANCE_OTHERS = new PermissionNode<>(
            "ecocraft", "balance.others",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> BALANCE_LIST = new PermissionNode<>(
            "ecocraft", "balance.list",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> PAY = new PermissionNode<>(
            "ecocraft", "pay",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> EXCHANGE = new PermissionNode<>(
            "ecocraft", "exchange",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> ADMIN_GIVE = new PermissionNode<>(
            "ecocraft", "admin.give",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> ADMIN_TAKE = new PermissionNode<>(
            "ecocraft", "admin.take",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> ADMIN_SET = new PermissionNode<>(
            "ecocraft", "admin.set",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    /**
     * Helper for Brigadier .requires() — checks PermissionAPI for players,
     * falls back to op level 2 for console/command blocks.
     */
    public static boolean check(CommandSourceStack source, PermissionNode<Boolean> node) {
        try {
            var player = source.getPlayerOrException();
            return PermissionAPI.getPermission(player, node);
        } catch (Exception e) {
            return source.hasPermission(2); // console/command blocks fallback
        }
    }
}

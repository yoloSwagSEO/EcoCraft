package net.ecocraft.mail.permission;

import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

public class MailPermissions {

    // --- Boolean nodes ---

    public static final PermissionNode<Boolean> READ = new PermissionNode<>(
            "ecocraft", "mail.read",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> COMMAND = new PermissionNode<>(
            "ecocraft", "mail.command",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> SEND = new PermissionNode<>(
            "ecocraft", "mail.send",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> ATTACH_ITEMS = new PermissionNode<>(
            "ecocraft", "mail.attach.items",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> ATTACH_CURRENCY = new PermissionNode<>(
            "ecocraft", "mail.attach.currency",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> COD = new PermissionNode<>(
            "ecocraft", "mail.cod",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> ADMIN = new PermissionNode<>(
            "ecocraft", "mail.admin",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    // --- Integer nodes ---

    public static final PermissionNode<Integer> MAX_ATTACHMENTS = new PermissionNode<>(
            "ecocraft", "mail.max_attachments",
            PermissionTypes.INTEGER,
            (player, uuid, ctx) -> -1  // -1 = unlimited
    );

    // --- Helper for Brigadier .requires() ---

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

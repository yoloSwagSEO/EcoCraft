package net.ecocraft.ah.permission;

import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

public class AHPermissions {

    // --- Boolean nodes ---

    public static final PermissionNode<Boolean> USE = new PermissionNode<>(
            "ecocraft", "ah.use",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> SELL = new PermissionNode<>(
            "ecocraft", "ah.sell",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> BID = new PermissionNode<>(
            "ecocraft", "ah.bid",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> CANCEL = new PermissionNode<>(
            "ecocraft", "ah.cancel",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> ADMIN_CANCEL = new PermissionNode<>(
            "ecocraft", "ah.admin.cancel",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> ADMIN_SETTINGS = new PermissionNode<>(
            "ecocraft", "ah.admin.settings",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> ADMIN_RELOAD = new PermissionNode<>(
            "ecocraft", "ah.admin.reload",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    // --- Integer nodes ---

    public static final PermissionNode<Integer> MAX_LISTINGS = new PermissionNode<>(
            "ecocraft", "ah.max_listings",
            PermissionTypes.INTEGER,
            (player, uuid, ctx) -> -1  // -1 = unlimited
    );

    public static final PermissionNode<Integer> TAX_RATE = new PermissionNode<>(
            "ecocraft", "ah.tax_rate",
            PermissionTypes.INTEGER,
            (player, uuid, ctx) -> -1  // -1 = use AH config
    );

    public static final PermissionNode<Integer> DEPOSIT_RATE = new PermissionNode<>(
            "ecocraft", "ah.deposit_rate",
            PermissionTypes.INTEGER,
            (player, uuid, ctx) -> -1  // -1 = use AH config
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

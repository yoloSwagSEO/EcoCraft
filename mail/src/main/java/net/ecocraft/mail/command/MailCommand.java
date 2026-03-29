package net.ecocraft.mail.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ecocraft.mail.permission.MailPermissions;
import net.ecocraft.mail.service.MailService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Registers the /mail command tree.
 */
public final class MailCommand {

    private MailCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<MailService> serviceSupplier) {
        dispatcher.register(Commands.literal("mail")
                // /mail — opens mailbox
                .requires(src -> MailPermissions.check(src, MailPermissions.COMMAND))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    // TODO: send OpenMailboxPayload to client (Task 6: network payloads)
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("\u00a7aOuverture de la bo\u00eete aux lettres..."),
                            false);
                    return 1;
                })

                // /mail send <player> <subject>
                .then(Commands.literal("send")
                        .requires(src -> MailPermissions.check(src, MailPermissions.SEND))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("subject", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer sender = ctx.getSource().getPlayerOrException();
                                            ServerPlayer recipient = EntityArgument.getPlayer(ctx, "player");
                                            String subject = StringArgumentType.getString(ctx, "subject");
                                            return executeSend(ctx.getSource(), sender, recipient, subject, serviceSupplier);
                                        })
                                )
                        )
                )

                // /mail admin ...
                .then(Commands.literal("admin")
                        .requires(src -> MailPermissions.check(src, MailPermissions.ADMIN))

                        // /mail admin send <player> <subject> <message>
                        .then(Commands.literal("send")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("subject", StringArgumentType.string())
                                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            ServerPlayer recipient = EntityArgument.getPlayer(ctx, "player");
                                                            String subject = StringArgumentType.getString(ctx, "subject");
                                                            String message = StringArgumentType.getString(ctx, "message");
                                                            return executeAdminSend(ctx.getSource(), recipient, subject, message, serviceSupplier);
                                                        })
                                                )
                                        )
                                )
                        )

                        // /mail admin sendall <subject> <message>
                        .then(Commands.literal("sendall")
                                .then(Commands.argument("subject", StringArgumentType.string())
                                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String subject = StringArgumentType.getString(ctx, "subject");
                                                    String message = StringArgumentType.getString(ctx, "message");
                                                    return executeAdminSendAll(ctx.getSource(), subject, message, serviceSupplier);
                                                })
                                        )
                                )
                        )

                        // /mail admin clear <player>
                        .then(Commands.literal("clear")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            return executeAdminClear(ctx.getSource(), target, serviceSupplier);
                                        })
                                )
                        )

                        // /mail admin purge
                        .then(Commands.literal("purge")
                                .executes(ctx -> executeAdminPurge(ctx.getSource(), serviceSupplier))
                        )
                )
        );
    }

    // -------------------------------------------------------------------------
    // Command executors
    // -------------------------------------------------------------------------

    private static int executeSend(CommandSourceStack source, ServerPlayer sender,
                                   ServerPlayer recipient, String subject,
                                   Supplier<MailService> serviceSupplier) {
        MailService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.literal("\u00a7cLe syst\u00e8me de mail n'est pas initialis\u00e9."));
            return 0;
        }

        try {
            service.sendMail(
                    sender.getUUID(),
                    sender.getName().getString(),
                    recipient.getUUID(),
                    subject,
                    "",  // no body via command
                    List.of(),  // no items via command
                    0, null,  // no currency
                    0, null   // no COD
            );
            source.sendSuccess(
                    () -> Component.literal("\u00a7aMail envoy\u00e9 \u00e0 " + recipient.getName().getString() + " !"),
                    false);
            return 1;
        } catch (MailService.MailException e) {
            source.sendFailure(Component.literal("\u00a7c" + e.getMessage()));
            return 0;
        }
    }

    private static int executeAdminSend(CommandSourceStack source, ServerPlayer recipient,
                                         String subject, String message,
                                         Supplier<MailService> serviceSupplier) {
        MailService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.literal("\u00a7cLe syst\u00e8me de mail n'est pas initialis\u00e9."));
            return 0;
        }

        try {
            service.sendSystemMail(
                    "[Admin]",
                    recipient.getUUID(),
                    subject,
                    message,
                    List.of(),  // no items
                    0, null,    // no currency
                    true,       // indestructible
                    0           // instant delivery
            );
            source.sendSuccess(
                    () -> Component.literal("\u00a7aMail syst\u00e8me envoy\u00e9 \u00e0 " + recipient.getName().getString() + " !"),
                    false);
            return 1;
        } catch (MailService.MailException e) {
            source.sendFailure(Component.literal("\u00a7c" + e.getMessage()));
            return 0;
        }
    }

    private static int executeAdminSendAll(CommandSourceStack source, String subject, String message,
                                            Supplier<MailService> serviceSupplier) {
        MailService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.literal("\u00a7cLe syst\u00e8me de mail n'est pas initialis\u00e9."));
            return 0;
        }

        var server = source.getServer();
        var players = server.getPlayerList().getPlayers();
        int count = 0;

        for (ServerPlayer player : players) {
            try {
                service.sendSystemMail(
                        "[Admin]",
                        player.getUUID(),
                        subject,
                        message,
                        List.of(),
                        0, null,
                        true,
                        0
                );
                count++;
            } catch (MailService.MailException e) {
                source.sendFailure(Component.literal(
                        "\u00a7cErreur pour " + player.getName().getString() + " : " + e.getMessage()));
            }
        }

        int finalCount = count;
        source.sendSuccess(
                () -> Component.literal("\u00a7aMail syst\u00e8me envoy\u00e9 \u00e0 " + finalCount + " joueur(s) !"),
                true);
        return count;
    }

    private static int executeAdminClear(CommandSourceStack source, ServerPlayer target,
                                          Supplier<MailService> serviceSupplier) {
        MailService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.literal("\u00a7cLe syst\u00e8me de mail n'est pas initialis\u00e9."));
            return 0;
        }

        int deleted = service.deleteAllMailsForPlayer(target.getUUID());
        source.sendSuccess(
                () -> Component.literal("\u00a7a" + deleted + " mail(s) supprim\u00e9(s) pour " + target.getName().getString() + "."),
                true);
        return deleted;
    }

    private static int executeAdminPurge(CommandSourceStack source,
                                          Supplier<MailService> serviceSupplier) {
        MailService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.literal("\u00a7cLe syst\u00e8me de mail n'est pas initialis\u00e9."));
            return 0;
        }

        service.expireMails();
        source.sendSuccess(
                () -> Component.literal("\u00a7aMails expir\u00e9s purg\u00e9s avec succ\u00e8s."),
                true);
        return 1;
    }
}

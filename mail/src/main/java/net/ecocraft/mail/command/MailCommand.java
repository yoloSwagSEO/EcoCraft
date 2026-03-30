package net.ecocraft.mail.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ecocraft.mail.network.payload.OpenMailboxPayload;
import net.ecocraft.mail.permission.MailPermissions;
import net.ecocraft.mail.service.MailService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

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
                    PacketDistributor.sendToPlayer(player, new OpenMailboxPayload(0));
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

                // /mail test — generates test mails
                .then(Commands.literal("test")
                        .requires(src -> MailPermissions.check(src, MailPermissions.ADMIN))
                        .executes(ctx -> executeTest(ctx.getSource(), serviceSupplier))
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
            source.sendFailure(Component.translatable("ecocraft_mail.command.error_not_initialized"));
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
                    0, null,  // no COD
                    false     // no read receipt
            );
            source.sendSuccess(
                    () -> Component.translatable("ecocraft_mail.command.mail_sent", recipient.getName().getString()),
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
            source.sendFailure(Component.translatable("ecocraft_mail.command.error_not_initialized"));
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
                    () -> Component.translatable("ecocraft_mail.command.system_mail_sent", recipient.getName().getString()),
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
            source.sendFailure(Component.translatable("ecocraft_mail.command.error_not_initialized"));
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
                source.sendFailure(Component.translatable(
                        "ecocraft_mail.command.error_player", player.getName().getString(), e.getMessage()));
            }
        }

        int finalCount = count;
        source.sendSuccess(
                () -> Component.translatable("ecocraft_mail.command.system_mail_sent_all", finalCount),
                true);
        return count;
    }

    private static int executeAdminClear(CommandSourceStack source, ServerPlayer target,
                                          Supplier<MailService> serviceSupplier) {
        MailService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.translatable("ecocraft_mail.command.error_not_initialized"));
            return 0;
        }

        int deleted = service.deleteAllMailsForPlayer(target.getUUID());
        source.sendSuccess(
                () -> Component.translatable("ecocraft_mail.command.mails_deleted", deleted, target.getName().getString()),
                true);
        return deleted;
    }

    private static int executeTest(CommandSourceStack source,
                                     Supplier<MailService> serviceSupplier) {
        MailService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.translatable("ecocraft_mail.command.error_not_initialized"));
            return 0;
        }

        try {
            ServerPlayer player = source.getPlayerOrException();
            UUID uuid = player.getUUID();

            // 1. Simple text mail
            service.sendSystemMail("[Système]", uuid,
                    "Bienvenue sur le serveur !",
                    "Ceci est un mail de bienvenue. Bonne aventure !",
                    List.of(), 0, null, false, 0);

            // 2. Mail with currency
            service.sendSystemMail("Banque Royale", uuid,
                    "Prime de connexion",
                    "Voici votre prime de connexion quotidienne.",
                    List.of(), 500, service.getDefaultCurrencyId(), false, 0);

            // 3. Mail with items
            service.sendSystemMail("Hôtel des Ventes", uuid,
                    "Vente réussie : Épée en diamant",
                    "Votre Épée en diamant a été vendue pour 250 Gold.",
                    List.of(new net.ecocraft.mail.data.MailItemAttachment(
                            "minecraft:diamond_sword", "Épée en diamant", null, 1)),
                    250, service.getDefaultCurrencyId(), false, 0);

            // 4. Mail with items + currency (simulating AH purchase)
            service.sendSystemMail("Hôtel des Ventes", uuid,
                    "Achat : 16x Bloc de diamant",
                    "Vous avez acheté 16 Blocs de diamant.",
                    List.of(new net.ecocraft.mail.data.MailItemAttachment(
                            "minecraft:diamond_block", "Bloc de diamant", null, 16)),
                    0, null, false, 0);

            // 5. Indestructible admin mail
            service.sendSystemMail("[Admin]", uuid,
                    "Règles du serveur",
                    "1. Pas de grief\n2. Respectez les autres\n3. Amusez-vous !",
                    List.of(), 0, null, true, 0);

            // 6. COD mail (from a fake player)
            service.sendSystemMail("Grimald", uuid,
                    "Commande spéciale",
                    "Voici les objets que tu m'as demandé. N'oublie pas de payer !",
                    List.of(
                            new net.ecocraft.mail.data.MailItemAttachment(
                                    "minecraft:golden_apple", "Pomme dorée", null, 5),
                            new net.ecocraft.mail.data.MailItemAttachment(
                                    "minecraft:ender_pearl", "Perle de l'Ender", null, 8)
                    ),
                    0, null, false, 0);
            // Note: COD requires sendMail (P2P), not sendSystemMail. We'll create one manually:
            long now = System.currentTimeMillis();
            var codMail = new net.ecocraft.mail.data.Mail(
                    java.util.UUID.randomUUID().toString(),
                    java.util.UUID.randomUUID(), // fake sender UUID
                    "Thoria",
                    uuid,
                    "Livraison contre remboursement",
                    "Tu me dois 300 Gold pour ces potions !",
                    List.of(
                            new net.ecocraft.mail.data.MailItemAttachment(
                                    "minecraft:potion", "Potion de soin", null, 3),
                            new net.ecocraft.mail.data.MailItemAttachment(
                                    "minecraft:golden_carrot", "Carotte dorée", null, 10)
                    ),
                    0, null,
                    300, service.getDefaultCurrencyId(), // COD: 300 Gold
                    false, false, false, false, false,
                    now, now,
                    now + MailService.DEFAULT_EXPIRY_MS
            );
            service.getStorage().createMail(codMail);

            source.sendSuccess(
                    () -> Component.literal("§a7 mails de test créés ! Ouvrez la boîte aux lettres."),
                    false);
            return 7;
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cErreur: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeAdminPurge(CommandSourceStack source,
                                          Supplier<MailService> serviceSupplier) {
        MailService service = serviceSupplier.get();
        if (service == null) {
            source.sendFailure(Component.translatable("ecocraft_mail.command.error_not_initialized"));
            return 0;
        }

        service.expireMails();
        source.sendSuccess(
                () -> Component.translatable("ecocraft_mail.command.purge_success"),
                true);
        return 1;
    }
}

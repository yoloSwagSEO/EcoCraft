package net.ecocraft.mail.network;

import com.mojang.logging.LogUtils;
import net.ecocraft.mail.MailServerEvents;
import net.ecocraft.mail.config.MailConfig;
import net.ecocraft.mail.data.Mail;
import net.ecocraft.mail.data.MailItemAttachment;
import net.ecocraft.mail.network.payload.*;
import net.ecocraft.mail.permission.MailPermissions;
import net.ecocraft.mail.service.MailService;
import net.ecocraft.mail.entity.PostmanEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles client-to-server packets for the mail system.
 * Delegates to {@link MailService} via {@link MailServerEvents}.
 */
public final class MailServerPayloadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private MailServerPayloadHandler() {}

    // -------------------------------------------------------------------------
    // Mail list
    // -------------------------------------------------------------------------

    public static void handleRequestMailList(RequestMailListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                MailService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                List<Mail> mails = service.getMailsForPlayer(player.getUUID());

                List<MailListResponsePayload.MailSummary> summaries = new ArrayList<>();
                for (Mail mail : mails) {
                    summaries.add(new MailListResponsePayload.MailSummary(
                            mail.id(),
                            mail.senderName(),
                            mail.subject(),
                            mail.read(),
                            mail.collected(),
                            mail.returned(),
                            mail.hasItems(),
                            mail.hasCurrency(),
                            mail.hasCOD(),
                            mail.codAmount(),
                            mail.currencyAmount(),
                            mail.createdAt()
                    ));
                }

                String symbol = service.getDefaultCurrencySymbol();
                int maxAttach = MailConfig.CONFIG.maxItemAttachments.get();
                long sendCost = MailConfig.CONFIG.sendCost.get();
                List<MailListResponsePayload.MailSummary> sentSummaries = new ArrayList<>();
                List<Mail> sentMails = service.getSentMailsForPlayer(player.getUUID());
                var server = player.getServer();
                for (Mail mail : sentMails) {
                    // Resolve recipient name from UUID for display in sent view
                    String recipientName = mail.recipientUuid().toString();
                    if (server != null) {
                        ServerPlayer onlineRecip = server.getPlayerList().getPlayer(mail.recipientUuid());
                        if (onlineRecip != null) {
                            recipientName = onlineRecip.getName().getString();
                        } else {
                            var profileCache = server.getProfileCache();
                            if (profileCache != null) {
                                var profile = profileCache.get(mail.recipientUuid());
                                if (profile.isPresent()) {
                                    recipientName = profile.get().getName();
                                }
                            }
                        }
                    }
                    sentSummaries.add(new MailListResponsePayload.MailSummary(
                            mail.id(),
                            recipientName,
                            mail.subject(),
                            mail.read(),
                            mail.collected(),
                            mail.returned(),
                            mail.hasItems(),
                            mail.hasCurrency(),
                            mail.hasCOD(),
                            mail.codAmount(),
                            mail.currencyAmount(),
                            mail.createdAt()
                    ));
                }
                long sendCostPerItem = MailConfig.CONFIG.sendCostPerItem.get();
                boolean allowReadReceipt = MailConfig.CONFIG.allowReadReceipt.get();
                long readReceiptCost = MailConfig.CONFIG.readReceiptCost.get();
                int codFeePercent = MailConfig.CONFIG.codFeePercent.get();
                context.reply(new MailListResponsePayload(summaries, sentSummaries, symbol, maxAttach, sendCost, sendCostPerItem, allowReadReceipt, readReceiptCost, codFeePercent));
            } catch (Exception e) {
                LOGGER.error("Error handling RequestMailList", e);
                context.reply(new MailListResponsePayload(List.of(), List.of(), "G", 12, 0, 0, true, 0, 0));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Mail detail
    // -------------------------------------------------------------------------

    public static void handleRequestMailDetail(RequestMailDetailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                MailService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                Mail mail = service.getMailDetail(payload.mailId(), player.getUUID());

                List<MailDetailResponsePayload.ItemEntry> items = new ArrayList<>();
                if (mail.items() != null) {
                    for (MailItemAttachment item : mail.items()) {
                        items.add(new MailDetailResponsePayload.ItemEntry(
                                item.itemId(),
                                item.itemName(),
                                item.itemNbt() != null ? item.itemNbt() : "",
                                item.quantity()
                        ));
                    }
                }

                context.reply(new MailDetailResponsePayload(
                        mail.id(),
                        mail.senderUuid() != null ? mail.senderUuid().toString() : "",
                        mail.senderName(),
                        mail.subject(),
                        mail.body(),
                        items,
                        mail.currencyAmount(),
                        mail.currencyId() != null ? mail.currencyId() : "",
                        mail.codAmount(),
                        mail.codCurrencyId() != null ? mail.codCurrencyId() : "",
                        mail.read(),
                        mail.collected(),
                        mail.indestructible(),
                        mail.returned(),
                        mail.createdAt(),
                        mail.availableAt(),
                        mail.expiresAt()
                ));
            } catch (MailService.MailException e) {
                LOGGER.warn("Mail detail error: {}", e.getMessage());
                // Send an empty response on error
                context.reply(new MailDetailResponsePayload(
                        "", "", "", e.getMessage(), "", List.of(),
                        0, "", 0, "",
                        false, false, false, false, 0, 0, 0
                ));
            } catch (Exception e) {
                LOGGER.error("Error handling RequestMailDetail", e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Collect
    // -------------------------------------------------------------------------

    public static void handleCollectMail(CollectMailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                MailService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();

                if ("ALL".equals(payload.mailId())) {
                    int count = service.collectAllMails(player.getUUID());
                    // Deliver items for all collected mails is handled by the service via ItemDeliverer
                    context.reply(new CollectMailResultPayload(
                            true,
                            count > 0 ? Component.translatable("ecocraft_mail.server.collect_all_result", count).getString()
                                      : Component.translatable("ecocraft_mail.server.collect_none").getString(),
                            count,
                            0
                    ));
                } else {
                    service.collectMail(player.getUUID(), payload.mailId());
                    context.reply(new CollectMailResultPayload(true, Component.translatable("ecocraft_mail.server.collect_one").getString(), 1, 0));
                }
            } catch (MailService.MailException e) {
                context.reply(new CollectMailResultPayload(false, e.getMessage(), 0, 0));
            } catch (Exception e) {
                LOGGER.error("Error handling CollectMail", e);
                context.reply(new CollectMailResultPayload(false, Component.translatable("ecocraft_mail.server.error_internal").getString(), 0, 0));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Send mail
    // -------------------------------------------------------------------------

    public static void handleSendMail(SendMailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                MailService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();
                var config = MailConfig.CONFIG;

                // Permission checks
                if (!PermissionAPI.getPermission(player, MailPermissions.SEND)) {
                    context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.permission_denied").getString()));
                    return;
                }

                if (!config.allowPlayerMail.get()) {
                    context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.mail_disabled").getString()));
                    return;
                }

                // Validate subject
                if (payload.subject() == null || payload.subject().isBlank()) {
                    context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.subject_required").getString()));
                    return;
                }

                // Item attachment permission check
                if (!payload.inventorySlots().isEmpty()) {
                    if (!config.allowItemAttachments.get()) {
                        context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.attachments_disabled").getString()));
                        return;
                    }
                    if (!PermissionAPI.getPermission(player, MailPermissions.ATTACH_ITEMS)) {
                        context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.attachments_no_permission").getString()));
                        return;
                    }
                    int maxAttachments = config.maxItemAttachments.get();
                    if (payload.inventorySlots().size() > maxAttachments) {
                        context.reply(new SendMailResultPayload(false,
                                Component.translatable("ecocraft_mail.server.attachments_max", maxAttachments).getString()));
                        return;
                    }
                }

                // Currency attachment permission check
                if (payload.currencyAmount() > 0) {
                    if (!config.allowCurrencyAttachments.get()) {
                        context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.currency_disabled").getString()));
                        return;
                    }
                    if (!PermissionAPI.getPermission(player, MailPermissions.ATTACH_CURRENCY)) {
                        context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.currency_no_permission").getString()));
                        return;
                    }
                }

                // COD permission check
                if (payload.codAmount() > 0) {
                    if (!config.allowCOD.get()) {
                        context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.cod_disabled").getString()));
                        return;
                    }
                    if (!PermissionAPI.getPermission(player, MailPermissions.COD)) {
                        context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.cod_no_permission").getString()));
                        return;
                    }
                }

                // Resolve recipient name to UUID
                var server = player.getServer();
                if (server == null) {
                    context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.error_server").getString()));
                    return;
                }

                UUID recipientUuid = null;

                // Try online player first
                ServerPlayer onlineRecipient = server.getPlayerList().getPlayerByName(payload.recipientName());
                if (onlineRecipient != null) {
                    recipientUuid = onlineRecipient.getUUID();
                } else {
                    // Try profile cache for offline players
                    var profileCache = server.getProfileCache();
                    if (profileCache != null) {
                        var profile = profileCache.get(payload.recipientName());
                        if (profile.isPresent()) {
                            recipientUuid = profile.get().getId();
                        }
                    }
                }

                if (recipientUuid == null) {
                    context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.player_not_found", payload.recipientName()).getString()));
                    return;
                }

                // Cannot send mail to yourself
                if (recipientUuid.equals(player.getUUID())) {
                    context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.cannot_self_send").getString()));
                    return;
                }

                // Extract items from player inventory
                List<MailItemAttachment> items = new ArrayList<>();
                for (int slotIndex : payload.inventorySlots()) {
                    if (slotIndex < 0 || slotIndex >= player.getInventory().getContainerSize()) continue;
                    ItemStack stack = player.getInventory().getItem(slotIndex);
                    if (stack.isEmpty()) continue;

                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    String itemName = stack.getHoverName().getString();
                    String itemNbt = serializeItemStack(stack, player);

                    items.add(new MailItemAttachment(itemId, itemName, itemNbt, stack.getCount()));

                    // Remove item from player inventory
                    player.getInventory().setItem(slotIndex, ItemStack.EMPTY);
                }

                // Determine currency ID (use default currency)
                String currencyId = null;
                if (payload.currencyAmount() > 0) {
                    var currencies = MailServerEvents.getService() != null
                            ? net.ecocraft.core.EcoServerEvents.getCurrencyRegistry() : null;
                    if (currencies != null && currencies.getDefault() != null) {
                        currencyId = currencies.getDefault().id();
                    }
                }

                String codCurrencyId = null;
                if (payload.codAmount() > 0) {
                    var currencies = net.ecocraft.core.EcoServerEvents.getCurrencyRegistry();
                    if (currencies != null && currencies.getDefault() != null) {
                        codCurrencyId = currencies.getDefault().id();
                    }
                }

                // Charge send cost (base + per-item + read receipt)
                long baseSendCost = config.sendCost.get();
                long perItemCost = config.sendCostPerItem.get();
                long readReceiptCost = (payload.readReceipt() && config.allowReadReceipt.get()) ? config.readReceiptCost.get() : 0;
                long totalSendCost = baseSendCost + (items.size() * perItemCost) + readReceiptCost;

                if (totalSendCost > 0) {
                    var currencyReg = net.ecocraft.core.EcoServerEvents.getCurrencyRegistry();
                    var defaultCurrency = currencyReg != null ? currencyReg.getDefault() : null;
                    if (defaultCurrency != null) {
                        var eco = net.ecocraft.core.EcoServerEvents.getEconomy();
                        java.math.BigDecimal costBD = MailService.fromSmallestUnit(totalSendCost, defaultCurrency);
                        var txResult = eco.withdraw(player.getUUID(), costBD, defaultCurrency);
                        if (!txResult.successful()) {
                            // Restore items to player inventory
                            for (int i = 0; i < items.size(); i++) {
                                int slotIndex = payload.inventorySlots().get(i);
                                var itemRL = net.minecraft.resources.ResourceLocation.parse(items.get(i).itemId());
                                var itemObj = BuiltInRegistries.ITEM.get(itemRL);
                                ItemStack restored;
                                if (items.get(i).itemNbt() != null && !items.get(i).itemNbt().isEmpty()) {
                                    try {
                                        var tag = TagParser.parseTag(items.get(i).itemNbt());
                                        restored = ItemStack.OPTIONAL_CODEC.parse(
                                                player.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag
                                        ).getOrThrow();
                                    } catch (Exception ex) {
                                        restored = new ItemStack(itemObj, items.get(i).quantity());
                                    }
                                } else {
                                    restored = new ItemStack(itemObj, items.get(i).quantity());
                                }
                                player.getInventory().setItem(slotIndex, restored);
                            }
                            context.reply(new SendMailResultPayload(false, "Fonds insuffisants pour l'envoi : " + txResult.errorMessage()));
                            return;
                        }
                    }
                }

                boolean readReceipt = payload.readReceipt() && config.allowReadReceipt.get();

                // Send the mail
                service.sendMail(
                        player.getUUID(),
                        player.getName().getString(),
                        recipientUuid,
                        payload.subject(),
                        payload.body(),
                        items,
                        payload.currencyAmount(),
                        currencyId,
                        payload.codAmount(),
                        codCurrencyId,
                        readReceipt
                );

                context.reply(new SendMailResultPayload(true, Component.translatable("ecocraft_mail.server.mail_sent_success").getString()));

                // Send notification to recipient if online
                if (onlineRecipient != null) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            onlineRecipient,
                            new MailNotificationPayload("NEW_MAIL", payload.subject(), player.getName().getString())
                    );
                }

            } catch (MailService.MailException e) {
                context.reply(new SendMailResultPayload(false, e.getMessage()));
            } catch (Exception e) {
                LOGGER.error("Error handling SendMail", e);
                context.reply(new SendMailResultPayload(false, Component.translatable("ecocraft_mail.server.error_internal").getString()));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    public static void handleDeleteMail(DeleteMailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                MailService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();
                service.deleteMail(player.getUUID(), payload.mailId());
                // Refresh the mail list after deletion
                handleRequestMailList(new RequestMailListPayload(), context);
            } catch (MailService.MailException e) {
                LOGGER.warn("Delete mail error: {}", e.getMessage());
            } catch (Exception e) {
                LOGGER.error("Error handling DeleteMail", e);
            }
        });
    }

    public static void handleMarkRead(MarkReadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                MailService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();
                service.markRead(player.getUUID(), payload.mailId(), player.getName().getString());
                // Refresh the mail list
                handleRequestMailList(new RequestMailListPayload(), context);
            } catch (MailService.MailException e) {
                LOGGER.warn("Mark read error: {}", e.getMessage());
            } catch (Exception e) {
                LOGGER.error("Error handling MarkRead", e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // COD
    // -------------------------------------------------------------------------

    public static void handleReturnCOD(ReturnCODPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                MailService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();
                service.returnCOD(player.getUUID(), payload.mailId());
                // Refresh the mail list
                handleRequestMailList(new RequestMailListPayload(), context);
            } catch (MailService.MailException e) {
                LOGGER.warn("Return COD error: {}", e.getMessage());
            } catch (Exception e) {
                LOGGER.error("Error handling ReturnCOD", e);
            }
        });
    }

    public static void handlePayCOD(PayCODPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                MailService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();
                service.payCOD(player.getUUID(), payload.mailId());
                context.reply(new CollectMailResultPayload(true, Component.translatable("ecocraft_mail.server.cod_paid").getString(), 1, 0));
            } catch (MailService.MailException e) {
                context.reply(new CollectMailResultPayload(false, e.getMessage(), 0, 0));
            } catch (Exception e) {
                LOGGER.error("Error handling PayCOD", e);
                context.reply(new CollectMailResultPayload(false, Component.translatable("ecocraft_mail.server.error_internal").getString(), 0, 0));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Settings
    // -------------------------------------------------------------------------

    public static void handleUpdateMailSettings(UpdateMailSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ServerPlayer player = (ServerPlayer) context.player();
                if (!PermissionAPI.getPermission(player, MailPermissions.ADMIN)) {
                    return;
                }
                var config = MailConfig.CONFIG;
                config.allowPlayerMail.set(payload.allowPlayerMail());
                config.allowItemAttachments.set(payload.allowItemAttachments());
                config.allowCurrencyAttachments.set(payload.allowCurrencyAttachments());
                config.allowCOD.set(payload.allowCOD());
                config.allowMailboxCraft.set(payload.allowMailboxCraft());
                config.maxItemAttachments.set(Math.max(1, Math.min(54, payload.maxItemAttachments())));
                config.mailExpiryDays.set(Math.max(1, Math.min(365, payload.mailExpiryDays())));
                config.sendCost.set(Math.max(0, payload.sendCost()));
                config.codFeePercent.set(Math.max(0, Math.min(100, payload.codFeePercent())));
                config.allowReadReceipt.set(payload.allowReadReceipt());
                config.readReceiptCost.set(Math.max(0, payload.readReceiptCost()));
                config.sendCostPerItem.set(Math.max(0, payload.sendCostPerItem()));
                MailConfig.CONFIG_SPEC.save();
                // Send updated settings back to the client
                context.reply(new MailSettingsPayload(
                        config.allowPlayerMail.get(),
                        config.allowItemAttachments.get(),
                        config.allowCurrencyAttachments.get(),
                        config.allowCOD.get(),
                        config.allowMailboxCraft.get(),
                        config.maxItemAttachments.get(),
                        config.mailExpiryDays.get(),
                        config.sendCost.get(),
                        config.codFeePercent.get(),
                        config.allowReadReceipt.get(),
                        config.readReceiptCost.get(),
                        config.sendCostPerItem.get()
                ));
            } catch (Exception e) {
                LOGGER.error("Error handling UpdateMailSettings", e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Postman skin
    // -------------------------------------------------------------------------

    public static void sendPostmanSkin(ServerPlayer player, int entityId) {
        if (entityId <= 0) return;
        var entity = player.level().getEntity(entityId);
        if (entity instanceof PostmanEntity postman) {
            PacketDistributor.sendToPlayer(player, new PostmanSkinPayload(entityId, postman.getSkinPlayerName()));
        }
    }

    public static void handleUpdatePostmanSkin(UpdatePostmanSkinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!PermissionAPI.getPermission(player, MailPermissions.ADMIN)) {
                return;
            }

            var entity = player.level().getEntity(payload.entityId());
            if (!(entity instanceof PostmanEntity postman)) {
                return;
            }

            String skinName = payload.skinPlayerName().trim();
            postman.setSkinPlayerName(skinName);

            if (skinName.isEmpty()) {
                postman.setSkinProfile(null);
                LOGGER.info("[Mail] Postman skin reset for entity {}", payload.entityId());
                return;
            }

            // Resolve GameProfile asynchronously
            var server = player.getServer();
            if (server == null) return;

            net.minecraft.Util.backgroundExecutor().execute(() -> {
                try {
                    var profileCache = server.getProfileCache();
                    if (profileCache == null) {
                        LOGGER.warn("[Mail] Profile cache unavailable for skin resolution");
                        return;
                    }
                    var optProfile = profileCache.get(skinName);
                    if (optProfile.isEmpty()) {
                        LOGGER.warn("[Mail] Player not found for skin: {}", skinName);
                        return;
                    }

                    var profile = optProfile.get();
                    var profileResult = server.getSessionService().fetchProfile(profile.getId(), true);
                    if (profileResult == null) {
                        LOGGER.warn("[Mail] Could not fetch profile for skin: {}", skinName);
                        return;
                    }
                    var filledProfile = profileResult.profile();

                    server.execute(() -> {
                        postman.setSkinProfile(filledProfile);
                        LOGGER.info("[Mail] Postman skin updated to '{}' for entity {}", skinName, payload.entityId());
                    });
                } catch (Exception e) {
                    LOGGER.error("Error resolving postman skin for " + skinName, e);
                }
            });
        });
    }

    // -------------------------------------------------------------------------
    // Drafts
    // -------------------------------------------------------------------------

    public static void handleSaveDraft(SaveDraftPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                MailService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();
                String draftId = UUID.randomUUID().toString();
                service.getStorage().saveDraft(draftId, player.getUUID(),
                        payload.recipient(), payload.subject(), payload.body(),
                        payload.currency(), payload.cod());
            } catch (Exception e) {
                LOGGER.error("Error handling SaveDraft", e);
            }
        });
    }

    public static void handleRequestDrafts(RequestDraftsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                MailService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();
                var drafts = service.getStorage().getDrafts(player.getUUID());
                List<DraftsResponsePayload.DraftEntry> entries = new ArrayList<>();
                for (var draft : drafts) {
                    entries.add(new DraftsResponsePayload.DraftEntry(
                            draft.id(), draft.recipient(), draft.subject(), draft.body(),
                            draft.currencyAmount(), draft.codAmount(), draft.createdAt()
                    ));
                }
                context.reply(new DraftsResponsePayload(entries));
            } catch (Exception e) {
                LOGGER.error("Error handling RequestDrafts", e);
                context.reply(new DraftsResponsePayload(List.of()));
            }
        });
    }

    public static void handleDeleteDraft(DeleteDraftPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                MailService service = requireService();
                ServerPlayer player = (ServerPlayer) context.player();
                service.getStorage().deleteDraft(payload.draftId());
                // Send updated drafts list
                handleRequestDrafts(new RequestDraftsPayload(), context);
            } catch (Exception e) {
                LOGGER.error("Error handling DeleteDraft", e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static MailService requireService() {
        MailService service = MailServerEvents.getService();
        if (service == null) {
            throw new IllegalStateException("MailService not initialized");
        }
        return service;
    }

    /**
     * Serializes an ItemStack to an NBT string preserving all DataComponents.
     * Same pattern as auction-house ItemStackSerializer.
     */
    private static String serializeItemStack(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty()) return null;
        try {
            var tag = ItemStack.OPTIONAL_CODEC.encodeStart(
                    player.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack
            ).getOrThrow();
            return tag.toString();
        } catch (Exception e) {
            LOGGER.error("Failed to serialize ItemStack {}", stack, e);
            return null;
        }
    }
}

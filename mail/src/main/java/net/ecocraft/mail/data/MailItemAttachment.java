package net.ecocraft.mail.data;

import org.jetbrains.annotations.Nullable;

public record MailItemAttachment(
    String itemId,
    String itemName,
    @Nullable String itemNbt,
    int quantity
) {}

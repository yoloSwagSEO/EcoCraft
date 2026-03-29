package net.ecocraft.mail.data;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record Mail(
    String id,
    @Nullable UUID senderUuid,
    String senderName,
    UUID recipientUuid,
    String subject,
    String body,
    List<MailItemAttachment> items,
    long currencyAmount,
    @Nullable String currencyId,
    long codAmount,
    @Nullable String codCurrencyId,
    boolean read,
    boolean collected,
    boolean indestructible,
    boolean returned,
    long createdAt,
    long availableAt,
    long expiresAt
) {
    public boolean hasItems() { return items != null && !items.isEmpty(); }
    public boolean hasCurrency() { return currencyAmount > 0 && currencyId != null; }
    public boolean hasCOD() { return codAmount > 0 && codCurrencyId != null; }
    public boolean hasAttachments() { return hasItems() || hasCurrency(); }
    public boolean isAvailable() { return availableAt <= System.currentTimeMillis(); }
    public boolean isExpired() { return !indestructible && expiresAt <= System.currentTimeMillis(); }
    public boolean canDelete() { return read && (!hasAttachments() || collected); }
}

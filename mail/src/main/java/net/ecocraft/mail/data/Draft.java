package net.ecocraft.mail.data;

public record Draft(
    String id,
    String recipient,
    String subject,
    String body,
    long currencyAmount,
    long codAmount,
    long createdAt
) {}

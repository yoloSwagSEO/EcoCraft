package net.ecocraft.mail.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class MailConfig {
    public static final MailConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    static {
        Pair<MailConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(MailConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    public final ModConfigSpec.BooleanValue allowPlayerMail;
    public final ModConfigSpec.BooleanValue allowItemAttachments;
    public final ModConfigSpec.BooleanValue allowCurrencyAttachments;
    public final ModConfigSpec.BooleanValue allowCOD;
    public final ModConfigSpec.IntValue maxItemAttachments;
    public final ModConfigSpec.IntValue mailExpiryDays;
    public final ModConfigSpec.LongValue sendCost;
    public final ModConfigSpec.IntValue codFeePercent;
    public final ModConfigSpec.BooleanValue allowMailboxCraft;
    public final ModConfigSpec.BooleanValue allowReadReceipt;
    public final ModConfigSpec.LongValue readReceiptCost;
    public final ModConfigSpec.LongValue sendCostPerItem;

    private MailConfig(ModConfigSpec.Builder builder) {
        builder.push("mail");

        allowPlayerMail = builder
            .comment("Players can send mails to each other")
            .define("allowPlayerMail", true);

        allowItemAttachments = builder
            .comment("Allow item attachments in mails")
            .define("allowItemAttachments", true);

        allowCurrencyAttachments = builder
            .comment("Allow currency attachments in mails")
            .define("allowCurrencyAttachments", true);

        allowCOD = builder
            .comment("Allow cash on delivery mails")
            .define("allowCOD", true);

        maxItemAttachments = builder
            .comment("Maximum number of item attachments per mail")
            .defineInRange("maxItemAttachments", 12, 1, 54);

        mailExpiryDays = builder
            .comment("Days before a mail auto-expires")
            .defineInRange("mailExpiryDays", 30, 1, 365);

        sendCost = builder
            .comment("Cost to send a mail (0 = free)")
            .defineInRange("sendCost", 0L, 0L, Long.MAX_VALUE);

        codFeePercent = builder
            .comment("Fee percentage on COD payments (taken from sender's received payment)")
            .defineInRange("codFeePercent", 0, 0, 100);

        allowMailboxCraft = builder
            .comment("Players can craft the mailbox block")
            .define("allowMailboxCraft", true);

        allowReadReceipt = builder
            .comment("Allow read receipts on mails")
            .define("allowReadReceipt", true);

        readReceiptCost = builder
            .comment("Cost to enable read receipt on a mail (0 = free)")
            .defineInRange("readReceiptCost", 0L, 0L, Long.MAX_VALUE);

        sendCostPerItem = builder
            .comment("Additional cost per item attachment (0 = free)")
            .defineInRange("sendCostPerItem", 0L, 0L, Long.MAX_VALUE);

        builder.pop();
    }
}

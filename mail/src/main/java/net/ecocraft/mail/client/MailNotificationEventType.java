package net.ecocraft.mail.client;

import net.ecocraft.gui.core.ToastLevel;

public enum MailNotificationEventType {
    NEW_MAIL("new_mail", MailNotificationChannel.BOTH, ToastLevel.INFO),
    COD_RECEIVED("cod_received", MailNotificationChannel.BOTH, ToastLevel.SUCCESS),
    MAIL_RETURNED("mail_returned", MailNotificationChannel.BOTH, ToastLevel.WARNING);

    private final String key;
    private final MailNotificationChannel defaultChannel;
    private final ToastLevel toastLevel;

    MailNotificationEventType(String key, MailNotificationChannel defaultChannel, ToastLevel toastLevel) {
        this.key = key;
        this.defaultChannel = defaultChannel;
        this.toastLevel = toastLevel;
    }

    public String getKey() { return key; }
    public MailNotificationChannel getDefaultChannel() { return defaultChannel; }
    public ToastLevel getToastLevel() { return toastLevel; }

    public static MailNotificationEventType fromKey(String key) {
        for (MailNotificationEventType type : values()) {
            if (type.key.equals(key)) return type;
        }
        return null;
    }
}

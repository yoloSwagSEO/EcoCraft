package net.ecocraft.ah.client;

import net.ecocraft.gui.core.ToastLevel;

public enum NotificationEventType {
    OUTBID("outbid", NotificationChannel.BOTH, ToastLevel.WARNING),
    AUCTION_WON("auction_won", NotificationChannel.BOTH, ToastLevel.SUCCESS),
    AUCTION_LOST("auction_lost", NotificationChannel.BOTH, ToastLevel.ERROR),
    SALE_COMPLETED("sale_completed", NotificationChannel.BOTH, ToastLevel.SUCCESS),
    LISTING_EXPIRED("listing_expired", NotificationChannel.BOTH, ToastLevel.WARNING);

    private final String key;
    private final NotificationChannel defaultChannel;
    private final ToastLevel toastLevel;

    NotificationEventType(String key, NotificationChannel defaultChannel, ToastLevel toastLevel) {
        this.key = key;
        this.defaultChannel = defaultChannel;
        this.toastLevel = toastLevel;
    }

    public String getKey() { return key; }
    public NotificationChannel getDefaultChannel() { return defaultChannel; }
    public ToastLevel getToastLevel() { return toastLevel; }

    public static NotificationEventType fromKey(String key) {
        for (NotificationEventType type : values()) {
            if (type.key.equals(key)) return type;
        }
        return null;
    }
}

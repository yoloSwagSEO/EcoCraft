package net.ecocraft.mail.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class MailNotificationConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailNotificationConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "ecocraft_mail_notifications.json");
    private static final Type MAP_TYPE = new TypeToken<HashMap<String, String>>() {}.getType();

    private static MailNotificationConfig instance;

    private final EnumMap<MailNotificationEventType, MailNotificationChannel> preferences =
            new EnumMap<>(MailNotificationEventType.class);

    private MailNotificationConfig() {
        for (MailNotificationEventType type : MailNotificationEventType.values()) {
            preferences.put(type, type.getDefaultChannel());
        }
    }

    public static MailNotificationConfig getInstance() {
        if (instance == null) {
            instance = new MailNotificationConfig();
            instance.load();
        }
        return instance;
    }

    public MailNotificationChannel getChannel(MailNotificationEventType type) {
        return preferences.getOrDefault(type, type.getDefaultChannel());
    }

    public void setChannel(MailNotificationEventType type, MailNotificationChannel channel) {
        preferences.put(type, channel);
        save();
    }

    private void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try {
            String json = Files.readString(CONFIG_PATH);
            Map<String, String> map = GSON.fromJson(json, MAP_TYPE);
            if (map == null) return;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                MailNotificationEventType type = MailNotificationEventType.fromKey(entry.getKey());
                if (type == null) continue;
                try {
                    MailNotificationChannel channel = MailNotificationChannel.valueOf(entry.getValue());
                    preferences.put(type, channel);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load mail notification config", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<MailNotificationEventType, MailNotificationChannel> entry : preferences.entrySet()) {
                map.put(entry.getKey().getKey(), entry.getValue().name());
            }
            Files.writeString(CONFIG_PATH, GSON.toJson(map));
        } catch (IOException e) {
            LOGGER.error("Failed to save mail notification config", e);
        }
    }
}

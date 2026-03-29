package net.ecocraft.ah.client;

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

public class NotificationConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "ecocraft_ah_notifications.json");
    private static final Type MAP_TYPE = new TypeToken<HashMap<String, String>>() {}.getType();

    private static NotificationConfig instance;

    private final EnumMap<NotificationEventType, NotificationChannel> preferences = new EnumMap<>(NotificationEventType.class);

    private NotificationConfig() {
        for (NotificationEventType type : NotificationEventType.values()) {
            preferences.put(type, type.getDefaultChannel());
        }
    }

    public static NotificationConfig getInstance() {
        if (instance == null) {
            instance = new NotificationConfig();
            instance.load();
        }
        return instance;
    }

    public NotificationChannel getChannel(NotificationEventType type) {
        return preferences.getOrDefault(type, type.getDefaultChannel());
    }

    public void setChannel(NotificationEventType type, NotificationChannel channel) {
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
                NotificationEventType type = NotificationEventType.fromKey(entry.getKey());
                if (type == null) continue;
                try {
                    NotificationChannel channel = NotificationChannel.valueOf(entry.getValue());
                    preferences.put(type, channel);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load notification config", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<NotificationEventType, NotificationChannel> entry : preferences.entrySet()) {
                map.put(entry.getKey().getKey(), entry.getValue().name());
            }
            Files.writeString(CONFIG_PATH, GSON.toJson(map));
        } catch (IOException e) {
            LOGGER.error("Failed to save notification config", e);
        }
    }
}

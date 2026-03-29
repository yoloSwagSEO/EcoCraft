package net.ecocraft.ah.screen;

import net.ecocraft.ah.client.NotificationChannel;
import net.ecocraft.ah.client.NotificationConfig;
import net.ecocraft.ah.client.NotificationEventType;
import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Notifications preferences tab in the Settings screen.
 * Visible to all players (not just OP).
 * Each notification type has a dropdown to choose the channel.
 */
public class NotificationsTab extends BaseWidget {

    private static final Theme THEME = Theme.dark();
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 4;

    private final Font font;

    public NotificationsTab(Font font, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.font = font;
        buildWidgets();
    }

    private void buildWidgets() {
        // Clear existing children
        new ArrayList<>(getChildren()).forEach(this::removeChild);

        List<String> channelLabels = List.of(
                Component.translatable("ecocraft_ah.settings.notif.channel.chat").getString(),
                Component.translatable("ecocraft_ah.settings.notif.channel.toast").getString(),
                Component.translatable("ecocraft_ah.settings.notif.channel.both").getString(),
                Component.translatable("ecocraft_ah.settings.notif.channel.none").getString()
        );
        NotificationChannel[] channelOrder = {
                NotificationChannel.CHAT, NotificationChannel.TOAST,
                NotificationChannel.BOTH, NotificationChannel.NONE
        };

        int contentX = getX() + 8;
        int contentW = getWidth() - 16;
        int y = getY() + 8;

        // Title
        Label title = new Label(font, contentX, y,
                Component.translatable("ecocraft_ah.settings.notifications"), THEME);
        title.setColor(THEME.accent);
        addChild(title);
        y += font.lineHeight + 8;

        // One row per event type
        NotificationConfig config = NotificationConfig.getInstance();
        int dropdownW = (int) (contentW * 0.35);
        int dropdownX = contentX + contentW - dropdownW;

        for (NotificationEventType eventType : NotificationEventType.values()) {
            // Label
            Label label = new Label(font, contentX, y + (ROW_HEIGHT - font.lineHeight) / 2,
                    Component.translatable("ecocraft_ah.settings.notif." + eventType.getKey()), THEME);
            label.setColor(THEME.textLight);
            addChild(label);

            // Dropdown
            int currentIndex = indexOf(channelOrder, config.getChannel(eventType));
            EcoDropdown dropdown = new EcoDropdown(dropdownX, y, dropdownW, ROW_HEIGHT - 4, THEME);
            dropdown.options(channelLabels);
            dropdown.selectedIndex(currentIndex);

            final NotificationEventType type = eventType;
            dropdown.responder(index -> {
                if (index >= 0 && index < channelOrder.length) {
                    config.setChannel(type, channelOrder[index]);
                }
            });
            addChild(dropdown);

            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    private static int indexOf(NotificationChannel[] arr, NotificationChannel target) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) return i;
        }
        return 2; // default to BOTH
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No custom rendering needed — children handle it
    }
}

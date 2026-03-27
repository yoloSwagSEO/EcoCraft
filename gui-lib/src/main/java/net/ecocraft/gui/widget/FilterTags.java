package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Clickable filter pill tags with Theme and disabled state support.
 *
 * <p>Supports two modes:</p>
 * <ul>
 *   <li><b>Radio mode</b> (default): clicking a tag deselects the previous one (single selection)</li>
 *   <li><b>Multi-select mode</b>: clicking a tag toggles it on/off, multiple can be selected simultaneously</li>
 * </ul>
 */
public class FilterTags extends AbstractWidget {

    private static final int TAG_HEIGHT = 20;
    private static final int TAG_GAP = 6;
    private static final int TAG_PADDING_H = 12;

    private final List<Component> tags;
    private final Theme theme;
    private boolean enabled = true;

    // Radio mode fields
    private int activeTag = 0;
    private final IntConsumer onTagChanged;

    // Multi-select mode fields
    private final boolean multiSelect;
    private final Set<Integer> selectedTags = new LinkedHashSet<>();
    private final Consumer<Set<Integer>> onMultiTagChanged;

    /**
     * Radio mode constructor (backward compatible) — single tag selection.
     */
    public FilterTags(int x, int y, List<Component> tags, IntConsumer onTagChanged, Theme theme) {
        super(x, y, 0, TAG_HEIGHT, Component.empty());
        this.tags = tags;
        this.onTagChanged = onTagChanged;
        this.theme = theme;
        this.multiSelect = false;
        this.onMultiTagChanged = null;
        this.width = calculateTotalWidth();
    }

    /**
     * Radio mode constructor with default theme (backward compatible).
     */
    public FilterTags(int x, int y, List<Component> tags, IntConsumer onTagChanged) {
        this(x, y, tags, onTagChanged, Theme.dark());
    }

    /**
     * Multi-select mode constructor — multiple tags can be toggled on/off.
     */
    public FilterTags(int x, int y, List<Component> tags, Consumer<Set<Integer>> onMultiTagChanged, Theme theme, boolean multiSelect) {
        super(x, y, 0, TAG_HEIGHT, Component.empty());
        this.tags = tags;
        this.theme = theme;
        this.multiSelect = multiSelect;
        this.onMultiTagChanged = onMultiTagChanged;
        this.onTagChanged = null;
        this.width = calculateTotalWidth();
    }

    private int calculateTotalWidth() {
        Font font = Minecraft.getInstance().font;
        int total = 0;
        for (Component tag : tags) {
            total += font.width(tag) + TAG_PADDING_H * 2 + TAG_GAP;
        }
        return total - TAG_GAP;
    }

    // --- Radio mode accessors ---

    public int getActiveTag() {
        return activeTag;
    }

    public void setActiveTag(int index) {
        if (index >= 0 && index < tags.size()) {
            this.activeTag = index;
        }
    }

    // --- Multi-select mode accessors ---

    /**
     * Returns the set of currently selected tag indices (multi-select mode).
     * Returns a copy to prevent external modification.
     */
    public Set<Integer> getSelectedTags() {
        return new LinkedHashSet<>(selectedTags);
    }

    /**
     * Sets the selected tags (multi-select mode).
     */
    public void setSelectedTags(Set<Integer> indices) {
        selectedTags.clear();
        for (int idx : indices) {
            if (idx >= 0 && idx < tags.size()) {
                selectedTags.add(idx);
            }
        }
    }

    public boolean isMultiSelect() {
        return multiSelect;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int currentX = getX();

        for (int i = 0; i < tags.size(); i++) {
            Component label = tags.get(i);
            int tagWidth = font.width(label) + TAG_PADDING_H * 2;
            boolean isActive = multiSelect ? selectedTags.contains(i) : (i == activeTag);
            boolean isHovered = enabled && mouseX >= currentX && mouseX < currentX + tagWidth
                    && mouseY >= getY() && mouseY < getY() + TAG_HEIGHT;

            int bg, border, textColor;
            if (!enabled) {
                bg = theme.disabledBg;
                border = theme.disabledBorder;
                textColor = theme.disabledText;
            } else {
                bg = isActive ? theme.accentBgDim : (isHovered ? theme.bgLight : theme.bgMedium);
                border = isActive ? theme.borderAccent : theme.borderLight;
                textColor = isActive ? theme.accent : theme.textGrey;
            }

            graphics.fill(currentX, getY(), currentX + tagWidth, getY() + TAG_HEIGHT, bg);
            graphics.fill(currentX, getY(), currentX + tagWidth, getY() + 1, border);
            graphics.fill(currentX, getY() + TAG_HEIGHT - 1, currentX + tagWidth, getY() + TAG_HEIGHT, border);
            graphics.fill(currentX, getY(), currentX + 1, getY() + TAG_HEIGHT, border);
            graphics.fill(currentX + tagWidth - 1, getY(), currentX + tagWidth, getY() + TAG_HEIGHT, border);

            int textX = currentX + TAG_PADDING_H;
            int textY = getY() + (TAG_HEIGHT - 8) / 2;
            graphics.drawString(font, label, textX, textY, textColor, false);

            currentX += tagWidth + TAG_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || button != 0 || !isMouseOver(mouseX, mouseY)) return false;

        Font font = Minecraft.getInstance().font;
        int currentX = getX();

        for (int i = 0; i < tags.size(); i++) {
            int tagWidth = font.width(tags.get(i)) + TAG_PADDING_H * 2;
            if (mouseX >= currentX && mouseX < currentX + tagWidth) {
                if (multiSelect) {
                    // Toggle selection
                    if (selectedTags.contains(i)) {
                        selectedTags.remove(i);
                    } else {
                        selectedTags.add(i);
                    }
                    if (onMultiTagChanged != null) {
                        onMultiTagChanged.accept(getSelectedTags());
                    }
                } else {
                    // Radio mode
                    if (i != activeTag) {
                        activeTag = i;
                        if (onTagChanged != null) {
                            onTagChanged.accept(i);
                        }
                    }
                }
                return true;
            }
            currentX += tagWidth + TAG_GAP;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + width
                && mouseY >= getY() && mouseY < getY() + TAG_HEIGHT;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

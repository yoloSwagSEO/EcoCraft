package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Clickable filter pill tags extending {@link BaseWidget}.
 *
 * <p>Supports two modes:</p>
 * <ul>
 *   <li><b>Radio mode</b> (default): clicking a tag deselects the previous one (single selection)</li>
 *   <li><b>Multi-select mode</b>: clicking a tag toggles it on/off, multiple can be selected simultaneously</li>
 * </ul>
 *
 * <p>Improvements over legacy {@code FilterTags}:</p>
 * <ul>
 *   <li>Tag widths are cached (computed once in constructor / {@link #setTags})</li>
 *   <li>Uses {@link DrawUtils#drawPanel} for tag backgrounds</li>
 * </ul>
 */
public class EcoFilterTags extends BaseWidget {

    private static final int TAG_HEIGHT = 20;
    private static final int TAG_GAP = 6;
    private static final int TAG_PADDING_H = 12;

    private List<Component> tags;
    private final Theme theme;
    private boolean enabled = true;

    // Cached per-tag widths (text width + 2 * padding)
    private int[] tagWidths;

    // Radio mode fields
    private int activeTag = 0;
    private final IntConsumer onTagChanged;

    // Multi-select mode fields
    private final boolean multiSelect;
    private final Set<Integer> selectedTags = new LinkedHashSet<>();
    private final Consumer<Set<Integer>> onMultiTagChanged;

    // Mouse position cached from last render for hover detection
    private double lastMouseX;
    private double lastMouseY;

    /**
     * Radio mode constructor (backward compatible) — single tag selection.
     */
    public EcoFilterTags(int x, int y, List<Component> tags, IntConsumer onTagChanged, Theme theme) {
        super(x, y, 0, TAG_HEIGHT);
        this.tags = new ArrayList<>(tags);
        this.onTagChanged = onTagChanged;
        this.theme = theme;
        this.multiSelect = false;
        this.onMultiTagChanged = null;
        cacheTagWidths();
    }

    /**
     * Radio mode constructor with default theme (backward compatible).
     */
    public EcoFilterTags(int x, int y, List<Component> tags, IntConsumer onTagChanged) {
        this(x, y, tags, onTagChanged, Theme.dark());
    }

    /**
     * Multi-select mode constructor — multiple tags can be toggled on/off.
     */
    public EcoFilterTags(int x, int y, List<Component> tags, Consumer<Set<Integer>> onMultiTagChanged, Theme theme, boolean multiSelect) {
        super(x, y, 0, TAG_HEIGHT);
        this.tags = new ArrayList<>(tags);
        this.theme = theme;
        this.multiSelect = multiSelect;
        this.onMultiTagChanged = onMultiTagChanged;
        this.onTagChanged = null;
        cacheTagWidths();
    }

    /**
     * Recompute and cache per-tag widths plus total widget width.
     */
    private void cacheTagWidths() {
        Font font = Minecraft.getInstance().font;
        tagWidths = new int[tags.size()];
        int total = 0;
        for (int i = 0; i < tags.size(); i++) {
            tagWidths[i] = font.width(tags.get(i)) + TAG_PADDING_H * 2;
            total += tagWidths[i] + TAG_GAP;
        }
        setSize(tags.isEmpty() ? 0 : total - TAG_GAP, TAG_HEIGHT);
    }

    /**
     * Replace the tag list at runtime. Re-caches widths.
     */
    public void setTags(List<Component> newTags) {
        this.tags = new ArrayList<>(newTags);
        this.activeTag = 0;
        this.selectedTags.clear();
        cacheTagWidths();
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

    // --- Focus ---

    @Override
    public boolean isFocusable() {
        return false;
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        Font font = Minecraft.getInstance().font;
        int currentX = getX();

        for (int i = 0; i < tags.size(); i++) {
            Component label = tags.get(i);
            int tagWidth = tagWidths[i];
            boolean isActive = multiSelect ? selectedTags.contains(i) : (i == activeTag);
            boolean isHovered = enabled
                    && mouseX >= currentX && mouseX < currentX + tagWidth
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

            DrawUtils.drawPanel(graphics, currentX, getY(), tagWidth, TAG_HEIGHT, bg, border);

            int textX = currentX + TAG_PADDING_H;
            int textY = getY() + (TAG_HEIGHT - 8) / 2;
            graphics.drawString(font, label, textX, textY, textColor, false);

            currentX += tagWidth + TAG_GAP;
        }
    }

    // --- Events ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!enabled || button != 0 || !containsPoint(mouseX, mouseY)) return false;

        int currentX = getX();

        for (int i = 0; i < tags.size(); i++) {
            int tagWidth = tagWidths[i];
            if (mouseX >= currentX && mouseX < currentX + tagWidth) {
                if (multiSelect) {
                    if (selectedTags.contains(i)) {
                        selectedTags.remove(i);
                    } else {
                        selectedTags.add(i);
                    }
                    if (onMultiTagChanged != null) {
                        onMultiTagChanged.accept(getSelectedTags());
                    }
                } else {
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
}

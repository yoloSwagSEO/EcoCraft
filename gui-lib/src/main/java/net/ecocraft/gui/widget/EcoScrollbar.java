package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class EcoScrollbar extends AbstractWidget {

    private static final int SCROLLBAR_WIDTH = 8;
    private static final int MIN_THUMB_HEIGHT = 16;

    private float scrollValue = 0f;
    private float contentRatio = 1f;
    private boolean dragging = false;
    private double dragOffset = 0;

    public EcoScrollbar(int x, int y, int height) {
        super(x, y, SCROLLBAR_WIDTH, height, Component.empty());
    }

    public void setContentRatio(float ratio) {
        this.contentRatio = Math.max(0f, Math.min(1f, ratio));
    }

    public float getScrollValue() {
        return scrollValue;
    }

    public void setScrollValue(float value) {
        this.scrollValue = Math.max(0f, Math.min(1f, value));
    }

    public boolean needsScrollbar() {
        return contentRatio < 1f;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!needsScrollbar()) return;

        graphics.fill(getX(), getY(), getX() + width, getY() + height, EcoColors.BG_DARKEST);
        graphics.fill(getX(), getY(), getX() + width, getY() + 1, EcoColors.BORDER);
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, EcoColors.BORDER);

        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, (int) (height * contentRatio));
        int trackSpace = height - thumbHeight;
        int thumbY = getY() + (int) (trackSpace * scrollValue);

        boolean hovered = isMouseOver(mouseX, mouseY);
        int thumbColor = (hovered || dragging) ? EcoColors.GOLD : EcoColors.GOLD_BG_DIM;
        int thumbBorder = (hovered || dragging) ? EcoColors.BORDER_GOLD : EcoColors.BORDER_LIGHT;

        graphics.fill(getX() + 1, thumbY, getX() + width - 1, thumbY + thumbHeight, thumbColor);
        graphics.fill(getX() + 1, thumbY, getX() + width - 1, thumbY + 1, thumbBorder);
        graphics.fill(getX() + 1, thumbY + thumbHeight - 1, getX() + width - 1, thumbY + thumbHeight, thumbBorder);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!needsScrollbar() || button != 0 || !isMouseOver(mouseX, mouseY)) return false;

        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, (int) (height * contentRatio));
        int trackSpace = height - thumbHeight;
        int thumbY = getY() + (int) (trackSpace * scrollValue);

        if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
            dragging = true;
            dragOffset = mouseY - thumbY;
        } else {
            float clickRatio = (float) (mouseY - getY() - thumbHeight / 2.0) / (height - thumbHeight);
            setScrollValue(clickRatio);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging) return false;

        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, (int) (height * contentRatio));
        int trackSpace = height - thumbHeight;
        if (trackSpace <= 0) return false;

        float newValue = (float) (mouseY - getY() - dragOffset) / trackSpace;
        setScrollValue(newValue);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!needsScrollbar()) return false;
        setScrollValue(scrollValue - (float) (scrollY * 0.05));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

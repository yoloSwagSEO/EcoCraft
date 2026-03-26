package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class EcoSearchBar extends EditBox {

    private final Consumer<String> onSearch;

    public EcoSearchBar(Font font, int x, int y, int width, int height,
                        Component placeholder, Consumer<String> onSearch) {
        super(font, x, y, width, height, placeholder);
        this.onSearch = onSearch;
        this.setHint(placeholder);
        this.setBordered(false);
        this.setTextColor(EcoColors.TEXT_LIGHT & 0x00FFFFFF);
        this.setResponder(onSearch);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int bg = isFocused() ? EcoColors.BG_MEDIUM : EcoColors.BG_DARK;
        int border = isFocused() ? EcoColors.BORDER_GOLD : EcoColors.BORDER;

        graphics.fill(getX() - 2, getY() - 2, getX() + width + 2, getY() + height + 2, bg);
        graphics.fill(getX() - 2, getY() - 2, getX() + width + 2, getY() - 1, border);
        graphics.fill(getX() - 2, getY() + height + 1, getX() + width + 2, getY() + height + 2, border);
        graphics.fill(getX() - 2, getY() - 2, getX() - 1, getY() + height + 2, border);
        graphics.fill(getX() + width + 1, getY() - 2, getX() + width + 2, getY() + height + 2, border);

        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}

package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Invisible root container. Never renders itself.
 * All top-level widgets are children of the root.
 */
public class RootNode extends BaseWidget {

    public RootNode() {
        super(0, 0, 0, 0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Root is invisible — only children render
    }

    @Override
    public boolean containsPoint(double mx, double my) {
        return true; // Root covers entire screen
    }
}

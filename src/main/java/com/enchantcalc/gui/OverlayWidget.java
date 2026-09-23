package com.enchantcalc.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Draws the whole overlay as one of the screen's widgets: after the anvil background, before the
 * slot items and tooltips. It never takes input itself; {@link AnvilOverlay} handles that through
 * Fabric's screen events, whose shape is the same for every widget and every version.
 */
final class OverlayWidget extends AbstractWidget {
    private final AnvilOverlay overlay;

    OverlayWidget(AnvilOverlay overlay) {
        super(0, 0, 0, 0, Component.empty());
        this.overlay = overlay;
        this.active = false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        overlay.render(new Canvas(graphics, overlay.screen()), mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}

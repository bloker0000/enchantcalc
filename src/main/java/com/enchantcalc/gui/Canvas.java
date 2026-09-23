package com.enchantcalc.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Drawing primitives for the overlay. Every Minecraft-version difference in 2D drawing lives here,
 * so the rest of the GUI code is identical on all supported versions.
 */
public final class Canvas {
    private final GuiGraphicsExtractor graphics;
    private final Screen screen;
    private final Font font;

    public Canvas(GuiGraphicsExtractor graphics, Screen screen) {
        this.graphics = graphics;
        this.screen = screen;
        this.font = Minecraft.getInstance().font;
    }

    public Font font() {
        return font;
    }

    public void fill(int x1, int y1, int x2, int y2, int argb) {
        graphics.fill(x1, y1, x2, y2, argb);
    }

    public void fill(Rect rect, int argb) {
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), argb);
    }

    /** A 1px rectangle outline inside {@code rect}. */
    public void outline(Rect rect, int argb) {
        fill(rect.x(), rect.y(), rect.right(), rect.y() + 1, argb);
        fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), argb);
        fill(rect.x(), rect.y() + 1, rect.x() + 1, rect.bottom() - 1, argb);
        fill(rect.right() - 1, rect.y() + 1, rect.right(), rect.bottom() - 1, argb);
    }

    public void text(String text, int x, int y, int argb, boolean shadow) {
        //? if >=26.1 {
        graphics.text(font, text, x, y, argb, shadow);
        //?} else {
        /*graphics.drawString(font, text, x, y, argb, shadow);
        *///?}
    }

    public void text(Component text, int x, int y, int argb, boolean shadow) {
        //? if >=26.1 {
        graphics.text(font, text, x, y, argb, shadow);
        //?} else {
        /*graphics.drawString(font, text, x, y, argb, shadow);
        *///?}
    }

    /** Draws {@code text} cut to {@code maxWidth} pixels, ending in an ellipsis when it had to be cut. */
    public void textClipped(String text, int x, int y, int maxWidth, int argb, boolean shadow) {
        text(ellipsize(font, text, maxWidth), x, y, argb, shadow);
    }

    public void textCentered(String text, int centerX, int y, int argb, boolean shadow) {
        text(text, centerX - font.width(text) / 2, y, argb, shadow);
    }

    public void text(FormattedCharSequence text, int x, int y, int argb, boolean shadow) {
        //? if >=26.1 {
        graphics.text(font, text, x, y, argb, shadow);
        //?} else {
        /*graphics.drawString(font, text, x, y, argb, shadow);
        *///?}
    }

    /** Word-wraps {@code text} to the width of {@code area} and centres the lines in it. */
    public void textWrappedCentered(Component text, Rect area, int argb) {
        List<FormattedCharSequence> lines = font.split(text, area.width() - 8);
        int y = area.y() + (area.height() - lines.size() * (font.lineHeight + 1)) / 2;
        for (FormattedCharSequence line : lines) {
            text(line, area.x() + (area.width() - font.width(line)) / 2, y, argb, false);
            y += font.lineHeight + 1;
        }
    }

    public void item(ItemStack stack, int x, int y) {
        //? if >=26.1 {
        graphics.item(stack, x, y);
        //?} else {
        /*graphics.renderItem(stack, x, y);
        *///?}
    }

    /** Restricts drawing to {@code rect} until {@link #popClip()}. */
    public void pushClip(Rect rect) {
        graphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
    }

    public void popClip() {
        graphics.disableScissor();
    }

    /** Shows a tooltip after everything else on screen has been drawn. */
    public void tooltip(List<Component> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        //? if >=1.21.6 {
        graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        //?} else {
        /*screen.setTooltipForNextRenderPass(lines.stream().map(Component::getVisualOrderText).toList());
        *///?}
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int room = maxWidth - font.width(ellipsis);
        return room <= 0 ? "" : font.plainSubstrByWidth(text, room).stripTrailing() + ellipsis;
    }
}

package com.enchantcalc.gui;

import com.enchantcalc.EnchantCalc;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Optional;

/**
 * Panel colours taken from the anvil texture of the active resource pack, so the overlay looks like
 * part of the anvil window in the default textures and in dark or recoloured packs alike.
 */
public record Theme(
    int panel, int highlight, int shadow, int outline,
    int well, int wellHighlight, int wellShadow,
    int text, int textMuted, int rowHover, int rowSelected, int accentText,
    int good, int caution, int warning, int curse
) {
    private static final Identifier ANVIL_TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/anvil.png");

    /** Colours of the vanilla anvil texture; used when the texture cannot be read. */
    public static final Theme VANILLA = derive(0xFFC6C6C6, 0xFFFFFFFF, 0xFF555555, 0xFF000000, 0xFF8B8B8B, 0xFFFFFFFF, 0xFF373737);

    /** Samples the anvil texture of the current resource pack stack. */
    public static Theme load() {
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(ANVIL_TEXTURE);
            if (resource.isEmpty()) {
                return VANILLA;
            }
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                return fromTexture(image);
            }
        } catch (Exception e) {
            EnchantCalc.LOGGER.debug("Could not sample the anvil texture, using default colours", e);
            return VANILLA;
        }
    }

    private static Theme fromTexture(NativeImage image) {
        // Coordinates in the vanilla 256x256 layout; high-resolution packs scale them.
        int panel = sample(image, 8, 80);
        int well = sample(image, 34, 55);
        if (alpha(panel) < 200 || alpha(well) < 200) {
            return VANILLA;
        }
        return derive(panel, sample(image, 1, 20), sample(image, 174, 20), sample(image, 0, 20),
            well, sample(image, 43, 63), sample(image, 26, 47));
    }

    private static Theme derive(int panel, int highlight, int shadow, int outline, int slot, int slotHighlight, int slotShadow) {
        boolean dark = luminance(panel) < 0.42;
        // The list background sits between the panel and slot colours so text stays readable.
        int well = blend(panel, slot, 0.45);
        return new Theme(
            opaque(panel), opaque(highlight), opaque(shadow), opaque(outline),
            opaque(well), opaque(slotHighlight), opaque(slotShadow),
            dark ? 0xFFE6E6E6 : 0xFF3F3F3F,
            dark ? 0xFFA2A2A2 : 0xFF6B6B6B,
            blend(well, 0xFFFFFFFF, dark ? 0.10 : 0.24),
            blend(well, 0xFF4CC24C, dark ? 0.32 : 0.40),
            dark ? 0xFFA6FF8F : 0xFF0E420E,
            dark ? 0xFF7CFC6A : 0xFF1F6A1F,
            dark ? 0xFFFFC04D : 0xFF8A5200,
            dark ? 0xFFFF7070 : 0xFFA61B1B,
            dark ? 0xFFFF8080 : 0xFF9E2020
        );
    }

    private static int sample(NativeImage image, int x, int y) {
        int px = Math.min(image.getWidth() - 1, x * image.getWidth() / 256);
        int py = Math.min(image.getHeight() - 1, y * image.getHeight() / 256);
        //? if >=1.21.2 {
        return image.getPixel(px, py);
        //?} else {
        /*int abgr = image.getPixelRGBA(px, py);
        return (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >>> 16) & 0xFF);
        *///?}
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }

    private static int opaque(int argb) {
        return argb | 0xFF000000;
    }

    private static double luminance(int argb) {
        return (0.2126 * ((argb >> 16) & 0xFF) + 0.7152 * ((argb >> 8) & 0xFF) + 0.0722 * (argb & 0xFF)) / 255.0;
    }

    /** Mixes {@code b} into {@code a} by {@code amount} (0..1); the result is opaque. */
    public static int blend(int a, int b, double amount) {
        int r = mix((a >> 16) & 0xFF, (b >> 16) & 0xFF, amount);
        int g = mix((a >> 8) & 0xFF, (b >> 8) & 0xFF, amount);
        int bl = mix(a & 0xFF, b & 0xFF, amount);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int mix(int a, int b, double amount) {
        return (int) Math.round(a + (b - a) * amount);
    }

    /** Draws a panel with the vanilla container frame: black rounded outline, light top-left and dark bottom-right bevel. */
    public void drawPanel(Canvas canvas, Rect r) {
        int x = r.x();
        int y = r.y();
        int right = r.right();
        int bottom = r.bottom();
        canvas.fill(x + 1, y + 1, right - 1, bottom - 1, panel);
        canvas.fill(x + 2, y, right - 2, y + 1, outline);
        canvas.fill(x + 2, bottom - 1, right - 2, bottom, outline);
        canvas.fill(x, y + 2, x + 1, bottom - 2, outline);
        canvas.fill(right - 1, y + 2, right, bottom - 2, outline);
        canvas.fill(x + 1, y + 1, x + 2, y + 2, outline);
        canvas.fill(right - 2, y + 1, right - 1, y + 2, outline);
        canvas.fill(x + 1, bottom - 2, x + 2, bottom - 1, outline);
        canvas.fill(right - 2, bottom - 2, right - 1, bottom - 1, outline);
        canvas.fill(x + 2, y + 1, right - 3, y + 3, highlight);
        canvas.fill(x + 1, y + 2, x + 3, bottom - 3, highlight);
        canvas.fill(x + 3, bottom - 3, right - 2, bottom - 1, shadow);
        canvas.fill(right - 3, y + 3, right - 1, bottom - 2, shadow);
    }

    /** Draws a sunken area like an inventory slot: dark top-left edge, light bottom-right edge. */
    public void drawWell(Canvas canvas, Rect r) {
        canvas.fill(r, well);
        canvas.fill(r.x(), r.y(), r.right() - 1, r.y() + 1, wellShadow);
        canvas.fill(r.x(), r.y() + 1, r.x() + 1, r.bottom() - 1, wellShadow);
        canvas.fill(r.x() + 1, r.bottom() - 1, r.right(), r.bottom(), wellHighlight);
        canvas.fill(r.right() - 1, r.y() + 1, r.right(), r.bottom() - 1, wellHighlight);
    }

    /** A level-cost chip styled like the anvil's own "Enchantment Cost" label. */
    public int drawCost(Canvas canvas, String text, int right, int y, boolean tooExpensive) {
        int width = canvas.font().width(text);
        int left = right - width - 4;
        canvas.fill(left, y - 2, right, y + 10, 0x4F000000);
        canvas.text(text, left + 2, y, tooExpensive ? 0xFFFF6060 : 0xFF80FF20, true);
        return left;
    }
}

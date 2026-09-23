package com.enchantcalc.gui;

/** An axis-aligned screen rectangle in GUI pixels. */
public record Rect(int x, int y, int width, int height) {
    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

    public Rect inset(int amount) {
        return new Rect(x + amount, y + amount, width - 2 * amount, height - 2 * amount);
    }

    /**
     * A row height between {@code min} and {@code max} at which a bordered list area (1px border) holds a
     * whole number of rows, so no row is cut off and little space is left over.
     */
    public static int rowHeight(Rect list, int min, int max) {
        int inside = list.height() - 2;
        int rows = Math.max(1, inside / min);
        return Math.clamp(inside / rows, min, max);
    }

    /** Shrinks a bordered list area (1px border) so its inside holds a whole number of rows. */
    public static Rect wholeRows(Rect list, int rowHeight) {
        int rows = Math.max(1, (list.height() - 2) / rowHeight);
        return new Rect(list.x(), list.y(), list.width(), rows * rowHeight + 2);
    }

    /** The part of this rectangle from {@code top} pixels below its top edge down to {@code bottomGap} above its bottom. */
    public Rect band(int top, int bottomGap) {
        return new Rect(x, y + top, width, Math.max(0, height - top - bottomGap));
    }
}

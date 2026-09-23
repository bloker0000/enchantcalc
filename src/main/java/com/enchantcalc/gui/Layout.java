package com.enchantcalc.gui;

/**
 * Where the panels go. With room on both sides of the anvil there is one panel each side ("wide");
 * otherwise the anvil moves left and a single tabbed panel sits to its right ("compact").
 *
 * @param left         enchantment panel (in compact mode the shared panel)
 * @param right        plan panel (in compact mode the shared panel)
 * @param collapsedTab the button that reopens the panels while they are hidden
 * @param shift        how far the anvil window moves horizontally
 */
public record Layout(boolean visible, boolean compact, Rect left, Rect right, Rect collapsedTab, int shift) {
    static final int GAP = 4;
    static final int MARGIN = 4;
    static final int PADDING = 5;
    static final int MIN_WIDTH = 124;
    static final int MAX_WIDTH = 176;
    static final int COMPACT_MIN_WIDTH = 100;
    static final int COMPACT_MAX_WIDTH = 196;
    static final int TAB_HEIGHT = 16;

    public static Layout compute(int screenWidth, int leftPos, int topPos, int imageWidth, int imageHeight, boolean visible) {
        if (!visible) {
            return new Layout(false, false, null, null, new Rect(leftPos + imageWidth - 3, topPos + 4, 24, 24), 0);
        }
        int side = Math.min(leftPos, screenWidth - leftPos - imageWidth) - GAP - MARGIN;
        if (side >= MIN_WIDTH) {
            int width = Math.min(side, MAX_WIDTH);
            return new Layout(true, false,
                new Rect(leftPos - GAP - width, topPos, width, imageHeight),
                new Rect(leftPos + imageWidth + GAP, topPos, width, imageHeight), null, 0);
        }
        int width = Math.clamp(screenWidth - imageWidth - GAP - 2 * MARGIN, COMPACT_MIN_WIDTH, COMPACT_MAX_WIDTH);
        int newLeft = Math.max(MARGIN, (screenWidth - imageWidth - GAP - width) / 2);
        Rect panel = new Rect(newLeft + imageWidth + GAP, topPos, width, imageHeight);
        return new Layout(true, true, panel, panel, null, newLeft - leftPos);
    }

    /** Row of tabs (compact mode), with room for the hide button at its right end. */
    public Rect tabs() {
        Rect inner = left.inset(PADDING);
        return new Rect(inner.x(), inner.y(), inner.width(), TAB_HEIGHT);
    }

    public Rect enchantContent() {
        Rect inner = left.inset(PADDING);
        return compact ? inner.band(TAB_HEIGHT + 4, 0) : inner;
    }

    public Rect planContent() {
        Rect inner = right.inset(PADDING);
        return compact ? inner.band(TAB_HEIGHT + 4, 0) : inner;
    }

    public boolean contains(double x, double y) {
        return visible ? left.contains(x, y) || right.contains(x, y) : collapsedTab.contains(x, y);
    }
}

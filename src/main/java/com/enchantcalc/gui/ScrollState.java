package com.enchantcalc.gui;

/** Scroll position of a list and its draggable scrollbar. */
final class ScrollState {
    static final int BAR_WIDTH = 6;
    private static final int MIN_THUMB = 10;

    private int offset;
    private boolean dragging;
    private int grab;

    int offset() {
        return offset;
    }

    void reset() {
        offset = 0;
        dragging = false;
    }

    void clamp(int content, int view) {
        offset = Math.clamp(offset, 0, Math.max(0, content - view));
    }

    void scroll(double notches, int step, int content, int view) {
        offset -= (int) Math.round(notches * step);
        clamp(content, view);
    }

    /** Scrolls the least amount that shows the band from {@code top} to {@code bottom} (content pixels). */
    void reveal(int top, int bottom, int content, int view) {
        if (top < offset) {
            offset = top;
        } else if (bottom > offset + view) {
            offset = bottom - view;
        }
        clamp(content, view);
    }

    static Rect track(Rect list) {
        return new Rect(list.right() - BAR_WIDTH, list.y(), BAR_WIDTH, list.height());
    }

    private Rect thumb(Rect track, int content, int view) {
        int height = Math.clamp((long) track.height() * view / Math.max(1, content), MIN_THUMB, track.height());
        int range = Math.max(1, content - view);
        int y = track.y() + (track.height() - height) * offset / range;
        return new Rect(track.x(), y, track.width(), height);
    }

    /** Starts dragging when the track is clicked; clicking beside the thumb jumps it under the mouse. */
    boolean press(double mouseX, double mouseY, Rect track, int content, int view) {
        if (!track.contains(mouseX, mouseY)) {
            return false;
        }
        Rect thumb = thumb(track, content, view);
        grab = thumb.contains(mouseX, mouseY) ? (int) mouseY - thumb.y() : thumb.height() / 2;
        dragging = true;
        drag(mouseY, track, content, view);
        return true;
    }

    /** Follows the mouse from {@link #press} until {@link #release}, which the overlay calls when the button goes up. */
    void drag(double mouseY, Rect track, int content, int view) {
        if (!dragging) {
            return;
        }
        Rect thumb = thumb(track, content, view);
        int free = track.height() - thumb.height();
        if (free > 0) {
            offset = (int) Math.round(((mouseY - grab) - track.y()) * (content - view) / free);
        }
        clamp(content, view);
    }

    void release() {
        dragging = false;
    }

    boolean dragging() {
        return dragging;
    }

    void draw(Canvas canvas, Theme theme, Rect track, int content, int view) {
        canvas.fill(track, Theme.blend(theme.well(), 0xFF000000, 0.18));
        Rect thumb = thumb(track, content, view);
        canvas.fill(thumb, theme.panel());
        canvas.fill(thumb.x(), thumb.y(), thumb.right() - 1, thumb.y() + 1, theme.highlight());
        canvas.fill(thumb.x(), thumb.y(), thumb.x() + 1, thumb.bottom() - 1, theme.highlight());
        canvas.fill(thumb.x() + 1, thumb.bottom() - 1, thumb.right(), thumb.bottom(), theme.shadow());
        canvas.fill(thumb.right() - 1, thumb.y() + 1, thumb.right(), thumb.bottom(), theme.shadow());
    }
}

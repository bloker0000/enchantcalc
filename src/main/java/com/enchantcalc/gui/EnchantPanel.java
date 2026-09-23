package com.enchantcalc.gui;

import com.enchantcalc.data.EnchantInfo;
import com.enchantcalc.data.Target;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Left panel: the enchantments that fit the item, with a level picker per row. */
final class EnchantPanel {
    private static final int MIN_ROW = 16;
    private static final int MAX_ROW = 20;
    private static final int HEADER = 16;
    private static final int SEARCH_TOP = 18;
    private static final int LIST_TOP = 37;
    private static final int FOOTER = 22;

    /**
     * One enchantment row.
     *
     * @param existing    level already on the item
     * @param ownedLevels levels of this enchantment found in the inventory, highest first
     * @param conflict    what blocks this enchantment, if anything
     * @param onItem      whether {@code conflict} is on the item (else it is another selection)
     */
    record Row(EnchantInfo info, int selected, int existing, List<Integer> ownedLevels, EnchantInfo conflict, boolean onItem) {
        boolean maxed() {
            return existing >= info.maxLevel();
        }

        boolean enabled() {
            return conflict == null && !maxed();
        }
    }

    private final AnvilOverlay overlay;
    private final ScrollState scroll = new ScrollState();
    private List<Row> rows = List.of();

    EnchantPanel(AnvilOverlay overlay) {
        this.overlay = overlay;
    }

    Rect content() {
        return overlay.layout().enchantContent();
    }

    Rect searchBox() {
        Rect c = content();
        return new Rect(c.x(), c.y() + SEARCH_TOP, c.width(), 16);
    }

    /** The enchantment list, sized to whole rows so none is cut off when the list is not scrolled. */
    Rect list() {
        return Rect.wholeRows(listArea(), row());
    }

    private Rect listArea() {
        return content().band(LIST_TOP, FOOTER);
    }

    /** Row height between MIN_ROW and MAX_ROW that fills the list with whole rows. */
    int row() {
        return Rect.rowHeight(listArea(), MIN_ROW, MAX_ROW);
    }

    int footerY() {
        return content().bottom() - 18;
    }

    void resetScroll() {
        scroll.reset();
    }

    void rebuild() {
        Target target = overlay.target();
        if (target == null) {
            rows = List.of();
            return;
        }
        Map<EnchantInfo, Integer> wanted = overlay.wanted();
        Map<EnchantInfo, List<Integer>> owned = overlay.ownedLevels();
        String query = overlay.searchText().trim().toLowerCase(Locale.ROOT);
        List<Row> list = new ArrayList<>();
        for (EnchantInfo info : target.applicable()) {
            if (!query.isEmpty() && !info.name().getString().toLowerCase(Locale.ROOT).contains(query)
                && !info.id().contains(query)) {
                continue;
            }
            int selected = wanted.getOrDefault(info, 0);
            EnchantInfo conflict = target.conflictOnItem(info);
            boolean onItem = conflict != null;
            if (conflict == null && selected == 0) {
                for (EnchantInfo other : wanted.keySet()) {
                    if (!info.compatibleWith(other)) {
                        conflict = other;
                        break;
                    }
                }
            }
            list.add(new Row(info, selected, target.existingLevel(info), owned.getOrDefault(info, List.of()), conflict, onItem));
        }
        rows = list;
    }

    void render(Canvas canvas, int mouseX, int mouseY) {
        Theme theme = overlay.theme();
        renderHeader(canvas, mouseX, mouseY);
        Rect list = list();
        theme.drawWell(canvas, list);
        Rect inner = list.inset(1);
        if (rows.isEmpty()) {
            canvas.textWrappedCentered(emptyMessage(), inner, theme.textMuted());
            return;
        }
        int content = rows.size() * row();
        boolean bar = content > inner.height();
        scroll.clamp(content, inner.height());
        Rect rowArea = bar ? new Rect(inner.x(), inner.y(), inner.width() - ScrollState.BAR_WIDTH, inner.height()) : inner;
        Row hovered = null;
        canvas.pushClip(inner);
        for (int i = scroll.offset() / row(); i < rows.size(); i++) {
            int y = inner.y() + i * row() - scroll.offset();
            if (y >= inner.bottom()) {
                break;
            }
            Rect r = new Rect(rowArea.x(), y, rowArea.width(), row());
            boolean over = r.contains(mouseX, mouseY) && inner.contains(mouseX, mouseY) && !scroll.dragging();
            renderRow(canvas, rows.get(i), r, over);
            if (over) {
                hovered = rows.get(i);
            }
        }
        canvas.popClip();
        if (bar) {
            scroll.draw(canvas, theme, ScrollState.track(inner), content, inner.height());
        }
        if (hovered != null) {
            canvas.tooltip(tooltip(hovered), mouseX, mouseY);
        }
    }

    private void renderHeader(Canvas canvas, int mouseX, int mouseY) {
        Theme theme = overlay.theme();
        Rect c = content();
        Rect header = new Rect(c.x(), c.y(), c.width(), HEADER);
        Target target = overlay.target();
        if (target == null) {
            canvas.text(Text.tr("title").getString(), header.x() + 1, header.y() + 4, theme.text(), false);
            return;
        }
        canvas.item(target.stack(), header.x(), header.y());
        canvas.textClipped(target.stack().getHoverName().getString(), header.x() + 19, header.y() + 4,
            header.width() - 20, theme.text(), false);
        if (header.contains(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(target.stack().getHoverName());
            if (!target.existing().isEmpty()) {
                Text.addEnchantmentLines(lines, target.existing());
            }
            lines.add(Text.tr("target.prior_work", target.repairCost()).withStyle(ChatFormatting.GRAY));
            if (target.stacked()) {
                lines.add(Text.tr("plan.stacked").withStyle(ChatFormatting.RED));
            }
            canvas.tooltip(lines, mouseX, mouseY);
        }
    }

    private void renderRow(Canvas canvas, Row row, Rect r, boolean hovered) {
        Theme theme = overlay.theme();
        if (row.selected() > 0) {
            canvas.fill(r, theme.rowSelected());
        } else if (hovered && row.enabled()) {
            canvas.fill(r, theme.rowHover());
        }
        if (!row.ownedLevels().isEmpty()) {
            canvas.item(overlay.bookIcon(), r.x() + 1, r.y() + (r.height() - 16) / 2);
        }
        String level = levelLabel(row);
        int levelWidth = canvas.font().width(level);
        int textY = r.y() + (r.height() - 8) / 2;
        int nameColor = !row.enabled() ? theme.textMuted() : row.info().curse() ? theme.curse() : theme.text();
        canvas.textClipped(row.info().name().getString(), r.x() + 19, textY, r.width() - 26 - levelWidth, nameColor, false);
        canvas.text(level, r.right() - 3 - levelWidth, textY, row.selected() > 0 ? theme.accentText() : theme.textMuted(), false);
    }

    private static String levelLabel(Row row) {
        if (row.selected() > 0) {
            return row.info().maxLevel() == 1 ? "✔" : EnchantInfo.levelName(row.selected()).getString();
        }
        if (row.maxed()) {
            return "✔";
        }
        if (row.conflict() != null || row.info().maxLevel() == 1) {
            return "";
        }
        return EnchantInfo.levelName(row.info().maxLevel()).getString();
    }

    private Component emptyMessage() {
        Target target = overlay.target();
        if (target == null) {
            return Text.tr("empty.no_item");
        }
        if (target.applicable().isEmpty()) {
            return Text.tr("empty.not_enchantable");
        }
        return Text.tr("empty.no_matches");
    }

    private List<Component> tooltip(Row row) {
        EnchantInfo info = row.info();
        List<Component> lines = new ArrayList<>();
        int shown = row.selected() > 0 ? row.selected() : info.maxLevel();
        lines.add(info.fullName(shown).copy().withStyle(info.curse() ? ChatFormatting.RED : ChatFormatting.WHITE));
        if (info.maxLevel() > 1) {
            lines.add(Text.tr("enchant.max_level", EnchantInfo.levelName(info.maxLevel())).withStyle(ChatFormatting.GRAY));
        }
        lines.add(Text.tr("enchant.book_cost", info.bookMultiplier()).withStyle(ChatFormatting.GRAY));
        if (row.existing() > 0) {
            lines.add((row.maxed() ? Text.tr("enchant.maxed") : Text.tr("enchant.on_item", EnchantInfo.levelName(row.existing())))
                .withStyle(ChatFormatting.GRAY));
        }
        if (!row.ownedLevels().isEmpty()) {
            String levels = row.ownedLevels().stream()
                .map(level -> EnchantInfo.levelName(level).getString())
                .collect(Collectors.joining(", "));
            lines.add(Text.tr("enchant.owned", levels).withStyle(ChatFormatting.GREEN));
        }
        if (row.conflict() != null) {
            lines.add(Text.tr(row.onItem() ? "enchant.conflict_item" : "enchant.conflict", row.conflict().name())
                .withStyle(ChatFormatting.RED));
        } else if (!row.maxed()) {
            lines.add(Component.empty());
            lines.add(Text.tr("enchant.controls").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Text.tr("enchant.controls_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    /** Handles a click in this panel's list; returns whether it was inside the list. */
    boolean click(double x, double y, MouseButton button, boolean shift) {
        Rect list = list();
        if (!list.contains(x, y)) {
            return false;
        }
        Rect inner = list.inset(1);
        int content = rows.size() * row();
        if (content > inner.height() && button == MouseButton.LEFT
            && scroll.press(x, y, ScrollState.track(inner), content, inner.height())) {
            return true;
        }
        Row row = rowAt(x, y);
        if (row != null && row.enabled() && button != MouseButton.OTHER) {
            int level = nextLevel(row, button, shift);
            if (level != row.selected()) {
                overlay.setLevel(row.info(), level);
                overlay.playClick();
            }
        }
        return true;
    }

    /** Screen area of the row for enchantment {@code id} after scrolling it into view, or {@code null}. */
    Rect rowRect(String id) {
        Rect inner = list().inset(1);
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).info().id().equals(id)) {
                scroll.reveal(i * row(), (i + 1) * row(), rows.size() * row(), inner.height());
                return new Rect(inner.x(), inner.y() + i * row() - scroll.offset(), inner.width() - ScrollState.BAR_WIDTH, row());
            }
        }
        return null;
    }

    private Row rowAt(double x, double y) {
        Rect inner = list().inset(1);
        if (!inner.contains(x, y)) {
            return null;
        }
        int index = (int) ((y - inner.y() + scroll.offset()) / row());
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    /** Left click raises the level and wraps to none, right click lowers it; with Shift: max, or none. */
    static int nextLevel(Row row, MouseButton button, boolean shift) {
        int min = row.existing() + 1;
        int max = row.info().maxLevel();
        int current = row.selected();
        if (button == MouseButton.MIDDLE || (button == MouseButton.RIGHT && shift)) {
            return 0;
        }
        if (button == MouseButton.RIGHT) {
            return current == 0 ? max : current <= min ? 0 : current - 1;
        }
        if (shift) {
            return current == max ? 0 : max;
        }
        return current == 0 ? min : current >= max ? 0 : current + 1;
    }

    boolean scroll(double x, double y, double notches) {
        Rect list = list();
        if (!list.contains(x, y)) {
            return false;
        }
        scroll.scroll(notches, row(), rows.size() * row(), list.inset(1).height());
        return true;
    }

    /** Moves the list along while its scrollbar is dragged. */
    void updateDrag(double mouseY) {
        if (!scroll.dragging()) {
            return;
        }
        Rect inner = list().inset(1);
        scroll.drag(mouseY, ScrollState.track(inner), rows.size() * row(), inner.height());
    }

    void stopDrag() {
        scroll.release();
    }

    /** The scrollbar, or {@code null} while every row fits. Test hook. */
    Rect scrollbar() {
        Rect inner = list().inset(1);
        return rows.size() * row() > inner.height() ? ScrollState.track(inner) : null;
    }

    int scrollOffset() {
        return scroll.offset();
    }
}

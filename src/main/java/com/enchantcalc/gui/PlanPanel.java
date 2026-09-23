package com.enchantcalc.gui;

import com.enchantcalc.core.AnvilMath;
import com.enchantcalc.data.Target;
import com.enchantcalc.plan.PlanView;
import com.enchantcalc.plan.Progress;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Right panel: totals and the list of anvil steps. */
final class PlanPanel {
    private static final int MIN_ROW = 20;
    private static final int MAX_ROW = 24;
    private static final int SUMMARY_TOP = 22;
    private static final int LIST_TOP = 46;
    private static final int FOOTER = 12;

    /** Marker colours linking a step to the anvil slot each item goes in. */
    static final int FIRST_SLOT_COLOR = 0xFF4FA3FF;
    static final int SECOND_SLOT_COLOR = 0xFFFFB02E;

    private final AnvilOverlay overlay;
    private final ScrollState scroll = new ScrollState();

    PlanPanel(AnvilOverlay overlay) {
        this.overlay = overlay;
    }

    Rect content() {
        return overlay.layout().planContent();
    }

    /** The step list, sized to whole rows so none is cut off when the list is not scrolled. */
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

    void resetScroll() {
        scroll.reset();
    }

    /** Scrolls so step {@code index} is visible. */
    void reveal(int index) {
        PlanView view = overlay.view();
        if (view == null || index < 0) {
            return;
        }
        Rect inner = list().inset(1);
        scroll.reveal(index * row(), (index + 1) * row(), view.steps().size() * row(), inner.height());
    }

    void render(Canvas canvas, int mouseX, int mouseY) {
        Theme theme = overlay.theme();
        PlanView view = overlay.view();
        renderSummary(canvas, view, mouseX, mouseY);
        Rect list = list();
        theme.drawWell(canvas, list);
        Rect inner = list.inset(1);
        if (view == null || view.steps().isEmpty()) {
            canvas.textWrappedCentered(emptyMessage(), inner, theme.textMuted());
            return;
        }
        List<PlanView.Step> steps = view.steps();
        int content = steps.size() * row();
        boolean bar = content > inner.height();
        scroll.clamp(content, inner.height());
        Rect rowArea = bar ? new Rect(inner.x(), inner.y(), inner.width() - ScrollState.BAR_WIDTH, inner.height()) : inner;
        PlanView.Step hovered = null;
        int hoveredIndex = -1;
        canvas.pushClip(inner);
        for (int i = scroll.offset() / row(); i < steps.size(); i++) {
            int y = inner.y() + i * row() - scroll.offset();
            if (y >= inner.bottom()) {
                break;
            }
            Rect r = new Rect(rowArea.x(), y, rowArea.width(), row());
            boolean over = r.contains(mouseX, mouseY) && inner.contains(mouseX, mouseY) && !scroll.dragging();
            renderStep(canvas, steps.get(i), i, r, over);
            if (over) {
                hovered = steps.get(i);
                hoveredIndex = i;
            }
        }
        canvas.popClip();
        if (bar) {
            scroll.draw(canvas, theme, ScrollState.track(inner), content, inner.height());
        }
        renderFooter(canvas, view, mouseX, mouseY);
        if (hovered != null) {
            canvas.tooltip(stepTooltip(view, hovered, hoveredIndex), mouseX, mouseY);
        }
    }

    private void renderSummary(Canvas canvas, PlanView view, int mouseX, int mouseY) {
        Theme theme = overlay.theme();
        Rect c = content();
        int top = c.y() + SUMMARY_TOP;
        if (view == null) {
            if (overlay.planning()) {
                canvas.text(Text.tr("plan.calculating").getString(), c.x() + 1, top + 6, theme.textMuted(), false);
            }
            return;
        }
        Rect icon = new Rect(c.x(), top, 16, 16);
        canvas.item(view.result().display(), icon.x(), icon.y());
        int x = c.x() + 20;
        String label = Text.tr("plan.total").getString();
        canvas.text(label, x, top + 1, theme.text(), false);
        x += canvas.font().width(label) + 6;
        String levels = Long.toString(view.plan().totalLevels());
        int chipLeft = x;
        int chipRight = x + canvas.font().width(levels) + 4;
        theme.drawCost(canvas, levels, chipRight, top + 1, !view.plan().withinLimit());
        String xp = Text.tr("plan.xp", Text.number(view.plan().totalExperience())).getString();
        canvas.textClipped(xp, chipRight + 5, top + 1, c.right() - chipRight - 5, theme.textMuted(), false);

        Status status = status(view);
        canvas.textClipped(status.text().getString(), c.x() + 20, top + 12, c.width() - 20, status.color(), false);

        if (icon.contains(mouseX, mouseY)) {
            canvas.tooltip(Screen.getTooltipFromItem(Minecraft.getInstance(), view.result().display()), mouseX, mouseY);
        } else if (new Rect(chipLeft - 2, top - 1, chipRight - chipLeft + 4, 12).contains(mouseX, mouseY)
            || new Rect(c.x() + 20, top + 10, c.width() - 20, 10).contains(mouseX, mouseY)) {
            canvas.tooltip(summaryTooltip(view), mouseX, mouseY);
        }
    }

    private record Status(Component text, int color) {
    }

    private Status status(PlanView view) {
        Theme theme = overlay.theme();
        Target target = view.target();
        Progress progress = overlay.progress();
        if (overlay.planning()) {
            return new Status(Text.tr("plan.calculating"), theme.textMuted());
        }
        if (target.stacked()) {
            return new Status(Text.tr("plan.stacked"), theme.warning());
        }
        if (!view.plan().withinLimit()) {
            return new Status(Text.tr("plan.too_expensive"), theme.warning());
        }
        if (progress != null && progress.complete()) {
            return new Status(Text.tr("plan.done"), theme.good());
        }
        if (view.missingBooks() > 0) {
            return new Status(Text.tr("plan.missing_books", view.missingBooks()), theme.caution());
        }
        if (view.request().creative()) {
            return new Status(Text.tr("plan.creative"), theme.textMuted());
        }
        // Steps already done are paid for; compare the player's levels with what is left.
        long remaining = 0;
        for (int i = 0; i < view.steps().size(); i++) {
            if (progress == null || !progress.done(i)) {
                remaining += view.steps().get(i).levels();
            }
        }
        int have = overlay.playerLevel();
        return have >= remaining
            ? new Status(Text.tr("plan.enough_levels", have), theme.good())
            : new Status(Text.tr("plan.not_enough_levels", have, remaining), theme.warning());
    }

    private List<Component> summaryTooltip(PlanView view) {
        List<Component> lines = new ArrayList<>();
        lines.add(Text.tr("plan.total_levels", view.plan().totalLevels()).withStyle(ChatFormatting.GREEN));
        lines.add(Text.tr("plan.xp_explained", Text.number(view.plan().totalExperience())).withStyle(ChatFormatting.GRAY));
        lines.add(Text.tr("plan.most_expensive", view.plan().maxStepLevels()).withStyle(ChatFormatting.GRAY));
        if (!view.plan().withinLimit()) {
            lines.add(Text.tr("plan.too_expensive_explained").withStyle(ChatFormatting.RED));
        }
        if (view.missingBooks() > 0) {
            lines.add(Text.tr("plan.missing_books_explained").withStyle(ChatFormatting.GOLD));
        }
        if (!view.plan().optimal()) {
            lines.add(Text.tr("plan.approximate").withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    private void renderStep(Canvas canvas, PlanView.Step step, int index, Rect r, boolean hovered) {
        Theme theme = overlay.theme();
        Progress progress = overlay.progress();
        boolean done = progress != null && progress.done(index);
        if (index == overlay.activeStep()) {
            canvas.fill(r, theme.rowSelected());
        } else if (hovered) {
            canvas.fill(r, theme.rowHover());
        }
        int textY = r.y() + (r.height() - 8) / 2;
        int iconY = r.y() + (r.height() - 18) / 2;
        int numberWidth = canvas.font().width(Integer.toString(overlay.view().steps().size()));
        String number = done ? "✔" : Integer.toString(step.number());
        canvas.text(number, r.x() + 1 + numberWidth - canvas.font().width(number), textY, done ? theme.good() : theme.textMuted(), false);
        // The first-slot item as an icon; the second slot always holds a book, so it is named instead.
        int leftX = r.x() + numberWidth + 3;
        canvas.item(step.left().display(), leftX, iconY);
        canvas.fill(leftX + 1, iconY + 17, leftX + 15, iconY + 18, FIRST_SLOT_COLOR);
        canvas.text("+", leftX + 17, textY, theme.textMuted(), false);
        int costLeft = theme.drawCost(canvas, Integer.toString(step.levels()), r.right() - 2, textY, step.tooExpensive());
        int labelX = leftX + 25;
        int labelColor = done ? theme.textMuted() : step.right().missing() ? theme.caution() : theme.text();
        String label = Text.enchantmentsShort(canvas.font(), step.right().enchantments(), costLeft - labelX - 3);
        canvas.text(label, labelX, textY, labelColor, false);
        canvas.fill(labelX, iconY + 17, labelX + canvas.font().width(label), iconY + 18, SECOND_SLOT_COLOR);
    }

    private List<Component> stepTooltip(PlanView view, PlanView.Step step, int index) {
        List<Component> lines = new ArrayList<>();
        lines.add(Text.tr("step.title", step.number(), view.steps().size()).withStyle(ChatFormatting.WHITE));
        lines.add(Text.tr("step.first_slot", step.left().display().getHoverName()).withStyle(style -> style.withColor(FIRST_SLOT_COLOR & 0xFFFFFF)));
        Text.addEnchantmentLines(lines, step.left().enchantments());
        lines.add(Text.tr("step.second_slot", step.right().display().getHoverName()).withStyle(style -> style.withColor(SECOND_SLOT_COLOR & 0xFFFFFF)));
        Text.addEnchantmentLines(lines, step.right().enchantments());
        if (step.tooExpensive()) {
            lines.add(Text.tr("step.too_expensive", step.levels()).withStyle(ChatFormatting.RED));
        } else {
            lines.add(Text.tr("step.cost", step.levels(), Text.number(step.experience())).withStyle(ChatFormatting.GREEN));
        }
        lines.add(Text.tr("step.prior_work_after", step.node().penalty(), AnvilMath.anvilUses(step.node().penalty()))
            .withStyle(ChatFormatting.GRAY));
        if (step.left().missing() || step.right().missing()) {
            lines.add(Text.tr("step.missing").withStyle(ChatFormatting.GOLD));
        }
        Progress progress = overlay.progress();
        if (progress != null && progress.done(index)) {
            lines.add(Text.tr("step.done").withStyle(ChatFormatting.GREEN));
        } else if (index == overlay.activeStep() && overlay.inputsReady()) {
            lines.add(Text.tr("step.ready").withStyle(ChatFormatting.GREEN));
        }
        lines.add(Text.tr("step.click").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private void renderFooter(Canvas canvas, PlanView view, int mouseX, int mouseY) {
        Theme theme = overlay.theme();
        Rect c = content();
        int y = c.bottom() - 9;
        int before = view.target().repairCost();
        int after = view.plan().finalPenalty();
        String text = Text.tr("plan.prior_work", before, after).getString();
        canvas.textClipped(text, c.x() + 1, y, c.width() - 2, theme.textMuted(), false);
        if (new Rect(c.x(), y - 1, c.width(), 10).contains(mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Text.tr("plan.prior_work_explained", after, AnvilMath.anvilUses(after)).withStyle(ChatFormatting.GRAY));
            canvas.tooltip(lines, mouseX, mouseY);
        }
    }

    private Component emptyMessage() {
        Target target = overlay.target();
        if (target == null) {
            return Text.tr("empty.no_item");
        }
        if (target.applicable().isEmpty()) {
            return Text.tr("empty.not_enchantable");
        }
        if (overlay.planning()) {
            return Text.tr("plan.calculating");
        }
        return Text.tr(overlay.layout().compact() ? "empty.no_selection_tab" : "empty.no_selection");
    }

    boolean click(double x, double y, MouseButton button) {
        Rect list = list();
        if (!list.contains(x, y)) {
            return false;
        }
        PlanView view = overlay.view();
        if (view == null) {
            return true;
        }
        Rect inner = list.inset(1);
        int content = view.steps().size() * row();
        if (content > inner.height() && button == MouseButton.LEFT
            && scroll.press(x, y, ScrollState.track(inner), content, inner.height())) {
            return true;
        }
        if (inner.contains(x, y)) {
            int index = (int) ((y - inner.y() + scroll.offset()) / row());
            if (index >= 0 && index < view.steps().size()) {
                overlay.focusStep(index);
                overlay.playClick();
            }
        }
        return true;
    }

    /** Screen area of step {@code index} after scrolling it into view. */
    Rect stepRect(int index) {
        reveal(index);
        Rect inner = list().inset(1);
        return new Rect(inner.x(), inner.y() + index * row() - scroll.offset(), inner.width() - ScrollState.BAR_WIDTH, row());
    }

    boolean scroll(double x, double y, double notches) {
        Rect list = list();
        PlanView view = overlay.view();
        if (!list.contains(x, y)) {
            return false;
        }
        if (view != null) {
            scroll.scroll(notches, row(), view.steps().size() * row(), list.inset(1).height());
        }
        return true;
    }

    /** Moves the list along while its scrollbar is dragged. */
    void updateDrag(double mouseY) {
        if (!scroll.dragging()) {
            return;
        }
        PlanView view = overlay.view();
        if (view == null) {
            scroll.release();
            return;
        }
        Rect inner = list().inset(1);
        scroll.drag(mouseY, ScrollState.track(inner), view.steps().size() * row(), inner.height());
    }

    void stopDrag() {
        scroll.release();
    }
}

package com.enchantcalc.gui;

import com.enchantcalc.EnchantCalc;
import com.enchantcalc.core.Mode;
import com.enchantcalc.data.Books;
import com.enchantcalc.data.Config;
import com.enchantcalc.data.EnchantCatalog;
import com.enchantcalc.data.EnchantInfo;
import com.enchantcalc.data.Target;
import com.enchantcalc.mixin.AbstractContainerScreenAccessor;
import com.enchantcalc.plan.PlanView;
import com.enchantcalc.plan.Planner;
import com.enchantcalc.plan.Progress;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?} else {
/*import org.lwjgl.glfw.GLFW;
*///?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Adds EnchantCalc to one anvil screen: the panels, their widgets, input handling and the plan state. */
public final class AnvilOverlay {
    private static AnvilOverlay current;

    private enum Tab { ENCHANTMENTS, PLAN }

    /** Inputs of the plan being shown, to know when to plan again. */
    private record PlanKey(Target target, Map<EnchantInfo, Integer> wanted, List<Books.Owned> owned, Mode mode, boolean creative) {
    }

    private final AnvilScreen screen;
    private final AnvilMenu menu;
    private final Config config = Config.get();
    private final EnchantPanel enchantPanel = new EnchantPanel(this);
    private final PlanPanel planPanel = new PlanPanel(this);
    private final List<AbstractWidget> widgets = new ArrayList<>();
    // Never static: from 26.1 an ItemStack can only be made once a world's registries are loaded.
    private final ItemStack bookIcon = new ItemStack(Items.ENCHANTED_BOOK);

    private Theme theme;
    private Layout layout;
    private EnchantCatalog catalog;
    private Target target;
    private List<Books.Owned> owned = List.of();
    private Map<EnchantInfo, List<Integer>> ownedLevels = Map.of();
    private PlanView view;
    private Progress progress;
    private PlanKey planKey;
    private boolean planning;
    private int requestId;
    private int focusedStep = -1;
    private int lastCurrent = Integer.MIN_VALUE;
    // One bit per mouse button whose last press the overlay handled; see onRelease.
    private int ownPresses;
    private boolean relayoutPending;
    private boolean closed;
    private Tab tab = Tab.ENCHANTMENTS;
    private String searchText = "";

    private EditBox search;
    private Button ownedButton;
    private Button clearButton;
    private Button modeButton;

    private AnvilOverlay(AnvilScreen screen) {
        this.screen = screen;
        this.menu = screen.getMenu();
    }

    /** Fabric {@code ScreenEvents.AFTER_INIT} listener; runs on every (re)initialisation of a screen. */
    public static void onScreenInit(Minecraft minecraft, Screen screen, int width, int height) {
        if (!(screen instanceof AnvilScreen anvil)) {
            return;
        }
        if (current == null || current.screen != anvil) {
            current = new AnvilOverlay(anvil);
        }
        current.init();
    }

    private AbstractContainerScreenAccessor frame() {
        return (AbstractContainerScreenAccessor) screen;
    }

    private void init() {
        if (theme == null) {
            theme = Theme.load();
        }
        AbstractContainerScreenAccessor frame = frame();
        layout = Layout.compute(screen.width, frame.enchantcalc$getLeftPos(), frame.enchantcalc$getTopPos(),
            frame.enchantcalc$getImageWidth(), frame.enchantcalc$getImageHeight(), config.visible);
        if (layout.shift() != 0) {
            // Move the anvil window aside; its own widgets were placed for the centred window.
            frame.enchantcalc$setLeftPos(frame.enchantcalc$getLeftPos() + layout.shift());
            for (AbstractWidget widget : Screens.getWidgets(screen)) {
                widget.setX(widget.getX() + layout.shift());
            }
        }
        widgets.clear();
        search = null;
        ownedButton = null;
        clearButton = null;
        modeButton = null;
        // Added first so the panels are drawn under their buttons (and under the anvil's slot items).
        add(new OverlayWidget(this));
        if (layout.visible()) {
            createWidgets();
        }
        registerEvents();
        tick();
    }

    private <T extends AbstractWidget> T add(T widget) {
        widgets.add(widget);
        Screens.getWidgets(screen).add(widget);
        return widget;
    }

    private void createWidgets() {
        Rect searchRect = enchantPanel.searchBox();
        search = new EditBox(Minecraft.getInstance().font, searchRect.x(), searchRect.y(), searchRect.width(),
            searchRect.height(), Text.tr("search"));
        search.setHint(Text.tr("search.hint").withStyle(ChatFormatting.GRAY));
        search.setMaxLength(50);
        search.setValue(searchText);
        search.setResponder(this::onSearchChanged);
        add(search);

        Rect enchant = layout.enchantContent();
        int half = (enchant.width() - 4) / 2;
        int footer = enchantPanel.footerY();
        ownedButton = add(Button.builder(Text.tr("button.use_owned"), button -> useOwnedBooks())
            .bounds(enchant.x(), footer, half, 18)
            .tooltip(Tooltip.create(Text.tr("button.use_owned.tooltip")))
            .build());
        clearButton = add(Button.builder(Text.tr("button.clear"), button -> clearSelection())
            .bounds(enchant.x() + half + 4, footer, enchant.width() - half - 4, 18)
            .tooltip(Tooltip.create(Text.tr("button.clear.tooltip")))
            .build());

        Rect plan = layout.planContent();
        Rect hide;
        int modeWidth;
        if (layout.compact()) {
            Rect tabs = layout.tabs();
            hide = new Rect(tabs.right() - 16, tabs.y(), 16, 16);
            modeWidth = plan.width();
        } else {
            hide = new Rect(plan.right() - 18, plan.y(), 18, 18);
            modeWidth = plan.width() - 20;
        }
        modeButton = add(Button.builder(modeLabel(), button -> cycleMode())
            .bounds(plan.x(), plan.y(), modeWidth, 18)
            .tooltip(Tooltip.create(modeTooltip()))
            .build());
        add(Button.builder(Component.literal("×"), button -> toggleVisible())
            .bounds(hide.x(), hide.y(), hide.width(), hide.height())
            .tooltip(Tooltip.create(Text.tr("button.hide")))
            .build());
        updateWidgets();
    }

    private void registerEvents() {
        ScreenEvents.remove(screen).register(removed -> onRemoved());
        ScreenEvents.afterTick(screen).register(ticked -> tick());
        ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, horizontal, vertical) -> !onScroll(x, y, vertical));
        //? if >=1.21.9 {
        // Shift is read from the keyboard, like Screen.hasShiftDown() in older versions.
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) ->
            !onClick(event.x(), event.y(), event.button(), Minecraft.getInstance().hasShiftDown()));
        ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> !onRelease(event.button()));
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !onKey(event));
        //?} else {
        /*ScreenMouseEvents.allowMouseClick(screen).register((s, x, y, button) -> !onClick(x, y, button, Screen.hasShiftDown()));
        ScreenMouseEvents.allowMouseRelease(screen).register((s, x, y, button) -> !onRelease(button));
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, modifiers) -> !onKey(key, scancode, modifiers));
        *///?}
    }

    // ---------------------------------------------------------------- state

    private void tick() {
        if (closed) {
            return;
        }
        if (relayoutPending) {
            relayoutPending = false;
            relayout();
            return;
        }
        EnchantCatalog now = EnchantCatalog.current();
        if (now == null) {
            return;
        }
        if (now != catalog) {
            catalog = now;
            resetTarget(null);
        }
        owned = Books.scan(menu, catalog);
        ownedLevels = collectOwnedLevels(owned);
        updateTarget();
        progress = view != null ? Progress.compute(view, menu, catalog) : null;
        int currentStep = progress != null ? progress.current() : -1;
        if (currentStep != lastCurrent) {
            lastCurrent = currentStep;
            focusedStep = -1;
            planPanel.reveal(currentStep);
        }
        requestPlan();
        enchantPanel.rebuild();
        updateWidgets();
    }

    /** Plans for a new item when one is put in the first slot, but not while the player works through the plan. */
    private void updateTarget() {
        ItemStack first = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem();
        if (first.isEmpty()) {
            return;
        }
        if (target != null && (target.sameAs(first, catalog) || isUnfinishedPlanItem(first))) {
            return;
        }
        resetTarget(Target.of(first, catalog));
    }

    private boolean isUnfinishedPlanItem(ItemStack stack) {
        if (view == null) {
            return false;
        }
        PlanView.Node result = view.result();
        for (PlanView.Node node : view.nodes()) {
            if (node != result && Progress.holds(stack, node, view.target(), catalog)) {
                return true;
            }
        }
        return false;
    }

    private void resetTarget(Target next) {
        target = next;
        view = null;
        progress = null;
        planKey = null;
        planning = false;
        requestId++;
        focusedStep = -1;
        lastCurrent = Integer.MIN_VALUE;
        enchantPanel.resetScroll();
        planPanel.resetScroll();
    }

    private void requestPlan() {
        if (target == null) {
            return;
        }
        Map<EnchantInfo, Integer> wanted = wanted();
        boolean creative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasInfiniteMaterials();
        Mode mode = config.mode;
        boolean sameIntent = planKey != null && planKey.target() == target && planKey.wanted().equals(wanted)
            && planKey.mode() == mode && planKey.creative() == creative;
        // Once the player has started, inventory changes are the plan being carried out: keep it.
        if (sameIntent && (planKey.owned().equals(owned) || (progress != null && progress.started()))) {
            return;
        }
        planKey = new PlanKey(target, wanted, owned, mode, creative);
        int id = ++requestId;
        if (wanted.isEmpty()) {
            view = null;
            progress = null;
            planning = false;
            return;
        }
        planning = true;
        Minecraft minecraft = Minecraft.getInstance();
        Planner.plan(new Planner.Request(target, wanted, owned, mode, creative))
            .whenComplete((result, error) -> minecraft.execute(() -> acceptPlan(id, result, error)));
    }

    private void acceptPlan(int id, PlanView result, Throwable error) {
        if (closed || id != requestId) {
            return;
        }
        planning = false;
        if (error != null) {
            EnchantCalc.LOGGER.error("EnchantCalc could not plan this item", error);
            view = null;
            return;
        }
        view = result;
        progress = Progress.compute(view, menu, catalog);
        lastCurrent = Integer.MIN_VALUE;
    }

    /** Selected enchantments that can still be added, at their chosen level. */
    Map<EnchantInfo, Integer> wanted() {
        Map<EnchantInfo, Integer> out = new LinkedHashMap<>();
        if (target == null) {
            return out;
        }
        Map<String, Integer> saved = config.selections.getOrDefault(target.itemId(), Map.of());
        for (EnchantInfo info : target.applicable()) {
            int level = Math.min(saved.getOrDefault(info.id(), 0), info.maxLevel());
            if (level <= target.existingLevel(info) || target.conflictOnItem(info) != null) {
                continue;
            }
            if (out.keySet().stream().allMatch(other -> other.compatibleWith(info))) {
                out.put(info, level);
            }
        }
        return out;
    }

    private static Map<EnchantInfo, List<Integer>> collectOwnedLevels(List<Books.Owned> books) {
        Map<EnchantInfo, List<Integer>> levels = new LinkedHashMap<>();
        for (Books.Owned book : books) {
            book.enchantments().forEach((info, level) -> {
                List<Integer> list = levels.computeIfAbsent(info, key -> new ArrayList<>());
                if (!list.contains(level)) {
                    list.add(level);
                }
            });
        }
        levels.values().forEach(list -> list.sort(Comparator.reverseOrder()));
        return levels;
    }

    // ---------------------------------------------------------------- actions

    void setLevel(EnchantInfo info, int level) {
        if (target == null) {
            return;
        }
        Map<String, Integer> saved = config.selection(target.itemId());
        if (level <= 0) {
            saved.remove(info.id());
        } else {
            saved.put(info.id(), level);
        }
        config.markDirty();
        selectionChanged();
    }

    /** Selects every enchantment the player has a book for, at the level of their best book. Curses are left out. */
    private void useOwnedBooks() {
        if (target == null) {
            return;
        }
        Map<EnchantInfo, Integer> chosen = wanted();
        for (EnchantInfo info : target.applicable()) {
            List<Integer> levels = ownedLevels.get(info);
            if (levels == null || info.curse()) {
                continue;
            }
            int level = Math.min(levels.get(0), info.maxLevel());
            if (level <= target.existingLevel(info) || target.conflictOnItem(info) != null) {
                continue;
            }
            // An enchantment that is already selected goes back to the level of the book.
            if (chosen.containsKey(info) || chosen.keySet().stream().allMatch(other -> other.compatibleWith(info))) {
                chosen.put(info, level);
            }
        }
        Map<String, Integer> saved = config.selection(target.itemId());
        chosen.forEach((info, level) -> saved.put(info.id(), level));
        config.markDirty();
        selectionChanged();
    }

    private void clearSelection() {
        if (target == null) {
            return;
        }
        config.selection(target.itemId()).clear();
        config.markDirty();
        selectionChanged();
    }

    private void selectionChanged() {
        enchantPanel.rebuild();
        requestPlan();
        updateWidgets();
    }

    private void cycleMode() {
        config.mode = config.mode.next();
        config.markDirty();
        if (modeButton != null) {
            modeButton.setMessage(modeLabel());
            modeButton.setTooltip(Tooltip.create(modeTooltip()));
        }
        requestPlan();
    }

    private Component modeLabel() {
        return Text.tr("mode.label", Text.tr("mode." + config.mode.name().toLowerCase(Locale.ROOT)));
    }

    private Component modeTooltip() {
        return Text.tr("mode." + config.mode.name().toLowerCase(Locale.ROOT) + ".tooltip");
    }

    private void toggleVisible() {
        config.visible = !config.visible;
        config.markDirty();
        // Rebuilding the screen from inside its own click handling is unsafe; wait for the next tick.
        relayoutPending = true;
    }

    private void relayout() {
        //? if >=1.21.11 {
        screen.resize(screen.width, screen.height);
        //?} else {
        /*screen.resize(Minecraft.getInstance(), screen.width, screen.height);
        *///?}
    }

    void focusStep(int index) {
        focusedStep = progress != null && index == progress.current() ? -1 : index;
    }

    void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void onSearchChanged(String text) {
        searchText = text;
        enchantPanel.resetScroll();
        enchantPanel.rebuild();
    }

    private void updateWidgets() {
        if (search == null) {
            return;
        }
        boolean enchantments = showsEnchantments();
        boolean hasTarget = target != null && !target.applicable().isEmpty();
        search.visible = enchantments;
        search.active = hasTarget;
        ownedButton.visible = enchantments;
        ownedButton.active = hasTarget && target.applicable().stream().anyMatch(info -> !info.curse() && ownedLevels.containsKey(info));
        clearButton.visible = enchantments;
        clearButton.active = hasTarget && !config.selections.getOrDefault(target.itemId(), Map.of()).isEmpty();
        modeButton.visible = showsPlan();
    }

    private void onRemoved() {
        closed = true;
        requestId++;
        config.saveIfDirty();
        if (current == this) {
            current = null;
        }
    }

    // ---------------------------------------------------------------- input

    private boolean showsEnchantments() {
        return layout.visible() && (!layout.compact() || tab == Tab.ENCHANTMENTS);
    }

    private boolean showsPlan() {
        return layout.visible() && (!layout.compact() || tab == Tab.PLAN);
    }

    /** Returns whether the click was ours, which stops the anvil from handling it (and its release). */
    private boolean onClick(double x, double y, int rawButton, boolean shift) {
        // A new press also ends a drag whose release never arrived.
        enchantPanel.stopDrag();
        planPanel.stopDrag();
        boolean ours = handleClick(x, y, MouseButton.of(rawButton), shift);
        int bit = buttonBit(rawButton);
        ownPresses = ours ? ownPresses | bit : ownPresses & ~bit;
        return ours;
    }

    /**
     * Ends scrollbar drags, and returns whether the release belongs to a press the overlay handled. The anvil
     * never saw that press, and given only the release while an item is on the cursor it drops the item.
     */
    private boolean onRelease(int rawButton) {
        if (MouseButton.of(rawButton) == MouseButton.LEFT) {
            enchantPanel.stopDrag();
            planPanel.stopDrag();
        }
        int bit = buttonBit(rawButton);
        boolean ours = (ownPresses & bit) != 0;
        ownPresses &= ~bit;
        return ours;
    }

    private static int buttonBit(int rawButton) {
        return 1 << (rawButton & 31);
    }

    private boolean handleClick(double x, double y, MouseButton button, boolean shift) {
        if (closed || layout == null) {
            return false;
        }
        if (!layout.visible()) {
            if (layout.collapsedTab().contains(x, y)) {
                toggleVisible();
                playClick();
                return true;
            }
            return false;
        }
        if (search != null && search.isFocused() && !search.isMouseOver(x, y)) {
            screen.setFocused(null);
        }
        if (layout.compact() && layout.tabs().contains(x, y)) {
            Tab clicked = tabAt(x);
            if (clicked != null) {
                if (clicked != tab) {
                    tab = clicked;
                    updateWidgets();
                    playClick();
                }
                return true;
            }
        }
        if (showsEnchantments() && enchantPanel.click(x, y, button, shift)) {
            return true;
        }
        if (showsPlan() && planPanel.click(x, y, button)) {
            return true;
        }
        for (AbstractWidget widget : widgets) {
            if (widget.visible && widget.isMouseOver(x, y)) {
                return false;
            }
        }
        // Swallow clicks on empty panel space, or the anvil would treat them as clicks outside its window.
        return layout.contains(x, y);
    }

    private boolean onScroll(double x, double y, double notches) {
        if (closed || layout == null || !layout.visible()) {
            return false;
        }
        if (showsEnchantments() && enchantPanel.scroll(x, y, notches)) {
            return true;
        }
        if (showsPlan() && planPanel.scroll(x, y, notches)) {
            return true;
        }
        return layout.contains(x, y);
    }

    // While the search box has focus, keys go to it so that e.g. "E" types instead of closing the anvil.
    //? if >=1.21.9 {
    private boolean onKey(KeyEvent event) {
        if (search == null || !search.visible || !search.isFocused() || event.isEscape() || event.isCycleFocus()) {
            return false;
        }
        search.keyPressed(event);
        return true;
    }
    //?} else {
    /*private boolean onKey(int key, int scancode, int modifiers) {
        if (search == null || !search.visible || !search.isFocused() || key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_TAB) {
            return false;
        }
        search.keyPressed(key, scancode, modifiers);
        return true;
    }
    *///?}

    private Tab tabAt(double x) {
        Rect tabs = layout.tabs();
        int width = (tabs.width() - 18) / 2;
        if (x < tabs.x() + width) {
            return Tab.ENCHANTMENTS;
        }
        if (x < tabs.x() + 2 * width + 2) {
            return Tab.PLAN;
        }
        return null;
    }

    // ---------------------------------------------------------------- rendering

    void render(Canvas canvas, int mouseX, int mouseY) {
        if (closed || layout == null) {
            return;
        }
        if (!layout.visible()) {
            renderCollapsedTab(canvas, mouseX, mouseY);
            return;
        }
        // Container screens pass no drag events on, so a scrollbar drag follows the mouse from here.
        enchantPanel.updateDrag(mouseY);
        planPanel.updateDrag(mouseY);
        renderSlotHighlights(canvas);
        theme.drawPanel(canvas, layout.left());
        if (layout.compact()) {
            renderTabs(canvas, mouseX, mouseY);
            if (tab == Tab.ENCHANTMENTS) {
                enchantPanel.render(canvas, mouseX, mouseY);
            } else {
                planPanel.render(canvas, mouseX, mouseY);
            }
        } else {
            enchantPanel.render(canvas, mouseX, mouseY);
            theme.drawPanel(canvas, layout.right());
            planPanel.render(canvas, mouseX, mouseY);
        }
    }

    private void renderCollapsedTab(Canvas canvas, int mouseX, int mouseY) {
        Rect tabRect = layout.collapsedTab();
        theme.drawPanel(canvas, tabRect);
        canvas.item(bookIcon, tabRect.x() + 4, tabRect.y() + 4);
        if (tabRect.contains(mouseX, mouseY)) {
            canvas.tooltip(List.of(Text.tr("button.show"), Text.tr("button.show.tooltip").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    private void renderTabs(Canvas canvas, int mouseX, int mouseY) {
        Rect tabs = layout.tabs();
        int width = (tabs.width() - 18) / 2;
        renderTab(canvas, new Rect(tabs.x(), tabs.y(), width, tabs.height()), Text.tr("tab.enchantments"), tab == Tab.ENCHANTMENTS, mouseX, mouseY);
        renderTab(canvas, new Rect(tabs.x() + width + 2, tabs.y(), width, tabs.height()), Text.tr("tab.plan"), tab == Tab.PLAN, mouseX, mouseY);
    }

    private void renderTab(Canvas canvas, Rect rect, Component label, boolean selected, int mouseX, int mouseY) {
        theme.drawWell(canvas, rect);
        if (selected) {
            canvas.fill(rect.inset(1), theme.rowSelected());
        } else if (rect.contains(mouseX, mouseY)) {
            canvas.fill(rect.inset(1), theme.rowHover());
        }
        String text = Canvas.ellipsize(canvas.font(), label.getString(), rect.width() - 6);
        canvas.textCentered(text, rect.x() + rect.width() / 2, rect.y() + 4, selected ? theme.text() : theme.textMuted(), false);
    }

    /** Colours the anvil slots and the inventory slots holding the items for the current step. */
    private void renderSlotHighlights(Canvas canvas) {
        PlanView.Step step = step(activeStep());
        if (step == null || progress == null) {
            return;
        }
        AbstractContainerScreenAccessor frame = frame();
        int left = frame.enchantcalc$getLeftPos();
        int top = frame.enchantcalc$getTopPos();
        Target planned = view.target();
        highlight(canvas, menu.getSlot(AnvilMenu.INPUT_SLOT), left, top, PlanPanel.FIRST_SLOT_COLOR,
            progress.inSlot(AnvilMenu.INPUT_SLOT, step.left(), planned));
        highlight(canvas, menu.getSlot(AnvilMenu.ADDITIONAL_SLOT), left, top, PlanPanel.SECOND_SLOT_COLOR,
            progress.inSlot(AnvilMenu.ADDITIONAL_SLOT, step.right(), planned));
        for (int slot : progress.slotsHolding(step.left(), planned)) {
            if (slot > AnvilMenu.RESULT_SLOT) {
                highlight(canvas, menu.getSlot(slot), left, top, PlanPanel.FIRST_SLOT_COLOR, true);
            }
        }
        for (int slot : progress.slotsHolding(step.right(), planned)) {
            if (slot > AnvilMenu.RESULT_SLOT) {
                highlight(canvas, menu.getSlot(slot), left, top, PlanPanel.SECOND_SLOT_COLOR, true);
            }
        }
    }

    private static void highlight(Canvas canvas, Slot slot, int left, int top, int color, boolean filled) {
        int x = left + slot.x;
        int y = top + slot.y;
        if (filled) {
            canvas.fill(x, y, x + 16, y + 16, (color & 0xFFFFFF) | 0x66000000);
        }
        canvas.outline(new Rect(x - 1, y - 1, 18, 18), color);
    }

    // ---------------------------------------------------------------- accessors for the panels and game tests

    /** The overlay of the open anvil screen, if any. */
    static AnvilOverlay current() {
        return current;
    }

    EnchantPanel enchantPanel() {
        return enchantPanel;
    }

    PlanPanel planPanel() {
        return planPanel;
    }

    EditBox search() {
        return search;
    }

    ItemStack bookIcon() {
        return bookIcon;
    }

    Screen screen() {
        return screen;
    }

    Theme theme() {
        return theme;
    }

    Layout layout() {
        return layout;
    }

    Target target() {
        return target;
    }

    PlanView view() {
        return view;
    }

    Progress progress() {
        return progress;
    }

    boolean planning() {
        return planning;
    }

    String searchText() {
        return searchText;
    }

    Map<EnchantInfo, List<Integer>> ownedLevels() {
        return ownedLevels;
    }

    int playerLevel() {
        return Minecraft.getInstance().player != null ? Minecraft.getInstance().player.experienceLevel : 0;
    }

    /** The step being shown: the one the player clicked, otherwise the first one still to do. */
    int activeStep() {
        if (view == null) {
            return -1;
        }
        if (focusedStep >= 0 && focusedStep < view.steps().size()) {
            return focusedStep;
        }
        return progress != null ? progress.current() : 0;
    }

    private PlanView.Step step(int index) {
        return view != null && index >= 0 && index < view.steps().size() ? view.steps().get(index) : null;
    }

    /** Whether both anvil slots hold the right items for the active step. */
    boolean inputsReady() {
        PlanView.Step step = step(activeStep());
        return step != null && progress != null
            && progress.inSlot(AnvilMenu.INPUT_SLOT, step.left(), view.target())
            && progress.inSlot(AnvilMenu.ADDITIONAL_SLOT, step.right(), view.target());
    }
}

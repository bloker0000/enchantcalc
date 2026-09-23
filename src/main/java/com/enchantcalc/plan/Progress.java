package com.enchantcalc.plan;

import com.enchantcalc.data.Books;
import com.enchantcalc.data.EnchantCatalog;
import com.enchantcalc.data.EnchantInfo;
import com.enchantcalc.data.Target;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks which steps are already done by looking at the items the player holds: a step is done
 * when its result exists, or when a later step that used that result is done.
 */
public final class Progress {
    /** An item in the anvil menu (slot -1 is the item on the cursor). */
    public record Held(int slot, Item item, Map<EnchantInfo, Integer> enchantments) {
        boolean matches(PlanView.Node node, Target target) {
            Item expected = node.target() ? target.stack().getItem() : Items.ENCHANTED_BOOK;
            return item == expected && enchantments.equals(node.enchantments());
        }
    }

    private final List<Held> held;
    private final boolean[] done;
    private final int current;

    private Progress(List<Held> held, boolean[] done) {
        this.held = held;
        this.done = done;
        int first = -1;
        for (int i = 0; i < done.length; i++) {
            if (!done[i]) {
                first = i;
                break;
            }
        }
        this.current = first;
    }

    public static Progress compute(PlanView view, AbstractContainerMenu menu, EnchantCatalog catalog) {
        List<Held> held = scan(menu, catalog);
        List<PlanView.Step> steps = view.steps();
        Map<Object, Integer> index = new IdentityHashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            index.put(steps.get(i).node(), i);
        }
        boolean[] done = new boolean[steps.size()];
        // The step that uses a result always comes later in the order, so walk backwards.
        for (int i = steps.size() - 1; i >= 0; i--) {
            PlanView.Step step = steps.get(i);
            Integer parent = index.get(view.parent(step.node()));
            done[i] = (parent != null && done[parent]) || isHeld(held, step.output(), view.target());
        }
        return new Progress(held, done);
    }

    private static List<Held> scan(AbstractContainerMenu menu, EnchantCatalog catalog) {
        List<Held> held = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            // The result slot only previews an anvil use that has not happened yet.
            if (i != AnvilMenu.RESULT_SLOT) {
                add(held, i, menu.slots.get(i).getItem(), catalog);
            }
        }
        add(held, -1, menu.getCarried(), catalog);
        return held;
    }

    private static void add(List<Held> held, int slot, ItemStack stack, EnchantCatalog catalog) {
        if (!stack.isEmpty()) {
            held.add(new Held(slot, stack.getItem(), Books.enchantments(stack, catalog)));
        }
    }

    /** Whether {@code stack} is the item {@code node} stands for (same item type and enchantments). */
    public static boolean holds(ItemStack stack, PlanView.Node node, Target target, EnchantCatalog catalog) {
        return !stack.isEmpty() && new Held(0, stack.getItem(), Books.enchantments(stack, catalog)).matches(node, target);
    }

    private static boolean isHeld(List<Held> held, PlanView.Node node, Target target) {
        for (Held item : held) {
            if (item.matches(node, target)) {
                return true;
            }
        }
        return false;
    }

    /** Menu slots holding the item for {@code node}, for highlighting. */
    public List<Integer> slotsHolding(PlanView.Node node, Target target) {
        List<Integer> slots = new ArrayList<>();
        for (Held item : held) {
            if (item.slot() >= 0 && item.matches(node, target)) {
                slots.add(item.slot());
            }
        }
        return slots;
    }

    public boolean inSlot(int slot, PlanView.Node node, Target target) {
        for (Held item : held) {
            if (item.slot() == slot) {
                return item.matches(node, target);
            }
        }
        return false;
    }

    public boolean done(int step) {
        return step >= 0 && step < done.length && done[step];
    }

    /** Index of the first step still to do, or -1 when every step is done. */
    public int current() {
        return current;
    }

    public boolean started() {
        for (boolean stepDone : done) {
            if (stepDone) {
                return true;
            }
        }
        return false;
    }

    public boolean complete() {
        return done.length > 0 && current < 0;
    }
}

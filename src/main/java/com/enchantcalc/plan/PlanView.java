package com.enchantcalc.plan;

import com.enchantcalc.core.AnvilMath;
import com.enchantcalc.core.Plan;
import com.enchantcalc.core.PlanNode;
import com.enchantcalc.data.Books;
import com.enchantcalc.data.EnchantInfo;
import com.enchantcalc.data.Target;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A solved plan with everything the GUI needs to show it: items to render, enchantments and costs per step. */
public final class PlanView {
    /**
     * An item that appears in the plan: the target, a book, or the result of a step.
     *
     * @param display an item stack to render and to show tooltips for
     * @param owned   the book from the inventory this leaf stands for, if any
     * @param missing a book the player does not have yet
     */
    public record Node(PlanNode node, Map<EnchantInfo, Integer> enchantments, ItemStack display, boolean target,
                       Books.Owned owned, boolean missing) {
    }

    /** One anvil use: {@code left} goes in the first slot, {@code right} in the second. */
    public record Step(int number, PlanNode node, Node left, Node right, Node output, boolean tooExpensive) {
        public int levels() {
            return node.stepLevels();
        }

        public long experience() {
            return AnvilMath.experienceForLevel(node.stepLevels());
        }
    }

    private final Planner.Request request;
    private final Plan plan;
    private final Map<PlanNode, Node> nodes;
    private final Map<PlanNode, PlanNode> parents;
    private final List<Step> steps;
    private final int missingBooks;

    private PlanView(Planner.Request request, Plan plan, Map<PlanNode, Node> nodes, Map<PlanNode, PlanNode> parents) {
        this.request = request;
        this.plan = plan;
        this.nodes = nodes;
        this.parents = parents;
        List<Step> list = new ArrayList<>();
        for (PlanNode step : plan.steps()) {
            list.add(new Step(list.size() + 1, step, nodes.get(step.left()), nodes.get(step.right()), nodes.get(step),
                plan.isTooExpensive(step)));
        }
        this.steps = List.copyOf(list);
        this.missingBooks = (int) nodes.values().stream().filter(Node::missing).count();
    }

    static PlanView create(Planner.Request request, List<Planner.LeafBook> books, Plan plan) {
        Map<PlanNode, Node> nodes = new IdentityHashMap<>();
        Map<PlanNode, PlanNode> parents = new IdentityHashMap<>();
        describe(plan.root(), null, request.target(), books, nodes, parents);
        return new PlanView(request, plan, nodes, parents);
    }

    private static Node describe(PlanNode node, PlanNode parent, Target target, List<Planner.LeafBook> books,
                                 Map<PlanNode, Node> nodes, Map<PlanNode, PlanNode> parents) {
        if (parent != null) {
            parents.put(node, parent);
        }
        Node described;
        if (node.isMerge()) {
            Node left = describe(node.left(), node, target, books, nodes, parents);
            Node right = describe(node.right(), node, target, books, nodes, parents);
            Map<EnchantInfo, Integer> merged = new LinkedHashMap<>(left.enchantments());
            right.enchantments().forEach((info, level) -> merged.merge(info, level, Math::max));
            ItemStack display = node.containsTarget()
                ? Books.withEnchantments(target.stack(), merged, node.penalty())
                : Books.book(merged, node.penalty());
            described = new Node(node, Collections.unmodifiableMap(merged), display, node.containsTarget(), null, false);
        } else if (node.leaf() == null) {
            described = new Node(node, target.existing(), target.stack().copy(), true, null, false);
        } else {
            Planner.LeafBook book = books.get(node.leaf().id());
            described = new Node(node, book.enchantments(), Books.book(book.enchantments(), node.penalty()), false,
                book.owned(), book.owned() == null);
        }
        nodes.put(node, described);
        return described;
    }

    public Planner.Request request() {
        return request;
    }

    public Target target() {
        return request.target();
    }

    public Plan plan() {
        return plan;
    }

    public List<Step> steps() {
        return steps;
    }

    public Node node(PlanNode node) {
        return nodes.get(node);
    }

    public Collection<Node> nodes() {
        return nodes.values();
    }

    /** The step that consumes {@code node}, or {@code null} for the finished item. */
    public PlanNode parent(PlanNode node) {
        return parents.get(node);
    }

    public Node result() {
        return nodes.get(plan.root());
    }

    /** Books in the plan that the player does not have yet. */
    public int missingBooks() {
        return missingBooks;
    }
}

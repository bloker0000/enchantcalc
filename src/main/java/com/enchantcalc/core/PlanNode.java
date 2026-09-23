package com.enchantcalc.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A node of an anvil plan: the target item, a single book, or the result of one anvil use that
 * combines {@link #left()} (first slot) with {@link #right()} (second slot, the sacrifice).
 */
public final class PlanNode {
    private final Leaf leaf;
    private final PlanNode left;
    private final PlanNode right;
    private final boolean target;
    private final int penalty;
    private final int value;
    private final int stepLevels;

    private PlanNode(Leaf leaf, PlanNode left, PlanNode right, boolean target, int penalty, int value, int stepLevels) {
        this.leaf = leaf;
        this.left = left;
        this.right = right;
        this.target = target;
        this.penalty = penalty;
        this.value = value;
        this.stepLevels = stepLevels;
    }

    public static PlanNode target(int penalty) {
        return new PlanNode(null, null, null, true, penalty, 0, 0);
    }

    public static PlanNode leaf(Leaf leaf) {
        return new PlanNode(leaf, null, null, false, leaf.penalty(), leaf.value(), 0);
    }

    /** One anvil use. The item can never be the sacrifice, so a node containing the target must be {@code left}. */
    public static PlanNode merge(PlanNode left, PlanNode right) {
        if (right.target) {
            throw new IllegalArgumentException("the target item cannot go in the second slot");
        }
        long cost = AnvilMath.stepCost(right.value, left.penalty, right.penalty);
        int value = (int) Math.min((long) left.value + right.value, Integer.MAX_VALUE);
        return new PlanNode(null, left, right, left.target, AnvilMath.nextPenalty(left.penalty, right.penalty), value,
            (int) Math.min(cost, Integer.MAX_VALUE));
    }

    public boolean isMerge() {
        return left != null;
    }

    /** The single book this node stands for, or {@code null} for the target and for merges. */
    public Leaf leaf() {
        return leaf;
    }

    public PlanNode left() {
        return left;
    }

    public PlanNode right() {
        return right;
    }

    /** Whether this node is the target item or a result that contains it. */
    public boolean containsTarget() {
        return target;
    }

    /** Prior work penalty of the item this node produces. */
    public int penalty() {
        return penalty;
    }

    /** Levels this node costs when used as a sacrifice (sum of its books' values). */
    public int value() {
        return value;
    }

    /** Level cost of the anvil use that produces this node; 0 for the target and books. */
    public int stepLevels() {
        return stepLevels;
    }

    /** Every book under this node, left to right. */
    public List<Leaf> leaves() {
        List<Leaf> out = new ArrayList<>();
        collectLeaves(out);
        return out;
    }

    private void collectLeaves(List<Leaf> out) {
        if (leaf != null) {
            out.add(leaf);
        } else if (left != null) {
            left.collectLeaves(out);
            right.collectLeaves(out);
        }
    }

    @Override
    public String toString() {
        if (leaf != null) {
            return "book#" + leaf.id();
        }
        if (left == null) {
            return "target";
        }
        return "(" + left + " + " + right + ")";
    }
}

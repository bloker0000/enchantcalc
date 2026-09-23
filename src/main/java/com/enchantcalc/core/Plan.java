package com.enchantcalc.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** A complete order of anvil uses that puts every book onto the target item. */
public final class Plan {
    private final PlanNode root;
    private final List<PlanNode> steps;
    private final long totalLevels;
    private final long totalExperience;
    private final int maxStepLevels;
    private final int stepLimit;
    private final boolean optimal;

    private Plan(PlanNode root, List<PlanNode> steps, int stepLimit, boolean optimal) {
        this.root = root;
        this.steps = List.copyOf(steps);
        this.stepLimit = stepLimit;
        this.optimal = optimal;
        long levels = 0;
        long experience = 0;
        int max = 0;
        for (PlanNode step : steps) {
            levels += step.stepLevels();
            experience += AnvilMath.experienceForLevel(step.stepLevels());
            max = Math.max(max, step.stepLevels());
        }
        this.totalLevels = levels;
        this.totalExperience = experience;
        this.maxStepLevels = max;
    }

    static Plan of(PlanNode root, int stepLimit, boolean optimal) {
        return new Plan(root, executionOrder(root), stepLimit, optimal);
    }

    /**
     * The order to perform the steps in. The player starts with the item in the anvil, so single books
     * go onto it straight away; then every combined book is prepared, and finally those go onto the item
     * one after another. That way the item leaves the anvil at most once.
     */
    static List<PlanNode> executionOrder(PlanNode root) {
        Deque<PlanNode> chain = new ArrayDeque<>();
        for (PlanNode node = root; node.isMerge(); node = node.left()) {
            chain.push(node);
        }
        List<PlanNode> order = new ArrayList<>();
        while (!chain.isEmpty() && !chain.peek().right().isMerge()) {
            order.add(chain.pop());
        }
        for (PlanNode itemStep : chain) {
            addBookMerges(itemStep.right(), order);
        }
        order.addAll(chain);
        return order;
    }

    private static void addBookMerges(PlanNode node, List<PlanNode> order) {
        if (node.isMerge()) {
            addBookMerges(node.left(), order);
            addBookMerges(node.right(), order);
            order.add(node);
        }
    }

    public PlanNode root() {
        return root;
    }

    /** Anvil uses in the order to perform them. */
    public List<PlanNode> steps() {
        return steps;
    }

    public long totalLevels() {
        return totalLevels;
    }

    /** Experience points if the levels for each step are gathered separately (the classic calculator figure). */
    public long totalExperience() {
        return totalExperience;
    }

    public int maxStepLevels() {
        return maxStepLevels;
    }

    public int finalPenalty() {
        return root.penalty();
    }

    /** Whether every step is allowed at the limit this plan was made for. */
    public boolean withinLimit() {
        return maxStepLevels <= stepLimit;
    }

    public boolean isTooExpensive(PlanNode step) {
        return step.stepLevels() > stepLimit;
    }

    /** False when the selection was too large for the exact search and a heuristic plan was used. */
    public boolean optimal() {
        return optimal;
    }
}

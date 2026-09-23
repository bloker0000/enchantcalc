package com.enchantcalc.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class OptimizerTest {
    private static final int SURVIVAL = AnvilMath.MAX_SURVIVAL_COST;

    @Test
    void noBooksMeansNoSteps() {
        Plan plan = Optimizer.solve(3, List.of(), Mode.LEVELS, SURVIVAL);
        assertTrue(plan.steps().isEmpty());
        assertEquals(0, plan.totalLevels());
        assertEquals(3, plan.finalPenalty());
    }

    @Test
    void oneBookCostsItsValue() {
        Plan plan = Optimizer.solve(0, List.of(new Leaf(0, 5, 0)), Mode.LEVELS, SURVIVAL);
        assertEquals(1, plan.steps().size());
        assertEquals(5, plan.totalLevels());
        assertEquals(1, plan.finalPenalty());
    }

    @Test
    void twoBooksPreferTheCheaperXpSplitWhenLevelsTie() {
        // Item+5 then +3 costs 5 + 4 = 9 levels (55 + 40 XP); item+3 then +5 costs 3 + 6 = 9 (27 + 72 XP).
        Plan plan = Optimizer.solve(0, List.of(new Leaf(0, 5, 0), new Leaf(1, 3, 0)), Mode.LEVELS, SURVIVAL);
        assertEquals(9, plan.totalLevels());
        assertEquals(95, plan.totalExperience());
        assertEquals(0, plan.steps().get(0).right().leaf().id());
    }

    @Test
    void existingPriorWorkIsCharged() {
        Plan plan = Optimizer.solve(7, List.of(new Leaf(0, 2, 0)), Mode.LEVELS, SURVIVAL);
        assertEquals(9, plan.totalLevels());
        assertEquals(15, plan.finalPenalty());
    }

    @Test
    void impossibleSelectionFallsBackToUnlimitedPlan() {
        Plan plan = Optimizer.solve(0, List.of(new Leaf(0, 45, 0)), Mode.LEVELS, SURVIVAL);
        assertFalse(plan.withinLimit());
        assertEquals(45, plan.totalLevels());
        assertTrue(plan.isTooExpensive(plan.steps().get(0)));
    }

    @Test
    void creativeHasNoLimit() {
        Plan plan = Optimizer.solve(0, List.of(new Leaf(0, 45, 0)), Mode.LEVELS, AnvilMath.NO_LIMIT);
        assertTrue(plan.withinLimit());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void matchesBruteForceOnRandomSelections(Mode mode) {
        Random random = new Random(20260923L + mode.ordinal());
        for (int round = 0; round < 250; round++) {
            int books = 1 + random.nextInt(5);
            List<Leaf> leaves = new ArrayList<>();
            for (int i = 0; i < books; i++) {
                int value = 1 + random.nextInt(random.nextBoolean() ? 6 : 14);
                int penalty = random.nextInt(6) == 0 ? (1 << random.nextInt(3)) - 1 : 0;
                leaves.add(new Leaf(i, value, penalty));
            }
            int targetPenalty = random.nextInt(4) == 0 ? (1 << random.nextInt(4)) - 1 : 0;
            int limit = random.nextInt(3) == 0 ? AnvilMath.NO_LIMIT : SURVIVAL;

            Plan plan = Optimizer.solve(targetPenalty, leaves, mode, limit);
            assertValid(plan, targetPenalty, leaves);

            long[] best = BruteForce.best(targetPenalty, leaves, mode, limit);
            if (best == null) {
                assertFalse(plan.withinLimit(), "brute force found nothing within the limit");
                best = BruteForce.best(targetPenalty, leaves, mode, AnvilMath.NO_LIMIT);
                assertNotNull(best);
            } else {
                assertTrue(plan.withinLimit(), "a plan within the limit exists");
            }
            String context = "round " + round + " target " + targetPenalty + " leaves " + leaves + " limit " + limit;
            assertEquals(best[0], objective(plan, mode)[0], context);
            assertEquals(best[1], objective(plan, mode)[1], context);
            assertEquals(best[2], objective(plan, mode)[2], context);
        }
    }

    @Test
    void manyIdenticalBooksAreSolvedExactlyAndQuickly() {
        List<Leaf> leaves = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            leaves.add(new Leaf(i, 1 + i % 3, 0));
        }
        Plan plan = assertTimeoutPreemptively(java.time.Duration.ofSeconds(5),
            () -> Optimizer.solve(0, leaves, Mode.LEVELS, AnvilMath.NO_LIMIT));
        assertTrue(plan.optimal());
        assertValid(plan, 0, leaves);
    }

    @Test
    void sixteenDistinctBooksStillSolveExactly() {
        List<Leaf> leaves = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            leaves.add(new Leaf(i, 1 + i, 0));
        }
        Plan plan = assertTimeoutPreemptively(java.time.Duration.ofSeconds(20),
            () -> Optimizer.solve(0, leaves, Mode.LEVELS, AnvilMath.NO_LIMIT));
        assertTrue(plan.optimal());
        assertValid(plan, 0, leaves);
    }

    @Test
    void hugeSelectionsUseTheGreedyFallback() {
        List<Leaf> leaves = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            leaves.add(new Leaf(i, 100 + i, 0));
        }
        Plan plan = Optimizer.solve(0, leaves, Mode.LEVELS, SURVIVAL);
        assertFalse(plan.optimal());
        assertValid(plan, 0, leaves);
    }

    @Test
    void priorWorkModeFindsTheLowestFinalPenalty() {
        // Four books fit in a balanced tree: final penalty 7 (three anvil uses deep).
        List<Leaf> leaves = List.of(new Leaf(0, 1, 0), new Leaf(1, 1, 0), new Leaf(2, 1, 0), new Leaf(3, 1, 0));
        Plan plan = Optimizer.solve(0, leaves, Mode.PRIOR_WORK, SURVIVAL);
        assertEquals(7, plan.finalPenalty());
    }

    /** Replays the plan's steps in order: every input must exist when used, and every book is used once. */
    private static void assertValid(Plan plan, int targetPenalty, List<Leaf> leaves) {
        Set<PlanNode> available = new HashSet<>();
        collectInputs(plan.root(), available);
        assertEquals(leaves.size(), plan.steps().size());
        for (PlanNode step : plan.steps()) {
            assertTrue(available.remove(step.left()), "left input available");
            assertTrue(available.remove(step.right()), "right input available");
            assertFalse(step.right().containsTarget(), "item never goes in the second slot");
            available.add(step);
        }
        assertEquals(Set.of(plan.root()), available);
        List<Integer> ids = plan.root().leaves().stream().map(Leaf::id).sorted().toList();
        assertEquals(leaves.stream().map(Leaf::id).sorted().toList(), ids);
        if (!plan.steps().isEmpty()) {
            assertTrue(plan.root().containsTarget());
        }
        assertEquals(targetPenalty, firstTarget(plan.root()).penalty());
    }

    private static void collectInputs(PlanNode node, Set<PlanNode> out) {
        if (node.isMerge()) {
            collectInputs(node.left(), out);
            collectInputs(node.right(), out);
        } else {
            out.add(node);
        }
    }

    private static PlanNode firstTarget(PlanNode node) {
        return node.isMerge() ? firstTarget(node.left()) : node;
    }

    /** The mode's ranking tuple: what is minimized first, second and third. */
    static long[] objective(Plan plan, Mode mode) {
        return rank(mode, plan.totalLevels(), plan.totalExperience(), plan.finalPenalty());
    }

    static long[] rank(Mode mode, long levels, long xp, long penalty) {
        return switch (mode) {
            case LEVELS -> new long[] {levels, xp, penalty};
            case EXPERIENCE -> new long[] {xp, levels, penalty};
            case PRIOR_WORK -> new long[] {penalty, levels, xp};
        };
    }

    /** Tries every order of anvil uses (feasible only for a handful of books). */
    private static final class BruteForce {
        static long[] best(int targetPenalty, List<Leaf> leaves, Mode mode, int limit) {
            List<PlanNode> nodes = new ArrayList<>();
            nodes.add(PlanNode.target(targetPenalty));
            leaves.forEach(leaf -> nodes.add(PlanNode.leaf(leaf)));
            long[][] best = new long[1][];
            search(nodes, 0, 0, mode, limit, best);
            return best[0];
        }

        private static void search(List<PlanNode> nodes, long levels, long xp, Mode mode, int limit, long[][] best) {
            if (nodes.size() == 1) {
                long[] candidate = rank(mode, levels, xp, nodes.get(0).penalty());
                if (best[0] == null || lexLess(candidate, best[0])) {
                    best[0] = candidate;
                }
                return;
            }
            for (int i = 0; i < nodes.size(); i++) {
                for (int j = 0; j < nodes.size(); j++) {
                    if (i == j || nodes.get(j).containsTarget()) {
                        continue;
                    }
                    PlanNode merged = PlanNode.merge(nodes.get(i), nodes.get(j));
                    if (merged.stepLevels() > limit) {
                        continue;
                    }
                    List<PlanNode> next = new ArrayList<>(nodes);
                    next.remove(Math.max(i, j));
                    next.remove(Math.min(i, j));
                    next.add(merged);
                    search(next, levels + merged.stepLevels(),
                        xp + AnvilMath.experienceForLevel(merged.stepLevels()), mode, limit, best);
                }
            }
        }

        private static boolean lexLess(long[] a, long[] b) {
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i]) {
                    return a[i] < b[i];
                }
            }
            return false;
        }
    }
}

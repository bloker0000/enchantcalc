package com.enchantcalc.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Finds the cheapest order to put a set of books onto an item with an anvil.
 *
 * <p>The search is exact dynamic programming over sets of books. For every set it keeps each
 * reachable prior work penalty with the cheapest way to reach it; a lower penalty is never worse
 * later on, so penalty/cost pairs dominated by another pair are dropped. Books with the same value
 * and penalty are interchangeable, so the sets are really multisets of book "classes", which keeps
 * large selections of similar books fast. Selections too large to search exactly get a balanced
 * greedy plan instead.
 */
public final class Optimizer {
    /** Split evaluations the exact search may do (all-distinct books: 3^n). 50M covers 16 distinct books. */
    static final long MAX_EXACT_WORK = 50_000_000L;

    private Optimizer() {
    }

    /**
     * @param targetPenalty prior work penalty of the item in the anvil
     * @param leaves        the books to apply
     * @param mode          what to minimize
     * @param stepLimit     the most a single anvil use may cost ({@link AnvilMath#MAX_SURVIVAL_COST} in
     *                      survival, {@link AnvilMath#NO_LIMIT} in creative). When no plan fits, the best
     *                      plan without a limit is returned and {@link Plan#withinLimit()} is false.
     */
    public static Plan solve(int targetPenalty, List<Leaf> leaves, Mode mode, int stepLimit) {
        Objects.requireNonNull(mode, "mode");
        if (leaves.isEmpty()) {
            return Plan.of(PlanNode.target(targetPenalty), stepLimit, true);
        }
        Groups groups = new Groups(leaves);
        if (groups.work <= MAX_EXACT_WORK) {
            PlanNode best = new Exact(groups, targetPenalty, mode, stepLimit).solve();
            if (best == null && stepLimit != AnvilMath.NO_LIMIT) {
                best = new Exact(groups, targetPenalty, mode, AnvilMath.NO_LIMIT).solve();
            }
            if (best != null) {
                return Plan.of(best, stepLimit, true);
            }
        }
        return Plan.of(greedy(targetPenalty, leaves), stepLimit, false);
    }

    /** Orders two costs for {@code mode}: levels then XP, or XP then levels. */
    static int compare(Mode mode, long levelsA, long xpA, long levelsB, long xpB) {
        if (mode == Mode.EXPERIENCE) {
            int c = Long.compare(xpA, xpB);
            return c != 0 ? c : Long.compare(levelsA, levelsB);
        }
        int c = Long.compare(levelsA, levelsB);
        return c != 0 ? c : Long.compare(xpA, xpB);
    }

    /**
     * Fallback for huge selections: repeatedly combine the two items with the lowest prior work,
     * like a tournament bracket, keeping the more valuable book in the first slot so it is not paid for.
     */
    static PlanNode greedy(int targetPenalty, List<Leaf> leaves) {
        List<PlanNode> nodes = new ArrayList<>();
        nodes.add(PlanNode.target(targetPenalty));
        for (Leaf leaf : leaves) {
            nodes.add(PlanNode.leaf(leaf));
        }
        Comparator<PlanNode> order = Comparator.comparingInt(PlanNode::penalty)
            .thenComparing(Comparator.comparingInt(PlanNode::value).reversed());
        while (nodes.size() > 1) {
            nodes.sort(order);
            PlanNode first = nodes.remove(0);
            int partner = 0;
            for (int i = 1; i < nodes.size() && nodes.get(i).penalty() == nodes.get(0).penalty(); i++) {
                if (nodes.get(i).value() < nodes.get(partner).value()) {
                    partner = i;
                }
            }
            PlanNode second = nodes.remove(partner);
            PlanNode left;
            PlanNode right;
            if (second.containsTarget() || (!first.containsTarget() && second.value() > first.value())) {
                left = second;
                right = first;
            } else {
                left = first;
                right = second;
            }
            nodes.add(PlanNode.merge(left, right));
        }
        return nodes.get(0);
    }

    /** Books grouped into interchangeable classes; a set of books is a mixed-radix number of per-class counts. */
    private static final class Groups {
        final int classes;
        final int[] value;
        final int[] penalty;
        final int[] size;
        final int[] place;
        final List<List<Leaf>> members;
        final int states;
        final long work;

        Groups(List<Leaf> leaves) {
            List<Leaf> sorted = new ArrayList<>(leaves);
            sorted.sort(Comparator.comparingInt(Leaf::value).reversed()
                .thenComparingInt(Leaf::penalty)
                .thenComparingInt(Leaf::id));
            Map<Long, List<Leaf>> byClass = new LinkedHashMap<>();
            for (Leaf leaf : sorted) {
                long key = ((long) leaf.value() << 32) | leaf.penalty();
                byClass.computeIfAbsent(key, k -> new ArrayList<>()).add(leaf);
            }
            classes = byClass.size();
            value = new int[classes];
            penalty = new int[classes];
            size = new int[classes];
            place = new int[classes];
            members = new ArrayList<>(byClass.values());
            long stateCount = 1;
            long pairs = 1;
            for (int c = 0; c < classes; c++) {
                List<Leaf> group = members.get(c);
                value[c] = group.get(0).value();
                penalty[c] = group.get(0).penalty();
                size[c] = group.size();
                place[c] = (int) Math.min(stateCount, Integer.MAX_VALUE);
                long radix = size[c] + 1L;
                stateCount = saturatingMultiply(stateCount, radix);
                pairs = saturatingMultiply(pairs, radix * (radix + 1) / 2);
            }
            // Larger state spaces are rejected by the work limit before any array is allocated.
            states = (int) Math.min(stateCount, Integer.MAX_VALUE);
            work = stateCount > Integer.MAX_VALUE ? Long.MAX_VALUE : pairs;
        }

        private static long saturatingMultiply(long a, long b) {
            return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
        }

        void decode(int state, int[] digits) {
            for (int c = 0; c < classes; c++) {
                digits[c] = (state / place[c]) % (size[c] + 1);
            }
        }
    }

    /** Non-dominated (penalty, cost) entries for one set of books, with back-pointers to rebuild the plan. */
    private static final class Front {
        int size;
        int[] penalty = new int[2];
        long[] levels = new long[2];
        long[] xp = new long[2];
        int[] aState = new int[2];
        int[] aIndex = new int[2];
        int[] bState = new int[2];
        int[] bIndex = new int[2];

        void offer(Mode mode, int p, long lv, long x, int as, int ai, int bs, int bi) {
            for (int i = 0; i < size; i++) {
                if (penalty[i] <= p && compare(mode, levels[i], xp[i], lv, x) <= 0) {
                    return;
                }
            }
            int kept = 0;
            for (int i = 0; i < size; i++) {
                boolean dominated = penalty[i] >= p && compare(mode, lv, x, levels[i], xp[i]) <= 0;
                if (!dominated) {
                    move(i, kept++);
                }
            }
            size = kept;
            if (size == penalty.length) {
                grow();
            }
            penalty[size] = p;
            levels[size] = lv;
            xp[size] = x;
            aState[size] = as;
            aIndex[size] = ai;
            bState[size] = bs;
            bIndex[size] = bi;
            size++;
        }

        private void move(int from, int to) {
            if (from != to) {
                penalty[to] = penalty[from];
                levels[to] = levels[from];
                xp[to] = xp[from];
                aState[to] = aState[from];
                aIndex[to] = aIndex[from];
                bState[to] = bState[from];
                bIndex[to] = bIndex[from];
            }
        }

        private void grow() {
            int capacity = penalty.length * 2;
            penalty = Arrays.copyOf(penalty, capacity);
            levels = Arrays.copyOf(levels, capacity);
            xp = Arrays.copyOf(xp, capacity);
            aState = Arrays.copyOf(aState, capacity);
            aIndex = Arrays.copyOf(aIndex, capacity);
            bState = Arrays.copyOf(bState, capacity);
            bIndex = Arrays.copyOf(bIndex, capacity);
        }
    }

    private static final class Exact {
        private final Groups groups;
        private final int targetPenalty;
        private final Mode mode;
        private final long limit;
        private final int[] stateValue;
        private final Front[] books;
        private final Front[] items;

        Exact(Groups groups, int targetPenalty, Mode mode, int limit) {
            this.groups = groups;
            this.targetPenalty = targetPenalty;
            this.mode = mode;
            this.limit = limit;
            int n = groups.states;
            stateValue = new int[n];
            books = new Front[n];
            items = new Front[n];
            int[] digits = new int[groups.classes];
            for (int s = 1; s < n; s++) {
                groups.decode(s, digits);
                int c = 0;
                while (digits[c] == 0) {
                    c++;
                }
                stateValue[s] = stateValue[s - groups.place[c]] + groups.value[c];
            }
        }

        /** The cheapest plan, or {@code null} if every plan has a step above the limit. */
        PlanNode solve() {
            int n = groups.states;
            int[] digits = new int[groups.classes];
            int[] sub = new int[groups.classes];

            for (int s = 1; s < n; s++) {
                groups.decode(s, digits);
                int single = singleBookClass(digits);
                if (single >= 0) {
                    Front base = new Front();
                    base.offer(mode, groups.penalty[single], 0, 0, -1, single, -1, -1);
                    books[s] = base;
                    continue;
                }
                Front front = null;
                System.arraycopy(digits, 0, sub, 0, digits.length);
                for (int a = previousSubState(s, digits, sub); a > 0; a = previousSubState(a, digits, sub)) {
                    front = combine(front, books[a], a, books[s - a], s - a);
                }
                books[s] = front;
            }

            Front start = new Front();
            start.offer(mode, targetPenalty, 0, 0, -1, -1, -1, -1);
            items[0] = start;
            for (int s = 1; s < n; s++) {
                groups.decode(s, digits);
                // The last anvil use puts book set `b` onto the item holding the other books.
                Front front = combine(null, items[0], 0, books[s], s);
                System.arraycopy(digits, 0, sub, 0, digits.length);
                for (int b = previousSubState(s, digits, sub); b > 0; b = previousSubState(b, digits, sub)) {
                    front = combine(front, items[s - b], s - b, books[b], b);
                }
                items[s] = front;
            }

            Front full = items[n - 1];
            if (full == null) {
                return null;
            }
            int best = 0;
            for (int i = 1; i < full.size; i++) {
                if (better(full, i, best)) {
                    best = i;
                }
            }
            List<ArrayDeque<Leaf>> pools = new ArrayList<>();
            for (List<Leaf> members : groups.members) {
                pools.add(new ArrayDeque<>(members));
            }
            return buildItem(n - 1, best, pools);
        }

        /** Adds every in-limit way of putting {@code right} (sacrifice) onto {@code left}. */
        private Front combine(Front out, Front left, int leftState, Front right, int rightState) {
            if (left == null || right == null) {
                return out;
            }
            long rightValue = stateValue[rightState];
            for (int i = 0; i < left.size; i++) {
                for (int j = 0; j < right.size; j++) {
                    long step = rightValue + left.penalty[i] + right.penalty[j];
                    if (step > limit) {
                        continue;
                    }
                    if (out == null) {
                        out = new Front();
                    }
                    int stepLevels = (int) Math.min(step, Integer.MAX_VALUE);
                    out.offer(mode,
                        AnvilMath.nextPenalty(left.penalty[i], right.penalty[j]),
                        left.levels[i] + right.levels[j] + step,
                        left.xp[i] + right.xp[j] + AnvilMath.experienceForLevel(stepLevels),
                        leftState, i, rightState, j);
                }
            }
            return out;
        }

        private boolean better(Front front, int i, int j) {
            if (mode == Mode.PRIOR_WORK && front.penalty[i] != front.penalty[j]) {
                return front.penalty[i] < front.penalty[j];
            }
            int c = compare(mode, front.levels[i], front.xp[i], front.levels[j], front.xp[j]);
            return c != 0 ? c < 0 : front.penalty[i] < front.penalty[j];
        }

        /** The class index if the digits describe exactly one book, otherwise -1. */
        private static int singleBookClass(int[] digits) {
            int found = -1;
            for (int c = 0; c < digits.length; c++) {
                if (digits[c] > 1 || (digits[c] == 1 && found >= 0)) {
                    return -1;
                }
                if (digits[c] == 1) {
                    found = c;
                }
            }
            return found;
        }

        /**
         * Steps through the sub-multisets of the multiset with {@code max} digits, from largest to
         * smallest. {@code current} holds the digits of {@code state} and is updated in place.
         * Returns 0 once every non-empty proper sub-multiset has been visited.
         */
        private int previousSubState(int state, int[] max, int[] current) {
            int c = 0;
            while (c < current.length && current[c] == 0) {
                c++;
            }
            if (c == current.length) {
                return 0;
            }
            current[c]--;
            state -= groups.place[c];
            for (int j = 0; j < c; j++) {
                state += (max[j] - current[j]) * groups.place[j];
                current[j] = max[j];
            }
            return state;
        }

        private PlanNode buildItem(int state, int index, List<ArrayDeque<Leaf>> pools) {
            if (state == 0) {
                return PlanNode.target(targetPenalty);
            }
            Front front = items[state];
            PlanNode item = buildItem(front.aState[index], front.aIndex[index], pools);
            PlanNode book = buildBook(front.bState[index], front.bIndex[index], pools);
            return PlanNode.merge(item, book);
        }

        private PlanNode buildBook(int state, int index, List<ArrayDeque<Leaf>> pools) {
            Front front = books[state];
            if (front.aState[index] < 0) {
                return PlanNode.leaf(pools.get(front.aIndex[index]).removeFirst());
            }
            PlanNode left = buildBook(front.aState[index], front.aIndex[index], pools);
            PlanNode right = buildBook(front.bState[index], front.bIndex[index], pools);
            return PlanNode.merge(left, right);
        }
    }
}

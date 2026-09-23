package com.enchantcalc.core;

/**
 * The vanilla anvil formulas from {@code AnvilMenu#createResult}, identical in every supported
 * Minecraft version. Pure Java so the optimizer can be unit tested without the game.
 */
public final class AnvilMath {
    /** Survival anvils refuse any operation costing this many levels or more ("Too Expensive!"). */
    public static final int TOO_EXPENSIVE = 40;
    /** The most a single anvil use may cost in survival. */
    public static final int MAX_SURVIVAL_COST = TOO_EXPENSIVE - 1;
    /** Step limit used when the player has infinite materials (creative). */
    public static final int NO_LIMIT = Integer.MAX_VALUE;

    private AnvilMath() {
    }

    /**
     * Levels charged per enchantment level when the sacrifice is an enchanted book:
     * the enchantment's anvil cost halved, but at least 1.
     */
    public static int bookMultiplier(int anvilCost) {
        return Math.max(1, anvilCost / 2);
    }

    /** Prior work penalty of an anvil result: twice the larger input penalty, plus one. */
    public static int nextPenalty(int left, int right) {
        return (int) Math.min(Math.max(left, right) * 2L + 1L, Integer.MAX_VALUE);
    }

    /** Number of anvil uses behind a penalty: 0, 1, 3, 7, 15, ... map to 0, 1, 2, 3, 4, ... */
    public static int anvilUses(int penalty) {
        return 32 - Integer.numberOfLeadingZeros(Math.max(0, penalty));
    }

    /** Level cost of one anvil use: the sacrifice's enchantment value plus both prior work penalties. */
    public static long stepCost(int rightValue, int leftPenalty, int rightPenalty) {
        return (long) rightValue + leftPenalty + rightPenalty;
    }

    /** Experience points a player needs to go from level 0 to {@code level}. */
    public static long experienceForLevel(int level) {
        // Clamped so absurd creative-mode costs cannot overflow.
        long l = Math.clamp(level, 0, 1_000_000);
        if (l <= 16) {
            return l * l + 6 * l;
        }
        if (l <= 31) {
            return (5 * l * l - 81 * l + 720) / 2;
        }
        return (9 * l * l - 325 * l + 4440) / 2;
    }
}

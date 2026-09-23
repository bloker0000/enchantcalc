package com.enchantcalc.core;

/** What the optimizer minimizes. Every mode keeps each step under the "Too Expensive!" limit when possible. */
public enum Mode {
    /** Fewest experience levels in total, then fewest experience points. */
    LEVELS,
    /**
     * Fewest experience points in total, then fewest levels. Assumes the player gathers the levels for
     * each step separately, so several cheap steps can beat one expensive step.
     */
    EXPERIENCE,
    /** Lowest prior work penalty on the finished item (cheaper future repairs), then fewest levels. */
    PRIOR_WORK;

    public Mode next() {
        Mode[] modes = values();
        return modes[(ordinal() + 1) % modes.length];
    }
}

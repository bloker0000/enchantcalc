package com.enchantcalc.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnvilMathTest {
    @Test
    void bookMultiplierHalvesAnvilCostButNeverBelowOne() {
        assertEquals(1, AnvilMath.bookMultiplier(1)); // Sharpness, Protection, Efficiency
        assertEquals(1, AnvilMath.bookMultiplier(2)); // Unbreaking
        assertEquals(2, AnvilMath.bookMultiplier(4)); // Mending, Looting
        assertEquals(4, AnvilMath.bookMultiplier(8)); // Silk Touch, Thorns
        assertEquals(1, AnvilMath.bookMultiplier(0));
    }

    @Test
    void penaltyDoublesPlusOneFromTheLargerInput() {
        assertEquals(1, AnvilMath.nextPenalty(0, 0));
        assertEquals(3, AnvilMath.nextPenalty(1, 0));
        assertEquals(3, AnvilMath.nextPenalty(0, 1));
        assertEquals(7, AnvilMath.nextPenalty(3, 1));
        assertEquals(Integer.MAX_VALUE, AnvilMath.nextPenalty(Integer.MAX_VALUE, 0));
    }

    @Test
    void anvilUsesCountsPenaltySteps() {
        assertEquals(0, AnvilMath.anvilUses(0));
        assertEquals(1, AnvilMath.anvilUses(1));
        assertEquals(2, AnvilMath.anvilUses(3));
        assertEquals(5, AnvilMath.anvilUses(31));
    }

    @Test
    void experienceMatchesTheWikiTable() {
        assertEquals(0, AnvilMath.experienceForLevel(0));
        assertEquals(7, AnvilMath.experienceForLevel(1));
        assertEquals(352, AnvilMath.experienceForLevel(16));
        assertEquals(394, AnvilMath.experienceForLevel(17));
        assertEquals(1395, AnvilMath.experienceForLevel(30));
        assertEquals(1507, AnvilMath.experienceForLevel(31));
        assertEquals(1628, AnvilMath.experienceForLevel(32));
        assertEquals(2727, AnvilMath.experienceForLevel(39));
    }
}

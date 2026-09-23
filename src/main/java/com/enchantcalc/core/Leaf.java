package com.enchantcalc.core;

/**
 * One enchanted book to apply.
 *
 * @param id      caller-chosen identifier, returned unchanged in the plan
 * @param value   levels charged when this book is the sacrifice (right slot):
 *                the sum of {@link AnvilMath#bookMultiplier} times level over its enchantments
 * @param penalty the book's prior work penalty ({@code minecraft:repair_cost}), 0 for fresh books
 */
public record Leaf(int id, int value, int penalty) {
    public Leaf {
        if (value < 0 || penalty < 0) {
            throw new IllegalArgumentException("value and penalty must be >= 0");
        }
    }
}

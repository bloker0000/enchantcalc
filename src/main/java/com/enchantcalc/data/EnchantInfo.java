package com.enchantcalc.data;

import com.enchantcalc.core.AnvilMath;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * An enchantment as the calculator sees it, read from the world's registry, so datapack and
 * modded enchantments work with their real costs.
 *
 * @param id        registry id, e.g. {@code minecraft:sharpness}; used for saved selections
 * @param anvilCost the enchantment's {@code anvil_cost}; books charge {@link #bookMultiplier()} per level
 */
public record EnchantInfo(Holder<Enchantment> holder, String id, Component name, int maxLevel, int anvilCost, boolean curse) {
    public int bookMultiplier() {
        return AnvilMath.bookMultiplier(anvilCost);
    }

    /** Levels charged for this enchantment at {@code level} when it comes from a book. */
    public int bookValue(int level) {
        return bookMultiplier() * level;
    }

    /** Name with the level numeral, as in tooltips ("Sharpness V"; single-level enchantments get no numeral). */
    public Component fullName(int level) {
        return Enchantment.getFullname(holder, level);
    }

    public boolean compatibleWith(EnchantInfo other) {
        return Enchantment.areCompatible(holder, other.holder);
    }

    /** The level numeral on its own, e.g. "IV", falling back to digits for levels without a translation. */
    public static Component levelName(int level) {
        return Component.translatableWithFallback("enchantment.level." + level, Integer.toString(level));
    }
}

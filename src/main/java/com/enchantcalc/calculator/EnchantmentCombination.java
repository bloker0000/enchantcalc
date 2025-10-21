package com.enchantcalc.calculator;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;

public record EnchantmentCombination(
    RegistryEntry<Enchantment> enchantment,
    int level
) {
}

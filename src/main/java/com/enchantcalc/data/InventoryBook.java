package com.enchantcalc.data;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;

public record InventoryBook(
    RegistryEntry<Enchantment> enchantment,
    int level,
    int slot
) {
}

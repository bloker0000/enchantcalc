package com.enchantcalc.data;

import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.List;

public record EnchantmentInfo(
    RegistryEntry<Enchantment> enchantment,
    int maxLevel,
    int weight,
    List<RegistryEntry<Enchantment>> incompatibleWith,
    boolean isCustom
) {
    public String getName() {
        return Enchantment.getName(enchantment, 1).getString();
    }
}

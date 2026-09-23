package com.enchantcalc.data;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The item in the anvil's first slot, which the plan enchants.
 *
 * @param stack      snapshot of the item
 * @param itemId     registry id of the item type; saved selections are keyed by it
 * @param book       an enchanted book accepts every enchantment in the anvil
 * @param existing   enchantments already on the item
 * @param repairCost the item's prior work penalty
 * @param applicable enchantments the anvil can put on this item, in catalog order
 */
public record Target(ItemStack stack, String itemId, boolean book, Map<EnchantInfo, Integer> existing,
                     int repairCost, List<EnchantInfo> applicable) {

    /** Reads the item, or returns {@code null} for an empty slot. */
    public static Target of(ItemStack stack, EnchantCatalog catalog) {
        if (stack.isEmpty()) {
            return null;
        }
        ItemStack copy = stack.copy();
        boolean book = copy.is(Items.ENCHANTED_BOOK);
        Map<EnchantInfo, Integer> existing = Books.enchantments(copy, catalog);
        List<EnchantInfo> applicable = new ArrayList<>();
        for (EnchantInfo info : catalog.all()) {
            if (book || info.holder().value().canEnchant(copy)) {
                applicable.add(info);
            }
        }
        String itemId = BuiltInRegistries.ITEM.getKey(copy.getItem()).toString();
        return new Target(copy, itemId, book, Collections.unmodifiableMap(new LinkedHashMap<>(existing)),
            copy.getOrDefault(DataComponents.REPAIR_COST, 0), List.copyOf(applicable));
    }

    public int existingLevel(EnchantInfo info) {
        return existing.getOrDefault(info, 0);
    }

    /** An enchantment already on the item that blocks {@code info}, or {@code null}. */
    public EnchantInfo conflictOnItem(EnchantInfo info) {
        for (EnchantInfo other : existing.keySet()) {
            if (!other.equals(info) && !info.compatibleWith(other)) {
                return other;
            }
        }
        return null;
    }

    /** Whether {@code other} is the same item in the same state (type, enchantments and prior work). */
    public boolean sameAs(ItemStack other, EnchantCatalog catalog) {
        return !other.isEmpty()
            && other.getItem() == stack.getItem()
            && other.getOrDefault(DataComponents.REPAIR_COST, 0) == repairCost
            && Books.enchantments(other, catalog).equals(existing);
    }

    /** Anvils refuse stacked items: the cost is forced to 40. */
    public boolean stacked() {
        return stack.getCount() > 1;
    }
}

package com.enchantcalc.data;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Enchanted books the player already has, and helpers for reading enchantments off items. */
public final class Books {
    private Books() {
    }

    /**
     * An enchanted book in the anvil's second slot or the player's inventory.
     *
     * @param slot index in the anvil menu, used to highlight it
     */
    public record Owned(int slot, Map<EnchantInfo, Integer> enchantments, int repairCost) {
    }

    /** Every enchanted book in the anvil menu, except the one being enchanted (first slot) and the preview (result slot). */
    public static List<Owned> scan(AbstractContainerMenu menu, EnchantCatalog catalog) {
        List<Owned> books = new ArrayList<>();
        for (int i = 0; i < menu.slots.size(); i++) {
            if (i == AnvilMenu.INPUT_SLOT || i == AnvilMenu.RESULT_SLOT) {
                continue;
            }
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            if (!stack.is(Items.ENCHANTED_BOOK)) {
                continue;
            }
            Map<EnchantInfo, Integer> enchantments = enchantments(stack, catalog);
            if (!enchantments.isEmpty()) {
                books.add(new Owned(i, Collections.unmodifiableMap(enchantments),
                    stack.getOrDefault(DataComponents.REPAIR_COST, 0)));
            }
        }
        return books;
    }

    /** Enchantments on an item, or stored in a book, as catalog entries. Unknown enchantments are skipped. */
    public static Map<EnchantInfo, Integer> enchantments(ItemStack stack, EnchantCatalog catalog) {
        Map<EnchantInfo, Integer> out = new LinkedHashMap<>();
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            EnchantInfo info = catalog.get(holder);
            if (info != null) {
                out.put(info, entry.getIntValue());
            }
        }
        return out;
    }

    /** A copy of {@code base} carrying exactly {@code enchantments} and the given prior work penalty, for display. */
    public static ItemStack withEnchantments(ItemStack base, Map<EnchantInfo, Integer> enchantments, int repairCost) {
        ItemStack stack = base.copy();
        EnchantmentHelper.updateEnchantments(stack, mutable -> {
            mutable.removeIf(holder -> true);
            enchantments.forEach((info, level) -> mutable.set(info.holder(), level));
        });
        stack.set(DataComponents.REPAIR_COST, repairCost);
        return stack;
    }

    public static ItemStack book(Map<EnchantInfo, Integer> enchantments, int repairCost) {
        return withEnchantments(new ItemStack(Items.ENCHANTED_BOOK), enchantments, repairCost);
    }
}

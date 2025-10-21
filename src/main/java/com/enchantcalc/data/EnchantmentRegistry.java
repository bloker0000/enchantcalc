package com.enchantcalc.data;

import com.enchantcalc.EnchantCalcClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.*;
import java.util.stream.Collectors;

public class EnchantmentRegistry {
    private static final Map<RegistryEntry<Enchantment>, EnchantmentInfo> ENCHANTMENT_DATA = new HashMap<>();
    private static boolean initialized = false;

    public static void initialize() {
        // Initialization is now lazy - called when needed
    }

    private static void ensureInitialized() {
        if (initialized) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            EnchantCalcClient.LOGGER.warn("Cannot initialize enchantment registry: not in a world");
            return;
        }
        
        ENCHANTMENT_DATA.clear();
        
        DynamicRegistryManager registryManager = client.world.getRegistryManager();
        RegistryWrapper<Enchantment> enchantmentRegistry = registryManager.getOrThrow(RegistryKeys.ENCHANTMENT);
        
        for (RegistryEntry.Reference<Enchantment> entry : enchantmentRegistry.streamEntries().toList()) {
            Enchantment enchantment = entry.value();
            int weight = getEnchantmentWeight(entry);
            int maxLevel = enchantment.getMaxLevel();
            List<RegistryEntry<Enchantment>> incompatible = getIncompatibleEnchantments(entry, enchantmentRegistry);
            
            EnchantmentInfo info = new EnchantmentInfo(entry, maxLevel, weight, incompatible, false);
            ENCHANTMENT_DATA.put(entry, info);
        }
        
        initialized = true;
        EnchantCalcClient.LOGGER.info("Dynamically loaded {} enchantments from registry (vanilla + modded)", ENCHANTMENT_DATA.size());
        
        ENCHANTMENT_DATA.keySet().forEach(entry -> {
            String id = entry.getIdAsString();
            if (!id.startsWith("minecraft:")) {
                EnchantCalcClient.LOGGER.info("Detected custom/modded enchantment: {}", id);
            }
        });
    }

    private static int getEnchantmentWeight(RegistryEntry<Enchantment> entry) {
        String id = entry.getIdAsString();
        String name = id.contains(":") ? id.split(":")[1] : id;
        
        return switch (name) {
            case "aqua_affinity", "blast_protection", "depth_strider", "fire_aspect",
                 "flame", "frost_walker", "impaling", "luck_of_the_sea", "lure",
                 "mending", "multishot", "punch", "respiration", "riptide",
                 "breach", "wind_burst", "looting", "fortune", "sweeping_edge" -> 2;
            case "soul_speed", "swift_sneak", "thorns", "binding_curse",
                 "vanishing_curse", "silk_touch", "infinity", "channeling" -> 4;
            default -> 1;
        };
    }

    private static List<RegistryEntry<Enchantment>> getIncompatibleEnchantments(
            RegistryEntry<Enchantment> entry,
            RegistryWrapper<Enchantment> registry) {
        List<RegistryEntry<Enchantment>> incompatible = new ArrayList<>();
        Enchantment enchantment = entry.value();
        
        for (RegistryEntry.Reference<Enchantment> other : registry.streamEntries().toList()) {
            if (!entry.equals(other) && !Enchantment.canBeCombined(entry, other)) {
                incompatible.add(other);
            }
        }
        
        return incompatible;
    }

    public static EnchantmentInfo getInfo(RegistryEntry<Enchantment> enchantment) {
        ensureInitialized();
        return ENCHANTMENT_DATA.get(enchantment);
    }

    public static List<RegistryEntry<Enchantment>> getApplicableEnchantments(ItemStack stack) {
        ensureInitialized();
        if (stack.isEmpty()) return List.of();
        
        return ENCHANTMENT_DATA.keySet().stream()
                .filter(entry -> entry.value().isAcceptableItem(stack))
                .collect(Collectors.toList());
    }

    public static List<InventoryBook> scanInventoryForBooks(MinecraftClient client) {
        ensureInitialized();
        List<InventoryBook> books = new ArrayList<>();
        
        if (client.player == null) return books;
        
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            
            if (stack.isOf(Items.ENCHANTED_BOOK)) {
                ItemEnchantmentsComponent enchantments = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
                if (enchantments != null) {
                    final int slotIndex = i;
                    enchantments.getEnchantments().forEach(enchantment -> {
                        int level = enchantments.getLevel(enchantment);
                        books.add(new InventoryBook(enchantment, level, slotIndex));
                    });
                }
            }
        }
        
        return books;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void reset() {
        ENCHANTMENT_DATA.clear();
        initialized = false;
    }
}

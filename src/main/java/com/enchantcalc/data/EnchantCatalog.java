package com.enchantcalc.data;

import com.enchantcalc.EnchantCalc;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every enchantment in the current world. Enchantments are data-driven, so the list is rebuilt for
 * each world or server (their registry objects differ per connection).
 */
public final class EnchantCatalog {
    private static EnchantCatalog cached;
    private static RegistryAccess cachedFor;

    private final List<EnchantInfo> all;
    private final Map<Holder<Enchantment>, EnchantInfo> byHolder = new HashMap<>();
    private final Map<String, EnchantInfo> byId = new HashMap<>();

    private EnchantCatalog(List<EnchantInfo> all) {
        this.all = Collections.unmodifiableList(all);
        for (EnchantInfo info : all) {
            byHolder.put(info.holder(), info);
            byId.put(info.id(), info);
        }
    }

    /** The catalog for the world the player is in, or {@code null} outside a world. */
    public static EnchantCatalog current() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        RegistryAccess access = minecraft.level.registryAccess();
        if (cached == null || cachedFor != access) {
            cached = build(access);
            cachedFor = access;
        }
        return cached;
    }

    /** Drops the cache; called when leaving or joining a world. */
    public static void invalidate() {
        cached = null;
        cachedFor = null;
    }

    private static EnchantCatalog build(RegistryAccess access) {
        List<EnchantInfo> list = new ArrayList<>();
        access.lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(holder -> {
            Enchantment enchantment = holder.value();
            list.add(new EnchantInfo(holder, holder.getRegisteredName(), enchantment.description(),
                enchantment.getMaxLevel(), enchantment.getAnvilCost(), holder.is(EnchantmentTags.CURSE)));
        });
        // Curses last, the rest alphabetically by their name in the current language.
        list.sort(Comparator.comparing(EnchantInfo::curse)
            .thenComparing(info -> info.name().getString(), String.CASE_INSENSITIVE_ORDER));
        EnchantCalc.LOGGER.debug("Loaded {} enchantments", list.size());
        return new EnchantCatalog(list);
    }

    public List<EnchantInfo> all() {
        return all;
    }

    /** Looks an enchantment up by holder, falling back to its id for holders from another registry snapshot. */
    public EnchantInfo get(Holder<Enchantment> holder) {
        EnchantInfo info = byHolder.get(holder);
        return info != null ? info : byId.get(holder.getRegisteredName());
    }

    public EnchantInfo get(String id) {
        return byId.get(id);
    }
}

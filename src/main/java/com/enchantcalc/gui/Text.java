package com.enchantcalc.gui;

import com.enchantcalc.EnchantCalc;
import com.enchantcalc.data.EnchantInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Translated text for the overlay (keys live in {@code assets/enchantcalc/lang}). */
final class Text {
    private Text() {
    }

    static MutableComponent tr(String key, Object... args) {
        return Component.translatable(EnchantCalc.MOD_ID + "." + key, args);
    }

    static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    /** "Sharpness V, Unbreaking III" */
    static String enchantments(Map<EnchantInfo, Integer> enchantments) {
        return enchantments.entrySet().stream()
            .map(entry -> entry.getKey().fullName(entry.getValue()).getString())
            .collect(Collectors.joining(", "));
    }

    /**
     * The enchantments, shortened to {@code maxWidth}: "Sharpness V, Looting III" if it fits, otherwise the
     * first one with its level kept ("Sweeping E… III +1").
     */
    static String enchantmentsShort(Font font, Map<EnchantInfo, Integer> enchantments, int maxWidth) {
        String full = enchantments(enchantments);
        if (enchantments.isEmpty() || font.width(full) <= maxWidth) {
            return full;
        }
        Map.Entry<EnchantInfo, Integer> first = enchantments.entrySet().iterator().next();
        EnchantInfo info = first.getKey();
        String level = info.maxLevel() > 1 ? " " + EnchantInfo.levelName(first.getValue()).getString() : "";
        String more = enchantments.size() > 1 ? " +" + (enchantments.size() - 1) : "";
        String tail = level + more;
        return Canvas.ellipsize(font, info.name().getString(), maxWidth - font.width(tail)) + tail;
    }

    /** One tooltip line per enchantment, indented under a heading. */
    static void addEnchantmentLines(List<Component> lines, Map<EnchantInfo, Integer> enchantments) {
        enchantments.forEach((info, level) -> lines.add(Component.literal("  ")
            .append(info.fullName(level).copy().withStyle(info.curse() ? ChatFormatting.RED : ChatFormatting.GRAY))));
    }
}

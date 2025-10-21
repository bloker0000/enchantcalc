package com.enchantcalc.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

import java.util.function.BiConsumer;

public class EnchantmentButton extends ButtonWidget {
    private final RegistryEntry<Enchantment> enchantment;
    private final int maxLevel;
    private final BiConsumer<RegistryEntry<Enchantment>, Integer> onLevelChange;
    private final boolean isFromInventory;
    
    private int currentLevel = 0;
    private boolean isEnabled = true;

    public EnchantmentButton(int x, int y, int width, int height, RegistryEntry<Enchantment> enchantment, int maxLevel, boolean isFromInventory, BiConsumer<RegistryEntry<Enchantment>, Integer> onLevelChange) {
        super(x, y, width, height, getButtonText(enchantment, 0, isFromInventory), button -> {}, DEFAULT_NARRATION_SUPPLIER);
        this.enchantment = enchantment;
        this.maxLevel = maxLevel;
        this.isFromInventory = isFromInventory;
        this.onLevelChange = onLevelChange;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (!isEnabled) return;
        
        currentLevel++;
        if (currentLevel > maxLevel) {
            currentLevel = 0;
        }
        
        setMessage(getButtonText(enchantment, currentLevel, isFromInventory));
        onLevelChange.accept(enchantment, currentLevel);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int backgroundColor;
        int borderColor;
        int textColor;
        
        if (!isEnabled) {
            backgroundColor = 0xFF555555;
            borderColor = 0xFF333333;
            textColor = 0xFF888888;
        } else if (currentLevel > 0) {
            backgroundColor = isFromInventory ? 0xFF4A7C4A : 0xFF5A5A8C;
            borderColor = 0xFF8B8B8B;
            textColor = 0xFFFFFFFF;
        } else if (isHovered()) {
            backgroundColor = 0xFF8B8B8B;
            borderColor = 0xFFAAAAAA;
            textColor = 0xFFFFFFFF;
        } else {
            backgroundColor = 0xFF6B6B6B;
            borderColor = 0xFF8B8B8B;
            textColor = 0xFFFFFFFF;
        }
        
        context.fill(getX(), getY(), getX() + width, getY() + height, backgroundColor);
        context.drawBorder(getX(), getY(), width, height, borderColor);
        
        context.drawCenteredTextWithShadow(
            MinecraftClient.getInstance().textRenderer,
            getMessage(),
            getX() + width / 2,
            getY() + (height - 8) / 2,
            textColor
        );
    }

    private static Text getButtonText(RegistryEntry<Enchantment> enchantment, int level, boolean fromInventory) {
        String name = Enchantment.getName(enchantment, 1).getString();
        if (name.endsWith(" I")) {
            name = name.substring(0, name.length() - 2);
        }
        
        String prefix = fromInventory ? "✓ " : "";
        
        if (level == 0) {
            return Text.literal(prefix + name);
        } else {
            return Text.literal(prefix + name + " " + level);
        }
    }

    public void setLevel(int level) {
        this.currentLevel = Math.max(0, Math.min(maxLevel, level));
        setMessage(getButtonText(enchantment, currentLevel, isFromInventory));
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public RegistryEntry<Enchantment> getEnchantment() {
        return enchantment;
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        this.active = enabled;
    }

    public boolean isEnchantmentEnabled() {
        return isEnabled;
    }
}

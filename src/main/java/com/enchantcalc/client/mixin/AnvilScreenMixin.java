package com.enchantcalc.client.mixin;

import com.enchantcalc.calculator.CalculationResult;
import com.enchantcalc.calculator.EnchantmentCalculator;
import com.enchantcalc.calculator.EnchantmentCombination;
import com.enchantcalc.calculator.OptimizationMode;
import com.enchantcalc.client.gui.EnchantmentButton;
import com.enchantcalc.client.gui.SearchFieldWidget;
import com.enchantcalc.data.EnchantmentInfo;
import com.enchantcalc.data.EnchantmentRegistry;
import com.enchantcalc.data.InventoryBook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.ForgingScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.stream.Collectors;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin extends ForgingScreen<AnvilScreenHandler> {
    @Unique private int dynamicPanelWidth = 180;
    @Unique private int dynamicPanelHeight = 200;
    @Unique private static final int MIN_PANEL_WIDTH = 140;
    @Unique private static final int MAX_PANEL_WIDTH = 220;
    @Unique private static final int MIN_PANEL_HEIGHT = 160;
    @Unique private static final int MAX_PANEL_HEIGHT = 280;
    @Unique private static final int PANEL_OFFSET = 10;
    @Unique private static final int BUTTON_HEIGHT = 16;
    @Unique private int maxVisibleEnchants = 6;
    @Unique private static final float SCROLL_SPEED = 0.15f;
    @Unique private boolean isExtremeGuiScale = false;
    @Unique private double currentGuiScale = 1.0;
    
    @Unique private static CalculationResult persistedCalculationResult = null;
    @Unique private static int persistedCurrentStepIndex = 0;
    @Unique private static OptimizationMode persistedOptimizationMode = OptimizationMode.LEVELS;
    @Unique private static final Map<RegistryEntry<Enchantment>, Integer> persistedSelectedEnchantments = new HashMap<>();

    @Unique private final List<EnchantmentButton> enchantmentButtons = new ArrayList<>();
    @Unique private final Map<RegistryEntry<Enchantment>, Integer> selectedEnchantments = new HashMap<>();
    @Unique private SearchFieldWidget searchField;
    @Unique private ButtonWidget calculateButton;
    @Unique private ButtonWidget clearButton;
    @Unique private ButtonWidget scrollUpButton;
    @Unique private ButtonWidget scrollDownButton;
    @Unique private ButtonWidget modeButton;

    @Unique private List<RegistryEntry<Enchantment>> availableEnchantments = new ArrayList<>();
    @Unique private List<InventoryBook> inventoryBooks = new ArrayList<>();
    @Unique private int scrollOffset = 0;
    @Unique private float smoothScrollOffset = 0.0f;
    @Unique private float targetScrollOffset = 0.0f;
    @Unique private ItemStack lastAnvilItem = ItemStack.EMPTY;
    @Unique private CalculationResult calculationResult;
    @Unique private int currentStepIndex = 0;
    @Unique private OptimizationMode optimizationMode = OptimizationMode.LEVELS;
    @Unique private boolean leftPanelVisible = false;
    @Unique private boolean rightPanelVisible = false;
    
    @Unique private ButtonWidget prevStepButton;
    @Unique private ButtonWidget nextStepButton;

    public AnvilScreenMixin(AnvilScreenHandler handler, PlayerInventory playerInventory, Text title) {
        super(handler, playerInventory, title, null);
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void onSetup(CallbackInfo ci) {
        calculateDynamicPanelSizes();
        restorePersistedState();
        updatePanelVisibility();
        if (leftPanelVisible) {
            setupLeftPanel();
        }
    }
    
    @Unique
    private void calculateDynamicPanelSizes() {
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        currentGuiScale = client.options.getGuiScale().getValue();
        isExtremeGuiScale = currentGuiScale >= 5.0;
        
        if (isExtremeGuiScale) {
            dynamicPanelWidth = Math.max(MIN_PANEL_WIDTH - 15, Math.min(MAX_PANEL_WIDTH - 30, screenWidth / 4));
        } else {
            dynamicPanelWidth = Math.max(MIN_PANEL_WIDTH + 10, Math.min(MAX_PANEL_WIDTH, screenWidth / 5));
        }
        
        dynamicPanelHeight = backgroundHeight;
        
        int bottomReservedSpace = isExtremeGuiScale ? 24 : 48;
        int availableScrollHeight = dynamicPanelHeight - 40 - bottomReservedSpace;
        maxVisibleEnchants = Math.max(3, Math.min(10, availableScrollHeight / (BUTTON_HEIGHT + 2)));
    }
    
    @Unique
    private int getLeftPanelX() {
        if (isExtremeGuiScale) {
            return x - dynamicPanelWidth - 3;
        }
        return x - dynamicPanelWidth - 8;
    }
    
    @Unique
    private int getRightPanelWidth() {
        if (isExtremeGuiScale) {
            return Math.max(MIN_PANEL_WIDTH - 15, dynamicPanelWidth - 10);
        }
        return dynamicPanelWidth;
    }
    
    @Unique
    private int getRightPanelX() {
        if (isExtremeGuiScale) {
            return x + backgroundWidth + 3;
        }
        return x + backgroundWidth + 8;
    }

    @Inject(method = "drawBackground", at = @At("TAIL"))
    private void onDrawBackground(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        double newGuiScale = client.options.getGuiScale().getValue();
        boolean scaleChanged = newGuiScale != currentGuiScale;
        
        if (scaleChanged) {
            currentGuiScale = newGuiScale;
            calculateDynamicPanelSizes();
            
            if (leftPanelVisible) {
                refreshLeftPanel();
            }
            if (rightPanelVisible && calculationResult != null) {
                setupRightPanelButtons();
            }
        }
        
        updateSmoothScroll();
        updatePanelVisibility();
        
        if (leftPanelVisible && !isSameItem(lastAnvilItem, handler.getSlot(0).getStack())) {
            lastAnvilItem = handler.getSlot(0).getStack().copy();
            refreshLeftPanel();
        }

        if (leftPanelVisible) {
            renderLeftPanel(context);
            applyEnchantmentButtonClipping(context, mouseX, mouseY, delta);
            renderScrollMask(context);
        }

        if (rightPanelVisible && calculationResult != null) {
            renderRightPanel(context);
        }
        
        renderSelectedEnchantmentsOverlay(context);
    }
    
    @Unique
    private void applyEnchantmentButtonClipping(DrawContext context, int mouseX, int mouseY, float delta) {
        if (enchantmentButtons.isEmpty()) return;
        
        int leftPanelX = getLeftPanelX();
        int leftPanelY = y;
        int clipX = leftPanelX + 6;
        int clipY = leftPanelY + 40;
        int clipWidth = isExtremeGuiScale ? dynamicPanelWidth - 12 : dynamicPanelWidth - 30;
        int bottomReservedSpace = isExtremeGuiScale ? 24 : 48;
        int clipHeight = dynamicPanelHeight - 40 - bottomReservedSpace;
        
        context.enableScissor(clipX, clipY, clipX + clipWidth, clipY + clipHeight);
        
        for (EnchantmentButton button : enchantmentButtons) {
            button.render(context, mouseX, mouseY, delta);
        }
        
        context.disableScissor();
    }

    @Unique
    private void restorePersistedState() {
        selectedEnchantments.clear();
        selectedEnchantments.putAll(persistedSelectedEnchantments);
        calculationResult = persistedCalculationResult;
        currentStepIndex = persistedCurrentStepIndex;
        optimizationMode = persistedOptimizationMode;
        
        if (calculationResult != null) {
            rightPanelVisible = true;
        }
    }
    
    @Unique
    private void persistState() {
        persistedSelectedEnchantments.clear();
        persistedSelectedEnchantments.putAll(selectedEnchantments);
        persistedCalculationResult = calculationResult;
        persistedCurrentStepIndex = currentStepIndex;
        persistedOptimizationMode = optimizationMode;
    }
    
    @Unique
    private void updateSmoothScroll() {
        boolean wasAnimating = Math.abs(smoothScrollOffset - targetScrollOffset) > 0.01f;
        
        if (wasAnimating) {
            smoothScrollOffset += (targetScrollOffset - smoothScrollOffset) * SCROLL_SPEED;
            if (Math.abs(smoothScrollOffset - targetScrollOffset) < 0.01f) {
                smoothScrollOffset = targetScrollOffset;
            }
            recreateVisibleButtons();
        }
    }
    
    @Unique
    private void recreateVisibleButtons() {
        enchantmentButtons.forEach(this::remove);
        enchantmentButtons.clear();

        List<RegistryEntry<Enchantment>> filtered = getFilteredEnchantments();
        int leftPanelX = getLeftPanelX();
        int leftPanelY = y;
        int startY = leftPanelY + 40;
        
        int bottomReservedSpace = isExtremeGuiScale ? 24 : 48;
        int availableHeight = dynamicPanelHeight - 40 - bottomReservedSpace;
        int maxButtons = Math.min(maxVisibleEnchants, availableHeight / (BUTTON_HEIGHT + 2));

        int baseIndex = (int) smoothScrollOffset;
        float scrollFraction = smoothScrollOffset - baseIndex;
        int yOffset = (int) (-scrollFraction * (BUTTON_HEIGHT + 2));

        int buttonWidth = isExtremeGuiScale ? dynamicPanelWidth - 12 : dynamicPanelWidth - 30;

        int visibleCount = Math.min(maxButtons + 2, filtered.size() - baseIndex);
        for (int i = 0; i < visibleCount; i++) {
            int index = baseIndex + i;
            if (index >= filtered.size()) break;

            RegistryEntry<Enchantment> enchantment = filtered.get(index);
            EnchantmentInfo info = EnchantmentRegistry.getInfo(enchantment);
            boolean isFromInventory = inventoryBooks.stream().anyMatch(book -> book.enchantment().equals(enchantment));

            int buttonY = startY + (i * (BUTTON_HEIGHT + 2)) + yOffset;

            EnchantmentButton button = new EnchantmentButton(
                leftPanelX + 6,
                buttonY,
                buttonWidth,
                BUTTON_HEIGHT,
                enchantment,
                info != null ? info.maxLevel() : 1,
                isFromInventory,
                this::onEnchantmentLevelChanged
            );

            if (selectedEnchantments.containsKey(enchantment)) {
                button.setLevel(selectedEnchantments.get(enchantment));
            }

            enchantmentButtons.add(button);
        }

        updateEnchantmentCompatibility();
        updateScrollButtons();
    }

    @Unique
    private void updatePanelVisibility() {
        ItemStack anvilItem = handler.getSlot(0).getStack();
        boolean shouldShowLeft = !anvilItem.isEmpty() && !EnchantmentRegistry.getApplicableEnchantments(anvilItem).isEmpty();
        
        if (shouldShowLeft != leftPanelVisible) {
            leftPanelVisible = shouldShowLeft;
            if (leftPanelVisible) {
                setupLeftPanel();
            } else {
                clearLeftPanelWidgets();
                availableEnchantments.clear();
                inventoryBooks.clear();
                scrollOffset = 0;
            }
        }
        
        if (calculationResult != null) {
            rightPanelVisible = true;
            if (prevStepButton == null && nextStepButton == null && calculationResult.getSteps().size() > 1) {
                setupRightPanelButtons();
            }
        }
    }

    @Unique
    private boolean isSameItem(ItemStack stack1, ItemStack stack2) {
        if (stack1.isEmpty() && stack2.isEmpty()) return true;
        if (stack1.isEmpty() || stack2.isEmpty()) return false;
        return ItemStack.areItemsEqual(stack1, stack2);
    }

    @Unique
    private void setupLeftPanel() {
        clearLeftPanelWidgets();
        
        ItemStack anvilItem = handler.getSlot(0).getStack();
        if (anvilItem.isEmpty()) return;

        availableEnchantments = EnchantmentRegistry.getApplicableEnchantments(anvilItem);
        inventoryBooks = EnchantmentRegistry.scanInventoryForBooks(MinecraftClient.getInstance());

        availableEnchantments.sort((a, b) -> {
            boolean aInInventory = inventoryBooks.stream().anyMatch(book -> book.enchantment().equals(a));
            boolean bInInventory = inventoryBooks.stream().anyMatch(book -> book.enchantment().equals(b));
            
            if (aInInventory != bInInventory) {
                return aInInventory ? -1 : 1;
            }
            
            return Enchantment.getName(a, 1).getString().compareTo(Enchantment.getName(b, 1).getString());
        });

        int leftPanelX = getLeftPanelX();
        int leftPanelY = y;

        searchField = new SearchFieldWidget(
            textRenderer,
            leftPanelX + 6,
            leftPanelY + 6,
            dynamicPanelWidth - 12,
            16,
            this::filterEnchantments
        );
        addDrawableChild(searchField);

        if (!isExtremeGuiScale) {
            scrollUpButton = ButtonWidget.builder(Text.literal("▲"), btn -> scrollUp())
                .dimensions(leftPanelX + dynamicPanelWidth - 22, leftPanelY + 26, 18, 16)
                .build();
            addDrawableChild(scrollUpButton);

            scrollDownButton = ButtonWidget.builder(Text.literal("▼"), btn -> scrollDown())
                .dimensions(leftPanelX + dynamicPanelWidth - 22, leftPanelY + dynamicPanelHeight - 64, 18, 16)
                .build();
            addDrawableChild(scrollDownButton);
        }

        if (isExtremeGuiScale) {
            int buttonY = y + backgroundHeight + 4;
            int buttonSpacing = 2;
            int buttonWidth = 58;
            int totalButtonWidth = (buttonWidth * 3) + (buttonSpacing * 2);
            int startX = x + (backgroundWidth - totalButtonWidth) / 2;
            
            modeButton = ButtonWidget.builder(getModeButtonText(), btn -> cycleMode())
                .dimensions(startX, buttonY, buttonWidth, 18)
                .build();
            addDrawableChild(modeButton);

            calculateButton = ButtonWidget.builder(Text.literal("Calculate"), btn -> calculate())
                .dimensions(startX + buttonWidth + buttonSpacing, buttonY, buttonWidth, 18)
                .build();
            calculateButton.active = false;
            addDrawableChild(calculateButton);

            clearButton = ButtonWidget.builder(Text.literal("Clear"), btn -> clearSelections())
                .dimensions(startX + (buttonWidth + buttonSpacing) * 2, buttonY, buttonWidth, 18)
                .build();
            addDrawableChild(clearButton);
        } else {
            int buttonWidth = dynamicPanelWidth - 12;
            int halfButtonWidth = (dynamicPanelWidth - 18) / 2;
            
            modeButton = ButtonWidget.builder(getModeButtonText(), btn -> cycleMode())
                .dimensions(leftPanelX + 6, leftPanelY + dynamicPanelHeight - 42, halfButtonWidth, 18)
                .build();
            addDrawableChild(modeButton);

            calculateButton = ButtonWidget.builder(Text.literal("Calculate"), btn -> calculate())
                .dimensions(leftPanelX + 6 + halfButtonWidth + 6, leftPanelY + dynamicPanelHeight - 42, halfButtonWidth, 18)
                .build();
            calculateButton.active = false;
            addDrawableChild(calculateButton);

            clearButton = ButtonWidget.builder(Text.literal("Clear"), btn -> clearSelections())
                .dimensions(leftPanelX + 6, leftPanelY + dynamicPanelHeight - 22, buttonWidth, 16)
                .build();
            addDrawableChild(clearButton);
        }

        updateEnchantmentButtons();
    }

    @Unique
    private void updateEnchantmentButtons() {
        recreateVisibleButtons();
    }

    @Unique
    private List<RegistryEntry<Enchantment>> getFilteredEnchantments() {
        if (searchField == null || searchField.getText().isEmpty()) {
            return availableEnchantments;
        }

        String query = searchField.getText().toLowerCase();
        return availableEnchantments.stream()
            .filter(e -> Enchantment.getName(e, 1).getString().toLowerCase().contains(query))
            .collect(Collectors.toList());
    }

    @Unique
    private void updateEnchantmentCompatibility() {
        for (EnchantmentButton button : enchantmentButtons) {
            button.setEnabled(true);
        }

        for (RegistryEntry<Enchantment> selected : selectedEnchantments.keySet()) {
            EnchantmentInfo info = EnchantmentRegistry.getInfo(selected);
            if (info != null) {
                for (RegistryEntry<Enchantment> incompatible : info.incompatibleWith()) {
                    for (EnchantmentButton button : enchantmentButtons) {
                        if (button.getEnchantment().equals(incompatible)) {
                            button.setEnabled(false);
                        }
                    }
                }
            }
        }
    }

    @Unique
    private void updateScrollButtons() {
        List<RegistryEntry<Enchantment>> filtered = getFilteredEnchantments();
        if (scrollUpButton != null) {
            scrollUpButton.active = scrollOffset > 0;
        }
        if (scrollDownButton != null) {
            scrollDownButton.active = scrollOffset + maxVisibleEnchants < filtered.size();
        }
    }

    @Unique
    private void onEnchantmentLevelChanged(RegistryEntry<Enchantment> enchantment, int level) {
        if (level > 0) {
            selectedEnchantments.put(enchantment, level);
        } else {
            selectedEnchantments.remove(enchantment);
        }

        updateEnchantmentCompatibility();
        calculateButton.active = !selectedEnchantments.isEmpty();
        persistState();
    }

    @Unique
    private void filterEnchantments() {
        scrollOffset = 0;
        targetScrollOffset = 0.0f;
        smoothScrollOffset = 0.0f;
        updateEnchantmentButtons();
    }

    @Unique
    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            targetScrollOffset = scrollOffset;
        }
    }

    @Unique
    private void scrollDown() {
        List<RegistryEntry<Enchantment>> filtered = getFilteredEnchantments();
        if (scrollOffset + maxVisibleEnchants < filtered.size()) {
            scrollOffset++;
            targetScrollOffset = scrollOffset;
        }
    }

    @Unique
    private void cycleMode() {
        optimizationMode = switch (optimizationMode) {
            case LEVELS -> OptimizationMode.EXPERIENCE;
            case EXPERIENCE -> OptimizationMode.PRIOR_WORK;
            case PRIOR_WORK -> OptimizationMode.LEVELS;
        };
        
        if (modeButton != null) {
            modeButton.setMessage(getModeButtonText());
        }

        if (calculationResult != null) {
            calculate();
        }
    }

    @Unique
    private Text getModeButtonText() {
        return Text.literal(switch (optimizationMode) {
            case LEVELS -> "Mode: Levels";
            case EXPERIENCE -> "Mode: XP";
            case PRIOR_WORK -> "Mode: Work";
        });
    }

    @Unique
    private void calculate() {
        if (selectedEnchantments.isEmpty()) {
            System.out.println("EnchantCalc: No enchantments selected");
            return;
        }

        ItemStack anvilItem = handler.getSlot(0).getStack();
        if (anvilItem.isEmpty()) {
            System.out.println("EnchantCalc: No item in anvil");
            return;
        }

        List<EnchantmentCombination> combinations = selectedEnchantments.entrySet().stream()
            .map(entry -> new EnchantmentCombination(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());

        System.out.println("EnchantCalc: Calculating for " + combinations.size() + " enchantments");
        
        try {
            calculationResult = EnchantmentCalculator.calculate(anvilItem, combinations, optimizationMode);
            currentStepIndex = 0;
            rightPanelVisible = true;
            setupRightPanelButtons();
            persistState();
            System.out.println("EnchantCalc: Calculation successful! Steps: " + 
                (calculationResult != null ? calculationResult.getSteps().size() : 0));
        } catch (Exception e) {
            System.err.println("EnchantCalc: Calculation failed: " + e.getMessage());
            e.printStackTrace();
            calculationResult = null;
            rightPanelVisible = false;
            clearRightPanelButtons();
        }
    }

    @Unique
    private void setupRightPanelButtons() {
        clearRightPanelButtons();
        
        if (calculationResult == null || calculationResult.getSteps().size() <= 1) return;
        
        int rightPanelX = getRightPanelX();
        int rightPanelY = y;
        int navY = rightPanelY + dynamicPanelHeight - 26;
        int rightPanelWidth = getRightPanelWidth();
        
        int buttonWidth = (rightPanelWidth - 18) / 2;
        
        prevStepButton = ButtonWidget.builder(Text.literal("◄ Prev"), btn -> previousStep())
            .dimensions(rightPanelX + 6, navY, buttonWidth, 20)
            .build();
        addDrawableChild(prevStepButton);
        
        nextStepButton = ButtonWidget.builder(Text.literal("Next ►"), btn -> nextStep())
            .dimensions(rightPanelX + 6 + buttonWidth + 6, navY, buttonWidth, 20)
            .build();
        addDrawableChild(nextStepButton);
        
        updateRightPanelButtons();
    }

    @Unique
    private void updateRightPanelButtons() {
        if (prevStepButton != null) {
            prevStepButton.active = currentStepIndex > 0;
        }
        if (nextStepButton != null) {
            nextStepButton.active = calculationResult != null && 
                                   currentStepIndex < calculationResult.getSteps().size() - 1;
        }
    }

    @Unique
    private void clearRightPanelButtons() {
        if (prevStepButton != null) {
            remove(prevStepButton);
            prevStepButton = null;
        }
        if (nextStepButton != null) {
            remove(nextStepButton);
            nextStepButton = null;
        }
    }

    @Unique
    private void clearSelections() {
        selectedEnchantments.clear();
        scrollOffset = 0;
        calculationResult = null;
        rightPanelVisible = false;
        calculateButton.active = false;
        clearRightPanelButtons();
        updateEnchantmentButtons();
        persistState();
    }

    @Unique
    private void refreshLeftPanel() {
        clearInterface();
        setupLeftPanel();
    }

    @Unique
    private void renderLeftPanel(DrawContext context) {
        int leftPanelX = getLeftPanelX();
        int leftPanelY = y;

        context.fill(leftPanelX, leftPanelY, leftPanelX + dynamicPanelWidth, leftPanelY + dynamicPanelHeight, 0xDD000000);
        context.drawBorder(leftPanelX, leftPanelY, dynamicPanelWidth, dynamicPanelHeight, 0xFF8B8B8B);

        int inventoryBookCount = inventoryBooks.size();
        List<RegistryEntry<Enchantment>> filtered = getFilteredEnchantments();
        String infoText;
        
        if (inventoryBookCount > 0) {
            infoText = inventoryBookCount + " book" + (inventoryBookCount != 1 ? "s" : "") + " | " + 
                      filtered.size() + " enchant" + (filtered.size() != 1 ? "s" : "");
        } else {
            infoText = filtered.size() + " enchants";
        }
        
        context.drawTextWithShadow(textRenderer, Text.literal(infoText), 
            leftPanelX + 6, leftPanelY + 26, 0xFFAAAAAA);
    }
    
    @Unique
    private void renderScrollMask(DrawContext context) {
        int leftPanelX = getLeftPanelX();
        int leftPanelY = y;
        
        context.fill(leftPanelX + 1, leftPanelY + 6, leftPanelX + dynamicPanelWidth - 1, leftPanelY + 23, 0xDD000000);
        
        if (!isExtremeGuiScale) {
            int bottomReservedSpace = 48;
            context.fill(leftPanelX + 1, leftPanelY + dynamicPanelHeight - bottomReservedSpace, 
                        leftPanelX + dynamicPanelWidth - 1, leftPanelY + dynamicPanelHeight - 1, 0xDD000000);
        }
    }

    @Unique
    private void renderRightPanel(DrawContext context) {
        int rightPanelX = getRightPanelX();
        int rightPanelY = y;
        int rightPanelWidth = getRightPanelWidth();

        context.fill(rightPanelX, rightPanelY, rightPanelX + rightPanelWidth, rightPanelY + dynamicPanelHeight, 0xDD000000);
        context.drawBorder(rightPanelX, rightPanelY, rightPanelWidth, dynamicPanelHeight, 0xFF8B8B8B);

        if (calculationResult == null) return;

        boolean hasImpossibleSteps = calculationResult.getSteps().stream()
            .anyMatch(CalculationResult.Step::isTooExpensive);
        
        String totalText = "Total: " + calculationResult.getTotalLevels() + " levels";
        int totalColor = hasImpossibleSteps ? 0xFFFF5555 : 0xFFFFFF55;
        context.drawTextWithShadow(textRenderer, Text.literal(totalText), 
            rightPanelX + 6, rightPanelY + 6, totalColor);
        
        if (hasImpossibleSteps) {
            context.drawTextWithShadow(textRenderer, Text.literal("⚠ IMPOSSIBLE"), 
                rightPanelX + 6 + textRenderer.getWidth(totalText) + 6, rightPanelY + 6, 0xFFFF5555);
        }

        if (calculationResult.getSteps().isEmpty()) {
            context.drawTextWithShadow(textRenderer, Text.literal("No solution found"), 
                rightPanelX + 6, rightPanelY + 24, 0xFFFF5555);
            return;
        }

        String stepText = "Step " + (currentStepIndex + 1) + " / " + calculationResult.getSteps().size();
        context.drawTextWithShadow(textRenderer, Text.literal(stepText), 
            rightPanelX + 6, rightPanelY + 20, 0xFFCCCCCC);

        CalculationResult.Step currentStep = calculationResult.getSteps().get(currentStepIndex);
        
        if (currentStep.isTooExpensive()) {
            context.drawTextWithShadow(textRenderer, Text.literal("⚠ WARNING: TOO EXPENSIVE! ⚠"), 
                rightPanelX + 6, rightPanelY + 36, 0xFFFF5555);
        }
        
        int textY = rightPanelY + (currentStep.isTooExpensive() ? 52 : 36);
        context.drawTextWithShadow(textRenderer, Text.literal("Action:"), 
            rightPanelX + 6, textY, 0xFFAAAAAA);
        textY += 12;

        List<String> wrappedDesc = wrapText(currentStep.getDescription(), rightPanelWidth - 16);
        for (String line : wrappedDesc) {
            int color = currentStep.isTooExpensive() ? 0xFFFF5555 : 0xFFFFFFFF;
            context.drawTextWithShadow(textRenderer, Text.literal(line), 
                rightPanelX + 10, textY, color);
            textY += 10;
        }

        textY += 6;
        int costColor = currentStep.isTooExpensive() ? 0xFFFF5555 : 0xFFFFFF55;
        context.drawTextWithShadow(textRenderer, 
            Text.literal("Cost: " + currentStep.getLevels() + " levels"), 
            rightPanelX + 6, textY, costColor);
        textY += 11;
        context.drawTextWithShadow(textRenderer, 
            Text.literal("XP: " + currentStep.getExperience()), 
            rightPanelX + 6, textY, 0xFF55FFAA);
        textY += 11;
        context.drawTextWithShadow(textRenderer, 
            Text.literal("Penalty: " + currentStep.getPriorWorkPenalty()), 
            rightPanelX + 6, textY, 0xFFFFAA55);
    }

    @Unique
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (textRenderer.getWidth(testLine) > maxWidth) {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            } else {
                if (!currentLine.isEmpty()) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }
    
    @Unique
    private void renderSelectedEnchantmentsOverlay(DrawContext context) {
        if (selectedEnchantments.isEmpty()) return;
        
        int leftPanelX = getLeftPanelX();
        int rightPanelX = getRightPanelX();
        int rightPanelWidth = getRightPanelWidth();
        int totalGuiWidth = (rightPanelX + rightPanelWidth) - leftPanelX;
        int guiCenterX = leftPanelX + (totalGuiWidth / 2);
        
        int startY = y - 16;
        
        List<String> enchantTexts = new ArrayList<>();
        for (Map.Entry<RegistryEntry<Enchantment>, Integer> entry : selectedEnchantments.entrySet()) {
            String enchantName = Enchantment.getName(entry.getKey(), entry.getValue()).getString();
            enchantTexts.add(enchantName);
        }
        
        int maxLineWidth = totalGuiWidth - 20;
        
        List<List<String>> lines = new ArrayList<>();
        List<String> currentLine = new ArrayList<>();
        int currentLineWidth = 0;
        
        for (String enchantText : enchantTexts) {
            int textWidth = textRenderer.getWidth(enchantText);
            int spacingWidth = 8;
            
            if (currentLineWidth + textWidth + spacingWidth > maxLineWidth && !currentLine.isEmpty()) {
                lines.add(new ArrayList<>(currentLine));
                currentLine.clear();
                currentLineWidth = 0;
            }
            
            currentLine.add(enchantText);
            currentLineWidth += textWidth + spacingWidth;
        }
        
        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }
        
        int totalHeight = (lines.size() * 14) + 14;
        
        int currentY = startY - totalHeight;
        
        String counterText = "Selected: " + selectedEnchantments.size() + " enchantment" + (selectedEnchantments.size() == 1 ? "" : "s");
        int counterWidth = textRenderer.getWidth(counterText);
        context.fill(guiCenterX - counterWidth / 2 - 4, currentY - 2, 
                    guiCenterX + counterWidth / 2 + 4, currentY + 10, 0xDD000000);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(counterText), 
                                          guiCenterX, currentY, 0xFFFFFF55);
        currentY += 14;
        
        for (List<String> line : lines) {
            int lineWidth = 0;
            for (String text : line) {
                lineWidth += textRenderer.getWidth(text) + 8;
            }
            lineWidth -= 8;
            
            int startX = guiCenterX - lineWidth / 2;
            int currentX = startX;
            
            for (String enchantText : line) {
                int textWidth = textRenderer.getWidth(enchantText);
                context.fill(currentX - 2, currentY - 2, currentX + textWidth + 2, currentY + 10, 0xDD000000);
                context.drawTextWithShadow(textRenderer, Text.literal(enchantText), currentX, currentY, 0xFF55FF55);
                currentX += textWidth + 8;
            }
            
            currentY += 14;
        }
    }

    @Unique
    private void previousStep() {
        if (currentStepIndex > 0) {
            currentStepIndex--;
            updateRightPanelButtons();
            persistState();
        }
    }

    @Unique
    private void nextStep() {
        if (calculationResult != null && currentStepIndex < calculationResult.getSteps().size() - 1) {
            currentStepIndex++;
            updateRightPanelButtons();
            persistState();
        }
    }

    @Unique
    private void clearLeftPanelWidgets() {
        enchantmentButtons.forEach(this::remove);
        enchantmentButtons.clear();

        if (searchField != null) {
            remove(searchField);
            searchField = null;
        }
        if (calculateButton != null) {
            remove(calculateButton);
            calculateButton = null;
        }
        if (clearButton != null) {
            remove(clearButton);
            clearButton = null;
        }
        if (scrollUpButton != null) {
            remove(scrollUpButton);
            scrollUpButton = null;
        }
        if (scrollDownButton != null) {
            remove(scrollDownButton);
            scrollDownButton = null;
        }
        if (modeButton != null) {
            remove(modeButton);
            modeButton = null;
        }
    }

    @Unique
    private void clearInterface() {
        clearLeftPanelWidgets();
        clearRightPanelButtons();
        availableEnchantments.clear();
        inventoryBooks.clear();
        scrollOffset = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (leftPanelVisible) {
            int leftPanelX = getLeftPanelX();
            int leftPanelY = y;
            
            if (mouseX >= leftPanelX && mouseX <= leftPanelX + dynamicPanelWidth &&
                mouseY >= leftPanelY && mouseY <= leftPanelY + dynamicPanelHeight) {
                
                if (verticalAmount > 0) {
                    scrollUp();
                    return true;
                } else if (verticalAmount < 0) {
                    scrollDown();
                    return true;
                }
            }
        }
        
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (leftPanelVisible && button == 0) {
            int leftPanelY = y;
            int clipY = leftPanelY + 40;
            int bottomReservedSpace = isExtremeGuiScale ? 24 : 48;
            int clipHeight = dynamicPanelHeight - 40 - bottomReservedSpace;
            
            for (EnchantmentButton enchantButton : enchantmentButtons) {
                int buttonY = enchantButton.getY();
                if (buttonY + BUTTON_HEIGHT >= clipY && buttonY <= clipY + clipHeight) {
                    if (enchantButton.isMouseOver(mouseX, mouseY)) {
                        enchantButton.onClick(mouseX, mouseY);
                        return true;
                    }
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        
        if (searchField != null && searchField.isFocused()) {
            if (searchField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        
        if (searchField != null && searchField.isFocused()) {
            if (searchField.charTyped(chr, modifiers)) {
                return true;
            }
        }
        
        return super.charTyped(chr, modifiers);
    }
}

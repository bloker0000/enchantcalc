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
    @Unique private static final int PANEL_WIDTH = 180;
    @Unique private static final int PANEL_HEIGHT = 200;
    @Unique private static final int PANEL_OFFSET = 10;
    @Unique private static final int BUTTON_HEIGHT = 16;
    @Unique private static final int MAX_VISIBLE_ENCHANTS = 6;
    @Unique private static final float SCROLL_SPEED = 0.15f;
    
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
        restorePersistedState();
        updatePanelVisibility();
        if (leftPanelVisible) {
            setupLeftPanel();
        }
    }

    @Inject(method = "drawBackground", at = @At("TAIL"))
    private void onDrawBackground(DrawContext context, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        updateSmoothScroll();
        updatePanelVisibility();
        
        if (leftPanelVisible && !isSameItem(lastAnvilItem, handler.getSlot(0).getStack())) {
            lastAnvilItem = handler.getSlot(0).getStack().copy();
            refreshLeftPanel();
        }

        if (leftPanelVisible) {
            renderLeftPanel(context);
        }

        if (rightPanelVisible && calculationResult != null) {
            renderRightPanel(context);
        }
        
        renderSelectedEnchantmentsOverlay(context);
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
        int leftPanelX = x - PANEL_WIDTH - PANEL_OFFSET;
        int leftPanelY = y;
        int startY = leftPanelY + 46;
        int availableHeight = PANEL_HEIGHT - 46 - 48;
        int maxButtons = Math.min(MAX_VISIBLE_ENCHANTS, availableHeight / (BUTTON_HEIGHT + 2));

        int baseIndex = (int) smoothScrollOffset;
        float scrollFraction = smoothScrollOffset - baseIndex;
        int yOffset = (int) (-scrollFraction * (BUTTON_HEIGHT + 2));

        int visibleCount = Math.min(maxButtons + 1, filtered.size() - baseIndex);
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
                PANEL_WIDTH - 30,
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
            addDrawableChild(button);
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

        int leftPanelX = x - PANEL_WIDTH - PANEL_OFFSET;
        int leftPanelY = y;

        
        searchField = new SearchFieldWidget(
            textRenderer,
            leftPanelX + 6,
            leftPanelY + 6,
            PANEL_WIDTH - 12,
            16,
            this::filterEnchantments
        );
        addDrawableChild(searchField);

        
        scrollUpButton = ButtonWidget.builder(Text.literal("▲"), btn -> scrollUp())
            .dimensions(leftPanelX + PANEL_WIDTH - 22, leftPanelY + 26, 18, 16)
            .build();
        addDrawableChild(scrollUpButton);

        scrollDownButton = ButtonWidget.builder(Text.literal("▼"), btn -> scrollDown())
            .dimensions(leftPanelX + PANEL_WIDTH - 22, leftPanelY + PANEL_HEIGHT - 64, 18, 16)
            .build();
        addDrawableChild(scrollDownButton);

        
        modeButton = ButtonWidget.builder(getModeButtonText(), btn -> cycleMode())
            .dimensions(leftPanelX + 6, leftPanelY + PANEL_HEIGHT - 42, 82, 18)
            .build();
        addDrawableChild(modeButton);

        calculateButton = ButtonWidget.builder(Text.literal("Calculate"), btn -> calculate())
            .dimensions(leftPanelX + 92, leftPanelY + PANEL_HEIGHT - 42, 82, 18)
            .build();
        calculateButton.active = false;
        addDrawableChild(calculateButton);

        clearButton = ButtonWidget.builder(Text.literal("Clear"), btn -> clearSelections())
            .dimensions(leftPanelX + 6, leftPanelY + PANEL_HEIGHT - 22, PANEL_WIDTH - 12, 16)
            .build();
        addDrawableChild(clearButton);

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
            scrollDownButton.active = scrollOffset + MAX_VISIBLE_ENCHANTS < filtered.size();
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
        if (scrollOffset + MAX_VISIBLE_ENCHANTS < filtered.size()) {
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
        
        int rightPanelX = x + backgroundWidth + PANEL_OFFSET;
        int rightPanelY = y;
        int navY = rightPanelY + PANEL_HEIGHT - 26;
        
        prevStepButton = ButtonWidget.builder(Text.literal("◄ Prev"), btn -> previousStep())
            .dimensions(rightPanelX + 6, navY, 82, 20)
            .build();
        addDrawableChild(prevStepButton);
        
        nextStepButton = ButtonWidget.builder(Text.literal("Next ►"), btn -> nextStep())
            .dimensions(rightPanelX + 92, navY, 82, 20)
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
        int leftPanelX = x - PANEL_WIDTH - PANEL_OFFSET;
        int leftPanelY = y;

        
        context.fill(leftPanelX, leftPanelY, leftPanelX + PANEL_WIDTH, leftPanelY + PANEL_HEIGHT, 0xDD000000);
        context.drawBorder(leftPanelX, leftPanelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF8B8B8B);

        
        int inventoryBookCount = inventoryBooks.size();
        List<RegistryEntry<Enchantment>> filtered = getFilteredEnchantments();
        String infoText;
        
        if (inventoryBookCount > 0) {
            infoText = inventoryBookCount + " book" + (inventoryBookCount != 1 ? "s" : "") + " | " + 
                      filtered.size() + " enchant" + (filtered.size() != 1 ? "s" : "");
        } else {
            infoText = filtered.size() + " enchantment" + (filtered.size() != 1 ? "s" : "") + " available";
        }
        
        context.drawTextWithShadow(textRenderer, Text.literal(infoText), 
            leftPanelX + 6, leftPanelY + 28, 0xFFAAAAAA);
    }

    @Unique
    private void renderRightPanel(DrawContext context) {
        int rightPanelX = x + backgroundWidth + PANEL_OFFSET;
        int rightPanelY = y;

        
        context.fill(rightPanelX, rightPanelY, rightPanelX + PANEL_WIDTH, rightPanelY + PANEL_HEIGHT, 0xDD000000);
        context.drawBorder(rightPanelX, rightPanelY, PANEL_WIDTH, PANEL_HEIGHT, 0xFF8B8B8B);

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

        List<String> wrappedDesc = wrapText(currentStep.getDescription(), PANEL_WIDTH - 16);
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
        
        int centerX = x + backgroundWidth / 2;
        int topY = y - 30;
        
        String counterText = "Selected: " + selectedEnchantments.size() + " enchantment" + (selectedEnchantments.size() == 1 ? "" : "s");
        int counterWidth = textRenderer.getWidth(counterText);
        
        context.fill(centerX - counterWidth / 2 - 4, topY - 2, centerX + counterWidth / 2 + 4, topY + 10, 0xDD000000);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(counterText), centerX, topY, 0xFFFFFF55);
        
        int enchantY = topY + 14;
        int currentX = centerX;
        int lineWidth = 0;
        List<String> enchantTexts = new ArrayList<>();
        
        for (Map.Entry<RegistryEntry<Enchantment>, Integer> entry : selectedEnchantments.entrySet()) {
            String enchantName = Enchantment.getName(entry.getKey(), entry.getValue()).getString();
            enchantTexts.add(enchantName);
            lineWidth += textRenderer.getWidth(enchantName) + 8;
        }
        
        int startX = centerX - lineWidth / 2;
        currentX = startX;
        
        for (String enchantText : enchantTexts) {
            int textWidth = textRenderer.getWidth(enchantText);
            context.fill(currentX - 2, enchantY - 2, currentX + textWidth + 2, enchantY + 10, 0xDD000000);
            context.drawTextWithShadow(textRenderer, Text.literal(enchantText), currentX, enchantY, 0xFF55FF55);
            currentX += textWidth + 8;
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
            int leftPanelX = x - PANEL_WIDTH - PANEL_OFFSET;
            int leftPanelY = y;
            
            
            if (mouseX >= leftPanelX && mouseX <= leftPanelX + PANEL_WIDTH &&
                mouseY >= leftPanelY && mouseY <= leftPanelY + PANEL_HEIGHT) {
                
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

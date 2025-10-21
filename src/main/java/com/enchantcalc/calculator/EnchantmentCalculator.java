package com.enchantcalc.calculator;

import com.enchantcalc.data.EnchantmentInfo;
import com.enchantcalc.data.EnchantmentRegistry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.*;

public class EnchantmentCalculator {
    private static final int[] ANVIL_COST_MULTIPLIERS = {0, 1, 3, 7, 15, 31, 63};
    private static final int MAX_ANVIL_COST = 39;

    public static CalculationResult calculate(ItemStack targetItem, List<EnchantmentCombination> enchantments, OptimizationMode mode) {
        String itemName = targetItem.getName().getString();
        EnchantItem baseItem = new EnchantItem(itemName, 0, true, 0);
        
        List<EnchantItem> books = new ArrayList<>();
        for (EnchantmentCombination combo : enchantments) {
            int bookCost = getBookEnchantmentCost(combo.enchantment(), combo.level());
            String enchantName = Enchantment.getName(combo.enchantment(), combo.level()).getString();
            books.add(new EnchantItem(enchantName, bookCost, false, 0, List.of(enchantName)));
        }
        
        TreeResult result = findOptimalSequence(baseItem, books, mode);
        return new CalculationResult(result.steps, result.totalLevels, result.totalExperience);
    }

    private static int getBookEnchantmentCost(RegistryEntry<Enchantment> enchantment, int level) {
        EnchantmentInfo info = EnchantmentRegistry.getInfo(enchantment);
        int weight = info != null ? info.weight() : 1;
        return weight * level;
    }

    private static TreeResult findOptimalSequence(EnchantItem baseItem, List<EnchantItem> books, OptimizationMode mode) {
        if (books.isEmpty()) {
            return new TreeResult(new ArrayList<>(), 0, 0);
        }
        
        books.sort((a, b) -> Integer.compare(b.enchantmentCost, a.enchantmentCost));
        
        EnchantItem mostExpensive = books.remove(0);
        
        EnchantItem currentItem = mergeItems(baseItem, mostExpensive, true);
        List<CalculationResult.Step> steps = new ArrayList<>();
        steps.add(currentItem.lastStep);
        
        List<EnchantItem> allItems = new ArrayList<>(books);
        allItems.add(currentItem);
        
        TreeResult result = findBestCombination(allItems, steps, mode);
        return result;
    }
    
    private static TreeResult findBestCombination(List<EnchantItem> items, List<CalculationResult.Step> existingSteps, OptimizationMode mode) {
        if (items.size() == 1) {
            EnchantItem finalItem = items.get(0);
            if (!finalItem.isItem) {
                return new TreeResult(new ArrayList<>(), Integer.MAX_VALUE, Integer.MAX_VALUE);
            }
            int totalLevels = existingSteps.stream().mapToInt(CalculationResult.Step::getLevels).sum();
            int totalExperience = existingSteps.stream().mapToInt(CalculationResult.Step::getExperience).sum();
            return new TreeResult(existingSteps, totalLevels, totalExperience);
        }
        
        TreeResult bestResult = null;
        int bestCost = Integer.MAX_VALUE;
        
        for (int i = 0; i < items.size(); i++) {
            for (int j = 0; j < items.size(); j++) {
                if (i == j) continue;
                
                EnchantItem left = items.get(i);
                EnchantItem right = items.get(j);
                
                if (!left.isItem && right.isItem) continue;
                
                try {
                    EnchantItem merged = mergeItems(left, right, false);
                    
                    List<EnchantItem> newItems = new ArrayList<>();
                    for (int k = 0; k < items.size(); k++) {
                        if (k != i && k != j) {
                            newItems.add(items.get(k));
                        }
                    }
                    newItems.add(merged);
                    
                    List<CalculationResult.Step> newSteps = new ArrayList<>(existingSteps);
                    newSteps.add(merged.lastStep);
                    
                    TreeResult result = findBestCombination(newItems, newSteps, mode);
                    
                    int cost = switch (mode) {
                        case LEVELS -> result.totalLevels;
                        case EXPERIENCE -> result.totalExperience;
                        case PRIOR_WORK -> result.steps.stream().mapToInt(CalculationResult.Step::getPriorWorkPenalty).sum();
                    };
                    
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestResult = result;
                    }
                } catch (Exception e) {
                }
            }
        }
        
        return bestResult != null ? bestResult : new TreeResult(new ArrayList<>(), Integer.MAX_VALUE, Integer.MAX_VALUE);
    }
    
    private static EnchantItem mergeItems(EnchantItem left, EnchantItem right, boolean isInitialMerge) {
        int enchantmentCost = right.enchantmentCost;
        int priorWorkPenalty = 0;
        
        if (left.anvilCost < ANVIL_COST_MULTIPLIERS.length) {
            priorWorkPenalty += ANVIL_COST_MULTIPLIERS[left.anvilCost];
        } else {
            priorWorkPenalty += 63;
        }
        
        if (right.anvilCost < ANVIL_COST_MULTIPLIERS.length) {
            priorWorkPenalty += ANVIL_COST_MULTIPLIERS[right.anvilCost];
        } else {
            priorWorkPenalty += 63;
        }
        
        int levelCost = enchantmentCost + priorWorkPenalty;
        int experienceCost = calculateExperience(levelCost);
        boolean tooExpensive = levelCost > MAX_ANVIL_COST;
        
        List<String> newEnchantments = new ArrayList<>(left.enchantments);
        newEnchantments.addAll(right.enchantments);
        
        String description;
        if (left.isItem && right.isItem) {
            description = "Combine " + left.name + " with " + right.name;
        } else if (left.isItem) {
            description = "Combine " + left.name + " with " + right.name + " (book)";
        } else if (right.isItem) {
            description = "Combine " + right.name + " (book) with " + left.name;
        } else {
            description = "Combine " + left.name + " (book) with " + right.name + " (book)";
        }
        
        if (tooExpensive) {
            description += " - TOO EXPENSIVE!";
        }
        
        CalculationResult.Step step = new CalculationResult.Step(
            description,
            levelCost,
            experienceCost,
            priorWorkPenalty,
            tooExpensive
        );
        
        int newAnvilCost = Math.max(left.anvilCost, right.anvilCost) + 1;
        boolean newIsItem = left.isItem || right.isItem;
        String newName = newIsItem ? (left.isItem ? left.name : right.name) : "Combined Book";
        
        EnchantItem result = new EnchantItem(newName, left.enchantmentCost + right.enchantmentCost, newIsItem, newAnvilCost, newEnchantments);
        result.lastStep = step;
        return result;
    }

    private static TreeResult findOptimalTree(List<EnchantItem> items, OptimizationMode mode) {
        int n = items.size();
        List<TreeStructure> structures = generateOptimalTreeStructures(n);
        
        TreeResult bestResult = null;
        int bestCost = Integer.MAX_VALUE;
        
        for (TreeStructure structure : structures) {
            List<EnchantItem> optimizedItems = optimizeItemPlacement(items, structure);
            List<CalculationResult.Step> steps = new ArrayList<>();
            
            try {
                TreeNode root = buildTreeWithItems(structure, optimizedItems, 0, steps);
                int totalLevels = steps.stream().mapToInt(CalculationResult.Step::getLevels).sum();
                int totalExperience = steps.stream().mapToInt(CalculationResult.Step::getExperience).sum();
                
                int cost = switch (mode) {
                    case LEVELS -> totalLevels;
                    case EXPERIENCE -> totalExperience;
                    case PRIOR_WORK -> steps.stream().mapToInt(CalculationResult.Step::getPriorWorkPenalty).sum();
                };
                
                if (cost < bestCost) {
                    bestCost = cost;
                    bestResult = new TreeResult(steps, totalLevels, totalExperience);
                }
            } catch (Exception e) {
            }
        }
        
        return bestResult != null ? bestResult : new TreeResult(new ArrayList<>(), 0, 0);
    }

    private static List<TreeStructure> generateOptimalTreeStructures(int n) {
        if (n <= 1) return List.of(new TreeStructure(0, 0));
        if (n == 2) return List.of(new TreeStructure(0, 1));
        
        List<TreeStructure> structures = new ArrayList<>();
        Map<Integer, TreeStructure> bestByDepth = new HashMap<>();
        
        for (int leftCount = 1; leftCount < n; leftCount++) {
            int rightCount = n - leftCount;
            List<TreeStructure> leftStructures = generateOptimalTreeStructures(leftCount);
            List<TreeStructure> rightStructures = generateOptimalTreeStructures(rightCount);
            
            for (TreeStructure left : leftStructures) {
                for (TreeStructure right : rightStructures) {
                    TreeStructure combined = new TreeStructure(left, right);
                    int depth = combined.getMaxDepth();
                    
                    if (!bestByDepth.containsKey(depth) || combined.getWeightedSum() < bestByDepth.get(depth).getWeightedSum()) {
                        bestByDepth.put(depth, combined);
                    }
                }
            }
        }
        
        structures.addAll(bestByDepth.values());
        return structures;
    }

    private static List<EnchantItem> optimizeItemPlacement(List<EnchantItem> items, TreeStructure tree) {
        List<EnchantItem> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> Integer.compare(b.enchantmentCost, a.enchantmentCost));
        
        List<Integer> contributions = tree.getLeafContributions();
        List<EnchantItem> optimized = new ArrayList<>();
        
        int itemCount = Math.min(sorted.size(), contributions.size());
        
        Map<Integer, EnchantItem> placementMap = new HashMap<>();
        for (int i = 0; i < itemCount; i++) {
            placementMap.put(contributions.get(i), sorted.get(i));
        }
        
        List<Integer> sortedContributions = new ArrayList<>(contributions.subList(0, itemCount));
        Collections.sort(sortedContributions);
        
        for (int contrib : sortedContributions) {
            optimized.add(placementMap.get(contrib));
        }
        
        return optimized;
    }

    private static TreeNode buildTreeWithItems(TreeStructure structure, List<EnchantItem> items, int startIndex, List<CalculationResult.Step> steps) {
        if (structure.isLeaf) {
            EnchantItem item = items.get(startIndex);
            return new TreeNode(item);
        }
        
        int leftSize = structure.left.getLeafCount();
        TreeNode leftNode = buildTreeWithItems(structure.left, items, startIndex, steps);
        TreeNode rightNode = buildTreeWithItems(structure.right, items, startIndex + leftSize, steps);
        
        int enchantmentCost = rightNode.item.enchantmentCost;
        int priorWorkPenalty = 0;
        
        if (leftNode.item.anvilCost < ANVIL_COST_MULTIPLIERS.length) {
            priorWorkPenalty += ANVIL_COST_MULTIPLIERS[leftNode.item.anvilCost];
        } else {
            priorWorkPenalty += 63;
        }
        
        if (rightNode.item.anvilCost < ANVIL_COST_MULTIPLIERS.length) {
            priorWorkPenalty += ANVIL_COST_MULTIPLIERS[rightNode.item.anvilCost];
        } else {
            priorWorkPenalty += 63;
        }
        
        int mergeCost = enchantmentCost + priorWorkPenalty;
        boolean tooExpensive = mergeCost > MAX_ANVIL_COST;
        
        String leftName = leftNode.item.name;
        String rightName = rightNode.item.name;
        int experience = calculateExperience(mergeCost);
        String description = "Combine " + leftName + " + " + rightName;
        if (tooExpensive) {
            description += " - TOO EXPENSIVE!";
        }
        
        steps.add(new CalculationResult.Step(description, mergeCost, experience, priorWorkPenalty, tooExpensive));
        
        String combinedName = leftNode.item.isItem ? leftNode.item.name : "Combined Item";
        List<String> combinedEnchantments = new ArrayList<>();
        combinedEnchantments.addAll(leftNode.item.enchantments);
        combinedEnchantments.addAll(rightNode.item.enchantments);
        
        int newAnvilCost = Math.max(leftNode.item.anvilCost, rightNode.item.anvilCost) + 1;
        int newEnchantmentCost = leftNode.item.enchantmentCost + rightNode.item.enchantmentCost;
        
        EnchantItem combinedItem = new EnchantItem(combinedName, newEnchantmentCost, leftNode.item.isItem, newAnvilCost, combinedEnchantments);
        return new TreeNode(combinedItem);
    }

    private static int calculateExperience(int levels) {
        if (levels <= 16) {
            return levels * levels + 6 * levels;
        } else if (levels <= 31) {
            return (int) (2.5 * levels * levels - 40.5 * levels + 360);
        } else {
            return (int) (4.5 * levels * levels - 162.5 * levels + 2220);
        }
    }

    private static class EnchantItem {
        String name;
        int enchantmentCost;
        boolean isItem;
        int anvilCost;
        List<String> enchantments;
        CalculationResult.Step lastStep;

        EnchantItem(String name, int enchantmentCost, boolean isItem, int anvilCost) {
            this(name, enchantmentCost, isItem, anvilCost, new ArrayList<>());
        }

        EnchantItem(String name, int enchantmentCost, boolean isItem, int anvilCost, List<String> enchantments) {
            this.name = name;
            this.enchantmentCost = enchantmentCost;
            this.isItem = isItem;
            this.anvilCost = anvilCost;
            this.enchantments = new ArrayList<>(enchantments);
        }
    }

    private static class TreeStructure {
        TreeStructure left;
        TreeStructure right;
        boolean isLeaf;

        TreeStructure(int leftIndex, int rightIndex) {
            this.isLeaf = true;
        }

        TreeStructure(TreeStructure left, TreeStructure right) {
            this.left = left;
            this.right = right;
            this.isLeaf = false;
        }

        int getMaxDepth() {
            if (isLeaf) return 0;
            return 1 + Math.max(left.getMaxDepth(), right.getMaxDepth());
        }

        int getLeafCount() {
            if (isLeaf) return 1;
            return left.getLeafCount() + right.getLeafCount();
        }

        int getWeightedSum() {
            return getWeightedSum(0);
        }

        int getWeightedSum(int depth) {
            if (isLeaf) return depth;
            return left.getWeightedSum(depth + 1) + right.getWeightedSum(depth + 1);
        }

        List<Integer> getLeafContributions() {
            List<Integer> contributions = new ArrayList<>();
            collectContributions(0, contributions);
            return contributions;
        }

        void collectContributions(int depth, List<Integer> contributions) {
            if (isLeaf) {
                contributions.add(depth);
            } else {
                left.collectContributions(depth + 1, contributions);
                right.collectContributions(depth + 1, contributions);
            }
        }
    }

    private static class TreeNode {
        EnchantItem item;

        TreeNode(EnchantItem item) {
            this.item = item;
        }
    }

    private static class TreeResult {
        List<CalculationResult.Step> steps;
        int totalLevels;
        int totalExperience;

        TreeResult(List<CalculationResult.Step> steps, int totalLevels, int totalExperience) {
            this.steps = steps;
            this.totalLevels = totalLevels;
            this.totalExperience = totalExperience;
        }
    }
}

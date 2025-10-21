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
        List<EnchantItem> items = new ArrayList<>();
        
        items.add(new EnchantItem(itemName, 0, true, 0));
        
        for (EnchantmentCombination combo : enchantments) {
            int bookCost = getBookEnchantmentCost(combo.enchantment(), combo.level());
            String enchantName = Enchantment.getName(combo.enchantment(), combo.level()).getString();
            String bookName = "Book (" + enchantName + ")";
            items.add(new EnchantItem(bookName, bookCost, false, 0, List.of(enchantName)));
        }
        
        TreeResult result = findOptimalTree(items, mode);
        return new CalculationResult(result.steps, result.totalLevels, result.totalExperience);
    }

    private static int getBookEnchantmentCost(RegistryEntry<Enchantment> enchantment, int level) {
        EnchantmentInfo info = EnchantmentRegistry.getInfo(enchantment);
        int weight = info != null ? info.weight() : 1;
        return weight * level;
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
        
        if (mergeCost > MAX_ANVIL_COST) {
            throw new RuntimeException("Too expensive");
        }
        
        String leftName = leftNode.item.name;
        String rightName = rightNode.item.name;
        int experience = calculateExperience(mergeCost);
        String description = "Combine " + leftName + " + " + rightName;
        
        steps.add(new CalculationResult.Step(description, mergeCost, experience, priorWorkPenalty));
        
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

package com.enchantcalc.calculator;

import java.util.List;

public class CalculationResult {
    private final List<Step> steps;
    private final int totalLevels;
    private final int totalExperience;

    public CalculationResult(List<Step> steps, int totalLevels, int totalExperience) {
        this.steps = steps;
        this.totalLevels = totalLevels;
        this.totalExperience = totalExperience;
    }

    public List<Step> getSteps() {
        return steps;
    }

    public int getTotalLevels() {
        return totalLevels;
    }

    public int getTotalExperience() {
        return totalExperience;
    }

    public static class Step {
        private final String description;
        private final int levels;
        private final int experience;
        private final int priorWorkPenalty;

        public Step(String description, int levels, int experience, int priorWorkPenalty) {
            this.description = description;
            this.levels = levels;
            this.experience = experience;
            this.priorWorkPenalty = priorWorkPenalty;
        }

        public String getDescription() {
            return description;
        }

        public int getLevels() {
            return levels;
        }

        public int getExperience() {
            return experience;
        }

        public int getPriorWorkPenalty() {
            return priorWorkPenalty;
        }
    }
}

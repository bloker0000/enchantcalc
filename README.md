# Enchant Calculator

![Logo](https://cdn.modrinth.com/data/fYkrQpQQ/images/04137a29bb286c86b1960070f0865d547614909e_350.webp)

Find the cheapest way to combine enchantments. This mod calculates the optimal order to avoid wasting levels and hitting "too expensive" errors.

---

**This mod is heavily inspired by the [Enchantment Calculator mod](https://modrinth.com/mod/enchantment-calculator) created by [FanyaOff](https://modrinth.com/user/FanyaOff). All credit goes to them for the original concept and design.**

---

## Overview

Enchant Calculator is a client-side mod that solves one of Minecraft's most annoying problems: combining multiple enchantments without wasting experience levels or hitting the anvil's "too expensive" limit.

When you place an item in an anvil, the mod automatically shows you exactly how to combine your enchantments in the most efficient order. No more guessing, no more wasted levels, and no more frustration.

![Showcase GIF](https://cdn.modrinth.com/data/fYkrQpQQ/images/4854c4e7436c6d6760d32d6f84a4a68ec3f0ed24.gif)

_Yes, i know the icon is AI, i suck at design.._

## How to Use

**Step 1:** Place your item in an anvil

Two panels appear on the sides of your screen.

![Left Panel Preview](https://cdn.modrinth.com/data/fYkrQpQQ/images/0628b390fd5ad369e963105d0d89c0858cbc97c9.png)

**Step 2:** Select enchantments from the left panel

The left panel displays all available enchantments you can add to your item. If you have enchanted books in your inventory, the mod shows which ones you have. Pick the enchantments you want and set their levels.

![Right Panel Preview](https://cdn.modrinth.com/data/fYkrQpQQ/images/233e51de528310d3f819080051db90a1e285114d.png)

**Step 3:** Click Calculate

The mod instantly analyzes hundreds or thousands of possible combination orders to find the best one.

**Step 4:** Follow the steps

The right panel shows you the exact order to combine your items. Each step tells you what to put in the anvil and where. Use the arrow buttons to move between steps.

![Selected Enchants Stacking](https://cdn.modrinth.com/data/fYkrQpQQ/images/d767a298496237744e131f9101589b0fb9a1d1fc_350.webp)

## Optimization Modes

The mod offers three different ways to calculate the best order:

**Levels Mode** - Minimizes the total number of experience levels you spend. This is what most players want and is the default mode.

**XP Mode** - Minimizes the total experience points instead of levels. Because higher levels cost more XP per level, this mode sometimes gives different results than Levels mode.

**Work Mode** - Focuses on avoiding the "too expensive" error. When you want to add many enchantments to one item, the anvil can refuse to work because the cost is over 39 levels. This mode finds combinations that stay under the limit.

Switch between modes using the mode button. The mod recalculates instantly when you change modes.

## Features

- Works with any enchantment in the game, including those from other mods
- Automatically detects enchanted books in your inventory
- Shows step by step instructions
- Tests all possible combination orders
- Three optimization modes
- Client-sided
- Responsive GUI that adapts to different GUI scales

## GUI Scale Compatibility

The mod's interface should adapt to different GUI scales:

**GUI Scale 4 and Below:**
![GUI Scale 4 and Below](https://cdn.modrinth.com/data/fYkrQpQQ/images/f523fff48343498446ee7f32971d829607db81ae_350.webp)

**GUI Scale 5+:**
![GUI Scale 5 and Above](https://cdn.modrinth.com/data/fYkrQpQQ/images/60d00dd12dec51430a5a843c7c5740ddc5811cb8_350.webp)

## Technical Details

The mod uses a smart algorithm to find the optimal combination order. For each set of enchantments you select, it generates all possible ways to combine them, calculates the cost for each way, and picks the cheapest one.

The algorithm accounts for:
- Enchantment rarity and level costs
- Prior work penalties from previous anvil uses
- The order items are placed in the anvil (left vs right slot)
- Compatibility between different enchantments (custom enchantment mods)

This means you always get the mathematically best solution for your chosen mode.

## Requirements

Minecraft 1.21.8
Fabric Loader
Fabric API

## Compatibility

Works with Minecraft 1.21.8 on Fabric. Requires Fabric API.
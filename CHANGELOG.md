# Changelog

## 2.0.0 - 2026-09-23

A complete rewrite: a new calculator, a new interface, and support for every Minecraft version from 1.21 to 26.3.

### Added
- Support for Minecraft 1.21–1.21.1, 1.21.2–1.21.3, 1.21.4, 1.21.5, 1.21.6–1.21.8, 1.21.9–1.21.10, 1.21.11, 26.1–26.1.2, 26.2 and 26.3 (one jar per range).
- The plan updates as soon as you pick an enchantment. There is no Calculate button any more.
- Plans use the enchanted books you already have, including books with several enchantments and books that were combined before (their prior work penalty is counted). "My books" selects everything you have a book for, at the level of your best book, and leaves curses out.
- The items for the current step are highlighted in your inventory, and the anvil slots show where each one goes: blue for the first slot, orange for the second.
- Progress tracking: finished steps get a check mark and the next step is highlighted automatically.
- The full list of steps with item icons, the cost of each step, and tooltips with every detail.
- Plan for an enchanted book to combine several books into one before applying them.
- Enchantments already on the item and its prior work penalty are taken into account.
- In creative mode there is no "Too Expensive!" limit, and the plan knows it.
- A compact layout with tabs for small windows and large GUI scales; the anvil moves aside to make room.
- The panels can be hidden, and stay hidden until you show them again.
- Your choices are remembered per item type, together with the mode, in `config/enchantcalc.json`.
- The panels take their colours from your resource pack, so dark packs get dark panels.
- Dutch translation.

### Changed
- The calculator now finds the cheapest order with an exact search, and never plans a step that costs 40 or more levels in survival. If no order fits, it tells you.
- "Work" mode is now "Lowest penalty": it keeps the finished item's prior work penalty as low as possible.
- Enchantment costs are read from the game, so modded and datapack enchantments cost what they really cost.

### Fixed
- The game froze, sometimes for minutes, when planning eight or more enchantments.
- Typing "e" in the search box closed the anvil, and number keys moved items around.
- Modded enchantments were all treated as if they cost 1 level per level.
- After leaving and rejoining a world, books in the inventory were no longer recognised, and enchantments from one server were shown on the next.
- Enchantments picked for one item carried over to a different item that can't have them.
- Plans ignored the item's existing prior work penalty and enchantments.
- Clicking just above or below the enchantment list changed an entry that was scrolled out of view.
- With GUI scale "Auto" the panels could end up off screen.
- Clicking empty panel space while holding an item could throw the item out of the inventory.

## 1.0.1 - 2025-10-31

### Fixed
- UI overhaul: Fixed all layout and interaction issues
- Improved calculate button responsiveness and reliability
- Lazy initialization for enchantment registry

### Improved
- Dynamic panel sizing based on GUI scale
- Better support for GUI scale x5
- Better handling of enchanted books detection from inventory

## 1.0.0 - 2025-10-23

### Added
- Initial release
- Enchantment calculator for anvil combinations
- Three optimization modes: Levels, XP, and Work
- Smart inventory book detection
- Panels on anvil GUI for enchantment selection and combination steps
- Support for enchantments from other mods
- Full Minecraft 1.21.8 compatibility with Fabric

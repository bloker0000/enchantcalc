# EnchantCalc

A powerful enchantment calculator mod for Minecraft that helps you combine and optimize enchantments efficiently. See all possible enchantments on any item, combine them on an anvil, and get step-by-step guidance for the best path to your goal.

## What Does This Mod Do

EnchantCalc makes working with enchantments in Minecraft much easier. Instead of guessing which enchantments go together or how to combine them on an anvil, this mod shows you:

- All enchantments that can go on your item
- Which enchantments are compatible with each other
- The exact steps needed to combine enchantments efficiently
- How much experience each step costs
- Enchanted books available in your inventory

The mod works with any enchantment in the game - both from Minecraft itself and from other mods.

## Installation

### Requirements

- Minecraft Java Edition 1.21.8
- Fabric Loader 0.16.9 or higher
- Fabric API 0.136.0+1.21.8 or higher
- Java 21 or higher

### Step-by-Step Installation

1. **Install Fabric** (if you haven't already)
   - Go to https://fabricmc.net/use/installer/
   - Download and run the Fabric installer
   - Select Minecraft 1.21.8
   - Click Install

2. **Download the Mod Files**
   - Download Fabric API from https://modrinth.com/mod/fabric-api or https://www.curseforge.com/minecraft/mc-mods/fabric-api
   - Choose version 0.136.0+1.21.8 or newer
   - Download EnchantCalc from the releases page

3. **Place Files in the Mods Folder**
   - Open your Minecraft folder (usually .minecraft)
   - Locate or create a folder called "mods"
   - Drag the Fabric API JAR file into the mods folder
   - Drag the enchantcalc JAR file into the mods folder

4. **Launch the Game**
   - Open Minecraft Launcher
   - Select the Fabric 1.21.8 profile
   - Click Play

## How to Use

### Opening the Enchantment Calculator

1. Open your inventory
2. You will see an "Enchantment Calculator" button on the left side of the anvil screen
3. Click this button to open the calculator interface

### Using the Interface

**Left Panel - Your Item:**
- Shows the item you currently have selected
- Displays all enchantments already on the item
- Shows the current level of each enchantment

**Right Panel - Available Enchantments:**
- Lists all enchantments that can be added to your item
- Shows the maximum level for each enchantment
- Indicates which enchantments are incompatible with your current ones
- Use the search bar to find enchantments quickly

**Search Feature:**
- Type to search for enchantments by name
- Results update instantly as you type
- Press Enter or click the search button

**Inventory Books:**
- Scroll down to see enchanted books in your inventory
- Shows which books are available to combine
- Displays the enchantment level on each book

### Combining Enchantments

1. Select your base item in the main inventory
2. Click on an enchantment in the right panel
3. The calculator will show you the optimal path to add that enchantment
4. Follow the steps in order to get the best result
5. Each step shows the experience cost

## Features

### Automatic Enchantment Detection

The mod automatically finds all enchantments in your game - from Minecraft itself, from mods you have installed, and from custom enchantments. You don't need to set anything up or configure anything. Just install and play.

### Smart Inventory Scanning

The calculator reads your inventory and finds all enchanted books. It shows you which books you have that can help with your project, saving you time searching through your storage.

### Compatibility Checking

The mod knows which enchantments work together and which ones don't. It prevents you from wasting time trying to combine impossible enchantment pairs. If two enchantments are incompatible, the calculator will show this clearly.

### Optimization Engine

The calculator uses an advanced algorithm to find the most cost-efficient way to combine enchantments. This means you spend less experience and get to your goal faster than if you just guessed.

### Mouse Scroll Support

Scroll your mouse wheel in the inventory list to quickly browse through available enchantments. This makes it faster to find what you need.

### Custom Enchantment Support

Any custom enchantments from mods will show up automatically. The calculator treats them just like vanilla enchantments - no configuration needed.

## Troubleshooting

### The Mod Won't Start

**Problem:** Minecraft won't launch with the mod

**Solution:**
- Check that you have Fabric Loader 0.16.9 or higher installed
- Check that you have Fabric API 0.136.0+1.21.8 or higher in your mods folder
- Make sure both JAR files are in the mods folder
- Check your Minecraft version is 1.21.8
- Look at the crash log to see if there are other mod conflicts

### The Calculator Button Doesn't Appear

**Problem:** I don't see the enchantment calculator button on the anvil screen

**Solution:**
- Make sure the mod is installed correctly (both enchantcalc JAR and Fabric API JAR)
- Close and reopen Minecraft completely
- Try creating a new world to test
- Check the game console for error messages

### The Mod Crashes When I Open the Calculator

**Problem:** The game crashes when I click the calculator button

**Solution:**
- This might be caused by another mod conflicting with EnchantCalc
- Try removing other mods one at a time to find the conflict
- Check the crash report to see which mod is causing the issue
- Report the issue on GitHub with the crash log

### Enchantments Aren't Showing Up

**Problem:** I don't see an enchantment I expect

**Solution:**
- The enchantment might not be available for the item you selected
- Some enchantments only work on certain items (for example, Aqua Affinity only works on helmets)
- Check if the enchantment is already on your item (incompatible enchantments won't show)
- Try a different item to confirm the enchantment exists

## For Modpack Creators

If you are including EnchantCalc in a modpack, make sure to include:

- Fabric Loader 0.16.9 or higher
- Fabric API 0.136.0+1.21.8 or higher
- The enchantcalc JAR file

The mod will work with all other mods automatically. No special configuration is needed. Custom enchantments from any mod will be detected automatically.

## Building From Source

If you want to build the mod yourself:

1. Install Java 21
2. Clone this repository
3. Run: `./gradlew build`
4. The compiled JAR will be in `build/libs/`

## Requirements for Building

- Java 21 or higher
- Gradle (comes with this project)

## File Transfer Note

To transfer the JAR file cleanly between computers, use one of these methods:

- Cloud storage (Google Drive, Dropbox, OneDrive, iCloud)
- Email attachment
- GitHub releases
- Direct network transfer using HTTP
- Secure file transfer (SCP/SFTP)

Do not use USB drives with FAT32 format, as this can corrupt the file. Use exFAT or NTFS instead.

## License

This project is licensed under the MIT License. See the LICENSE file for details.

## Support

If you find a bug or have a suggestion, please open an issue on GitHub at: https://github.com/bloker0000/enchantcalc

## Credits

EnchantCalc was created as an improvement to enchantment management in Minecraft. It uses Fabric and is built on the Minecraft Forge modding framework.

## Version History

### Version 1.0.0 (Current)

- Initial release
- Automatic enchantment detection for vanilla and modded enchantments
- Enchantment combination optimizer
- Inventory book scanner
- Mouse scroll support in enchantment lists
- Search functionality for finding enchantments
- Support for Minecraft 1.21.8

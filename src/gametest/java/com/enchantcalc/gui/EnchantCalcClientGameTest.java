package com.enchantcalc.gui;

import com.enchantcalc.data.EnchantInfo;
import com.enchantcalc.plan.PlanView;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Starts the game, opens a real anvil with a sword and some books, drives the overlay with the mouse
 * and keyboard, and takes screenshots of each layout ({@code build/run/clientGameTest/screenshots}).
 */
public final class EnchantCalcClientGameTest implements FabricClientGameTest {
    // GLFW and SDL (26.3+) number mouse buttons differently; the constant is right for each version.
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    private static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            //? if >=26.2 {
            singleplayer.getConnection().waitForChunksRender();
            //?} elif >=26.1 {
            /*singleplayer.getClientLevel().waitForChunksRender();
            *///?} else {
            /*singleplayer.getClientWorld().waitForChunksRender();
            *///?}

            // 1920x1080 at automatic GUI scale leaves room for a panel on each side of the anvil.
            context.getInput().resizeWindow(1920, 1080);
            context.waitTicks(2);
            openAnvil(singleplayer);
            context.waitForScreen(AnvilScreen.class);
            context.waitFor(client -> overlay() != null && overlay().target() != null);
            check(!context.computeOnClient(client -> overlay().layout().compact()), "wide layout at 1920x1080");
            context.takeScreenshot("wide_start");

            // Real clicks: Shift-click selects the max level, a click raises the level by one.
            clickRow(context, "minecraft:sharpness", true);
            clickRow(context, "minecraft:unbreaking", true);
            clickRow(context, "minecraft:knockback", false);
            check(context.computeOnClient(client -> level("minecraft:sharpness")) == 5, "Sharpness V selected");
            check(context.computeOnClient(client -> level("minecraft:knockback")) == 1, "Knockback I selected");
            // Smite conflicts with Sharpness, so clicking it must do nothing.
            clickRow(context, "minecraft:smite", false);
            check(context.computeOnClient(client -> level("minecraft:smite")) == 0, "Smite blocked by Sharpness");

            // Selects Looting III, Mending and Fire Aspect II + Sweeping Edge III from the inventory books.
            context.clickScreenButton("My books");
            waitForPlan(context);
            check(context.computeOnClient(client -> level("minecraft:looting")) == 3, "Looting III from my books");
            check(context.computeOnClient(client -> overlay().view().plan().withinLimit()), "plan fits in survival");
            context.takeScreenshot("wide_plan");

            // "My books" again puts a level the player changed back to the level of their book.
            clickRow(context, "minecraft:looting", false, RIGHT);
            check(context.computeOnClient(client -> level("minecraft:looting")) == 2, "Looting lowered to II");
            context.clickScreenButton("My books");
            waitForPlan(context);
            check(context.computeOnClient(client -> level("minecraft:looting")) == 3, "My books restores Looting III");

            // Clicking panel space with an item on the cursor must not drop the item. The anvil drops a
            // carried item when the mouse is released outside its window, so the release must not reach it.
            setCarried(singleplayer, new ItemStack(Items.DIAMOND));
            context.waitFor(client -> !client.player.containerMenu.getCarried().isEmpty());
            Rect panel = context.computeOnClient(client -> overlay().layout().left());
            click(context, panel.x() + 3, panel.y() + 3, LEFT);
            context.waitTicks(3);
            check(singleplayer.getServer().computeOnServer(server ->
                server.getPlayerList().getPlayers().get(0).containerMenu.getCarried().is(Items.DIAMOND)), "the item on the cursor is kept");
            setCarried(singleplayer, ItemStack.EMPTY);
            context.waitFor(client -> client.player.containerMenu.getCarried().isEmpty());

            // Hovering a step shows its details.
            Rect step = context.computeOnClient(client -> overlay().planPanel().stepRect(0));
            moveTo(context, step.x() + step.width() / 2, step.y() + step.height() / 2);
            context.waitTicks(2);
            context.takeScreenshot("wide_step_tooltip");

            // Typing in the search box: "e" must not close the anvil and number keys must not move items.
            Rect search = context.computeOnClient(client -> overlay().enchantPanel().searchBox());
            click(context, search.x() + 10, search.y() + 8, LEFT);
            context.getInput().typeChars("sweep 1");
            context.waitTicks(2);
            context.waitForScreen(AnvilScreen.class);
            String searchState = context.computeOnClient(client -> "text='" + overlay().searchText() + "' focused="
                + overlay().search().isFocused() + " screenFocus=" + overlay().screen().getFocused());
            check(context.computeOnClient(client -> overlay().searchText()).equals("sweep 1"), "search text typed, got " + searchState);
            context.takeScreenshot("wide_search");
            context.runOnClient(client -> overlay().search().setValue(""));

            // Doing the first step (as far as the plan can tell) moves the highlight on.
            giveStepOutput(context, singleplayer, 0);
            context.waitFor(client -> overlay().progress() != null && overlay().progress().done(0));
            context.takeScreenshot("wide_progress");

            // A small window switches to the tabbed layout with the anvil moved aside.
            context.getInput().resizeWindow(854, 480);
            context.waitTicks(3);
            check(context.computeOnClient(client -> overlay().layout().compact()), "compact layout at 854x480");
            context.takeScreenshot("compact_enchantments");

            // Dragging the scrollbar scrolls the list, and letting go of the button ends the drag.
            Rect bar = context.computeOnClient(client -> overlay().enchantPanel().scrollbar());
            check(bar != null, "a scrollbar on the enchantment list at 854x480");
            int barX = bar.x() + bar.width() / 2;
            moveTo(context, barX, bar.y() + 2);
            context.getInput().holdMouse(LEFT);
            context.waitTicks(1);
            moveTo(context, barX, bar.bottom() - 1);
            context.waitTicks(2);
            int scrolled = context.computeOnClient(client -> overlay().enchantPanel().scrollOffset());
            check(scrolled > 0, "dragging the scrollbar scrolls the list");
            context.takeScreenshot("compact_scrolled");
            context.getInput().releaseMouse(LEFT);
            context.waitTicks(1);
            moveTo(context, barX, bar.y() + 2);
            context.waitTicks(2);
            check(context.computeOnClient(client -> overlay().enchantPanel().scrollOffset()) == scrolled, "the drag ends on release");

            Rect tabs = context.computeOnClient(client -> overlay().layout().tabs());
            click(context, tabs.x() + tabs.width() * 3 / 4 - 9, tabs.y() + 8, LEFT);
            context.waitTicks(2);
            context.takeScreenshot("compact_plan");

            // Hiding and showing again.
            context.clickScreenButton("×");
            context.waitFor(client -> !overlay().layout().visible());
            context.takeScreenshot("collapsed");
            Rect tab = context.computeOnClient(client -> overlay().layout().collapsedTab());
            click(context, tab.x() + tab.width() / 2, tab.y() + tab.height() / 2, LEFT);
            context.waitFor(client -> overlay().layout().visible());
        }
    }

    private static AnvilOverlay overlay() {
        return AnvilOverlay.current();
    }

    private static int level(String id) {
        for (Map.Entry<EnchantInfo, Integer> entry : overlay().wanted().entrySet()) {
            if (entry.getKey().id().equals(id)) {
                return entry.getValue();
            }
        }
        return 0;
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError("Expected: " + what);
        }
    }

    /** Places an anvil next to the player, opens it and puts a diamond sword in its first slot. */
    private static void openAnvil(TestSingleplayerContext singleplayer) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            HolderGetter<Enchantment> enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            player.getInventory().add(book(enchantments, Map.of(Enchantments.LOOTING, 3)));
            player.getInventory().add(book(enchantments, Map.of(Enchantments.MENDING, 1)));
            player.getInventory().add(book(enchantments, Map.of(Enchantments.SHARPNESS, 4)));
            Map<ResourceKey<Enchantment>, Integer> pair = new LinkedHashMap<>();
            pair.put(Enchantments.FIRE_ASPECT, 2);
            pair.put(Enchantments.SWEEPING_EDGE, 3);
            player.getInventory().add(book(enchantments, pair));
            player.experienceLevel = 30;
            BlockPos pos = player.blockPosition().east(2);
            player.level().setBlockAndUpdate(pos, Blocks.ANVIL.defaultBlockState());
            player.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                new AnvilMenu(id, inventory, ContainerLevelAccess.create(player.level(), pos)), Component.translatable("container.repair")));
            player.containerMenu.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.DIAMOND_SWORD));
            player.containerMenu.broadcastChanges();
        });
    }

    private static ItemStack book(HolderGetter<Enchantment> enchantments, Map<ResourceKey<Enchantment>, Integer> levels) {
        return enchant(new ItemStack(Items.ENCHANTED_BOOK), enchantments, levels);
    }

    private static ItemStack enchant(ItemStack stack, HolderGetter<Enchantment> enchantments, Map<ResourceKey<Enchantment>, Integer> levels) {
        EnchantmentHelper.updateEnchantments(stack, mutable ->
            levels.forEach((key, level) -> mutable.set(enchantments.getOrThrow(key), level)));
        return stack;
    }

    private record StepOutput(boolean sword, Map<String, Integer> levels) {
    }

    /** Gives the player the item that step {@code index} produces, as if they had just done it. */
    private static void giveStepOutput(ClientGameTestContext context, TestSingleplayerContext singleplayer, int index) {
        StepOutput output = context.computeOnClient(client -> {
            PlanView.Step step = overlay().view().steps().get(index);
            Map<String, Integer> levels = new LinkedHashMap<>();
            step.output().enchantments().forEach((info, level) -> levels.put(info.id(), level));
            return new StepOutput(step.output().target(), levels);
        });
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            HolderGetter<Enchantment> enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Map<ResourceKey<Enchantment>, Integer> keys = new LinkedHashMap<>();
            output.levels().forEach((id, level) -> keys.put(ResourceKey.create(Registries.ENCHANTMENT, identifier(id)), level));
            ItemStack base = new ItemStack(output.sword() ? Items.DIAMOND_SWORD : Items.ENCHANTED_BOOK);
            player.getInventory().add(enchant(base, enchantments, keys));
            player.containerMenu.broadcastChanges();
        });
    }

    private static Identifier identifier(String id) {
        return Identifier.parse(id);
    }

    private static void waitForPlan(ClientGameTestContext context) {
        context.waitFor(client -> overlay().view() != null && !overlay().planning());
    }

    /** Puts {@code stack} on the player's cursor (the item held by the mouse in the open menu). */
    private static void setCarried(TestSingleplayerContext singleplayer, ItemStack stack) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.containerMenu.setCarried(stack);
            player.containerMenu.broadcastChanges();
        });
    }

    private static void clickRow(ClientGameTestContext context, String id, boolean shift) {
        clickRow(context, id, shift, LEFT);
    }

    private static void clickRow(ClientGameTestContext context, String id, boolean shift, int button) {
        Rect row = context.computeOnClient(client -> overlay().enchantPanel().rowRect(id));
        check(row != null, "a row for " + id);
        if (shift) {
            context.getInput().holdShift();
        }
        click(context, row.x() + row.width() / 2, row.y() + row.height() / 2, button);
        if (shift) {
            context.getInput().releaseShift();
        }
        context.waitTicks(1);
    }

    private static void moveTo(ClientGameTestContext context, int guiX, int guiY) {
        double scale = context.computeOnClient(client ->
            (double) client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth());
        context.getInput().setCursorPos(guiX * scale + scale / 2, guiY * scale + scale / 2);
    }

    private static void click(ClientGameTestContext context, int guiX, int guiY, int button) {
        moveTo(context, guiX, guiY);
        context.getInput().pressMouse(button);
        context.waitTicks(1);
    }
}

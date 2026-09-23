package com.enchantcalc;

import com.enchantcalc.data.Config;
import com.enchantcalc.data.EnchantCatalog;
import com.enchantcalc.gui.AnvilOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EnchantCalc implements ClientModInitializer {
    public static final String MOD_ID = "enchantcalc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register(AnvilOverlay::onScreenInit);
        // Enchantments are data-driven: every world or server has its own registry.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> EnchantCatalog.invalidate());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Config.get().saveIfDirty());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            EnchantCatalog.invalidate();
            Config.get().saveIfDirty();
        });
    }
}

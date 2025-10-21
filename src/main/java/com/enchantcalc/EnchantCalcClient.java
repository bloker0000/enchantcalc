package com.enchantcalc;

import com.enchantcalc.data.EnchantmentRegistry;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnchantCalcClient implements ClientModInitializer {
    public static final String MOD_ID = "enchantcalc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("EnchantCalc initializing with automatic enchantment detection...");
        EnchantmentRegistry.initialize();
        LOGGER.info("EnchantCalc initialized successfully");
    }
}

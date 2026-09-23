package com.enchantcalc.data;

import com.enchantcalc.EnchantCalc;
import com.enchantcalc.core.Mode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Settings and remembered selections, stored in {@code config/enchantcalc.json}. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    /** What plans minimize. */
    public Mode mode = Mode.LEVELS;
    /** Whether the panels are open next to the anvil. */
    public boolean visible = true;
    /** Chosen levels per item type: item id to enchantment id to level. */
    public Map<String, Map<String, Integer>> selections = new LinkedHashMap<>();

    private transient boolean dirty;

    public static Config get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(EnchantCalc.MOD_ID + ".json");
    }

    private static Config load() {
        Path path = path();
        Config config = null;
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, Config.class);
            } catch (Exception e) {
                EnchantCalc.LOGGER.warn("Could not read {}, using defaults", path, e);
            }
        }
        if (config == null) {
            config = new Config();
        }
        if (config.mode == null) {
            config.mode = Mode.LEVELS;
        }
        if (config.selections == null) {
            config.selections = new LinkedHashMap<>();
        }
        config.selections.values().removeIf(map -> map == null);
        config.selections.values().forEach(map -> map.values().removeIf(level -> level == null || level <= 0));
        return config;
    }

    /** The saved levels for one item type; changes must be followed by {@link #markDirty()}. */
    public Map<String, Integer> selection(String itemId) {
        return selections.computeIfAbsent(itemId, id -> new LinkedHashMap<>());
    }

    public void markDirty() {
        dirty = true;
    }

    public void saveIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        selections.values().removeIf(Map::isEmpty);
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            EnchantCalc.LOGGER.warn("Could not save {}", path, e);
        }
    }
}

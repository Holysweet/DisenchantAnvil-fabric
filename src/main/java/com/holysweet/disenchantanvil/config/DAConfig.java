package com.holysweet.disenchantanvil.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DAConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File CONFIG_FILE = new File(
            FabricLoader.getInstance().getConfigDir().toFile(),
            "disenchantanvil.json"
    );

    private static volatile Set<Block> cachedBlocks = Collections.emptySet();

    private DAConfig() {
    }

    private static final class ConfigData {
        public List<String> allowedBaseBlocks = List.of(
                "minecraft:obsidian",
                "minecraft:crying_obsidian"
        );
    }

    public static void loadAndBake() {
        ConfigData data = loadOrCreateConfig();

        Set<Block> bakedBlocks = new HashSet<>();

        for (String blockIdText : data.allowedBaseBlocks) {
            Identifier blockId = Identifier.tryParse(blockIdText);

            if (blockId == null) {
                System.err.println("[Disenchant Anvil] Ignoring invalid block id in config: " + blockIdText);
                continue;
            }

            Block block = BuiltInRegistries.BLOCK.getValue(blockId);

            if (block == null) {
                System.err.println("[Disenchant Anvil] Ignoring unknown block id in config: " + blockIdText);
                continue;
            }

            bakedBlocks.add(block);
        }

        cachedBlocks = Collections.unmodifiableSet(bakedBlocks);

        System.out.println("[Disenchant Anvil] Loaded " + cachedBlocks.size() + " allowed base blocks.");
    }

    public static boolean isAllowedBase(Block block) {
        return cachedBlocks.contains(block);
    }

    private static ConfigData loadOrCreateConfig() {
        ConfigData defaults = new ConfigData();

        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ConfigData loaded = GSON.fromJson(reader, ConfigData.class);

                if (loaded != null) {
                    if (loaded.allowedBaseBlocks == null || loaded.allowedBaseBlocks.isEmpty()) {
                        loaded.allowedBaseBlocks = defaults.allowedBaseBlocks;
                    }

                    return loaded;
                }
            } catch (IOException exception) {
                System.err.println("[Disenchant Anvil] Failed to load config file, using defaults.");
                exception.printStackTrace();
            }
        }

        saveDefaultConfig(defaults);
        return defaults;
    }

    private static void saveDefaultConfig(ConfigData data) {
        try {
            File parent = CONFIG_FILE.getParentFile();

            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException exception) {
            System.err.println("[Disenchant Anvil] Failed to save default config file.");
            exception.printStackTrace();
        }
    }
}
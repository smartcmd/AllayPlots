package me.daoge.allayplots.config;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.OkaeriConfigInitializer;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Accessors(fluent = true)
public final class PluginConfig extends OkaeriConfig {
    @Comment("EconomyAPI integration settings.")
    private EconomySettings economy = new EconomySettings();

    @Comment("General plugin settings.")
    private Settings settings = new Settings();

    @Comment("Plot storage backend settings.")
    private StorageSettings storage = new StorageSettings();

    @Comment("Plot world definitions keyed by world name.")
    private Map<String, PlotWorldConfig> worlds = defaultWorlds();

    public static PluginConfig load(Path dataFolder, Logger logger) {
        Path file = dataFolder.resolve("config.yml");
        PluginConfig config = ConfigManager.create(PluginConfig.class, createInitializer(file));
        config.applyWorldNames();
        if (config.worlds().isEmpty()) {
            logger.warn("No plot worlds configured. Add entries under worlds in config.yml.");
        }
        return config;
    }

    public PlotWorldConfig world(String worldName) {
        return worlds.get(worldName);
    }

    private void applyWorldNames() {
        for (Map.Entry<String, PlotWorldConfig> entry : worlds.entrySet()) {
            entry.getValue().worldName(entry.getKey());
        }
    }

    private static Map<String, PlotWorldConfig> defaultWorlds() {
        Map<String, PlotWorldConfig> defaults = new LinkedHashMap<>();
        defaults.put("plotworld", new PlotWorldConfig());
        return defaults;
    }

    private static OkaeriConfigInitializer createInitializer(Path path) {
        return it -> {
            it.withConfigurer(new YamlSnakeYamlConfigurer());
            it.withBindFile(path);
            it.withRemoveOrphans(true);
            it.saveDefaults();
            it.load(true);
        };
    }

    @Getter
    @Accessors(fluent = true)
    public static class EconomySettings extends OkaeriConfig {
        @Comment("Enable EconomyAPI pricing for claiming/deleting plots.")
        private boolean enabled = false;

        @Comment("Currency id to use; empty means server default.")
        private String currency = "";
    }

    @Getter
    @Accessors(fluent = true)
    public static class Settings extends OkaeriConfig {
        @Comment("Protect road blocks outside plots.")
        @CustomKey("protect-roads")
        private boolean protectRoads = true;

        @Comment("Auto-save interval in ticks (0 to disable).")
        @CustomKey("auto-save-interval-ticks")
        private int autoSaveIntervalTicks = 6000;

        @Comment("Use action bar for enter/leave messages.")
        @CustomKey("use-action-bar")
        private boolean useActionBar = true;
    }

    @Getter
    @Accessors(fluent = true)
    public static class StorageSettings extends OkaeriConfig {
        @Comment("Storage type: yaml, sqlite, or h2.")
        private String type = "yaml";
    }
}

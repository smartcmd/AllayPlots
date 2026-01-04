package me.daoge.allayplots.config;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.OkaeriConfigInitializer;
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
    private int version = 1;

    private EconomySettings economy = new EconomySettings();

    private Settings settings = new Settings();

    @CustomKey("worlds")
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

    public boolean economyEnabled() {
        return economy.enabled();
    }

    public String economyCurrency() {
        return economy.currency();
    }

    public boolean protectRoads() {
        return settings.protectRoads();
    }

    public int autoSaveIntervalTicks() {
        return settings.autoSaveIntervalTicks();
    }

    public boolean useActionBar() {
        return settings.useActionBar();
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
        private boolean enabled = false;
        private String currency = "";
    }

    @Getter
    @Accessors(fluent = true)
    public static class Settings extends OkaeriConfig {
        @CustomKey("protect-roads")
        private boolean protectRoads = true;

        @CustomKey("auto-save-interval-ticks")
        private int autoSaveIntervalTicks = 6000;

        @CustomKey("use-action-bar")
        private boolean useActionBar = true;
    }
}

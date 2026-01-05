package me.daoge.allayplots.storage;

import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import me.daoge.allayplots.plot.PlotMergeDirection;
import me.daoge.allayplots.plot.PlotWorld;
import org.allaymc.api.utils.config.Config;
import org.allaymc.api.utils.config.ConfigSection;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.*;

public final class YamlPlotStorage implements PlotStorage {
    private final Path file;
    private final Logger logger;

    public YamlPlotStorage(Path dataFolder, Logger logger) {
        this.file = dataFolder.resolve("plots.yml");
        this.logger = logger;
    }

    @Override
    public Map<String, Map<PlotId, Plot>> load() {
        ConfigSection defaults = new ConfigSection();
        defaults.set("version", 1);
        defaults.set("worlds", new ConfigSection());

        Config config = new Config(file.toFile(), Config.YAML, defaults);
        Map<String, Map<PlotId, Plot>> result = new HashMap<>();
        ConfigSection worldsSection = config.getSection("worlds");

        for (String worldName : worldsSection.getKeys(false)) {
            ConfigSection worldSection = worldsSection.getSection(worldName);
            ConfigSection plotsSection = worldSection.getSection("plots");
            Map<PlotId, Plot> plots = new HashMap<>();

            for (String plotKey : plotsSection.getKeys(false)) {
                PlotId id;
                try {
                    id = PlotId.fromString(plotKey);
                } catch (IllegalArgumentException ex) {
                    logger.warn("Skipping invalid plot id {} in {}", plotKey, worldName);
                    continue;
                }

                ConfigSection plotSection = plotsSection.getSection(plotKey);
                Plot plot = new Plot(worldName, id);

                String ownerRaw = plotSection.getString("owner", "");
                if (!ownerRaw.isBlank()) {
                    try {
                        String ownerName = plotSection.getString("ownerName", "");
                        plot = plot.withOwner(UUID.fromString(ownerRaw), ownerName.isBlank() ? null : ownerName);
                    } catch (IllegalArgumentException ex) {
                        logger.warn("Invalid owner uuid {} for plot {} in {}", ownerRaw, plotKey, worldName);
                    }
                }

                for (String raw : plotSection.getStringList("trusted")) {
                    try {
                        plot = plot.withTrustedAdded(UUID.fromString(raw));
                    } catch (IllegalArgumentException ex) {
                        logger.warn("Invalid trusted uuid {} for plot {} in {}", raw, plotKey, worldName);
                    }
                }

                for (String raw : plotSection.getStringList("denied")) {
                    try {
                        plot = plot.withDeniedAdded(UUID.fromString(raw));
                    } catch (IllegalArgumentException ex) {
                        logger.warn("Invalid denied uuid {} for plot {} in {}", raw, plotKey, worldName);
                    }
                }

                if (plot.isClaimed() && plotSection.getBoolean("home", false)) {
                    plot = plot.withHome(true);
                }

                ConfigSection flagsSection = plotSection.getSection("flags");
                for (String flagKey : flagsSection.getKeys(false)) {
                    String value = flagsSection.getString(flagKey, "");
                    if (!value.isBlank()) {
                        plot = plot.withFlagRaw(flagKey, value);
                    }
                }

                for (String raw : plotSection.getStringList("merged")) {
                    PlotMergeDirection direction = PlotMergeDirection.fromString(raw);
                    if (direction != null) {
                        plot = plot.withMergedDirectionAdded(direction);
                    } else if (raw != null && !raw.isBlank()) {
                        logger.warn("Invalid merged direction {} for plot {} in {}", raw, plotKey, worldName);
                    }
                }

                if (!plot.isDefault()) {
                    plots.put(id, plot);
                }
            }

            if (!plots.isEmpty()) {
                result.put(worldName, plots);
            }
        }

        return result;
    }

    @Override
    public void save(Map<String, PlotWorld> worlds) {
        ConfigSection root = new ConfigSection();
        root.set("version", 1);

        ConfigSection worldsSection = new ConfigSection();
        for (PlotWorld world : worlds.values()) {
            ConfigSection worldSection = new ConfigSection();
            ConfigSection plotsSection = new ConfigSection();

            for (Plot plot : world.getPlots().values()) {
                if (plot.isDefault()) {
                    continue;
                }
                ConfigSection plotSection = new ConfigSection();
                if (plot.getOwner() != null) {
                    plotSection.set("owner", plot.getOwner().toString());
                    String ownerName = plot.getOwnerName();
                    if (ownerName != null && !ownerName.isBlank()) {
                        plotSection.set("ownerName", ownerName);
                    }
                }
                if (!plot.getTrusted().isEmpty()) {
                    plotSection.set("trusted", toStringList(plot.getTrusted()));
                }
                if (!plot.getDenied().isEmpty()) {
                    plotSection.set("denied", toStringList(plot.getDenied()));
                }
                if (plot.isHome()) {
                    plotSection.set("home", true);
                }
                if (!plot.getFlags().isEmpty()) {
                    ConfigSection flagsSection = new ConfigSection();
                    for (Map.Entry<String, String> entry : plot.getFlags().entrySet()) {
                        String value = entry.getValue();
                        if (value != null && !value.isBlank()) {
                            flagsSection.set(entry.getKey(), value);
                        }
                    }
                    if (!flagsSection.isEmpty()) {
                        plotSection.set("flags", flagsSection);
                    }
                }
                if (!plot.getMergedDirections().isEmpty()) {
                    List<String> merged = new ArrayList<>(plot.getMergedDirections().size());
                    for (PlotMergeDirection direction : plot.getMergedDirections()) {
                        merged.add(direction.getLowerCaseName());
                    }
                    plotSection.set("merged", merged);
                }
                plotsSection.set(plot.getId().asString(), plotSection);
            }

            worldSection.set("plots", plotsSection);
            worldsSection.set(world.getConfig().worldName(), worldSection);
        }

        root.set("worlds", worldsSection);
        Config config = new Config(file.toFile(), Config.YAML);
        config.setAll(root);
        config.save();
    }

    private List<String> toStringList(Set<UUID> uuids) {
        List<String> list = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) {
            list.add(uuid.toString());
        }
        return list;
    }
}

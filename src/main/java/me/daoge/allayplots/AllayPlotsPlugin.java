package me.daoge.allayplots;

import me.daoge.allayplots.command.PlotCommand;
import me.daoge.allayplots.config.PlotWorldConfig;
import me.daoge.allayplots.config.PluginConfig;
import me.daoge.allayplots.generator.PlotWorldGeneratorFactory;
import me.daoge.allayplots.i18n.MessageService;
import me.daoge.allayplots.listener.PlotDamageListener;
import me.daoge.allayplots.listener.PlotMovementListener;
import me.daoge.allayplots.listener.PlotProtectionListener;
import me.daoge.allayplots.plot.PlotService;
import me.daoge.allayplots.storage.H2PlotStorage;
import me.daoge.allayplots.storage.PlotStorage;
import me.daoge.allayplots.storage.SqlitePlotStorage;
import me.daoge.allayplots.storage.YamlPlotStorage;
import org.allaymc.api.plugin.Plugin;
import org.allaymc.api.registry.Registries;
import org.allaymc.api.server.Server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class AllayPlotsPlugin extends Plugin {
    private PluginConfig config;
    private PlotService plotService;

    @Override
    public void onLoad() {
        PlotWorldGeneratorFactory.register(this.pluginLogger);
    }

    @Override
    public void onEnable() {
        Path dataFolder = getPluginContainer().dataFolder();
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException ex) {
            this.pluginLogger.error("Failed to create plugin data folder.", ex);
        }

        config = PluginConfig.load(dataFolder, this.pluginLogger);
        plotService = new PlotService(config, createStorage(dataFolder), this.pluginLogger);
        plotService.start();
        plotService.load();

        ensurePlotWorldsLoaded();

        MessageService messageService = new MessageService();
        var eventBus = Server.getInstance().getEventBus();
        eventBus.registerListener(new PlotProtectionListener(plotService, config, messageService));
        eventBus.registerListener(new PlotMovementListener(plotService, config, messageService));
        eventBus.registerListener(new PlotDamageListener(plotService));

        Registries.COMMANDS.register(new PlotCommand(plotService, config, messageService, this.pluginLogger));

        if (config.settings().autoSaveIntervalTicks() > 0) {
            Server.getInstance().getScheduler().scheduleRepeating(this, plotService::requestSave, config.settings().autoSaveIntervalTicks());
        }

        this.pluginLogger.info("AllayPlots enabled for {} plot worlds.", plotService.worldCount());
    }

    @Override
    public void onDisable() {
        if (plotService != null) {
            plotService.save();
            plotService.shutdown();
        }
    }

    private PlotStorage createStorage(Path dataFolder) {
        String rawType = config.storage().type();
        String type = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "sqlite" -> new SqlitePlotStorage(dataFolder, this.pluginLogger);
            case "h2" -> new H2PlotStorage(dataFolder, this.pluginLogger);
            case "", "yaml", "yml" -> new YamlPlotStorage(dataFolder, this.pluginLogger);
            default -> {
                this.pluginLogger.warn("Unknown storage type '{}', falling back to yaml.", rawType);
                yield new YamlPlotStorage(dataFolder, this.pluginLogger);
            }
        };
    }

    private void ensurePlotWorldsLoaded() {
        if (config.worlds().isEmpty()) {
            return;
        }

        var worldPool = Server.getInstance().getWorldPool();
        for (PlotWorldConfig worldConfig : config.worlds().values()) {
            String worldName = worldConfig.worldName();
            var existing = worldPool.getWorld(worldName);
            if (existing != null) {
                var generatorName = existing.getOverWorld().getWorldGenerator().getName();
                if (!PlotWorldGeneratorFactory.GENERATOR_NAME.equalsIgnoreCase(generatorName)) {
                    this.pluginLogger.warn(
                            "World {} is loaded with generator {}, expected {} for plot worlds.",
                            worldName,
                            generatorName,
                            PlotWorldGeneratorFactory.GENERATOR_NAME
                    );
                }
                continue;
            }

            var storageFactory = Registries.WORLD_STORAGE_FACTORIES.get("LEVELDB");
            if (storageFactory == null) {
                this.pluginLogger.error("World storage type LEVELDB is not available; cannot create plot world {}.", worldName);
                continue;
            }

            var generator = PlotWorldGeneratorFactory.createFromConfig(worldConfig);
            try {
                worldPool.loadWorld(
                        worldName,
                        storageFactory.apply(worldPool.getWorldFolder().resolve(worldName)),
                        generator,
                        null,
                        null
                );
            } catch (IllegalArgumentException ex) {
                this.pluginLogger.warn("Plot world {} is already loaded.", worldName);
            } catch (Exception ex) {
                this.pluginLogger.error("Failed to load plot world {}.", worldName, ex);
            }
        }
    }
}

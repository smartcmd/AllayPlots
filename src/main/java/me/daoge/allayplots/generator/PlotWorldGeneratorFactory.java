package me.daoge.allayplots.generator;

import me.daoge.allayplots.config.PlotWorldConfig;
import org.allaymc.api.registry.Registries;
import org.allaymc.api.world.generator.WorldGenerator;
import org.slf4j.Logger;

public final class PlotWorldGeneratorFactory {
    public static final String GENERATOR_NAME = "PLOT";

    private PlotWorldGeneratorFactory() {
    }

    public static void register(Logger logger) {
        if (Registries.WORLD_GENERATOR_FACTORIES.get(GENERATOR_NAME) != null) {
            logger.warn("World generator {} already registered; skipping.", GENERATOR_NAME);
            return;
        }
        Registries.WORLD_GENERATOR_FACTORIES.register(GENERATOR_NAME, PlotWorldGeneratorFactory::create);
    }

    public static WorldGenerator create(String preset) {
        String safePreset = preset == null ? "" : preset;
        return WorldGenerator.builder()
                .name(GENERATOR_NAME)
                .preset(safePreset)
                .noisers(new PlotNoiser())
                .build();
    }

    public static WorldGenerator createFromConfig(PlotWorldConfig config) {
        PlotGeneratorPreset preset = PlotGeneratorPreset.fromConfig(config);
        return create(preset.toPresetString());
    }
}

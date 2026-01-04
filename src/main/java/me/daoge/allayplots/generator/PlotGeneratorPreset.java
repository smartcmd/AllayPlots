package me.daoge.allayplots.generator;

import me.daoge.allayplots.config.PlotWorldConfig;

import java.util.HashMap;
import java.util.Map;

public record PlotGeneratorPreset(
        int plotSize,
        int roadSize,
        int groundY,
        String plotBlock,
        String roadBlock,
        String roadEdgeBlock,
        String roadCornerBlock,
        String fillBlock,
        String bedrockBlock
) {
    public static final String KEY_PLOT_SIZE = "plot-size";
    public static final String KEY_ROAD_SIZE = "road-size";
    public static final String KEY_GROUND_Y = "ground-y";
    public static final String KEY_PLOT_BLOCK = "plot-block";
    public static final String KEY_ROAD_BLOCK = "road-block";
    public static final String KEY_ROAD_EDGE_BLOCK = "road-edge-block";
    public static final String KEY_ROAD_CORNER_BLOCK = "road-corner-block";
    public static final String KEY_FILL_BLOCK = "fill-block";
    public static final String KEY_BEDROCK_BLOCK = "bedrock-block";

    public static PlotGeneratorPreset defaults() {
        return new PlotGeneratorPreset(
                35,
                7,
                64,
                "minecraft:grass_block",
                "minecraft:oak_planks",
                "minecraft:smooth_stone_slab",
                "minecraft:smooth_stone_slab",
                "minecraft:dirt",
                "minecraft:bedrock"
        );
    }

    public static PlotGeneratorPreset fromConfig(PlotWorldConfig config) {
        PlotGeneratorPreset defaults = defaults();
        String roadEdgeBlock = config.roadEdgeBlock();
        if (roadEdgeBlock == null || roadEdgeBlock.isBlank()) {
            roadEdgeBlock = defaults.roadEdgeBlock();
        }
        String roadCornerBlock = config.roadCornerBlock();
        if (roadCornerBlock == null || roadCornerBlock.isBlank()) {
            roadCornerBlock = defaults.roadCornerBlock();
        }
        return new PlotGeneratorPreset(
                config.plotSize(),
                config.roadSize(),
                config.groundY(),
                defaults.plotBlock(),
                defaults.roadBlock(),
                roadEdgeBlock,
                roadCornerBlock,
                defaults.fillBlock(),
                defaults.bedrockBlock()
        );
    }

    public static PlotGeneratorPreset fromPreset(String preset) {
        PlotGeneratorPreset defaults = defaults();
        if (preset == null || preset.isBlank()) {
            return defaults;
        }

        int plotSize = defaults.plotSize();
        int roadSize = defaults.roadSize();
        int groundY = defaults.groundY();
        String plotBlock = defaults.plotBlock();
        String roadBlock = defaults.roadBlock();
        String roadEdgeBlock = defaults.roadEdgeBlock();
        String roadCornerBlock = defaults.roadCornerBlock();
        String fillBlock = defaults.fillBlock();
        String bedrockBlock = defaults.bedrockBlock();

        String[] parts = preset.split(";");
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String value = kv[1].trim();
            if (key.equalsIgnoreCase(KEY_PLOT_SIZE)) {
                plotSize = parseInt(value, plotSize);
            } else if (key.equalsIgnoreCase(KEY_ROAD_SIZE)) {
                roadSize = parseInt(value, roadSize);
            } else if (key.equalsIgnoreCase(KEY_GROUND_Y)) {
                groundY = parseInt(value, groundY);
            } else if (key.equalsIgnoreCase(KEY_PLOT_BLOCK)) {
                plotBlock = value;
            } else if (key.equalsIgnoreCase(KEY_ROAD_BLOCK)) {
                roadBlock = value;
            } else if (key.equalsIgnoreCase(KEY_ROAD_EDGE_BLOCK)) {
                roadEdgeBlock = value;
            } else if (key.equalsIgnoreCase(KEY_ROAD_CORNER_BLOCK)) {
                roadCornerBlock = value;
            } else if (key.equalsIgnoreCase(KEY_FILL_BLOCK)) {
                fillBlock = value;
            } else if (key.equalsIgnoreCase(KEY_BEDROCK_BLOCK)) {
                bedrockBlock = value;
            }
        }

        return new PlotGeneratorPreset(
                Math.max(1, plotSize),
                Math.max(0, roadSize),
                groundY,
                plotBlock,
                roadBlock,
                roadEdgeBlock,
                roadCornerBlock,
                fillBlock,
                bedrockBlock
        );
    }

    public String toPresetString() {
        Map<String, String> values = new HashMap<>();
        values.put(KEY_PLOT_SIZE, String.valueOf(plotSize));
        values.put(KEY_ROAD_SIZE, String.valueOf(roadSize));
        values.put(KEY_GROUND_Y, String.valueOf(groundY));
        values.put(KEY_PLOT_BLOCK, plotBlock);
        values.put(KEY_ROAD_BLOCK, roadBlock);
        values.put(KEY_ROAD_EDGE_BLOCK, roadEdgeBlock);
        values.put(KEY_ROAD_CORNER_BLOCK, roadCornerBlock);
        values.put(KEY_FILL_BLOCK, fillBlock);
        values.put(KEY_BEDROCK_BLOCK, bedrockBlock);

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}

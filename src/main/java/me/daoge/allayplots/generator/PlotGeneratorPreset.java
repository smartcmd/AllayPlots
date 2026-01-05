package me.daoge.allayplots.generator;

import me.daoge.allayplots.config.PlotWorldConfig;

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
        PlotGeneratorPreset d = defaults();

        int plotSize = Math.max(1, config.plotSize());
        int roadSize = Math.max(0, config.roadSize());
        int groundY = config.groundY();

        return new PlotGeneratorPreset(
                plotSize,
                roadSize,
                groundY,
                orDefaultBlank(config.plotBlock(), d.plotBlock()),
                orDefaultBlank(config.roadBlock(), d.roadBlock()),
                orDefaultBlank(config.roadEdgeBlock(), d.roadEdgeBlock()),
                orDefaultBlank(config.roadCornerBlock(), d.roadCornerBlock()),
                orDefaultBlank(config.fillBlock(), d.fillBlock()),
                orDefaultBlank(config.bedrockBlock(), d.bedrockBlock())
        );
    }

    public static PlotGeneratorPreset fromPreset(String preset) {
        PlotGeneratorPreset d = defaults();
        if (preset == null || preset.isBlank()) {
            return d;
        }

        int plotSize = d.plotSize();
        int roadSize = d.roadSize();
        int groundY = d.groundY();
        String plotBlock = d.plotBlock();
        String roadBlock = d.roadBlock();
        String roadEdgeBlock = d.roadEdgeBlock();
        String roadCornerBlock = d.roadCornerBlock();
        String fillBlock = d.fillBlock();
        String bedrockBlock = d.bedrockBlock();

        for (String part : preset.split(";")) {
            if (part.isBlank()) continue;

            int eq = part.indexOf('=');
            if (eq <= 0 || eq == part.length() - 1) continue;

            String key = part.substring(0, eq).trim().toLowerCase();
            String value = part.substring(eq + 1).trim();

            switch (key) {
                case KEY_PLOT_SIZE -> plotSize = parseInt(value, plotSize);
                case KEY_ROAD_SIZE -> roadSize = parseInt(value, roadSize);
                case KEY_GROUND_Y -> groundY = parseInt(value, groundY);
                case KEY_PLOT_BLOCK -> plotBlock = value;
                case KEY_ROAD_BLOCK -> roadBlock = value;
                case KEY_ROAD_EDGE_BLOCK -> roadEdgeBlock = value;
                case KEY_ROAD_CORNER_BLOCK -> roadCornerBlock = value;
                case KEY_FILL_BLOCK -> fillBlock = value;
                case KEY_BEDROCK_BLOCK -> bedrockBlock = value;
                default -> {
                    // ignore unknown keys
                }
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
        StringBuilder b = new StringBuilder(160);
        appendKV(b, KEY_PLOT_SIZE, String.valueOf(plotSize));
        appendKV(b, KEY_ROAD_SIZE, String.valueOf(roadSize));
        appendKV(b, KEY_GROUND_Y, String.valueOf(groundY));
        appendKV(b, KEY_PLOT_BLOCK, plotBlock);
        appendKV(b, KEY_ROAD_BLOCK, roadBlock);
        appendKV(b, KEY_ROAD_EDGE_BLOCK, roadEdgeBlock);
        appendKV(b, KEY_ROAD_CORNER_BLOCK, roadCornerBlock);
        appendKV(b, KEY_FILL_BLOCK, fillBlock);
        appendKV(b, KEY_BEDROCK_BLOCK, bedrockBlock);
        return b.toString();
    }

    private static void appendKV(StringBuilder b, String key, String value) {
        if (!b.isEmpty()) b.append(';');
        b.append(key).append('=').append(value);
    }

    private static String orDefaultBlank(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}

package me.daoge.allayplots.generator;

import org.allaymc.api.block.type.BlockState;
import org.allaymc.api.block.type.BlockTypes;
import org.allaymc.api.registry.Registries;
import org.allaymc.api.utils.identifier.Identifier;
import org.allaymc.api.world.generator.context.NoiseContext;
import org.allaymc.api.world.generator.function.Noiser;

public final class PlotNoiser implements Noiser {
    private PlotGeneratorPreset preset;
    private PlotGrid grid;
    private BlockState plotBlock;
    private BlockState roadBlock;
    private BlockState roadEdgeBlock;
    private BlockState roadCornerBlock;
    private BlockState fillBlock;
    private BlockState bedrockBlock;

    @Override
    public void init(String presetString) {
        preset = PlotGeneratorPreset.fromPreset(presetString);
        grid = new PlotGrid(preset.plotSize(), preset.roadSize());
        plotBlock = resolveBlockState(preset.plotBlock(), BlockTypes.GRASS_BLOCK.getDefaultState());
        roadBlock = resolveBlockState(preset.roadBlock(), BlockTypes.OAK_PLANKS.getDefaultState());
        roadEdgeBlock = resolveBlockState(preset.roadEdgeBlock(), BlockTypes.SMOOTH_STONE_SLAB.getDefaultState());
        roadCornerBlock = resolveBlockState(preset.roadCornerBlock(), BlockTypes.SMOOTH_STONE_SLAB.getDefaultState());
        fillBlock = resolveBlockState(preset.fillBlock(), BlockTypes.DIRT.getDefaultState());
        bedrockBlock = resolveBlockState(preset.bedrockBlock(), BlockTypes.BEDROCK.getDefaultState());
    }

    @Override
    public boolean apply(NoiseContext context) {
        var chunk = context.getCurrentChunk();
        var dimensionInfo = chunk.getDimensionInfo();
        int minY = dimensionInfo.minHeight();
        int maxY = dimensionInfo.maxHeight();
        int surfaceY = resolveSurfaceY(minY, maxY, preset.groundY());
        int fillTop = surfaceY - 1;

        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        for (int localX = 0; localX < 16; localX++) {
            int worldX = (chunkX << 4) + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = (chunkZ << 4) + localZ;
                boolean road = grid.isRoad(worldX, worldZ);
                boolean roadCorner = road && grid.isRoadCorner(worldX, worldZ);
                boolean roadEdge = road && grid.isRoadEdge(worldX, worldZ);
                BlockState surface = road ? roadBlock : plotBlock;

                chunk.setBlockState(localX, minY, localZ, bedrockBlock, 0, false);
                if (fillTop >= minY + 1) {
                    for (int y = minY + 1; y <= fillTop; y++) {
                        chunk.setBlockState(localX, y, localZ, fillBlock, 0, false);
                    }
                }
                if (surfaceY >= minY && surfaceY <= maxY) {
                    chunk.setBlockState(localX, surfaceY, localZ, surface, 0, false);
                }
                if (surfaceY + 1 <= maxY) {
                    if (roadCorner) {
                        chunk.setBlockState(localX, surfaceY + 1, localZ, roadCornerBlock, 0, false);
                    } else if (roadEdge) {
                        chunk.setBlockState(localX, surfaceY + 1, localZ, roadEdgeBlock, 0, false);
                    }
                }
            }
        }
        return true;
    }

    private static int resolveSurfaceY(int minY, int maxY, int groundY) {
        int surface = Math.min(maxY, groundY);
        if (surface <= minY) {
            surface = Math.min(maxY, minY + 1);
        }
        return surface;
    }

    private static BlockState resolveBlockState(String id, BlockState fallback) {
        if (id == null || id.isBlank()) {
            return fallback;
        }
        try {
            var type = Registries.BLOCKS.get(new Identifier(id));
            if (type == null) {
                return fallback;
            }
            return type.getDefaultState();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}

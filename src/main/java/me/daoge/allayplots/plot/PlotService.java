package me.daoge.allayplots.plot;

import me.daoge.allayplots.config.PlotWorldConfig;
import me.daoge.allayplots.config.PluginConfig;
import me.daoge.allayplots.generator.PlotGeneratorPreset;
import me.daoge.allayplots.storage.PlotStorage;
import org.allaymc.api.block.type.BlockState;
import org.allaymc.api.block.type.BlockTypes;
import org.allaymc.api.math.MathUtils;
import org.allaymc.api.math.location.Location3dc;
import org.allaymc.api.registry.Registries;
import org.allaymc.api.server.Server;
import org.allaymc.api.utils.identifier.Identifier;
import org.allaymc.api.world.Dimension;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class PlotService {
    private final PluginConfig config;
    private final PlotStorage storage;
    private final Logger logger;

    private final Map<String, PlotWorld> worlds = new HashMap<>();
    private final Map<UUID, PlotLocation> homeByOwner = new HashMap<>();

    public PlotService(PluginConfig config, PlotStorage storage, Logger logger) {
        this.config = config;
        this.storage = storage;
        this.logger = logger;
    }

    public void load() {
        Map<String, Map<PlotId, Plot>> stored = storage.load();
        worlds.clear();
        homeByOwner.clear();

        for (Map.Entry<String, PlotWorldConfig> entry : config.worlds().entrySet()) {
            PlotWorld world = new PlotWorld(entry.getValue());

            Map<PlotId, Plot> worldPlots = stored.get(entry.getKey());
            if (worldPlots != null) {
                world.getPlots().putAll(worldPlots);
                world.normalizeMerges();
            }

            worlds.put(entry.getKey(), world);
        }

        rebuildOwnerIndexes();

        if (!stored.isEmpty() && worlds.isEmpty()) {
            logger.warn("Plot data exists but no plot worlds are configured.");
        }
    }

    public void save() {
        storage.save(snapshotWorlds());
    }

    public int worldCount() {
        return worlds.size();
    }

    public PlotWorld getPlotWorld(Dimension dimension) {
        return getPlotWorld(dimension.getWorld().getName());
    }

    public PlotWorld getPlotWorld(String worldName) {
        return worlds.get(worldName);
    }

    public PlotLocation resolvePlot(Location3dc location) {
        PlotWorld world = getPlotWorld(location.dimension());
        if (world == null) return null;

        var blockPos = MathUtils.floor(location);
        PlotId id = world.getPlotIdAt(blockPos.x(), blockPos.z());
        if (id == null) return null;

        return new PlotLocation(world, id, world.getPlot(id));
    }

    public PlotLocation resolvePlot(Dimension dimension, int x, int z) {
        PlotWorld world = getPlotWorld(dimension);
        if (world == null) return null;

        PlotId id = world.getPlotIdAt(x, z);
        if (id == null) return null;

        return new PlotLocation(world, id, world.getPlot(id));
    }

    public Plot claimPlot(PlotWorld world, PlotId id, UUID owner, String ownerName) {
        Plot plot = world.claimPlot(id, owner, ownerName);

        PlotLocation loc = new PlotLocation(world, id, plot);
        if (plot.isHome()) {
            homeByOwner.put(owner, loc);
        } else if (!homeByOwner.containsKey(owner)) {
            plot.setHome(true);
            homeByOwner.put(owner, loc);
        }
        return plot;
    }

    public void deletePlot(PlotWorld world, PlotId id) {
        Plot removed = world.getPlot(id);
        Set<PlotMergeDirection> mergedDirections = removed != null
                ? Set.copyOf(removed.getMergedDirections())
                : Set.of();

        world.clearMergedConnections(id);
        world.removePlot(id);
        for (PlotMergeDirection direction : mergedDirections) {
            updateMergeRoads(world, id, direction);
        }

        if (removed != null && removed.getOwner() != null) {
            UUID owner = removed.getOwner();
            PlotLocation homeLoc = homeByOwner.get(owner);

            boolean affectsHome = removed.isHome()
                    || (homeLoc != null && homeLoc.world() == world && homeLoc.id().equals(id));

            if (affectsHome) {
                recomputeOwnerIndexes(owner);
            }
        }
    }

    public int countOwnedPlots(PlotWorld world, UUID owner) {
        return world.countOwnedPlots(owner);
    }

    public PlotId findNextFreePlotId(PlotWorld world) {
        return world.findNextFreePlotId();
    }

    public PlotLocation findHomePlot(UUID owner) {
        return homeByOwner.get(owner);
    }

    public boolean setHomePlot(UUID owner, PlotWorld world, PlotId id) {
        Plot plot = world.getPlot(id);
        if (plot == null || !plot.isOwner(owner)) return false;

        PlotLocation oldHome = homeByOwner.get(owner);
        if (oldHome != null) {
            if (!(oldHome.world() == world && oldHome.id().equals(id))) {
                Plot oldPlot = oldHome.world().getPlot(oldHome.id());
                if (oldPlot != null && oldPlot.isOwner(owner) && oldPlot.isHome()) {
                    oldPlot.setHome(false);
                }
            }
        }

        plot.setHome(true);
        PlotLocation newHome = new PlotLocation(world, id, plot);
        homeByOwner.put(owner, newHome);

        return true;
    }

    public boolean setPlotOwner(PlotWorld world, PlotId id, UUID newOwner, String newOwnerName) {
        Plot plot = world.getPlot(id);
        if (plot == null || !plot.isClaimed()) return false;

        UUID oldOwner = plot.getOwner();
        if (oldOwner != null && oldOwner.equals(newOwner)) {
            plot.setOwnerName(newOwnerName);
            return true;
        }

        world.clearMergedConnections(id);
        plot.setOwner(newOwner, newOwnerName);

        if (oldOwner != null) {
            recomputeOwnerIndexes(oldOwner);
        }

        PlotLocation loc = new PlotLocation(world, id, plot);
        if (!homeByOwner.containsKey(newOwner)) {
            plot.setHome(true);
            homeByOwner.put(newOwner, loc);
        }

        return true;
    }

    public Map<String, PlotWorld> snapshotWorlds() {
        return new HashMap<>(worlds);
    }

    public void applyToMergeGroup(PlotWorld world, PlotId id, Consumer<Plot> action) {
        for (PlotId plotId : world.getMergeGroup(id)) {
            Plot plot = world.getPlot(plotId);
            if (plot != null) action.accept(plot);
        }
    }

    public void syncPlotSettings(PlotWorld world, PlotId id, Plot source) {
        applyToMergeGroup(world, id, plot -> {
            if (plot == source) return;

            plot.getTrusted().clear();
            plot.getTrusted().addAll(source.getTrusted());

            plot.getDenied().clear();
            plot.getDenied().addAll(source.getDenied());

            plot.getFlags().clear();
            plot.getFlags().putAll(source.getFlags());
        });
    }

    public void updateMergeRoads(PlotWorld world, PlotId plotId, PlotMergeDirection direction) {
        if (world == null || plotId == null || direction == null) return;

        PlotWorldConfig cfg = world.getConfig();
        if (cfg.roadSize() <= 0) return;

        Dimension dim = resolveOverworldDimension(cfg.worldName());
        if (dim == null) return;

        var info = dim.getDimensionInfo();
        int surfaceY = resolveSurfaceY(info.minHeight(), info.maxHeight(), cfg.groundY());
        int topY = surfaceY + 1;
        boolean hasTop = topY <= info.maxHeight();

        PlotBounds base = world.getPlotBounds(plotId);
        boolean merged = world.isMerged(plotId, direction);

        UpdateArea area = UpdateArea.forMerge(base, cfg.roadSize(), direction);
        loadChunks(dim, area.minX, area.maxX, area.minZ, area.maxZ);

        String preset = dim.getWorldGenerator() != null ? dim.getWorldGenerator().getPreset() : "";
        PlotSurfacePalette pal = PlotSurfacePalette.fromWorld(cfg, preset);

        PlotMask mask = PlotMask.build(world, area, merged);

        for (int x = area.minX; x <= area.maxX; x++) {
            for (int z = area.minZ; z <= area.maxZ; z++) {
                boolean plotArea = mask.isPlot(x, z);

                // surface
                dim.setBlockState(x, surfaceY, z, plotArea ? pal.plotBlock() : pal.roadBlock());

                if (!hasTop) continue;

                // top
                if (plotArea) {
                    dim.setBlockState(x, topY, z, BlockTypes.AIR.getDefaultState());
                } else {
                    dim.setBlockState(x, topY, z, resolveRoadTop(mask, x, z, pal));
                }
            }
        }
    }

    public record PlotLocation(PlotWorld world, PlotId id, Plot plot) {}

    private Dimension resolveOverworldDimension(String worldName) {
        var targetWorld = Server.getInstance().getWorldPool().getWorld(worldName);
        if (targetWorld == null) {
            logger.warn("Plot world {} is not loaded; skipping road update.", worldName);
            return null;
        }

        Dimension dim = targetWorld.getOverWorld();
        if (dim == null) {
            logger.warn("Plot world {} has no overworld dimension; skipping road update.", worldName);
            return null;
        }
        return dim;
    }

    private static BlockState resolveRoadTop(PlotMask mask, int x, int z, PlotSurfacePalette pal) {
        // N/S/E/W adjacency to plot
        boolean plotW = mask.isPlot(x - 1, z);
        boolean plotE = mask.isPlot(x + 1, z);
        boolean plotN = mask.isPlot(x, z - 1);
        boolean plotS = mask.isPlot(x, z + 1);

        boolean edgeX = plotW || plotE;
        boolean edgeZ = plotN || plotS;

        if (edgeX && edgeZ) return pal.roadCornerBlock();
        if (edgeX || edgeZ) return pal.roadEdgeBlock();

        // Diagonal-only adjacency (fixes missing corner after merge/unmerge)
        boolean plotNW = mask.isPlot(x - 1, z - 1);
        boolean plotNE = mask.isPlot(x + 1, z - 1);
        boolean plotSW = mask.isPlot(x - 1, z + 1);
        boolean plotSE = mask.isPlot(x + 1, z + 1);

        if (plotNW || plotNE || plotSW || plotSE) return pal.roadCornerBlock();

        return BlockTypes.AIR.getDefaultState();
    }

    private void loadChunks(Dimension dimension, int minX, int maxX, int minZ, int maxZ) {
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        var chunkManager = dimension.getChunkManager();
        var futures = new ArrayList<CompletableFuture<?>>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                final int cx = chunkX;
                final int cz = chunkZ;

                try {
                    CompletableFuture<?> f = chunkManager.getOrLoadChunk(cx, cz)
                            .handle((chunk, ex) -> {
                                if (ex != null) {
                                    logger.warn("Failed to load chunk {},{} for road update.", cx, cz, ex);
                                }
                                return null;
                            });
                    futures.add(f);
                } catch (RuntimeException ex) {
                    logger.warn("Failed to schedule chunk load {},{} for road update.", cx, cz, ex);
                }
            }
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private static int resolveSurfaceY(int minY, int maxY, int groundY) {
        int surface = Math.min(maxY, groundY);
        if (surface <= minY) surface = Math.min(maxY, minY + 1);
        return surface;
    }

    private static BlockState resolveBlockState(String id, BlockState fallback) {
        if (id == null || id.isBlank()) return fallback;
        try {
            var type = Registries.BLOCKS.get(new Identifier(id));
            return type != null ? type.getDefaultState() : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private void rebuildOwnerIndexes() {
        Map<UUID, PlotLocation> fallbackByOwner = new HashMap<>();

        for (PlotWorld world : worlds.values()) {
            for (Plot plot : world.getPlots().values()) {
                UUID owner = plot.getOwner();
                if (owner == null) continue;

                PlotLocation loc = new PlotLocation(world, plot.getId(), plot);

                if (plot.isHome()) {
                    homeByOwner.put(owner, loc);
                } else {
                    fallbackByOwner.putIfAbsent(owner, loc);
                }
            }
        }

        for (Map.Entry<UUID, PlotLocation> entry : fallbackByOwner.entrySet()) {
            if (homeByOwner.containsKey(entry.getKey())) continue;
            PlotLocation loc = entry.getValue();
            loc.plot().setHome(true);
            homeByOwner.put(entry.getKey(), loc);
        }
    }

    private void recomputeOwnerIndexes(UUID owner) {
        homeByOwner.remove(owner);

        PlotLocation fallback = null;

        for (PlotWorld world : worlds.values()) {
            for (Plot plot : world.getPlots().values()) {
                if (!plot.isOwner(owner)) continue;

                PlotLocation loc = new PlotLocation(world, plot.getId(), plot);

                if (plot.isHome()) {
                    homeByOwner.put(owner, loc);
                    return;
                }

                if (fallback == null) fallback = loc;
            }
        }

        if (fallback != null) {
            fallback.plot().setHome(true);
            homeByOwner.put(owner, fallback);
        }
    }

    private record UpdateArea(
            int minX, int maxX, int minZ, int maxZ,
            int stripMinX, int stripMaxX, int stripMinZ, int stripMaxZ
    ) {
        static UpdateArea forMerge(PlotBounds base, int roadSize, PlotMergeDirection dir) {
            return switch (dir) {
                case EAST -> new UpdateArea(
                        base.maxX() + 1, base.maxX() + roadSize,
                        base.minZ() - roadSize, base.maxZ() + roadSize,
                        base.maxX() + 1, base.maxX() + roadSize,
                        base.minZ(), base.maxZ()
                );
                case WEST -> new UpdateArea(
                        base.minX() - roadSize, base.minX() - 1,
                        base.minZ() - roadSize, base.maxZ() + roadSize,
                        base.minX() - roadSize, base.minX() - 1,
                        base.minZ(), base.maxZ()
                );
                case SOUTH -> new UpdateArea(
                        base.minX() - roadSize, base.maxX() + roadSize,
                        base.maxZ() + 1, base.maxZ() + roadSize,
                        base.minX(), base.maxX(),
                        base.maxZ() + 1, base.maxZ() + roadSize
                );
                case NORTH -> new UpdateArea(
                        base.minX() - roadSize, base.maxX() + roadSize,
                        base.minZ() - roadSize, base.minZ() - 1,
                        base.minX(), base.maxX(),
                        base.minZ() - roadSize, base.minZ() - 1
                );
            };
        }
    }

    private record PlotSurfacePalette(
            BlockState plotBlock,
            BlockState roadBlock,
            BlockState roadEdgeBlock,
            BlockState roadCornerBlock
    ) {
        static PlotSurfacePalette fromWorld(PlotWorldConfig config, String presetString) {
            PlotGeneratorPreset preset = (presetString == null || presetString.isBlank())
                    ? PlotGeneratorPreset.fromConfig(config)
                    : PlotGeneratorPreset.fromPreset(presetString);

            BlockState plotBlock = resolveBlockState(preset.plotBlock(), BlockTypes.GRASS_BLOCK.getDefaultState());
            BlockState roadBlock = resolveBlockState(preset.roadBlock(), BlockTypes.OAK_PLANKS.getDefaultState());
            BlockState roadEdgeBlock = resolveBlockState(preset.roadEdgeBlock(), BlockTypes.SMOOTH_STONE_SLAB.getDefaultState());
            BlockState roadCornerBlock = resolveBlockState(preset.roadCornerBlock(), BlockTypes.SMOOTH_STONE_SLAB.getDefaultState());

            return new PlotSurfacePalette(plotBlock, roadBlock, roadEdgeBlock, roadCornerBlock);
        }
    }

    private record PlotMask(int minX, int minZ, int width, int depth, boolean[] mask) {
        static PlotMask build(PlotWorld world, UpdateArea area, boolean merged) {
                int padMinX = area.minX - 1;
                int padMaxX = area.maxX + 1;
                int padMinZ = area.minZ - 1;
                int padMaxZ = area.maxZ + 1;

                int width = padMaxX - padMinX + 1;
                int depth = padMaxZ - padMinZ + 1;
                boolean[] mask = new boolean[width * depth];

                for (int x = padMinX; x <= padMaxX; x++) {
                    for (int z = padMinZ; z <= padMaxZ; z++) {
                        boolean inStrip = merged
                                          && x >= area.stripMinX && x <= area.stripMaxX
                                          && z >= area.stripMinZ && z <= area.stripMaxZ;

                        boolean isPlot = inStrip || world.getPlotIdAt(x, z) != null;
                        mask[(x - padMinX) * depth + (z - padMinZ)] = isPlot;
                    }
                }

                return new PlotMask(padMinX, padMinZ, width, depth, mask);
            }

            boolean isPlot(int x, int z) {
                int dx = x - minX;
                int dz = z - minZ;
                if (dx < 0 || dz < 0 || dx >= width || dz >= depth) return false;
                return mask[dx * depth + dz];
            }
        }
}

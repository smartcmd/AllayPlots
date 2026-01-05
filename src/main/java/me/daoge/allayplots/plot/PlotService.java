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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

public final class PlotService {
    private final PluginConfig config;
    private final PlotStorage storage;
    private final Logger logger;

    private final Map<String, PlotWorld> worlds = new ConcurrentHashMap<>();
    private final Map<UUID, PlotLocation> homeByOwner = new ConcurrentHashMap<>();
    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean dirty = false;
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("AllayPlots-PlotSave", 0).factory());
    private final AtomicBoolean saveInFlight = new AtomicBoolean(false);
    private final AtomicReference<Map<String, Map<PlotId, Plot>>> pendingSave = new AtomicReference<>();
    private final Object saveLock = new Object();
    private Thread serviceThread;
    private static final Runnable POISON_PILL = () -> {};

    public PlotService(PluginConfig config, PlotStorage storage, Logger logger) {
        this.config = config;
        this.storage = storage;
        this.logger = logger;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            serviceThread = Thread.ofPlatform()
                    .name("AllayPlots-PlotService")
                    .start(this::runLoop);
        }
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        taskQueue.offer(POISON_PILL);
        if (serviceThread != null) {
            try {
                serviceThread.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Plot save executor did not stop in time; forcing shutdown.");
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            saveExecutor.shutdownNow();
        }
    }

    private void runLoop() {
        while (true) {
            Runnable task;
            try {
                task = taskQueue.take();
            } catch (InterruptedException ex) {
                if (!running.get()) {
                    break;
                }
                continue;
            }
            if (task == POISON_PILL) {
                break;
            }
            try {
                task.run();
            } catch (Throwable ex) {
                logger.error("Plot service task failed.", ex);
            }
        }
    }

    private boolean isPlotThread() {
        return Thread.currentThread() == serviceThread;
    }

    private void ensureRunning() {
        if (!running.get()) {
            throw new IllegalStateException("Plot service thread is not running.");
        }
    }

    private void enqueue(Runnable task) {
        ensureRunning();
        try {
            taskQueue.put(task);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while enqueuing plot task.", ex);
        }
    }

    private void markDirty() {
        dirty = true;
    }

    private Map<String, Map<PlotId, Plot>> snapshotIfDirty() {
        if (!dirty) {
            return null;
        }
        Map<String, Map<PlotId, Plot>> snapshot = snapshotPlots();
        dirty = false;
        return snapshot;
    }

    private <T> T runOnPlotThread(Callable<T> action) {
        if (isPlotThread()) {
            try {
                return action.call();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        enqueue(() -> {
            try {
                future.complete(action.call());
            } catch (Throwable ex) {
                future.completeExceptionally(ex);
            }
        });
        return future.join();
    }

    private void submitAsync(Runnable action) {
        if (isPlotThread()) {
            action.run();
            return;
        }
        if (!running.get()) {
            return;
        }
        try {
            taskQueue.put(() -> {
                try {
                    action.run();
                } catch (Throwable ex) {
                    logger.error("Plot service task failed.", ex);
                }
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    public void load() {
        runOnPlotThread(() -> {
            loadInternal();
            return null;
        });
    }

    private void loadInternal() {
        Map<String, Map<PlotId, Plot>> stored = storage.load();
        worlds.clear();
        homeByOwner.clear();
        boolean changed = false;

        for (Map.Entry<String, PlotWorldConfig> entry : config.worlds().entrySet()) {
            PlotWorld world = new PlotWorld(entry.getValue());

            Map<PlotId, Plot> worldPlots = stored.get(entry.getKey());
            if (worldPlots != null) {
                world.putPlots(worldPlots);
                if (world.normalizeMerges()) {
                    changed = true;
                }
            }

            worlds.put(entry.getKey(), world);
        }

        if (rebuildOwnerIndexes()) {
            changed = true;
        }

        if (!stored.isEmpty() && worlds.isEmpty()) {
            logger.warn("Plot data exists but no plot worlds are configured.");
        }
        if (changed) {
            markDirty();
        }
    }

    public void save() {
        Map<String, Map<PlotId, Plot>> snapshot = runOnPlotThread(this::snapshotIfDirty);
        if (snapshot == null) {
            return;
        }
        saveBlocking(snapshot);
    }

    public void requestSave() {
        submitAsync(() -> {
            Map<String, Map<PlotId, Plot>> snapshot = snapshotIfDirty();
            if (snapshot == null) {
                return;
            }
            enqueueSave(snapshot);
        });
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

        return new PlotLocation(world, id);
    }

    public PlotLocation resolvePlot(Dimension dimension, int x, int z) {
        PlotWorld world = getPlotWorld(dimension);
        if (world == null) return null;

        PlotId id = world.getPlotIdAt(x, z);
        if (id == null) return null;

        return new PlotLocation(world, id);
    }

    public enum ClaimResult {
        SUCCESS,
        ALREADY_CLAIMED,
        TOO_MANY,
        FAILED
    }

    public enum OwnerActionResult {
        SUCCESS,
        UNCLAIMED,
        NOT_OWNER
    }

    public enum UnmergeResult {
        SUCCESS,
        NOT_MERGED,
        UNCLAIMED,
        NOT_OWNER,
        FAILED
    }

    public ClaimResult claimPlot(PlotWorld world, PlotId id, UUID owner, String ownerName, int maxPlots) {
        return runOnPlotThread(() -> claimPlotInternal(world, id, owner, ownerName, maxPlots));
    }

    private ClaimResult claimPlotInternal(PlotWorld world, PlotId id, UUID owner, String ownerName, int maxPlots) {
        if (world == null || id == null || owner == null) {
            return ClaimResult.FAILED;
        }
        Plot existing = world.getPlot(id);
        if (existing != null && existing.isClaimed()) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        if (maxPlots > 0 && world.countOwnedPlots(owner) >= maxPlots) {
            return ClaimResult.TOO_MANY;
        }
        claimPlotUnchecked(world, id, owner, ownerName);
        return ClaimResult.SUCCESS;
    }

    private Plot claimPlotUnchecked(PlotWorld world, PlotId id, UUID owner, String ownerName) {
        Plot plot = world.claimPlot(id, owner, ownerName);

        PlotLocation loc = new PlotLocation(world, id);
        if (plot.isHome()) {
            homeByOwner.put(owner, loc);
        } else if (!homeByOwner.containsKey(owner)) {
            plot = plot.withHome(true);
            world.putPlot(id, plot);
            homeByOwner.put(owner, loc);
        }
        markDirty();
        return plot;
    }

    private record OwnerCheck(OwnerActionResult result, Plot plot) {}

    private OwnerCheck checkOwnedPlot(PlotWorld world, PlotId id, UUID requester, boolean bypassOwner) {
        if (world == null || id == null) {
            return new OwnerCheck(OwnerActionResult.UNCLAIMED, null);
        }
        Plot plot = world.getPlot(id);
        if (plot == null || !plot.isClaimed()) {
            return new OwnerCheck(OwnerActionResult.UNCLAIMED, plot);
        }
        if (!bypassOwner && (requester == null || !plot.isOwner(requester))) {
            return new OwnerCheck(OwnerActionResult.NOT_OWNER, plot);
        }
        return new OwnerCheck(OwnerActionResult.SUCCESS, plot);
    }

    public OwnerActionResult deletePlot(PlotWorld world, PlotId id, UUID requester, boolean bypassOwner) {
        return runOnPlotThread(() -> deletePlotInternal(world, id, requester, bypassOwner));
    }

    private OwnerActionResult deletePlotInternal(PlotWorld world, PlotId id, UUID requester, boolean bypassOwner) {
        OwnerCheck check = checkOwnedPlot(world, id, requester, bypassOwner);
        if (check.result != OwnerActionResult.SUCCESS) {
            return check.result;
        }
        deletePlotUnchecked(world, id);
        return OwnerActionResult.SUCCESS;
    }

    private void deletePlotUnchecked(PlotWorld world, PlotId id) {
        Plot removed = world.getPlot(id);
        Set<PlotMergeDirection> mergedDirections = removed != null
                ? Set.copyOf(removed.getMergedDirections())
                : Set.of();

        if (world.clearMergedConnections(id)) {
            markDirty();
        }
        world.removePlot(id);
        markDirty();
        for (PlotMergeDirection direction : mergedDirections) {
            updateMergeRoadsInternal(world, id, direction);
        }

        if (removed != null && removed.getOwner() != null) {
            UUID owner = removed.getOwner();
            PlotLocation homeLoc = homeByOwner.get(owner);

            boolean affectsHome = removed.isHome()
                    || (homeLoc != null && homeLoc.world() == world && homeLoc.id().equals(id));

            if (affectsHome && recomputeOwnerIndexes(owner)) {
                markDirty();
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

    public OwnerActionResult setHomePlot(UUID owner, PlotWorld world, PlotId id) {
        return runOnPlotThread(() -> setHomePlotInternal(owner, world, id));
    }

    private OwnerActionResult setHomePlotInternal(UUID owner, PlotWorld world, PlotId id) {
        OwnerCheck check = checkOwnedPlot(world, id, owner, false);
        if (check.result != OwnerActionResult.SUCCESS) {
            return check.result;
        }
        Plot plot = check.plot;
        boolean changed = false;

        PlotLocation oldHome = homeByOwner.get(owner);
        if (oldHome != null) {
            if (!(oldHome.world() == world && oldHome.id().equals(id))) {
                Plot oldPlot = oldHome.plot();
                if (oldPlot != null && oldPlot.isOwner(owner) && oldPlot.isHome()) {
                    Plot updated = oldPlot.withHome(false);
                    if (updated != oldPlot) {
                        oldHome.world().putPlot(oldHome.id(), updated);
                        changed = true;
                    }
                }
            }
        }

        Plot updated = plot.withHome(true);
        if (updated != plot) {
            world.putPlot(id, updated);
            changed = true;
        }
        PlotLocation newHome = new PlotLocation(world, id);
        homeByOwner.put(owner, newHome);

        if (changed) {
            markDirty();
        }
        return OwnerActionResult.SUCCESS;
    }

    public OwnerActionResult setPlotOwner(
            PlotWorld world,
            PlotId id,
            UUID requester,
            boolean bypassOwner,
            UUID newOwner,
            String newOwnerName
    ) {
        return runOnPlotThread(() -> setPlotOwnerInternal(world, id, requester, bypassOwner, newOwner, newOwnerName));
    }

    private OwnerActionResult setPlotOwnerInternal(
            PlotWorld world,
            PlotId id,
            UUID requester,
            boolean bypassOwner,
            UUID newOwner,
            String newOwnerName
    ) {
        OwnerCheck check = checkOwnedPlot(world, id, requester, bypassOwner);
        if (check.result != OwnerActionResult.SUCCESS) {
            return check.result;
        }
        if (!setPlotOwnerUnchecked(world, id, newOwner, newOwnerName)) {
            return OwnerActionResult.UNCLAIMED;
        }
        return OwnerActionResult.SUCCESS;
    }

    private boolean setPlotOwnerUnchecked(PlotWorld world, PlotId id, UUID newOwner, String newOwnerName) {
        Plot plot = world.getPlot(id);
        if (plot == null || !plot.isClaimed()) return false;
        boolean changed = false;

        UUID oldOwner = plot.getOwner();
        if (oldOwner != null && oldOwner.equals(newOwner)) {
            Plot updated = plot.withOwner(newOwner, newOwnerName);
            if (updated != plot) {
                world.putPlot(id, updated);
                changed = true;
            }
            if (changed) {
                markDirty();
            }
            return true;
        }

        if (world.clearMergedConnections(id)) {
            changed = true;
        }
        plot = plot.withOwner(newOwner, newOwnerName);
        world.putPlot(id, plot);
        changed = true;

        if (oldOwner != null) {
            if (recomputeOwnerIndexes(oldOwner)) {
                changed = true;
            }
        }

        if (newOwner != null && !homeByOwner.containsKey(newOwner)) {
            Plot updated = plot.withHome(true);
            if (updated != plot) {
                plot = updated;
                world.putPlot(id, plot);
                changed = true;
            }
            homeByOwner.put(newOwner, new PlotLocation(world, id));
        }

        if (changed) {
            markDirty();
        }
        return true;
    }

    private void enqueueSave(Map<String, Map<PlotId, Plot>> snapshot) {
        pendingSave.set(snapshot);
        if (saveInFlight.compareAndSet(false, true)) {
            saveExecutor.execute(this::runSaveLoop);
        }
    }

    private void runSaveLoop() {
        try {
            while (true) {
                Map<String, Map<PlotId, Plot>> snapshot = pendingSave.getAndSet(null);
                if (snapshot == null) {
                    break;
                }
                try {
                    saveBlocking(snapshot);
                } catch (Throwable ex) {
                    logger.error("Failed to save plot data.", ex);
                }
            }
        } finally {
            saveInFlight.set(false);
            if (pendingSave.get() != null && saveInFlight.compareAndSet(false, true)) {
                saveExecutor.execute(this::runSaveLoop);
            }
        }
    }

    private void saveBlocking(Map<String, Map<PlotId, Plot>> snapshot) {
        synchronized (saveLock) {
            storage.save(snapshot);
        }
    }

    private Map<String, Map<PlotId, Plot>> snapshotPlots() {
        Map<String, Map<PlotId, Plot>> snapshot = new HashMap<>(worlds.size());
        for (Map.Entry<String, PlotWorld> entry : worlds.entrySet()) {
            snapshot.put(entry.getKey(), new HashMap<>(entry.getValue().getPlots()));
        }
        return snapshot;
    }

    public OwnerActionResult updateMergeGroupOwned(
            PlotWorld world,
            PlotId id,
            UUID requester,
            boolean bypassOwner,
            UnaryOperator<Plot> updater
    ) {
        return runOnPlotThread(() -> {
            OwnerCheck check = checkOwnedPlot(world, id, requester, bypassOwner);
            if (check.result != OwnerActionResult.SUCCESS) {
                return check.result;
            }
            UUID baseOwner = check.plot.getOwner();
            updateMergeGroupInternal(world, id, plot -> {
                if (!Objects.equals(plot.getOwner(), baseOwner)) {
                    return plot;
                }
                return updater.apply(plot);
            });
            return OwnerActionResult.SUCCESS;
        });
    }

    public void syncPlotSettings(PlotWorld world, PlotId id, Plot source) {
        runOnPlotThread(() -> {
            syncPlotSettingsInternal(world, id, source);
            return null;
        });
    }

    private void updateMergeGroupInternal(PlotWorld world, PlotId id, UnaryOperator<Plot> updater) {
        boolean changed = false;
        for (PlotId plotId : world.getMergeGroup(id)) {
            Plot plot = world.getPlot(plotId);
            if (plot == null) continue;
            Plot updated = updater.apply(plot);
            if (updated != plot) {
                world.putPlot(plotId, updated);
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
    }

    private void syncPlotSettingsInternal(PlotWorld world, PlotId id, Plot source) {
        if (source == null) {
            return;
        }
        updateMergeGroupInternal(world, id, plot -> {
            if (plot.getId().equals(source.getId())) {
                return plot;
            }
            return plot.withSettingsFrom(source);
        });
    }

    public enum MergeResult {
        SUCCESS,
        UNCLAIMED,
        NOT_OWNER,
        TARGET_UNCLAIMED,
        NOT_SAME_OWNER,
        ALREADY_MERGED,
        FAILED
    }

    public MergeResult mergePlots(
            PlotWorld world,
            PlotId plotId,
            PlotMergeDirection direction,
            UUID requester,
            boolean bypassOwner
    ) {
        return runOnPlotThread(() -> mergePlotsInternal(world, plotId, direction, requester, bypassOwner));
    }

    private MergeResult mergePlotsInternal(
            PlotWorld world,
            PlotId plotId,
            PlotMergeDirection direction,
            UUID requester,
            boolean bypassOwner
    ) {
        if (world == null || plotId == null || direction == null) {
            return MergeResult.FAILED;
        }
        OwnerCheck check = checkOwnedPlot(world, plotId, requester, bypassOwner);
        if (check.result == OwnerActionResult.UNCLAIMED) {
            return MergeResult.UNCLAIMED;
        }
        if (check.result == OwnerActionResult.NOT_OWNER) {
            return MergeResult.NOT_OWNER;
        }
        Plot plot = check.plot;

        PlotId targetId = world.getAdjacentPlotId(plotId, direction);
        Plot targetPlot = world.getPlot(targetId);
        if (targetPlot == null || !targetPlot.isClaimed()) {
            return MergeResult.TARGET_UNCLAIMED;
        }
        if (!Objects.equals(plot.getOwner(), targetPlot.getOwner())) {
            return MergeResult.NOT_SAME_OWNER;
        }
        if (world.isMerged(plotId, direction)) {
            return MergeResult.ALREADY_MERGED;
        }
        if (!world.setMerged(plotId, direction, true)) {
            return MergeResult.FAILED;
        }
        markDirty();

        Plot source = world.getPlot(plotId);
        syncPlotSettingsInternal(world, plotId, source);
        updateMergeRoadsInternal(world, plotId, direction);
        return MergeResult.SUCCESS;
    }

    public UnmergeResult unmergePlots(
            PlotWorld world,
            PlotId plotId,
            PlotMergeDirection direction,
            UUID requester,
            boolean bypassOwner
    ) {
        return runOnPlotThread(() -> unmergePlotsInternal(world, plotId, direction, requester, bypassOwner));
    }

    private UnmergeResult unmergePlotsInternal(
            PlotWorld world,
            PlotId plotId,
            PlotMergeDirection direction,
            UUID requester,
            boolean bypassOwner
    ) {
        if (world == null || plotId == null || direction == null) {
            return UnmergeResult.FAILED;
        }
        Plot plot = world.getPlot(plotId);
        if (plot == null || !plot.isClaimed()) {
            return UnmergeResult.UNCLAIMED;
        }
        if (!bypassOwner && (requester == null || !plot.isOwner(requester))) {
            return UnmergeResult.NOT_OWNER;
        }
        if (!world.isMerged(plotId, direction)) {
            return UnmergeResult.NOT_MERGED;
        }
        if (!world.setMerged(plotId, direction, false)) {
            return UnmergeResult.FAILED;
        }
        markDirty();
        updateMergeRoadsInternal(world, plotId, direction);
        return UnmergeResult.SUCCESS;
    }

    public void updateMergeRoads(PlotWorld world, PlotId plotId, PlotMergeDirection direction) {
        runOnPlotThread(() -> {
            updateMergeRoadsInternal(world, plotId, direction);
            return null;
        });
    }

    private void updateMergeRoadsInternal(PlotWorld world, PlotId plotId, PlotMergeDirection direction) {
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

    public record PlotLocation(PlotWorld world, PlotId id) {
        public Plot plot() {
            return world != null ? world.getPlot(id) : null;
        }
    }

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

    private boolean rebuildOwnerIndexes() {
        Map<UUID, PlotLocation> fallbackByOwner = new HashMap<>();
        boolean changed = false;

        for (PlotWorld world : worlds.values()) {
            for (Plot plot : world.getPlots().values()) {
                UUID owner = plot.getOwner();
                if (owner == null) continue;

                PlotLocation loc = new PlotLocation(world, plot.getId());

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
            Plot plot = loc.plot();
            if (plot != null && !plot.isHome()) {
                Plot updated = plot.withHome(true);
                if (updated != plot) {
                    loc.world().putPlot(loc.id(), updated);
                    changed = true;
                }
            }
            homeByOwner.put(entry.getKey(), loc);
        }
        return changed;
    }

    private boolean recomputeOwnerIndexes(UUID owner) {
        homeByOwner.remove(owner);

        PlotLocation fallback = null;
        boolean changed = false;

        for (PlotWorld world : worlds.values()) {
            for (Plot plot : world.getPlots().values()) {
                if (!plot.isOwner(owner)) continue;

                PlotLocation loc = new PlotLocation(world, plot.getId());

                if (plot.isHome()) {
                    homeByOwner.put(owner, loc);
                    return changed;
                }

                if (fallback == null) fallback = loc;
            }
        }

        if (fallback != null) {
            Plot plot = fallback.plot();
            if (plot != null && !plot.isHome()) {
                Plot updated = plot.withHome(true);
                if (updated != plot) {
                    fallback.world().putPlot(fallback.id(), updated);
                    changed = true;
                }
            }
            homeByOwner.put(owner, fallback);
        }
        return changed;
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

package me.daoge.allayplots.plot;

import me.daoge.allayplots.config.PlotWorldConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PlotWorld {
    private final PlotWorldConfig config;
    private final Map<PlotId, Plot> plots = new ConcurrentHashMap<>();
    private final Map<PlotId, Plot> plotsView = Collections.unmodifiableMap(plots);

    private final Set<PlotId> dirtyPlots = new HashSet<>();
    private final Set<PlotId> deletedPlots = new HashSet<>();

    public PlotWorld(PlotWorldConfig config) {
        this.config = config;
    }

    public PlotWorldConfig getConfig() {
        return config;
    }

    public Map<PlotId, Plot> getPlots() {
        return plotsView;
    }

    void putPlots(Map<PlotId, Plot> plots) {
        this.plots.putAll(plots);
    }

    void putPlot(PlotId id, Plot plot) {
        if (plot == null) {
            removePlot(id);
            return;
        }
        plots.put(id, plot);
        markDirty(id);
    }

    private void markDirty(PlotId id) {
        deletedPlots.remove(id);
        dirtyPlots.add(id);
    }

    private void markDeleted(PlotId id) {
        dirtyPlots.remove(id);
        deletedPlots.add(id);
    }

    public Set<PlotId> getDirtyPlots() {
        return Collections.unmodifiableSet(dirtyPlots);
    }

    public Set<PlotId> getDeletedPlots() {
        return Collections.unmodifiableSet(deletedPlots);
    }

    public boolean hasChanges() {
        return !dirtyPlots.isEmpty() || !deletedPlots.isEmpty();
    }

    public void clearChanges() {
        dirtyPlots.clear();
        deletedPlots.clear();
    }

    /**
     * Returns the plot id for the given block position.
     * - Inside plot area -> id of that plot.
     * - On merged road strip / merged 2x2 intersection -> id of the merge root plot (current cell).
     * - Otherwise -> null (road).
     */
    public PlotId getPlotIdAt(int x, int z) {
        int plotSize = config.plotSize();
        int totalSize = config.totalSize();

        int idX = toCellIndex(x, totalSize);
        int idZ = toCellIndex(z, totalSize);

        int difX = toCellOffset(x, totalSize);
        int difZ = toCellOffset(z, totalSize);

        boolean inPlotX = difX > 0 && difX <= plotSize;
        boolean inPlotZ = difZ > 0 && difZ <= plotSize;

        PlotId current = new PlotId(idX, idZ);

        // Inside plot
        if (inPlotX && inPlotZ) return current;

        // On horizontal road strip (between north/south plots): allow if merged south
        if (inPlotX && isMerged(current, PlotMergeDirection.SOUTH)) return current;

        // On vertical road strip (between west/east plots): allow if merged east
        if (inPlotZ && isMerged(current, PlotMergeDirection.EAST)) return current;

        // On intersection: allow only if it's a fully merged 2x2 block
        PlotId east = new PlotId(idX + 1, idZ);
        PlotId south = new PlotId(idX, idZ + 1);

        boolean eastNorth = isMerged(current, PlotMergeDirection.EAST);
        boolean eastSouth = isMerged(south, PlotMergeDirection.EAST);
        boolean southWest = isMerged(current, PlotMergeDirection.SOUTH);
        boolean southEast = isMerged(east, PlotMergeDirection.SOUTH);

        return (eastNorth && eastSouth && southWest && southEast) ? current : null;
    }

    private static int toCellIndex(int coordinate, int totalSize) {
        // Mirrors Nukkit behavior to keep alignment stable for negative coordinates
        return coordinate >= 0 ? coordinate / totalSize : ((coordinate + 1) / totalSize) - 1;
    }

    private static int toCellOffset(int coordinate, int totalSize) {
        int raw = (coordinate + 1) % totalSize;
        return coordinate >= 0 ? raw : totalSize + raw;
    }

    public PlotBounds getPlotBounds(PlotId id) {
        int totalSize = config.totalSize();
        int originX = id.x() * totalSize;
        int originZ = id.z() * totalSize;
        int maxX = originX + config.plotSize() - 1;
        int maxZ = originZ + config.plotSize() - 1;
        return new PlotBounds(originX, maxX, originZ, maxZ);
    }

    public PlotBounds getMergedPlotBounds(PlotId id) {
        Set<PlotId> group = getMergeGroup(id);
        if (group.isEmpty()) return getPlotBounds(id);

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (PlotId plotId : group) {
            minX = Math.min(minX, plotId.x());
            maxX = Math.max(maxX, plotId.x());
            minZ = Math.min(minZ, plotId.z());
            maxZ = Math.max(maxZ, plotId.z());
        }

        int totalSize = config.totalSize();
        int minBlockX = minX * totalSize;
        int minBlockZ = minZ * totalSize;
        int maxBlockX = maxX * totalSize + config.plotSize() - 1;
        int maxBlockZ = maxZ * totalSize + config.plotSize() - 1;
        return new PlotBounds(minBlockX, maxBlockX, minBlockZ, maxBlockZ);
    }

    public Plot getPlot(PlotId id) {
        return plots.get(id);
    }

    public Plot claimPlot(PlotId id, UUID owner, String ownerName) {
        Plot result = plots.compute(id, (key, existing) -> {
            Plot base = existing == null ? new Plot(config.worldName(), key) : existing;
            return base.withOwner(owner, ownerName);
        });
        markDirty(id);
        return result;
    }

    public void removePlot(PlotId id) {
        if (plots.remove(id) != null) {
            markDeleted(id);
        }
    }

    public int countOwnedPlots(UUID owner) {
        int count = 0;
        for (Plot plot : plots.values()) {
            if (plot.isOwner(owner)) count++;
        }
        return count;
    }

    private static final int MAX_AUTO_CLAIM_RADIUS = 10000;

    public PlotId findNextFreePlotId() {
        for (int radius = 0; radius <= MAX_AUTO_CLAIM_RADIUS; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean onBorder = (x == radius || x == -radius) || (z == radius || z == -radius);
                    if (!onBorder) continue;

                    PlotId id = new PlotId(x, z);
                    Plot plot = plots.get(id);
                    if (plot == null || !plot.isClaimed()) return id;
                }
            }
        }
        return null;
    }

    public PlotId getAdjacentPlotId(PlotId id, PlotMergeDirection direction) {
        return new PlotId(id.x() + direction.dx(), id.z() + direction.dz());
    }

    public boolean isMerged(PlotId id, PlotMergeDirection direction) {
        Plot plot = plots.get(id);
        if (plot == null || !plot.isClaimed() || !plot.isMerged(direction)) return false;

        PlotId neighborId = getAdjacentPlotId(id, direction);
        Plot neighbor = plots.get(neighborId);
        if (neighbor == null || !neighbor.isClaimed() || !neighbor.isMerged(direction.opposite())) return false;

        return Objects.equals(plot.getOwner(), neighbor.getOwner());
    }

    public boolean setMerged(PlotId id, PlotMergeDirection direction, boolean merged) {
        Plot plot = plots.get(id);
        if (plot == null) return false;

        PlotId neighborId = getAdjacentPlotId(id, direction);
        Plot neighbor = plots.get(neighborId);
        if (neighbor == null) return false;

        if (merged) {
            plot = plot.withMergedDirectionAdded(direction);
            neighbor = neighbor.withMergedDirectionAdded(direction.opposite());
        } else {
            plot = plot.withMergedDirectionRemoved(direction);
            neighbor = neighbor.withMergedDirectionRemoved(direction.opposite());
        }
        plots.put(id, plot);
        plots.put(neighborId, neighbor);
        markDirty(id);
        markDirty(neighborId);
        return true;
    }

    public boolean clearMergedConnections(PlotId id) {
        Plot plot = plots.get(id);
        boolean changed = false;
        for (PlotMergeDirection dir : PlotMergeDirection.values()) {
            PlotId neighborId = getAdjacentPlotId(id, dir);
            Plot neighbor = plots.get(neighborId);
            if (neighbor != null) {
                Plot updated = neighbor.withMergedDirectionRemoved(dir.opposite());
                if (updated != neighbor) {
                    plots.put(neighborId, updated);
                    markDirty(neighborId);
                    changed = true;
                }
            }
            if (plot != null) {
                Plot updated = plot.withMergedDirectionRemoved(dir);
                if (updated != plot) {
                    plot = updated;
                    changed = true;
                }
            }
        }
        if (plot != null) {
            plots.put(id, plot);
            if (changed) {
                markDirty(id);
            }
        }
        return changed;
    }

    public Set<PlotId> getMergeGroup(PlotId id) {
        Plot plot = plots.get(id);
        if (plot == null) return Set.of();

        Set<PlotId> visited = new HashSet<>();
        ArrayDeque<PlotId> queue = new ArrayDeque<>();
        queue.add(id);

        while (!queue.isEmpty()) {
            PlotId current = queue.removeFirst();
            if (!visited.add(current)) continue;

            Plot currentPlot = plots.get(current);
            if (currentPlot == null) continue;

            for (PlotMergeDirection dir : currentPlot.getMergedDirections()) {
                if (!isMerged(current, dir)) continue;

                PlotId neighborId = getAdjacentPlotId(current, dir);
                if (!visited.contains(neighborId)) queue.add(neighborId);
            }
        }

        return visited;
    }

    public PlotId getMergeRoot(PlotId id) {
        Set<PlotId> group = getMergeGroup(id);
        PlotId root = null;

        for (PlotId candidate : group) {
            if (root == null
                || candidate.x() < root.x()
                || (candidate.x() == root.x() && candidate.z() < root.z())) {
                root = candidate;
            }
        }

        return root != null ? root : id;
    }

    public boolean normalizeMerges() {
        boolean changed = false;
        for (Map.Entry<PlotId, Plot> entry : plots.entrySet()) {
            PlotId id = entry.getKey();
            Plot plot = entry.getValue();
            Plot updated = plot;

            for (PlotMergeDirection dir : Set.copyOf(plot.getMergedDirections())) {
                PlotId neighborId = getAdjacentPlotId(id, dir);
                Plot neighbor = plots.get(neighborId);

                boolean ok = neighbor != null
                             && plot.isClaimed()
                             && neighbor.isClaimed()
                             && Objects.equals(plot.getOwner(), neighbor.getOwner());

                if (!ok) {
                    updated = updated.withMergedDirectionRemoved(dir);
                    if (neighbor != null) {
                        Plot updatedNeighbor = neighbor.withMergedDirectionRemoved(dir.opposite());
                        if (updatedNeighbor != neighbor) {
                            plots.put(neighborId, updatedNeighbor);
                            markDirty(neighborId);
                            changed = true;
                        }
                    }
                    continue;
                }

                if (!neighbor.isMerged(dir.opposite())) {
                    updated = updated.withMergedDirectionRemoved(dir);
                }
            }
            if (updated != plot) {
                plots.put(id, updated);
                markDirty(id);
                changed = true;
            }
        }
        return changed;
    }
}

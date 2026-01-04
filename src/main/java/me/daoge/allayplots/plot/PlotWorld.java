package me.daoge.allayplots.plot;

import me.daoge.allayplots.config.PlotWorldConfig;

import java.util.*;

public final class PlotWorld {
    private final PlotWorldConfig config;
    private final Map<PlotId, Plot> plots = new HashMap<>();

    public PlotWorld(PlotWorldConfig config) {
        this.config = config;
    }

    public PlotWorldConfig getConfig() {
        return config;
    }

    public Map<PlotId, Plot> getPlots() {
        return plots;
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
        Plot plot = plots.computeIfAbsent(id, key -> new Plot(config.worldName(), key));
        plot.setOwner(owner, ownerName);
        return plot;
    }

    public void removePlot(PlotId id) {
        plots.remove(id);
    }

    public int countOwnedPlots(UUID owner) {
        int count = 0;
        for (Plot plot : plots.values()) {
            if (plot.isOwner(owner)) count++;
        }
        return count;
    }

    public PlotId findNextFreePlotId() {
        int radius = 0;
        while (true) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean onBorder = (x == radius || x == -radius) || (z == radius || z == -radius);
                    if (!onBorder) continue;

                    PlotId id = new PlotId(x, z);
                    Plot plot = plots.get(id);
                    if (plot == null || !plot.isClaimed()) return id;
                }
            }
            radius++;
        }
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
            plot.addMergedDirection(direction);
            neighbor.addMergedDirection(direction.opposite());
        } else {
            plot.removeMergedDirection(direction);
            neighbor.removeMergedDirection(direction.opposite());
        }
        return true;
    }

    public void clearMergedConnections(PlotId id) {
        Plot plot = plots.get(id);
        if (plot == null) return;

        for (PlotMergeDirection dir : Set.copyOf(plot.getMergedDirections())) {
            PlotId neighborId = getAdjacentPlotId(id, dir);
            Plot neighbor = plots.get(neighborId);
            if (neighbor != null) neighbor.removeMergedDirection(dir.opposite());
            plot.removeMergedDirection(dir);
        }
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

    public void normalizeMerges() {
        for (Map.Entry<PlotId, Plot> entry : plots.entrySet()) {
            PlotId id = entry.getKey();
            Plot plot = entry.getValue();

            for (PlotMergeDirection dir : Set.copyOf(plot.getMergedDirections())) {
                PlotId neighborId = getAdjacentPlotId(id, dir);
                Plot neighbor = plots.get(neighborId);

                boolean ok = neighbor != null
                             && plot.isClaimed()
                             && neighbor.isClaimed()
                             && Objects.equals(plot.getOwner(), neighbor.getOwner());

                if (!ok) {
                    plot.removeMergedDirection(dir);
                    if (neighbor != null) neighbor.removeMergedDirection(dir.opposite());
                    continue;
                }

                if (!neighbor.isMerged(dir.opposite())) {
                    plot.removeMergedDirection(dir);
                }
            }
        }
    }
}

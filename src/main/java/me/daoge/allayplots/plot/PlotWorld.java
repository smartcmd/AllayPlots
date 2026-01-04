package me.daoge.allayplots.plot;

import me.daoge.allayplots.config.PlotWorldConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlotWorld {
    private final PlotWorldConfig config;
    private final Map<PlotId, Plot> plots = new ConcurrentHashMap<>();

    public PlotWorld(PlotWorldConfig config) {
        this.config = config;
    }

    public PlotWorldConfig getConfig() {
        return config;
    }

    public Map<PlotId, Plot> getPlots() {
        return plots;
    }

    public PlotId getPlotIdAt(int x, int z) {
        int plotSize = config.plotSize();
        int totalSize = config.totalSize();

        // Negative coordinate math mirrors Nukkit's plot id formula to keep grid alignment stable.
        int idX = x >= 0 ? x / totalSize : ((x + 1) / totalSize) - 1;
        int idZ = z >= 0 ? z / totalSize : ((z + 1) / totalSize) - 1;

        int difX = x >= 0 ? (x + 1) % totalSize : totalSize + ((x + 1) % totalSize);
        int difZ = z >= 0 ? (z + 1) % totalSize : totalSize + ((z + 1) % totalSize);

        boolean onRoad = difX > plotSize || difX == 0 || difZ > plotSize || difZ == 0;
        if (onRoad) {
            return null;
        }
        return new PlotId(idX, idZ);
    }

    public PlotBounds getPlotBounds(PlotId id) {
        int totalSize = config.totalSize();
        int originX = id.x() * totalSize;
        int originZ = id.z() * totalSize;
        int maxX = originX + config.plotSize() - 1;
        int maxZ = originZ + config.plotSize() - 1;
        return new PlotBounds(originX, maxX, originZ, maxZ);
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
            if (plot.isOwner(owner)) {
                count++;
            }
        }
        return count;
    }

    public PlotId findNextFreePlotId() {
        int radius = 0;
        while (true) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if ((x != radius && x != -radius) && (z != radius && z != -radius)) {
                        continue;
                    }
                    PlotId id = new PlotId(x, z);
                    Plot plot = plots.get(id);
                    if (plot == null || !plot.isClaimed()) {
                        return id;
                    }
                }
            }
            radius++;
        }
    }
}

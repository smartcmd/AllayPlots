package me.daoge.allayplots.generator;

final class PlotGrid {
    private final int plotSize;
    private final int totalSize;

    PlotGrid(int plotSize, int roadSize) {
        int normalizedPlotSize = Math.max(1, plotSize);
        int normalizedRoadSize = Math.max(0, roadSize);
        this.plotSize = normalizedPlotSize;
        this.totalSize = normalizedPlotSize + normalizedRoadSize;
    }

    boolean isRoad(int x, int z) {
        int difX = diff(x);
        int difZ = diff(z);
        return difX > plotSize || difX == 0 || difZ > plotSize || difZ == 0;
    }

    boolean isRoadEdge(int x, int z) {
        int difX = diff(x);
        int difZ = diff(z);
        boolean roadX = difX > plotSize || difX == 0;
        boolean roadZ = difZ > plotSize || difZ == 0;
        if (!roadX && !roadZ) {
            return false;
        }
        boolean edgeX = difX == 0 || difX == plotSize + 1 || difX == totalSize;
        boolean edgeZ = difZ == 0 || difZ == plotSize + 1 || difZ == totalSize;
        if (roadX && roadZ) {
            return false;
        }
        return (roadX && edgeX) || (roadZ && edgeZ);
    }

    boolean isRoadCorner(int x, int z) {
        int difX = diff(x);
        int difZ = diff(z);
        boolean roadX = difX > plotSize || difX == 0;
        boolean roadZ = difZ > plotSize || difZ == 0;
        if (!roadX || !roadZ) {
            return false;
        }
        boolean edgeX = difX == 0 || difX == plotSize + 1 || difX == totalSize;
        boolean edgeZ = difZ == 0 || difZ == plotSize + 1 || difZ == totalSize;
        return edgeX && edgeZ;
    }

    private int diff(int coordinate) {
        // Keep grid alignment consistent with PlotWorld's negative coordinate math.
        int raw = (coordinate + 1) % totalSize;
        return coordinate >= 0 ? raw : totalSize + raw;
    }
}

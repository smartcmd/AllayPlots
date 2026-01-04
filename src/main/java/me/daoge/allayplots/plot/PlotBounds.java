package me.daoge.allayplots.plot;

public record PlotBounds(int minX, int maxX, int minZ, int maxZ) {
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }
}

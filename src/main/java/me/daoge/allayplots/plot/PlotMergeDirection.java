package me.daoge.allayplots.plot;

import java.util.Locale;

public enum PlotMergeDirection {
    NORTH(0, -1),
    EAST(1, 0),
    SOUTH(0, 1),
    WEST(-1, 0);

    private final int dx;
    private final int dz;

    PlotMergeDirection(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    public PlotMergeDirection opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case EAST -> WEST;
            case SOUTH -> NORTH;
            case WEST -> EAST;
        };
    }

    public String getLowerCaseName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PlotMergeDirection fromYaw(double yaw) {
        double normalized = yaw % 360.0;
        if (normalized < 0) {
            normalized += 360.0;
        }
        if (normalized >= 45.0 && normalized < 135.0) {
            return WEST;
        }
        if (normalized >= 135.0 && normalized < 225.0) {
            return NORTH;
        }
        if (normalized >= 225.0 && normalized < 315.0) {
            return EAST;
        }
        return SOUTH;
    }

    public static PlotMergeDirection fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return PlotMergeDirection.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

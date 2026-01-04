package me.daoge.allayplots.plot;

public record PlotId(int x, int z) {
    public static PlotId fromString(String raw) {
        String[] parts = raw.split(";", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid plot id: " + raw);
        }
        return new PlotId(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    public String asString() {
        return x + ";" + z;
    }
}

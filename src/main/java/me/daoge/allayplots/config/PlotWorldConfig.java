package me.daoge.allayplots.config;

public record PlotWorldConfig(
        String worldName,
        int plotSize,
        int roadSize,
        int groundY,
        int maxPlotsPerPlayer,
        double claimPrice,
        double sellRefund,
        boolean teleportOnClaim,
        String roadEdgeBlock,
        String roadCornerBlock
) {
    public int totalSize() {
        return plotSize + roadSize;
    }
}

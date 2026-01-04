package me.daoge.allayplots.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class PlotWorldConfig extends OkaeriConfig {
    private transient String worldName = "plotworld";

    @CustomKey("plot-size")
    private int plotSize = 35;

    @CustomKey("road-size")
    private int roadSize = 7;

    @CustomKey("ground-y")
    private int groundY = 64;

    @CustomKey("max-plots-per-player")
    private int maxPlotsPerPlayer = 2;

    @CustomKey("claim-price")
    private double claimPrice = 100.0;

    @CustomKey("sell-refund")
    private double sellRefund = 50.0;

    @CustomKey("teleport-on-claim")
    private boolean teleportOnClaim = true;

    @CustomKey("road-edge-block")
    private String roadEdgeBlock = "minecraft:smooth_stone_slab";

    @CustomKey("road-corner-block")
    private String roadCornerBlock = "minecraft:smooth_stone_slab";

    public void worldName(String worldName) {
        if (worldName != null && !worldName.isBlank()) {
            this.worldName = worldName;
        }
    }

    public int totalSize() {
        return plotSize + roadSize;
    }
}

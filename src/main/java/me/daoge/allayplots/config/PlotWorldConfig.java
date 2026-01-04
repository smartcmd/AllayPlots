package me.daoge.allayplots.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class PlotWorldConfig extends OkaeriConfig {
    private transient String worldName = "plotworld";

    @Comment("Plot size in blocks (inside area, excluding roads).")
    @CustomKey("plot-size")
    private int plotSize = 35;

    @Comment("Road width in blocks between plots.")
    @CustomKey("road-size")
    private int roadSize = 7;

    @Comment("Ground Y level for plot generation.")
    @CustomKey("ground-y")
    private int groundY = 64;

    @Comment("Maximum plots per player (0 for unlimited).")
    @CustomKey("max-plots-per-player")
    private int maxPlotsPerPlayer = 2;

    @Comment("Price to claim a plot (0 to disable).")
    @CustomKey("claim-price")
    private double claimPrice = 100.0;

    @Comment("Refund given when deleting a plot (0 to disable).")
    @CustomKey("sell-refund")
    private double sellRefund = 50.0;

    @Comment("Teleport player to the plot after claiming.")
    @CustomKey("teleport-on-claim")
    private boolean teleportOnClaim = true;

    @Comment("Block used for road edges.")
    @CustomKey("road-edge-block")
    private String roadEdgeBlock = "minecraft:smooth_stone_slab";

    @Comment("Block used for road corners.")
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

package me.daoge.allayplots.plot;

import me.daoge.allayplots.config.PlotWorldConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PlotWorld")
class PlotWorldTest {

    private PlotWorld plotWorld;
    private PlotWorldConfig config;

    @BeforeEach
    void setUp() {
        config = new PlotWorldConfig();
        config.worldName("testworld");
        plotWorld = new PlotWorld(config);
    }

    @Nested
    @DisplayName("Change Tracking")
    class ChangeTracking {

        @Test
        @DisplayName("new PlotWorld has no changes")
        void newPlotWorld_hasNoChanges() {
            assertThat(plotWorld.hasChanges()).isFalse();
            assertThat(plotWorld.getDirtyPlots()).isEmpty();
            assertThat(plotWorld.getDeletedPlots()).isEmpty();
        }

        @Test
        @DisplayName("putPlot marks plot as dirty")
        void putPlot_marksDirty() {
            PlotId id = new PlotId(0, 0);
            Plot plot = new Plot("testworld", id).withOwner(UUID.randomUUID(), "Player");

            plotWorld.putPlot(id, plot);

            assertThat(plotWorld.hasChanges()).isTrue();
            assertThat(plotWorld.getDirtyPlots()).contains(id);
            assertThat(plotWorld.getDeletedPlots()).isEmpty();
        }

        @Test
        @DisplayName("removePlot marks plot as deleted")
        void removePlot_marksDeleted() {
            PlotId id = new PlotId(0, 0);
            Plot plot = new Plot("testworld", id).withOwner(UUID.randomUUID(), "Player");
            plotWorld.putPlot(id, plot);
            plotWorld.clearChanges();

            plotWorld.removePlot(id);

            assertThat(plotWorld.hasChanges()).isTrue();
            assertThat(plotWorld.getDeletedPlots()).contains(id);
            assertThat(plotWorld.getDirtyPlots()).isEmpty();
        }

        @Test
        @DisplayName("removePlot on non-existent plot does not mark deleted")
        void removePlot_nonExistent_noChange() {
            PlotId id = new PlotId(99, 99);

            plotWorld.removePlot(id);

            assertThat(plotWorld.hasChanges()).isFalse();
        }

        @Test
        @DisplayName("claimPlot marks plot as dirty")
        void claimPlot_marksDirty() {
            PlotId id = new PlotId(1, 1);

            plotWorld.claimPlot(id, UUID.randomUUID(), "Player");

            assertThat(plotWorld.hasChanges()).isTrue();
            assertThat(plotWorld.getDirtyPlots()).contains(id);
        }

        @Test
        @DisplayName("clearChanges clears all tracking")
        void clearChanges_clearsAllTracking() {
            PlotId id1 = new PlotId(0, 0);
            PlotId id2 = new PlotId(1, 1);
            plotWorld.putPlot(id1, new Plot("testworld", id1).withOwner(UUID.randomUUID(), "P1"));
            plotWorld.putPlot(id2, new Plot("testworld", id2).withOwner(UUID.randomUUID(), "P2"));
            plotWorld.clearChanges();
            plotWorld.removePlot(id2);

            plotWorld.clearChanges();

            assertThat(plotWorld.hasChanges()).isFalse();
            assertThat(plotWorld.getDirtyPlots()).isEmpty();
            assertThat(plotWorld.getDeletedPlots()).isEmpty();
        }

        @Test
        @DisplayName("putPlot with null removes plot and marks deleted")
        void putPlot_withNull_marksDeleted() {
            PlotId id = new PlotId(0, 0);
            Plot plot = new Plot("testworld", id).withOwner(UUID.randomUUID(), "Player");
            plotWorld.putPlot(id, plot);
            plotWorld.clearChanges();

            plotWorld.putPlot(id, null);

            assertThat(plotWorld.hasChanges()).isTrue();
            assertThat(plotWorld.getDeletedPlots()).contains(id);
            assertThat(plotWorld.getPlot(id)).isNull();
        }

        @Test
        @DisplayName("re-adding deleted plot moves from deleted to dirty")
        void reAddDeletedPlot_movesToDirty() {
            PlotId id = new PlotId(0, 0);
            Plot plot = new Plot("testworld", id).withOwner(UUID.randomUUID(), "Player");
            plotWorld.putPlot(id, plot);
            plotWorld.clearChanges();
            plotWorld.removePlot(id);

            assertThat(plotWorld.getDeletedPlots()).contains(id);

            Plot newPlot = new Plot("testworld", id).withOwner(UUID.randomUUID(), "NewPlayer");
            plotWorld.putPlot(id, newPlot);

            assertThat(plotWorld.getDirtyPlots()).contains(id);
            assertThat(plotWorld.getDeletedPlots()).doesNotContain(id);
        }
    }

    @Nested
    @DisplayName("Merge Change Tracking")
    class MergeChangeTracking {

        @Test
        @DisplayName("setMerged marks both plots as dirty")
        void setMerged_marksBothDirty() {
            PlotId id1 = new PlotId(0, 0);
            PlotId id2 = new PlotId(1, 0);
            UUID owner = UUID.randomUUID();
            plotWorld.putPlot(id1, new Plot("testworld", id1).withOwner(owner, "Player"));
            plotWorld.putPlot(id2, new Plot("testworld", id2).withOwner(owner, "Player"));
            plotWorld.clearChanges();

            plotWorld.setMerged(id1, PlotMergeDirection.EAST, true);

            assertThat(plotWorld.hasChanges()).isTrue();
            assertThat(plotWorld.getDirtyPlots()).contains(id1, id2);
        }

        @Test
        @DisplayName("clearMergedConnections marks affected plots as dirty")
        void clearMergedConnections_marksDirty() {
            PlotId id1 = new PlotId(0, 0);
            PlotId id2 = new PlotId(1, 0);
            UUID owner = UUID.randomUUID();
            plotWorld.putPlot(id1, new Plot("testworld", id1).withOwner(owner, "Player"));
            plotWorld.putPlot(id2, new Plot("testworld", id2).withOwner(owner, "Player"));
            plotWorld.setMerged(id1, PlotMergeDirection.EAST, true);
            plotWorld.clearChanges();

            plotWorld.clearMergedConnections(id1);

            assertThat(plotWorld.hasChanges()).isTrue();
        }
    }

    @Nested
    @DisplayName("Basic Operations")
    class BasicOperations {

        @Test
        @DisplayName("getPlot returns null for non-existent plot")
        void getPlot_nonExistent_returnsNull() {
            assertThat(plotWorld.getPlot(new PlotId(99, 99))).isNull();
        }

        @Test
        @DisplayName("getPlot returns stored plot")
        void getPlot_existing_returnsPlot() {
            PlotId id = new PlotId(0, 0);
            Plot plot = new Plot("testworld", id).withOwner(UUID.randomUUID(), "Player");
            plotWorld.putPlot(id, plot);

            assertThat(plotWorld.getPlot(id)).isEqualTo(plot);
        }

        @Test
        @DisplayName("findNextFreePlotId returns origin for empty world")
        void findNextFreePlotId_emptyWorld_returnsOrigin() {
            PlotId free = plotWorld.findNextFreePlotId();

            assertThat(free).isEqualTo(new PlotId(0, 0));
        }

        @Test
        @DisplayName("findNextFreePlotId skips claimed plots")
        void findNextFreePlotId_skipsClaimedPlots() {
            plotWorld.claimPlot(new PlotId(0, 0), UUID.randomUUID(), "P1");

            PlotId free = plotWorld.findNextFreePlotId();

            assertThat(free).isNotEqualTo(new PlotId(0, 0));
        }

        @Test
        @DisplayName("countOwnedPlots returns correct count")
        void countOwnedPlots_returnsCorrectCount() {
            UUID owner = UUID.randomUUID();
            plotWorld.claimPlot(new PlotId(0, 0), owner, "Player");
            plotWorld.claimPlot(new PlotId(1, 0), owner, "Player");
            plotWorld.claimPlot(new PlotId(2, 0), UUID.randomUUID(), "Other");

            assertThat(plotWorld.countOwnedPlots(owner)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Coordinate Calculations (getPlotIdAt)")
    class CoordinateCalculations {
        // Default config: plotSize=35, roadSize=7, totalSize=42
        // Plot 0,0 covers blocks 0-34 (x and z)
        // Road covers blocks 35-41
        // Plot 1,0 covers blocks 42-76

        @Test
        @DisplayName("returns correct id for origin plot center")
        void originPlotCenter() {
            assertThat(plotWorld.getPlotIdAt(17, 17)).isEqualTo(new PlotId(0, 0));
        }

        @Test
        @DisplayName("returns correct id for origin plot corner")
        void originPlotCorner() {
            assertThat(plotWorld.getPlotIdAt(0, 0)).isEqualTo(new PlotId(0, 0));
            assertThat(plotWorld.getPlotIdAt(34, 34)).isEqualTo(new PlotId(0, 0));
        }

        @Test
        @DisplayName("returns null for road between plots")
        void roadBetweenPlots() {
            // Road is at x=35-41 (between plot 0 and plot 1)
            assertThat(plotWorld.getPlotIdAt(35, 17)).isNull();
            assertThat(plotWorld.getPlotIdAt(40, 17)).isNull();
        }

        @Test
        @DisplayName("returns correct id for positive plot")
        void positivePlot() {
            // Plot 1,0 starts at x=42
            assertThat(plotWorld.getPlotIdAt(42, 0)).isEqualTo(new PlotId(1, 0));
            assertThat(plotWorld.getPlotIdAt(50, 17)).isEqualTo(new PlotId(1, 0));
        }

        @Test
        @DisplayName("returns correct id for negative plot")
        void negativePlot() {
            // Plot -1,0 is at x = -42 to -8 (roughly)
            assertThat(plotWorld.getPlotIdAt(-20, 17)).isEqualTo(new PlotId(-1, 0));
        }

        @Test
        @DisplayName("returns null for road intersection")
        void roadIntersection() {
            // Road intersection at x=35-41, z=35-41
            assertThat(plotWorld.getPlotIdAt(38, 38)).isNull();
        }

        @Test
        @DisplayName("returns correct id for far positive plot")
        void farPositivePlot() {
            // Plot 2,3 starts at x=84, z=126
            assertThat(plotWorld.getPlotIdAt(100, 140)).isEqualTo(new PlotId(2, 3));
        }

        @Test
        @DisplayName("returns correct id for far negative plot")
        void farNegativePlot() {
            assertThat(plotWorld.getPlotIdAt(-100, -100)).isEqualTo(new PlotId(-3, -3));
        }
    }

    @Nested
    @DisplayName("Plot Bounds")
    class PlotBoundsTests {

        @Test
        @DisplayName("getPlotBounds returns correct bounds for origin")
        void originBounds() {
            PlotBounds bounds = plotWorld.getPlotBounds(new PlotId(0, 0));

            assertThat(bounds.minX()).isEqualTo(0);
            assertThat(bounds.maxX()).isEqualTo(34);
            assertThat(bounds.minZ()).isEqualTo(0);
            assertThat(bounds.maxZ()).isEqualTo(34);
        }

        @Test
        @DisplayName("getPlotBounds returns correct bounds for positive plot")
        void positivePlotBounds() {
            PlotBounds bounds = plotWorld.getPlotBounds(new PlotId(1, 1));

            assertThat(bounds.minX()).isEqualTo(42);
            assertThat(bounds.maxX()).isEqualTo(76);
            assertThat(bounds.minZ()).isEqualTo(42);
            assertThat(bounds.maxZ()).isEqualTo(76);
        }

        @Test
        @DisplayName("getPlotBounds returns correct bounds for negative plot")
        void negativePlotBounds() {
            PlotBounds bounds = plotWorld.getPlotBounds(new PlotId(-1, -1));

            assertThat(bounds.minX()).isEqualTo(-42);
            assertThat(bounds.maxX()).isEqualTo(-8);
            assertThat(bounds.minZ()).isEqualTo(-42);
            assertThat(bounds.maxZ()).isEqualTo(-8);
        }
    }

    @Nested
    @DisplayName("Merge Logic")
    class MergeLogic {

        @Test
        @DisplayName("isMerged returns false for unclaimed plots")
        void isMerged_unclaimed_returnsFalse() {
            assertThat(plotWorld.isMerged(new PlotId(0, 0), PlotMergeDirection.EAST)).isFalse();
        }

        @Test
        @DisplayName("isMerged returns false when not merged")
        void isMerged_notMerged_returnsFalse() {
            UUID owner = UUID.randomUUID();
            plotWorld.claimPlot(new PlotId(0, 0), owner, "Player");
            plotWorld.claimPlot(new PlotId(1, 0), owner, "Player");

            assertThat(plotWorld.isMerged(new PlotId(0, 0), PlotMergeDirection.EAST)).isFalse();
        }

        @Test
        @DisplayName("setMerged and isMerged work correctly")
        void setMerged_isMerged_works() {
            UUID owner = UUID.randomUUID();
            plotWorld.claimPlot(new PlotId(0, 0), owner, "Player");
            plotWorld.claimPlot(new PlotId(1, 0), owner, "Player");

            plotWorld.setMerged(new PlotId(0, 0), PlotMergeDirection.EAST, true);

            assertThat(plotWorld.isMerged(new PlotId(0, 0), PlotMergeDirection.EAST)).isTrue();
            assertThat(plotWorld.isMerged(new PlotId(1, 0), PlotMergeDirection.WEST)).isTrue();
        }

        @Test
        @DisplayName("setMerged returns false for null plot")
        void setMerged_nullPlot_returnsFalse() {
            assertThat(plotWorld.setMerged(new PlotId(0, 0), PlotMergeDirection.EAST, true)).isFalse();
        }

        @Test
        @DisplayName("setMerged can unmerge plots")
        void setMerged_canUnmerge() {
            UUID owner = UUID.randomUUID();
            plotWorld.claimPlot(new PlotId(0, 0), owner, "Player");
            plotWorld.claimPlot(new PlotId(1, 0), owner, "Player");
            plotWorld.setMerged(new PlotId(0, 0), PlotMergeDirection.EAST, true);

            plotWorld.setMerged(new PlotId(0, 0), PlotMergeDirection.EAST, false);

            assertThat(plotWorld.isMerged(new PlotId(0, 0), PlotMergeDirection.EAST)).isFalse();
        }

        @Test
        @DisplayName("getMergeGroup returns single plot when not merged")
        void getMergeGroup_notMerged_returnsSingle() {
            plotWorld.claimPlot(new PlotId(0, 0), UUID.randomUUID(), "Player");

            var group = plotWorld.getMergeGroup(new PlotId(0, 0));

            assertThat(group).containsExactly(new PlotId(0, 0));
        }

        @Test
        @DisplayName("getMergeGroup returns empty for null plot")
        void getMergeGroup_nullPlot_returnsEmpty() {
            var group = plotWorld.getMergeGroup(new PlotId(99, 99));

            assertThat(group).isEmpty();
        }

        @Test
        @DisplayName("getMergeGroup returns all merged plots")
        void getMergeGroup_merged_returnsAll() {
            UUID owner = UUID.randomUUID();
            plotWorld.claimPlot(new PlotId(0, 0), owner, "P");
            plotWorld.claimPlot(new PlotId(1, 0), owner, "P");
            plotWorld.claimPlot(new PlotId(2, 0), owner, "P");
            plotWorld.setMerged(new PlotId(0, 0), PlotMergeDirection.EAST, true);
            plotWorld.setMerged(new PlotId(1, 0), PlotMergeDirection.EAST, true);

            var group = plotWorld.getMergeGroup(new PlotId(1, 0));

            assertThat(group).containsExactlyInAnyOrder(
                    new PlotId(0, 0),
                    new PlotId(1, 0),
                    new PlotId(2, 0)
            );
        }

        @Test
        @DisplayName("getMergeRoot returns smallest coordinate plot")
        void getMergeRoot_returnsSmallest() {
            UUID owner = UUID.randomUUID();
            plotWorld.claimPlot(new PlotId(0, 0), owner, "P");
            plotWorld.claimPlot(new PlotId(1, 0), owner, "P");
            plotWorld.claimPlot(new PlotId(0, 1), owner, "P");
            plotWorld.setMerged(new PlotId(0, 0), PlotMergeDirection.EAST, true);
            plotWorld.setMerged(new PlotId(0, 0), PlotMergeDirection.SOUTH, true);

            assertThat(plotWorld.getMergeRoot(new PlotId(1, 0))).isEqualTo(new PlotId(0, 0));
            assertThat(plotWorld.getMergeRoot(new PlotId(0, 1))).isEqualTo(new PlotId(0, 0));
        }

        @Test
        @DisplayName("getAdjacentPlotId returns correct neighbor")
        void getAdjacentPlotId_returnsCorrectNeighbor() {
            PlotId origin = new PlotId(5, 5);

            assertThat(plotWorld.getAdjacentPlotId(origin, PlotMergeDirection.NORTH))
                    .isEqualTo(new PlotId(5, 4));
            assertThat(plotWorld.getAdjacentPlotId(origin, PlotMergeDirection.EAST))
                    .isEqualTo(new PlotId(6, 5));
            assertThat(plotWorld.getAdjacentPlotId(origin, PlotMergeDirection.SOUTH))
                    .isEqualTo(new PlotId(5, 6));
            assertThat(plotWorld.getAdjacentPlotId(origin, PlotMergeDirection.WEST))
                    .isEqualTo(new PlotId(4, 5));
        }

        @Test
        @DisplayName("clearMergedConnections removes all merge directions")
        void clearMergedConnections_removesAll() {
            UUID owner = UUID.randomUUID();
            plotWorld.claimPlot(new PlotId(0, 0), owner, "P");
            plotWorld.claimPlot(new PlotId(1, 0), owner, "P");
            plotWorld.claimPlot(new PlotId(0, 1), owner, "P");
            plotWorld.setMerged(new PlotId(0, 0), PlotMergeDirection.EAST, true);
            plotWorld.setMerged(new PlotId(0, 0), PlotMergeDirection.SOUTH, true);

            plotWorld.clearMergedConnections(new PlotId(0, 0));

            assertThat(plotWorld.isMerged(new PlotId(0, 0), PlotMergeDirection.EAST)).isFalse();
            assertThat(plotWorld.isMerged(new PlotId(0, 0), PlotMergeDirection.SOUTH)).isFalse();
        }
    }

    @Nested
    @DisplayName("Merged Plot Bounds")
    class MergedPlotBounds {

        @Test
        @DisplayName("getMergedPlotBounds returns single plot bounds when not merged")
        void notMerged_returnsSingleBounds() {
            plotWorld.claimPlot(new PlotId(0, 0), UUID.randomUUID(), "P");

            PlotBounds bounds = plotWorld.getMergedPlotBounds(new PlotId(0, 0));
            PlotBounds single = plotWorld.getPlotBounds(new PlotId(0, 0));

            assertThat(bounds).isEqualTo(single);
        }

        @Test
        @DisplayName("getMergedPlotBounds expands for merged plots")
        void merged_expandsBounds() {
            UUID owner = UUID.randomUUID();
            plotWorld.claimPlot(new PlotId(0, 0), owner, "P");
            plotWorld.claimPlot(new PlotId(1, 0), owner, "P");
            plotWorld.setMerged(new PlotId(0, 0), PlotMergeDirection.EAST, true);

            PlotBounds bounds = plotWorld.getMergedPlotBounds(new PlotId(0, 0));

            // Should span from plot 0,0 to plot 1,0
            assertThat(bounds.minX()).isEqualTo(0);
            assertThat(bounds.maxX()).isEqualTo(76); // Plot 1,0 maxX
            assertThat(bounds.minZ()).isEqualTo(0);
            assertThat(bounds.maxZ()).isEqualTo(34);
        }
    }
}

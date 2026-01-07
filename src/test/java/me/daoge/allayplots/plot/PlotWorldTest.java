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
}

package me.daoge.allayplots.storage;

import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import me.daoge.allayplots.plot.PlotMergeDirection;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("H2 Plot Storage - Incremental Save")
class H2PlotStorageTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(H2PlotStorageTest.class);

    private TestH2Storage storage;

    /**
     * Test-specific H2 storage that uses in-memory database.
     */
    private static class TestH2Storage extends AbstractDatabasePlotStorage {
        private final String dbName;

        TestH2Storage(String dbName) {
            super(Path.of(System.getProperty("java.io.tmpdir")), LOGGER);
            this.dbName = dbName;
        }

        @Override
        protected String getDatabaseName() {
            return "H2-Test";
        }

        @Override
        protected String getDriverClassName() {
            return "org.h2.Driver";
        }

        @Override
        protected String getJdbcUrl() {
            // Use in-memory database with unique name per test
            return "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        }
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        // Create unique database for each test
        storage = new TestH2Storage("test_" + testInfo.getDisplayName().hashCode());
    }

    @Nested
    @DisplayName("supportsIncrementalSave")
    class SupportsIncrementalSave {

        @Test
        @DisplayName("returns true for database storage")
        void returnsTrue() {
            assertThat(storage.supportsIncrementalSave()).isTrue();
        }
    }

    @Nested
    @DisplayName("saveIncremental - Insert")
    class SaveIncrementalInsert {

        @Test
        @DisplayName("inserts new plot with owner")
        void insertsNewPlot() {
            UUID owner = UUID.randomUUID();
            PlotId id = new PlotId(0, 0);
            Plot plot = new Plot("world", id).withOwner(owner, "TestPlayer");

            Map<String, Map<PlotId, Plot>> dirty = Map.of("world", Map.of(id, plot));
            storage.saveIncremental(dirty, Map.of());

            Map<String, Map<PlotId, Plot>> loaded = storage.load();
            assertThat(loaded).containsKey("world");
            assertThat(loaded.get("world")).containsKey(id);

            Plot loadedPlot = loaded.get("world").get(id);
            assertThat(loadedPlot.getOwner()).isEqualTo(owner);
            assertThat(loadedPlot.getOwnerName()).isEqualTo("TestPlayer");
        }

        @Test
        @DisplayName("inserts plot with trusted players")
        void insertsWithTrusted() {
            UUID owner = UUID.randomUUID();
            UUID trusted1 = UUID.randomUUID();
            UUID trusted2 = UUID.randomUUID();
            PlotId id = new PlotId(1, 1);
            Plot plot = new Plot("world", id)
                    .withOwner(owner, "Owner")
                    .withTrustedAdded(trusted1)
                    .withTrustedAdded(trusted2);

            storage.saveIncremental(Map.of("world", Map.of(id, plot)), Map.of());

            Plot loaded = storage.load().get("world").get(id);
            assertThat(loaded.getTrusted()).containsExactlyInAnyOrder(trusted1, trusted2);
        }

        @Test
        @DisplayName("inserts plot with denied players")
        void insertsWithDenied() {
            UUID owner = UUID.randomUUID();
            UUID denied = UUID.randomUUID();
            PlotId id = new PlotId(2, 2);
            Plot plot = new Plot("world", id)
                    .withOwner(owner, "Owner")
                    .withDeniedAdded(denied);

            storage.saveIncremental(Map.of("world", Map.of(id, plot)), Map.of());

            Plot loaded = storage.load().get("world").get(id);
            assertThat(loaded.getDenied()).contains(denied);
        }

        @Test
        @DisplayName("inserts plot with flags")
        void insertsWithFlags() {
            PlotId id = new PlotId(3, 3);
            Plot plot = new Plot("world", id)
                    .withOwner(UUID.randomUUID(), "Owner")
                    .withFlagRaw("pvp", "false")
                    .withFlagRaw("build", "true");

            storage.saveIncremental(Map.of("world", Map.of(id, plot)), Map.of());

            Plot loaded = storage.load().get("world").get(id);
            assertThat(loaded.getFlags()).containsEntry("pvp", "false");
            assertThat(loaded.getFlags()).containsEntry("build", "true");
        }

        @Test
        @DisplayName("inserts plot with merged directions")
        void insertsWithMerged() {
            PlotId id = new PlotId(4, 4);
            Plot plot = new Plot("world", id)
                    .withOwner(UUID.randomUUID(), "Owner")
                    .withMergedDirectionAdded(PlotMergeDirection.EAST)
                    .withMergedDirectionAdded(PlotMergeDirection.SOUTH);

            storage.saveIncremental(Map.of("world", Map.of(id, plot)), Map.of());

            Plot loaded = storage.load().get("world").get(id);
            assertThat(loaded.getMergedDirections())
                    .containsExactlyInAnyOrder(PlotMergeDirection.EAST, PlotMergeDirection.SOUTH);
        }

        @Test
        @DisplayName("inserts plot marked as home")
        void insertsWithHome() {
            PlotId id = new PlotId(5, 5);
            Plot plot = new Plot("world", id)
                    .withOwner(UUID.randomUUID(), "Owner")
                    .withHome(true);

            storage.saveIncremental(Map.of("world", Map.of(id, plot)), Map.of());

            Plot loaded = storage.load().get("world").get(id);
            assertThat(loaded.isHome()).isTrue();
        }
    }

    @Nested
    @DisplayName("saveIncremental - Update")
    class SaveIncrementalUpdate {

        @Test
        @DisplayName("updates existing plot")
        void updatesExistingPlot() {
            UUID owner = UUID.randomUUID();
            PlotId id = new PlotId(0, 0);
            Plot original = new Plot("world", id).withOwner(owner, "OriginalName");
            storage.saveIncremental(Map.of("world", Map.of(id, original)), Map.of());

            // Update the plot
            Plot updated = new Plot("world", id).withOwner(owner, "UpdatedName");
            storage.saveIncremental(Map.of("world", Map.of(id, updated)), Map.of());

            Plot loaded = storage.load().get("world").get(id);
            assertThat(loaded.getOwnerName()).isEqualTo("UpdatedName");
        }

        @Test
        @DisplayName("updates trusted list correctly")
        void updatesTrustedList() {
            UUID owner = UUID.randomUUID();
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            PlotId id = new PlotId(1, 0);

            // Initial save with player1 trusted
            Plot original = new Plot("world", id)
                    .withOwner(owner, "Owner")
                    .withTrustedAdded(player1);
            storage.saveIncremental(Map.of("world", Map.of(id, original)), Map.of());

            // Update: remove player1, add player2
            Plot updated = new Plot("world", id)
                    .withOwner(owner, "Owner")
                    .withTrustedAdded(player2);
            storage.saveIncremental(Map.of("world", Map.of(id, updated)), Map.of());

            Plot loaded = storage.load().get("world").get(id);
            assertThat(loaded.getTrusted()).containsExactly(player2);
            assertThat(loaded.getTrusted()).doesNotContain(player1);
        }
    }

    @Nested
    @DisplayName("saveIncremental - Delete")
    class SaveIncrementalDelete {

        @Test
        @DisplayName("deletes removed plot")
        void deletesRemovedPlot() {
            PlotId id = new PlotId(0, 0);
            Plot plot = new Plot("world", id).withOwner(UUID.randomUUID(), "Owner");
            storage.saveIncremental(Map.of("world", Map.of(id, plot)), Map.of());

            // Verify it exists
            assertThat(storage.load().get("world")).containsKey(id);

            // Delete it
            storage.saveIncremental(Map.of(), Map.of("world", Set.of(id)));

            // Verify it's gone
            Map<String, Map<PlotId, Plot>> loaded = storage.load();
            assertThat(loaded.getOrDefault("world", Map.of())).doesNotContainKey(id);
        }

        @Test
        @DisplayName("deletes plot with all related data")
        void deletesAllRelatedData() {
            UUID owner = UUID.randomUUID();
            PlotId id = new PlotId(1, 1);
            Plot plot = new Plot("world", id)
                    .withOwner(owner, "Owner")
                    .withTrustedAdded(UUID.randomUUID())
                    .withDeniedAdded(UUID.randomUUID())
                    .withFlagRaw("pvp", "false")
                    .withMergedDirectionAdded(PlotMergeDirection.NORTH);
            storage.saveIncremental(Map.of("world", Map.of(id, plot)), Map.of());

            // Delete
            storage.saveIncremental(Map.of(), Map.of("world", Set.of(id)));

            // Verify completely removed
            Map<String, Map<PlotId, Plot>> loaded = storage.load();
            assertThat(loaded.getOrDefault("world", Map.of())).doesNotContainKey(id);
        }
    }

    @Nested
    @DisplayName("saveIncremental - Mixed Operations")
    class SaveIncrementalMixed {

        @Test
        @DisplayName("handles insert and delete in same batch")
        void handlesInsertAndDelete() {
            PlotId toDelete = new PlotId(0, 0);
            PlotId toInsert = new PlotId(1, 1);

            // Setup: create plot to delete
            storage.saveIncremental(
                    Map.of("world", Map.of(toDelete, new Plot("world", toDelete).withOwner(UUID.randomUUID(), "ToDelete"))),
                    Map.of()
            );

            // Batch: delete one, insert another
            Plot newPlot = new Plot("world", toInsert).withOwner(UUID.randomUUID(), "NewPlot");
            storage.saveIncremental(
                    Map.of("world", Map.of(toInsert, newPlot)),
                    Map.of("world", Set.of(toDelete))
            );

            Map<String, Map<PlotId, Plot>> loaded = storage.load();
            assertThat(loaded.get("world")).doesNotContainKey(toDelete);
            assertThat(loaded.get("world")).containsKey(toInsert);
        }

        @Test
        @DisplayName("handles multiple worlds")
        void handlesMultipleWorlds() {
            PlotId id = new PlotId(0, 0);
            Plot plot1 = new Plot("world1", id).withOwner(UUID.randomUUID(), "Player1");
            Plot plot2 = new Plot("world2", id).withOwner(UUID.randomUUID(), "Player2");

            Map<String, Map<PlotId, Plot>> dirty = new HashMap<>();
            dirty.put("world1", Map.of(id, plot1));
            dirty.put("world2", Map.of(id, plot2));
            storage.saveIncremental(dirty, Map.of());

            Map<String, Map<PlotId, Plot>> loaded = storage.load();
            assertThat(loaded).containsKeys("world1", "world2");
            assertThat(loaded.get("world1").get(id).getOwnerName()).isEqualTo("Player1");
            assertThat(loaded.get("world2").get(id).getOwnerName()).isEqualTo("Player2");
        }

        @Test
        @DisplayName("empty changes does nothing")
        void emptyChangesDoesNothing() {
            PlotId id = new PlotId(0, 0);
            Plot plot = new Plot("world", id).withOwner(UUID.randomUUID(), "Owner");
            storage.saveIncremental(Map.of("world", Map.of(id, plot)), Map.of());

            // Save with empty changes
            storage.saveIncremental(Map.of(), Map.of());

            // Verify data unchanged
            assertThat(storage.load().get("world")).containsKey(id);
        }
    }
}

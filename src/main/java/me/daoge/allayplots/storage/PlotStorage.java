package me.daoge.allayplots.storage;

import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import java.util.Map;
import java.util.Set;

public interface PlotStorage {
    Map<String, Map<PlotId, Plot>> load();

    void save(Map<String, Map<PlotId, Plot>> worlds);

    /**
     * Incrementally save only the changed plots.
     *
     * @param dirtyPlots  map of world name to plots that were modified (upsert)
     * @param deletedPlots map of world name to plot IDs that were deleted
     */
    default void saveIncremental(
            Map<String, Map<PlotId, Plot>> dirtyPlots,
            Map<String, Set<PlotId>> deletedPlots
    ) {
        // Default implementation: fall back to full save if not overridden
        // This requires the caller to provide all plots, not just dirty ones
        // Subclasses should override for true incremental behavior
        save(dirtyPlots);
    }

    /**
     * Returns true if this storage supports efficient incremental saves.
     */
    default boolean supportsIncrementalSave() {
        return false;
    }
}

package me.daoge.allayplots.plot;

import me.daoge.allayplots.config.PlotWorldConfig;
import me.daoge.allayplots.config.PluginConfig;
import me.daoge.allayplots.storage.PlotStorage;
import org.allaymc.api.math.MathUtils;
import org.allaymc.api.math.location.Location3dc;
import org.allaymc.api.player.Player;
import org.allaymc.api.server.Server;
import org.allaymc.api.world.Dimension;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlotService {
    private final PluginConfig config;
    private final PlotStorage storage;
    private final Logger logger;
    private final Map<String, PlotWorld> worlds = new ConcurrentHashMap<>();
    private final Map<UUID, String> ownerNames = new ConcurrentHashMap<>();
    private final Set<UUID> ownerNameSynced = ConcurrentHashMap.newKeySet();

    public PlotService(PluginConfig config, PlotStorage storage, Logger logger) {
        this.config = config;
        this.storage = storage;
        this.logger = logger;
    }

    public void load() {
        Map<String, Map<PlotId, Plot>> stored = storage.load();
        for (Map.Entry<String, PlotWorldConfig> entry : config.worlds().entrySet()) {
            PlotWorld world = new PlotWorld(entry.getValue());
            Map<PlotId, Plot> worldPlots = stored.get(entry.getKey());
            if (worldPlots != null) {
                world.getPlots().putAll(worldPlots);
                for (Plot plot : worldPlots.values()) {
                    cacheOwnerName(plot);
                }
            }
            worlds.put(entry.getKey(), world);
        }
        if (!stored.isEmpty() && worlds.isEmpty()) {
            logger.warn("Plot data exists but no plot worlds are configured.");
        }
    }

    public void save() {
        storage.save(worlds);
    }

    public int worldCount() {
        return worlds.size();
    }

    public PlotWorld getPlotWorld(Dimension dimension) {
        if (dimension == null || dimension.getWorld() == null) {
            return null;
        }
        return worlds.get(dimension.getWorld().getName());
    }

    public PlotWorld getPlotWorld(String worldName) {
        return worlds.get(worldName);
    }

    public PlotLocation resolvePlot(Location3dc location) {
        PlotWorld world = getPlotWorld(location.dimension());
        if (world == null) {
            return null;
        }
        var blockPos = MathUtils.floor(location);
        PlotId id = world.getPlotIdAt(blockPos.x(), blockPos.z());
        if (id == null) {
            return null;
        }
        return new PlotLocation(world, id, world.getPlot(id));
    }

    public Plot claimPlot(PlotWorld world, PlotId id, UUID owner, String ownerName) {
        Plot plot = world.claimPlot(id, owner, ownerName);
        cacheOwnerName(plot);
        return plot;
    }

    public void deletePlot(PlotWorld world, PlotId id) {
        world.removePlot(id);
    }

    public int countOwnedPlots(PlotWorld world, UUID owner) {
        return world.countOwnedPlots(owner);
    }

    public PlotId findNextFreePlotId(PlotWorld world) {
        return world.findNextFreePlotId();
    }

    public PlotLocation findHomePlot(UUID owner) {
        PlotLocation fallback = null;
        for (PlotWorld world : worlds.values()) {
            for (Plot plot : world.getPlots().values()) {
                if (!plot.isOwner(owner)) {
                    continue;
                }
                PlotLocation location = new PlotLocation(world, plot.getId(), plot);
                if (plot.isHome()) {
                    return location;
                }
                if (fallback == null) {
                    fallback = location;
                }
            }
        }
        return fallback;
    }

    public boolean setHomePlot(UUID owner, PlotWorld world, PlotId id) {
        Plot plot = world.getPlot(id);
        if (plot == null || !plot.isOwner(owner)) {
            return false;
        }
        for (PlotWorld plotWorld : worlds.values()) {
            for (Plot candidate : plotWorld.getPlots().values()) {
                if (candidate.isOwner(owner) && candidate.isHome()) {
                    candidate.setHome(false);
                }
            }
        }
        plot.setHome(true);
        return true;
    }

    public String resolvePlayerName(UUID uuid) {
        if (uuid == null) {
            return "";
        }
        Player player = Server.getInstance().getPlayerManager().getPlayers().get(uuid);
        if (player == null) {
            String cached = ownerNames.get(uuid);
            return cached != null ? cached : uuid.toString();
        }
        String name = player.getOriginName();
        ownerNames.put(uuid, name);
        syncOwnerName(uuid, name);
        return name;
    }

    public Map<String, PlotWorld> snapshotWorlds() {
        return new HashMap<>(worlds);
    }

    public record PlotLocation(PlotWorld world, PlotId id, Plot plot) {
    }

    private void cacheOwnerName(Plot plot) {
        if (plot == null || plot.getOwner() == null) {
            return;
        }
        String name = plot.getOwnerName();
        if (name != null && !name.isBlank()) {
            ownerNames.put(plot.getOwner(), name);
        }
    }

    private void syncOwnerName(UUID owner, String name) {
        if (name == null || name.isBlank() || !ownerNameSynced.add(owner)) {
            return;
        }
        for (PlotWorld world : worlds.values()) {
            for (Plot plot : world.getPlots().values()) {
                if (owner.equals(plot.getOwner()) && (plot.getOwnerName() == null || plot.getOwnerName().isBlank())) {
                    plot.setOwnerName(name);
                }
            }
        }
    }
}

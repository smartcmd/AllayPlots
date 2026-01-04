package me.daoge.allayplots.storage;

import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import me.daoge.allayplots.plot.PlotMergeDirection;
import me.daoge.allayplots.plot.PlotWorld;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class AbstractDatabasePlotStorage implements PlotStorage {
    private static final String CREATE_PLOTS_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS plots (
                world_name VARCHAR(255) NOT NULL,
                plot_x INTEGER NOT NULL,
                plot_z INTEGER NOT NULL,
                owner VARCHAR(36),
                owner_name VARCHAR(255),
                home INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (world_name, plot_x, plot_z)
            )
            """;
    private static final String CREATE_TRUSTED_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS plot_trusted (
                world_name VARCHAR(255) NOT NULL,
                plot_x INTEGER NOT NULL,
                plot_z INTEGER NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                PRIMARY KEY (world_name, plot_x, plot_z, player_uuid)
            )
            """;
    private static final String CREATE_DENIED_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS plot_denied (
                world_name VARCHAR(255) NOT NULL,
                plot_x INTEGER NOT NULL,
                plot_z INTEGER NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                PRIMARY KEY (world_name, plot_x, plot_z, player_uuid)
            )
            """;
    private static final String CREATE_FLAGS_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS plot_flags (
                world_name VARCHAR(255) NOT NULL,
                plot_x INTEGER NOT NULL,
                plot_z INTEGER NOT NULL,
                flag_key VARCHAR(64) NOT NULL,
                flag_value VARCHAR(255) NOT NULL,
                PRIMARY KEY (world_name, plot_x, plot_z, flag_key)
            )
            """;
    private static final String CREATE_MERGED_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS plot_merged (
                world_name VARCHAR(255) NOT NULL,
                plot_x INTEGER NOT NULL,
                plot_z INTEGER NOT NULL,
                direction VARCHAR(16) NOT NULL,
                PRIMARY KEY (world_name, plot_x, plot_z, direction)
            )
            """;

    protected final Path dataFolder;
    protected final Logger logger;

    protected AbstractDatabasePlotStorage(Path dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        loadDriver();
        ensureDataFolder();
    }

    protected abstract String getDatabaseName();

    protected abstract String getDriverClassName();

    protected abstract String getJdbcUrl();

    @Override
    public Map<String, Map<PlotId, Plot>> load() {
        Map<String, Map<PlotId, Plot>> result = new HashMap<>();
        try (Connection connection = openConnection()) {
            initSchema(connection);
            loadPlots(connection, result);
            loadAccessList(connection, result, "plot_trusted", true);
            loadAccessList(connection, result, "plot_denied", false);
            loadFlags(connection, result);
            loadMerged(connection, result);
            pruneDefaults(result);
        } catch (SQLException ex) {
            logger.error("Failed to load plot data from {} storage.", getDatabaseName(), ex);
        }
        return result;
    }

    @Override
    public void save(Map<String, PlotWorld> worlds) {
        try (Connection connection = openConnection()) {
            initSchema(connection);
            connection.setAutoCommit(false);
            try {
                clearTables(connection);
                insertPlots(connection, worlds);
                insertAccessLists(connection, worlds, true);
                insertAccessLists(connection, worlds, false);
                insertFlags(connection, worlds);
                insertMerged(connection, worlds);
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                logger.error("Failed to save plot data to {} storage.", getDatabaseName(), ex);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            logger.error("Failed to open {} storage connection.", getDatabaseName(), ex);
        }
    }

    protected Connection openConnection() throws SQLException {
        return DriverManager.getConnection(getJdbcUrl());
    }

    private void loadDriver() {
        try {
            Class.forName(getDriverClassName());
        } catch (ClassNotFoundException ex) {
            logger.error("Database driver {} not found for {} storage.", getDriverClassName(), getDatabaseName(), ex);
        }
    }

    private void ensureDataFolder() {
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException ex) {
            logger.error("Failed to create data folder for {} storage.", getDatabaseName(), ex);
        }
    }

    private void initSchema(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(CREATE_PLOTS_TABLE_SQL);
            stmt.execute(CREATE_TRUSTED_TABLE_SQL);
            stmt.execute(CREATE_DENIED_TABLE_SQL);
            stmt.execute(CREATE_FLAGS_TABLE_SQL);
            stmt.execute(CREATE_MERGED_TABLE_SQL);
        }
    }

    private void clearTables(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("DELETE FROM plot_flags");
            stmt.executeUpdate("DELETE FROM plot_merged");
            stmt.executeUpdate("DELETE FROM plot_denied");
            stmt.executeUpdate("DELETE FROM plot_trusted");
            stmt.executeUpdate("DELETE FROM plots");
        }
    }

    private void loadPlots(Connection connection, Map<String, Map<PlotId, Plot>> result) throws SQLException {
        String sql = "SELECT world_name, plot_x, plot_z, owner, owner_name, home FROM plots";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String worldName = rs.getString("world_name");
                int plotX = rs.getInt("plot_x");
                int plotZ = rs.getInt("plot_z");
                PlotId id = new PlotId(plotX, plotZ);
                Plot plot = new Plot(worldName, id);
                String ownerRaw = rs.getString("owner");
                if (ownerRaw != null && !ownerRaw.isBlank()) {
                    try {
                        String ownerName = rs.getString("owner_name");
                        plot.setOwner(UUID.fromString(ownerRaw), ownerName == null || ownerName.isBlank() ? null : ownerName);
                    } catch (IllegalArgumentException ex) {
                        logger.warn("Invalid owner uuid {} for plot {} in {}", ownerRaw, id.asString(), worldName);
                    }
                }
                if (rs.getInt("home") == 1 && plot.isClaimed()) {
                    plot.setHome(true);
                }
                result.computeIfAbsent(worldName, key -> new HashMap<>()).put(id, plot);
            }
        }
    }

    private void loadAccessList(Connection connection, Map<String, Map<PlotId, Plot>> result, String table, boolean trusted)
            throws SQLException {
        String sql = "SELECT world_name, plot_x, plot_z, player_uuid FROM " + table;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Plot plot = resolvePlot(result, rs.getString("world_name"), rs.getInt("plot_x"), rs.getInt("plot_z"));
                if (plot == null) {
                    continue;
                }
                String raw = rs.getString("player_uuid");
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                try {
                    UUID uuid = UUID.fromString(raw);
                    if (trusted) {
                        plot.addTrusted(uuid);
                    } else {
                        plot.addDenied(uuid);
                    }
                } catch (IllegalArgumentException ex) {
                    logger.warn("Invalid {} uuid {} for plot {} in {}", trusted ? "trusted" : "denied", raw,
                            plot.getId().asString(), plot.getWorldName());
                }
            }
        }
    }

    private void loadFlags(Connection connection, Map<String, Map<PlotId, Plot>> result) throws SQLException {
        String sql = "SELECT world_name, plot_x, plot_z, flag_key, flag_value FROM plot_flags";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Plot plot = resolvePlot(result, rs.getString("world_name"), rs.getInt("plot_x"), rs.getInt("plot_z"));
                if (plot == null) {
                    continue;
                }
                String key = rs.getString("flag_key");
                String value = rs.getString("flag_value");
                if (key == null || key.isBlank() || value == null || value.isBlank()) {
                    continue;
                }
                plot.setFlagRaw(key, value);
            }
        }
    }

    private void loadMerged(Connection connection, Map<String, Map<PlotId, Plot>> result) throws SQLException {
        String sql = "SELECT world_name, plot_x, plot_z, direction FROM plot_merged";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Plot plot = resolvePlot(result, rs.getString("world_name"), rs.getInt("plot_x"), rs.getInt("plot_z"));
                if (plot == null) {
                    continue;
                }
                String raw = rs.getString("direction");
                PlotMergeDirection direction = PlotMergeDirection.fromString(raw);
                if (direction == null) {
                    if (raw != null && !raw.isBlank()) {
                        logger.warn("Invalid merge direction {} for plot {} in {}", raw, plot.getId().asString(),
                                plot.getWorldName());
                    }
                    continue;
                }
                plot.addMergedDirection(direction);
            }
        }
    }

    private Plot resolvePlot(Map<String, Map<PlotId, Plot>> result, String worldName, int plotX, int plotZ) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        Map<PlotId, Plot> worldPlots = result.get(worldName);
        if (worldPlots == null) {
            return null;
        }
        return worldPlots.get(new PlotId(plotX, plotZ));
    }

    private void pruneDefaults(Map<String, Map<PlotId, Plot>> result) {
        for (Map<PlotId, Plot> worldPlots : result.values()) {
            worldPlots.entrySet().removeIf(entry -> entry.getValue().isDefault());
        }
        result.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void insertPlots(Connection connection, Map<String, PlotWorld> worlds) throws SQLException {
        String sql = "INSERT INTO plots (world_name, plot_x, plot_z, owner, owner_name, home) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (PlotWorld world : worlds.values()) {
                String worldName = world.getConfig().worldName();
                for (Plot plot : world.getPlots().values()) {
                    if (plot.isDefault()) {
                        continue;
                    }
                    stmt.setString(1, worldName);
                    stmt.setInt(2, plot.getId().x());
                    stmt.setInt(3, plot.getId().z());
                    if (plot.getOwner() != null) {
                        stmt.setString(4, plot.getOwner().toString());
                    } else {
                        stmt.setNull(4, Types.VARCHAR);
                    }
                    String ownerName = plot.getOwnerName();
                    if (ownerName != null && !ownerName.isBlank()) {
                        stmt.setString(5, ownerName);
                    } else {
                        stmt.setNull(5, Types.VARCHAR);
                    }
                    stmt.setInt(6, plot.isHome() ? 1 : 0);
                    stmt.addBatch();
                }
            }
            stmt.executeBatch();
        }
    }

    private void insertAccessLists(Connection connection, Map<String, PlotWorld> worlds, boolean trusted) throws SQLException {
        String table = trusted ? "plot_trusted" : "plot_denied";
        String sql = "INSERT INTO " + table + " (world_name, plot_x, plot_z, player_uuid) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (PlotWorld world : worlds.values()) {
                String worldName = world.getConfig().worldName();
                for (Plot plot : world.getPlots().values()) {
                    if (plot.isDefault()) {
                        continue;
                    }
                    for (UUID uuid : trusted ? plot.getTrusted() : plot.getDenied()) {
                        stmt.setString(1, worldName);
                        stmt.setInt(2, plot.getId().x());
                        stmt.setInt(3, plot.getId().z());
                        stmt.setString(4, uuid.toString());
                        stmt.addBatch();
                    }
                }
            }
            stmt.executeBatch();
        }
    }

    private void insertFlags(Connection connection, Map<String, PlotWorld> worlds) throws SQLException {
        String sql = "INSERT INTO plot_flags (world_name, plot_x, plot_z, flag_key, flag_value) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (PlotWorld world : worlds.values()) {
                String worldName = world.getConfig().worldName();
                for (Plot plot : world.getPlots().values()) {
                    if (plot.isDefault() || plot.getFlags().isEmpty()) {
                        continue;
                    }
                    for (Map.Entry<String, String> entry : plot.getFlags().entrySet()) {
                        String value = entry.getValue();
                        if (value == null || value.isBlank()) {
                            continue;
                        }
                        stmt.setString(1, worldName);
                        stmt.setInt(2, plot.getId().x());
                        stmt.setInt(3, plot.getId().z());
                        stmt.setString(4, entry.getKey());
                        stmt.setString(5, value);
                        stmt.addBatch();
                    }
                }
            }
            stmt.executeBatch();
        }
    }

    private void insertMerged(Connection connection, Map<String, PlotWorld> worlds) throws SQLException {
        String sql = "INSERT INTO plot_merged (world_name, plot_x, plot_z, direction) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (PlotWorld world : worlds.values()) {
                String worldName = world.getConfig().worldName();
                for (Plot plot : world.getPlots().values()) {
                    if (plot.isDefault() || plot.getMergedDirections().isEmpty()) {
                        continue;
                    }
                    for (PlotMergeDirection direction : plot.getMergedDirections()) {
                        stmt.setString(1, worldName);
                        stmt.setInt(2, plot.getId().x());
                        stmt.setInt(3, plot.getId().z());
                        stmt.setString(4, direction.getLowerCaseName());
                        stmt.addBatch();
                    }
                }
            }
            stmt.executeBatch();
        }
    }
}

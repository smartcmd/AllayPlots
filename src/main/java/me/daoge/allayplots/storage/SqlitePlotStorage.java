package me.daoge.allayplots.storage;

import org.slf4j.Logger;

import java.nio.file.Path;

public final class SqlitePlotStorage extends AbstractDatabasePlotStorage {
    private static final String DB_FILE_NAME = "plots.db";

    public SqlitePlotStorage(Path dataFolder, Logger logger) {
        super(dataFolder, logger);
    }

    @Override
    protected String getDatabaseName() {
        return "SQLite";
    }

    @Override
    protected String getDriverClassName() {
        return "org.sqlite.JDBC";
    }

    @Override
    protected String getJdbcUrl() {
        return "jdbc:sqlite:" + dataFolder.resolve(DB_FILE_NAME).toAbsolutePath();
    }
}

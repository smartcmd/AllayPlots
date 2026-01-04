package me.daoge.allayplots.storage;

import org.slf4j.Logger;

import java.nio.file.Path;

public final class H2PlotStorage extends AbstractDatabasePlotStorage {
    private static final String DB_FILE_NAME = "plots";

    public H2PlotStorage(Path dataFolder, Logger logger) {
        super(dataFolder, logger);
    }

    @Override
    protected String getDatabaseName() {
        return "H2";
    }

    @Override
    protected String getDriverClassName() {
        return "org.h2.Driver";
    }

    @Override
    protected String getJdbcUrl() {
        return "jdbc:h2:" + dataFolder.resolve(DB_FILE_NAME).toAbsolutePath();
    }
}

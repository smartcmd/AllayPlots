package me.daoge.allayplots.storage;

import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import me.daoge.allayplots.plot.PlotWorld;

import java.util.Map;

public interface PlotStorage {
    Map<String, Map<PlotId, Plot>> load();

    void save(Map<String, PlotWorld> worlds);
}

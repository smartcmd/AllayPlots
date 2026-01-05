package me.daoge.allayplots.storage;

import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import java.util.Map;

public interface PlotStorage {
    Map<String, Map<PlotId, Plot>> load();

    void save(Map<String, Map<PlotId, Plot>> worlds);
}

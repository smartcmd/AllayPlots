package me.daoge.allayplots.event;

import lombok.Getter;
import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import me.daoge.allayplots.plot.PlotWorld;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.eventbus.event.player.PlayerEvent;

@Getter
public class PlotLeaveEvent extends PlayerEvent {
    private final PlotWorld world;
    private final PlotId plotId;
    // Null when leaving an unclaimed plot.
    private final Plot plot;

    public PlotLeaveEvent(EntityPlayer player, PlotWorld world, PlotId plotId, Plot plot) {
        super(player);
        this.world = world;
        this.plotId = plotId;
        this.plot = plot;
    }
}

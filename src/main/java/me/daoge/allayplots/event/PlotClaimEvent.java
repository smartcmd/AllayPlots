package me.daoge.allayplots.event;

import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import me.daoge.allayplots.plot.PlotWorld;
import lombok.Getter;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.eventbus.event.player.PlayerEvent;

@Getter
public class PlotClaimEvent extends PlayerEvent {
    private final PlotWorld world;
    private final PlotId plotId;
    private final Plot plot;
    private final boolean auto;

    public PlotClaimEvent(EntityPlayer player, PlotWorld world, PlotId plotId, Plot plot, boolean auto) {
        super(player);
        this.world = world;
        this.plotId = plotId;
        this.plot = plot;
        this.auto = auto;
    }
}

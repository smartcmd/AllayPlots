package me.daoge.allayplots.event;

import lombok.Getter;
import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotWorld;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.eventbus.event.player.PlayerEvent;

@Getter
public class PlotClaimEvent extends PlayerEvent {
    private final PlotWorld world;
    private final Plot plot;

    public PlotClaimEvent(EntityPlayer player, PlotWorld world, Plot plot) {
        super(player);
        this.world = world;
        this.plot = plot;
    }
}

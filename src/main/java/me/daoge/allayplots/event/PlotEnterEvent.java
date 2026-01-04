package me.daoge.allayplots.event;

import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import me.daoge.allayplots.plot.PlotWorld;
import lombok.Getter;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.eventbus.event.CancellableEvent;
import org.allaymc.api.eventbus.event.player.PlayerEvent;

@Getter
public class PlotEnterEvent extends PlayerEvent implements CancellableEvent {
    private final PlotWorld world;
    // Null when entering an unclaimed plot.
    private final Plot plot;

    public PlotEnterEvent(EntityPlayer player, PlotWorld world, Plot plot) {
        super(player);
        this.world = world;
        this.plot = plot;
    }
}

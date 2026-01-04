package me.daoge.allayplots.listener;

import me.daoge.allayplots.Permissions;
import me.daoge.allayplots.config.PluginConfig;
import me.daoge.allayplots.event.PlotEnterEvent;
import me.daoge.allayplots.event.PlotLeaveEvent;
import me.daoge.allayplots.i18n.LangKeys;
import me.daoge.allayplots.i18n.MessageService;
import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import me.daoge.allayplots.plot.PlotService;
import me.daoge.allayplots.plot.PlotWorld;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.eventbus.EventHandler;
import org.allaymc.api.eventbus.event.player.PlayerMoveEvent;

import java.util.Objects;

public final class PlotMovementListener {
    private final PlotService plotService;
    private final PluginConfig config;
    private final MessageService messages;

    public PlotMovementListener(PlotService plotService, PluginConfig config, MessageService messages) {
        this.plotService = plotService;
        this.config = config;
        this.messages = messages;
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        EntityPlayer player = event.getPlayer();
        var from = event.getFrom();
        var to = event.getTo();
        if (from == null || to == null) {
            return;
        }

        if (from.dimension() == to.dimension()
                && (int) Math.floor(from.x()) == (int) Math.floor(to.x())
                && (int) Math.floor(from.z()) == (int) Math.floor(to.z())) {
            return;
        }

        PlotService.PlotLocation fromPlot = plotService.resolvePlot(from);
        PlotService.PlotLocation toPlot = plotService.resolvePlot(to);
        PlotKey fromKey = PlotKey.from(fromPlot);
        PlotKey toKey = PlotKey.from(toPlot);

        if (Objects.equals(fromKey, toKey)) {
            return;
        }

        if (toPlot != null && !canEnter(player, toPlot.plot()) && !player.hasPermission(Permissions.ADMIN_BYPASS).asBoolean()) {
            event.setCancelled(true);
            sendMessage(player, messages.render(player, LangKeys.MESSAGE_ENTER_DENIED));
            return;
        }

        if (fromPlot != null) {
            new PlotLeaveEvent(player, fromPlot.world(), fromPlot.plot()).call();
            sendMessage(player, renderLeaveMessage(player, fromPlot.id()));
        }

        if (toPlot != null) {
            PlotEnterEvent enterEvent = new PlotEnterEvent(player, toPlot.world(), toPlot.plot());
            if (!enterEvent.call()) {
                event.setCancelled(true);
                return;
            }
            sendMessage(player, renderEnterMessage(player, toPlot.world(), toPlot.id(), toPlot.plot()));
        }
    }

    private boolean canEnter(EntityPlayer player, Plot plot) {
        if (plot == null || !plot.isClaimed()) {
            return true;
        }
        return plot.canEnter(player.getUniqueId());
    }

    private String renderEnterMessage(EntityPlayer player, PlotWorld world, PlotId plotId, Plot plot) {
        String ownerInfo = messages.renderInline(player, LangKeys.MESSAGE_UNCLAIMED_INFO);
        if (plot != null && plot.isClaimed()) {
            String ownerName = plotService.resolvePlayerName(plot.getOwner());
            ownerInfo = messages.renderInline(player, LangKeys.MESSAGE_CLAIMED_INFO, ownerName);
        }
        return messages.render(
                player,
                LangKeys.MESSAGE_ENTER,
                plotId.x(),
                plotId.z(),
                ownerInfo,
                world.getConfig().worldName()
        );
    }

    private String renderLeaveMessage(EntityPlayer player, PlotId plotId) {
        return messages.render(player, LangKeys.MESSAGE_LEAVE, plotId.x(), plotId.z());
    }

    private void sendMessage(EntityPlayer player, String message) {
        if (config.settings().useActionBar() && player.getController() != null) {
            player.getController().sendActionBar(message);
            return;
        }
        player.sendMessage(message);
    }

    private record PlotKey(String worldName, PlotId plotId) {
        static PlotKey from(PlotService.PlotLocation location) {
            if (location == null) {
                return null;
            }
            PlotId root = location.world().getMergeRoot(location.id());
            return new PlotKey(location.world().getConfig().worldName(), root);
        }
    }
}

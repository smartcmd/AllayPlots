package me.daoge.allayplots.listener;

import me.daoge.allayplots.Permissions;
import me.daoge.allayplots.config.PluginConfig;
import me.daoge.allayplots.i18n.LangKeys;
import me.daoge.allayplots.i18n.MessageService;
import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import me.daoge.allayplots.plot.PlotService;
import me.daoge.allayplots.plot.PlotWorld;
import org.allaymc.api.block.dto.Block;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.eventbus.EventHandler;
import org.allaymc.api.eventbus.event.block.BlockBreakEvent;
import org.allaymc.api.eventbus.event.block.BlockPlaceEvent;
import org.allaymc.api.eventbus.event.player.PlayerBucketEmptyEvent;
import org.allaymc.api.eventbus.event.player.PlayerBucketFillEvent;
import org.allaymc.api.eventbus.event.player.PlayerInteractBlockEvent;
import org.allaymc.api.world.Dimension;

public final class PlotProtectionListener {
    private final PlotService plotService;
    private final PluginConfig config;
    private final MessageService messages;

    public PlotProtectionListener(PlotService plotService, PluginConfig config, MessageService messages) {
        this.plotService = plotService;
        this.config = config;
        this.messages = messages;
    }

    @EventHandler
    private void onBlockBreak(BlockBreakEvent event) {
        if (!(event.getEntity() instanceof EntityPlayer player)) {
            return;
        }
        Block block = event.getBlock();
        if (shouldCancel(player, block.getDimension(), block.getPosition().x(), block.getPosition().z())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onBlockPlace(BlockPlaceEvent event) {
        if (event.getInteractInfo() == null) {
            return;
        }
        EntityPlayer player = event.getInteractInfo().player();
        if (player == null) {
            return;
        }
        Block block = event.getBlock();
        if (shouldCancel(player, block.getDimension(), block.getPosition().x(), block.getPosition().z())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onInteractBlock(PlayerInteractBlockEvent event) {
        var info = event.getInteractInfo();
        if (info == null) {
            return;
        }
        EntityPlayer player = event.getPlayer();
        if (shouldCancel(player, player.getDimension(), info.clickedBlockPos().x(), info.clickedBlockPos().z())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onBucketEmpty(PlayerBucketEmptyEvent event) {
        var info = event.getInteractInfo();
        if (info == null) {
            return;
        }
        EntityPlayer player = event.getPlayer();
        if (shouldCancel(player, player.getDimension(), info.clickedBlockPos().x(), info.clickedBlockPos().z())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onBucketFill(PlayerBucketFillEvent event) {
        var info = event.getInteractInfo();
        if (info == null) {
            return;
        }
        EntityPlayer player = event.getPlayer();
        if (shouldCancel(player, player.getDimension(), info.clickedBlockPos().x(), info.clickedBlockPos().z())) {
            event.setCancelled(true);
        }
    }

    private boolean shouldCancel(EntityPlayer player, Dimension dimension, int x, int z) {
        PlotWorld world = plotService.getPlotWorld(dimension);
        if (world == null) {
            return false;
        }
        if (player.hasPermission(Permissions.ADMIN_BYPASS).asBoolean()) {
            return false;
        }
        PlotId id = world.getPlotIdAt(x, z);
        if (id == null) {
            if (config.settings().protectRoads()) {
                player.sendMessage(messages.render(player, LangKeys.MESSAGE_BUILD_DENIED));
                return true;
            }
            return false;
        }
        Plot plot = world.getPlot(id);
        if (plot == null || !plot.canBuild(player.getUniqueId())) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_BUILD_DENIED));
            return true;
        }
        return false;
    }
}

package me.daoge.allayplots.listener;

import me.daoge.allayplots.Permissions;
import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotFlag;
import me.daoge.allayplots.plot.PlotService;
import org.allaymc.api.entity.Entity;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.entity.interfaces.EntityProjectile;
import org.allaymc.api.eventbus.EventHandler;
import org.allaymc.api.eventbus.event.entity.EntityDamageEvent;

public final class PlotDamageListener {
    private final PlotService plotService;

    public PlotDamageListener(PlotService plotService) {
        this.plotService = plotService;
    }

    @EventHandler
    private void onEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }
        Entity target = event.getEntity();
        var location = plotService.resolvePlot(target.getLocation());
        if (location == null) {
            return;
        }
        Plot plot = location.plot();
        if (plot == null || !plot.isClaimed()) {
            return;
        }

        if (target instanceof EntityPlayer victim) {
            if (!plot.getFlag(PlotFlag.DAMAGE) && !victim.hasPermission(Permissions.ADMIN_BYPASS).asBoolean()) {
                event.setCancelled(true);
                return;
            }
        }

        EntityPlayer attacker = resolveAttackingPlayer(event);
        if (attacker == null || attacker.hasPermission(Permissions.ADMIN_BYPASS).asBoolean()) {
            return;
        }

        if (target instanceof EntityPlayer victim) {
            if (attacker.getUniqueId().equals(victim.getUniqueId())) {
                return;
            }
            if (!plot.getFlag(PlotFlag.PVP)) {
                event.setCancelled(true);
            }
            return;
        }

        if (!plot.getFlag(PlotFlag.PVE)) {
            event.setCancelled(true);
        }
    }

    private EntityPlayer resolveAttackingPlayer(EntityDamageEvent event) {
        switch (event.getDamageContainer().getAttacker()) {
            case EntityPlayer player -> {
                return player;
            }
            case EntityProjectile projectile -> {
                if (projectile.getShooter() instanceof EntityPlayer player) {
                    return player;
                }
            }
            case null, default -> {
            }
        }
        return null;
    }
}

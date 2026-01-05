package me.daoge.allayplots.command;

import me.daoge.allayplots.Permissions;
import me.daoge.allayplots.config.PlotWorldConfig;
import me.daoge.allayplots.config.PluginConfig;
import me.daoge.allayplots.event.PlotClaimEvent;
import me.daoge.allayplots.i18n.LangKeys;
import me.daoge.allayplots.i18n.MessageService;
import me.daoge.allayplots.plot.*;
import org.allaymc.api.command.Command;
import org.allaymc.api.command.CommandResult;
import org.allaymc.api.command.SenderType;
import org.allaymc.api.command.tree.CommandContext;
import org.allaymc.api.command.tree.CommandTree;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.math.location.Location3d;
import org.allaymc.api.permission.OpPermissionCalculator;
import org.allaymc.api.server.Server;
import org.allaymc.economyapi.Account;
import org.allaymc.economyapi.Currency;
import org.allaymc.economyapi.EconomyAPI;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class PlotCommand extends Command {
    private final PlotService plotService;
    private final PluginConfig config;
    private final MessageService messages;

    private final EconomyAPI economyApi = EconomyAPI.getAPI();
    private final boolean economyEnabled;
    private final Currency currency;

    public PlotCommand(PlotService plotService, PluginConfig config, MessageService messages, Logger logger) {
        super("plot", LangKeys.COMMAND_PLOT_DESCRIPTION, Permissions.COMMAND_PLOT);
        this.plotService = plotService;
        this.config = config;
        this.messages = messages;

        this.economyEnabled = config.economy().enabled();
        this.currency = economyEnabled ? resolveCurrency(logger) : economyApi.getDefaultCurrency();

        aliases.addAll(List.of("plots", "p"));
        OpPermissionCalculator.NON_OP_PERMISSIONS.addAll(this.permissions);
    }

    @Override
    public void prepareCommandTree(CommandTree tree) {
        var root = tree.getRoot();
        root.key("help").exec(this::sendHelp);

        root.key("claim").exec(this::handleClaim, SenderType.PLAYER);
        root.key("auto").exec(this::handleAuto, SenderType.PLAYER);
        root.key("delete").exec(this::handleDelete, SenderType.PLAYER);

        root.key("merge").enumClass("direction", PlotMergeDirection.class).optional()
                .exec(this::handleMerge, SenderType.PLAYER);
        root.key("unmerge").enumClass("direction", PlotMergeDirection.class).optional()
                .exec(this::handleUnmerge, SenderType.PLAYER);

        root.key("info").exec(this::handleInfo, SenderType.PLAYER);

        root.key("home").playerTarget("player").optional()
                .exec(this::handleHomeOther, SenderType.PLAYER);

        root.key("sethome").exec(this::handleSetHome, SenderType.PLAYER);
        root.key("setowner").playerTarget("player")
                .exec(this::handleSetOwner, SenderType.PLAYER);

        root.key("trust").playerTarget("player").exec(this::handleTrust, SenderType.PLAYER);
        root.key("untrust").playerTarget("player").exec(this::handleUntrust, SenderType.PLAYER);
        root.key("deny").playerTarget("player").exec(this::handleDeny, SenderType.PLAYER);
        root.key("undeny").playerTarget("player").exec(this::handleUndeny, SenderType.PLAYER);

        root.key("flag").enumClass("flag", PlotFlag.class).optional()
                .str("value").optional()
                .exec(this::handleFlag, SenderType.PLAYER);
    }

    private CommandResult sendHelp(CommandContext context) {
        EntityPlayer player = context.getSender() instanceof EntityPlayer p ? p : null;
        context.addOutput(messages.renderInline(player, LangKeys.COMMAND_PLOT_HELP));
        return context.success();
    }

    private CommandResult handleClaim(CommandContext context, EntityPlayer player) {
        PlotContext pc = resolvePlotContext(context, player);
        if (pc == null) return context.fail();
        return doClaim(context, player, pc);
    }

    private CommandResult handleAuto(CommandContext context, EntityPlayer player) {
        PlotWorld world = plotService.getPlotWorld(player.getDimension());
        if (world == null) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_NOT_PLOT_WORLD));
            return context.fail();
        }

        PlotId nextId = plotService.findNextFreePlotId(world);
        return doClaim(context, player, new PlotContext(world, nextId));
    }

    private CommandResult handleDelete(CommandContext context, EntityPlayer player) {
        PlotContext pc = resolveClaimedPlotContext(context, player);
        if (pc == null) return context.fail();

        Plot plot = pc.plot(); // guaranteed non-null & claimed by resolveClaimedPlotContext
        if (!isOwnerOrAdminDelete(player, plot)) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_NOT_OWNER));
            return context.fail();
        }

        PlotWorldConfig wc = pc.world().getConfig();
        if (shouldRefundOnDelete(player, wc)) {
            UUID receiver = plot.getOwner() != null ? plot.getOwner() : player.getUniqueId();
            deposit(receiver, BigDecimal.valueOf(wc.sellRefund()));
        }

        plotService.deletePlot(pc.world(), pc.plotId());
        context.addOutput(messages.render(player, LangKeys.MESSAGE_DELETE_SUCCESS));
        return context.success();
    }

    private CommandResult handleMerge(CommandContext context, EntityPlayer player) {
        PlotContext pc = resolvePlotContext(context, player);
        if (pc == null) return context.fail();

        Plot plot = requireOwnedPlot(context, player, pc);
        if (plot == null) return context.fail();

        PlotMergeDirection dir = resolveMergeDirection(context, player);
        PlotId targetId = pc.world().getAdjacentPlotId(pc.plotId(), dir);
        Plot targetPlot = pc.world().getPlot(targetId);

        if (targetPlot == null || !targetPlot.isClaimed()) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_MERGE_TARGET_UNCLAIMED));
            return context.fail();
        }
        if (!Objects.equals(plot.getOwner(), targetPlot.getOwner())) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_MERGE_NOT_SAME_OWNER));
            return context.fail();
        }
        if (pc.world().isMerged(pc.plotId(), dir)) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_MERGE_ALREADY));
            return context.fail();
        }
        if (!pc.world().setMerged(pc.plotId(), dir, true)) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_MERGE_FAILED));
            return context.fail();
        }

        plotService.syncPlotSettings(pc.world(), pc.plotId(), plot);
        plotService.updateMergeRoads(pc.world(), pc.plotId(), dir);

        context.addOutput(messages.render(player, LangKeys.MESSAGE_MERGE_SUCCESS, targetId.x(), targetId.z()));
        return context.success();
    }

    private CommandResult handleUnmerge(CommandContext context, EntityPlayer player) {
        PlotContext pc = resolvePlotContext(context, player);
        if (pc == null) return context.fail();

        if (requireOwnedPlot(context, player, pc) == null) return context.fail();

        PlotMergeDirection dir = resolveMergeDirection(context, player);
        if (!pc.world().isMerged(pc.plotId(), dir)) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_UNMERGE_NOT_MERGED));
            return context.fail();
        }

        pc.world().setMerged(pc.plotId(), dir, false);
        plotService.updateMergeRoads(pc.world(), pc.plotId(), dir);

        PlotId targetId = pc.world().getAdjacentPlotId(pc.plotId(), dir);
        context.addOutput(messages.render(player, LangKeys.MESSAGE_UNMERGE_SUCCESS, targetId.x(), targetId.z()));
        return context.success();
    }

    private CommandResult handleInfo(CommandContext context, EntityPlayer player) {
        PlotContext pc = resolvePlotContext(context, player);
        if (pc == null) return context.fail();

        Plot plot = pc.world().getPlot(pc.plotId());

        String ownerLine = messages.renderInline(player, LangKeys.MESSAGE_UNCLAIMED_INFO);
        if (plot != null && plot.isClaimed()) {
            ownerLine = messages.renderInline(player, LangKeys.MESSAGE_CLAIMED_INFO, plot.getOwnerNameOrUUID());
        }

        context.addOutput(messages.renderInline(
                player,
                LangKeys.COMMAND_PLOT_INFO_HEADER,
                pc.plotId().x(),
                pc.plotId().z(),
                pc.world().getConfig().worldName()
        ));

        context.addOutput(ownerLine);

        if (plot != null && plot.isClaimed()) {
            context.addOutput(messages.renderInline(
                    player,
                    LangKeys.COMMAND_PLOT_INFO_ACCESS,
                    String.valueOf(plot.getTrusted().size()),
                    String.valueOf(plot.getDenied().size())
            ));
        }

        return context.success();
    }

    private CommandResult handleHomeOther(CommandContext context, EntityPlayer player) {
        List<EntityPlayer> targets = context.getResult(1);

        // /plot home
        if (targets == null || targets.isEmpty()) {
            return teleportHome(context, player, player.getUniqueId(), player.getDisplayName());
        }
        if (targets.size() > 1) {
            context.addTooManyTargetsError();
            return context.fail();
        }

        EntityPlayer target = targets.getFirst();
        boolean same = target.getUniqueId().equals(player.getUniqueId());
        if (!same && !player.hasPermission(Permissions.ADMIN_BYPASS).asBoolean()) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_NO_PERMISSION));
            return context.fail();
        }

        return teleportHome(context, player, target.getUniqueId(), target.getDisplayName());
    }

    private CommandResult handleSetHome(CommandContext context, EntityPlayer player) {
        PlotContext pc = resolvePlotContext(context, player);
        if (pc == null) return context.fail();

        if (requireOwnedPlot(context, player, pc) == null) return context.fail();

        plotService.setHomePlot(player.getUniqueId(), pc.world(), pc.plotId());
        context.addOutput(messages.render(
                player,
                LangKeys.MESSAGE_HOME_SET,
                pc.plotId().x(),
                pc.plotId().z(),
                pc.world().getConfig().worldName()
        ));
        return context.success();
    }

    private CommandResult handleSetOwner(CommandContext context, EntityPlayer player) {
        PlotContext pc = resolvePlotContext(context, player);
        if (pc == null) return context.fail();

        if (requireOwnedPlot(context, player, pc) == null) return context.fail();

        EntityPlayer target = resolveSingleTarget(context, 1);
        if (target == null) return context.fail();

        if (!plotService.setPlotOwner(pc.world(), pc.plotId(), target.getUniqueId(), resolveOwnerName(target))) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_PLOT_UNCLAIMED));
            return context.fail();
        }

        context.addOutput(messages.render(
                player,
                LangKeys.MESSAGE_OWNER_SET,
                target.getDisplayName(),
                pc.plotId().x(),
                pc.plotId().z(),
                pc.world().getConfig().worldName()
        ));
        return context.success();
    }

    private CommandResult handleTrust(CommandContext context, EntityPlayer player) {
        PlotTarget pt = resolveOwnedPlotTarget(context, player);
        if (pt == null) return context.fail();

        EntityPlayer target = pt.target();
        plotService.applyToMergeGroup(pt.world(), pt.plotId(), plot -> {
            plot.addTrusted(target.getUniqueId());
            plot.getDenied().remove(target.getUniqueId());
        });

        context.addOutput(messages.render(player, LangKeys.MESSAGE_TRUST_ADDED, target.getDisplayName()));
        return context.success();
    }

    private CommandResult handleUntrust(CommandContext context, EntityPlayer player) {
        PlotTarget pt = resolveOwnedPlotTarget(context, player);
        if (pt == null) return context.fail();

        EntityPlayer target = pt.target();
        plotService.applyToMergeGroup(pt.world(), pt.plotId(), plot -> plot.removeTrusted(target.getUniqueId()));

        context.addOutput(messages.render(player, LangKeys.MESSAGE_TRUST_REMOVED, target.getDisplayName()));
        return context.success();
    }

    private CommandResult handleDeny(CommandContext context, EntityPlayer player) {
        PlotTarget pt = resolveOwnedPlotTarget(context, player);
        if (pt == null) return context.fail();

        EntityPlayer target = pt.target();
        plotService.applyToMergeGroup(pt.world(), pt.plotId(), plot -> {
            plot.addDenied(target.getUniqueId());
            plot.getTrusted().remove(target.getUniqueId());
        });

        context.addOutput(messages.render(player, LangKeys.MESSAGE_DENY_ADDED, target.getDisplayName()));
        return context.success();
    }

    private CommandResult handleUndeny(CommandContext context, EntityPlayer player) {
        PlotTarget pt = resolveOwnedPlotTarget(context, player);
        if (pt == null) return context.fail();

        EntityPlayer target = pt.target();
        plotService.applyToMergeGroup(pt.world(), pt.plotId(), plot -> plot.removeDenied(target.getUniqueId()));

        context.addOutput(messages.render(player, LangKeys.MESSAGE_DENY_REMOVED, target.getDisplayName()));
        return context.success();
    }

    private CommandResult handleFlag(CommandContext context, EntityPlayer player) {
        PlotFlag flag = context.getResult(1);
        if (flag == null) return handleFlagList(context, player);

        String rawValue = context.getResult(2);
        if (rawValue == null || rawValue.isBlank()) return handleFlagShow(context, player, flag);

        return handleFlagSet(context, player, flag, rawValue);
    }

    private CommandResult handleFlagList(CommandContext context, EntityPlayer player) {
        PlotContext pc = resolveClaimedPlotContext(context, player);
        if (pc == null) return context.fail();

        context.addOutput(messages.render(player, LangKeys.MESSAGE_FLAG_LIST, renderFlags(pc.plot())));
        return context.success();
    }

    private CommandResult handleFlagShow(CommandContext context, EntityPlayer player, PlotFlag flag) {
        PlotContext pc = resolveClaimedPlotContext(context, player);
        if (pc == null) return context.fail();

        context.addOutput(messages.render(
                player,
                LangKeys.MESSAGE_FLAG_VALUE,
                flag.getLowerCaseName(),
                PlotFlagValue.format(pc.plot().getFlag(flag))
        ));
        return context.success();
    }

    private CommandResult handleFlagSet(CommandContext context, EntityPlayer player, PlotFlag flag, String rawValue) {
        PlotContext pc = resolvePlotContext(context, player);
        if (pc == null) return context.fail();
        if (requireOwnedPlot(context, player, pc) == null) return context.fail();

        if (PlotFlagValue.isReset(rawValue)) {
            plotService.applyToMergeGroup(pc.world(), pc.plotId(), p -> p.removeFlag(flag.getLowerCaseName()));
            context.addOutput(messages.render(
                    player,
                    LangKeys.MESSAGE_FLAG_RESET,
                    flag.getLowerCaseName(),
                    PlotFlagValue.format(flag.defaultValue())
            ));
            return context.success();
        }

        Boolean parsed = PlotFlagValue.parseBoolean(rawValue);
        if (parsed == null) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_FLAG_INVALID_VALUE));
            return context.fail();
        }

        plotService.applyToMergeGroup(pc.world(), pc.plotId(), p -> p.setFlag(flag, parsed));
        context.addOutput(messages.render(
                player,
                LangKeys.MESSAGE_FLAG_SET,
                flag.getLowerCaseName(),
                PlotFlagValue.format(parsed)
        ));
        return context.success();
    }

    private PlotContext resolvePlotContext(CommandContext context, EntityPlayer player) {
        PlotWorld world = plotService.getPlotWorld(player.getDimension());
        if (world == null) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_NOT_PLOT_WORLD));
            return null;
        }

        int x = (int) Math.floor(player.getLocation().x());
        int z = (int) Math.floor(player.getLocation().z());
        PlotId plotId = world.getPlotIdAt(x, z);

        if (plotId == null) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_NOT_IN_PLOT));
            return null;
        }

        return new PlotContext(world, plotId);
    }

    private PlotContext resolveClaimedPlotContext(CommandContext context, EntityPlayer player) {
        PlotContext pc = resolvePlotContext(context, player);
        if (pc == null) return null;

        Plot plot = pc.plot();
        if (plot == null || !plot.isClaimed()) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_PLOT_UNCLAIMED));
            return null;
        }
        return pc;
    }

    private Plot requireOwnedPlot(CommandContext context, EntityPlayer player, PlotContext pc) {
        Plot plot = pc.plot();
        if (plot == null || !plot.isClaimed()) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_PLOT_UNCLAIMED));
            return null;
        }
        if (!plot.isOwner(player.getUniqueId()) && !player.hasPermission(Permissions.ADMIN_BYPASS).asBoolean()) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_NOT_OWNER));
            return null;
        }
        return plot;
    }

    private PlotTarget resolveOwnedPlotTarget(CommandContext context, EntityPlayer player) {
        PlotContext pc = resolvePlotContext(context, player);
        if (pc == null) return null;

        if (requireOwnedPlot(context, player, pc) == null) return null;

        EntityPlayer target = resolveSingleTarget(context, 1);
        if (target == null) return null;

        return new PlotTarget(pc.world(), pc.plotId(), target);
    }

    private EntityPlayer resolveSingleTarget(CommandContext context, int index) {
        List<EntityPlayer> targets = context.getResult(index);
        if (targets == null || targets.isEmpty()) {
            context.addNoTargetMatchError();
            return null;
        }
        if (targets.size() > 1) {
            context.addTooManyTargetsError();
            return null;
        }
        return targets.getFirst();
    }

    private PlotMergeDirection resolveMergeDirection(CommandContext context, EntityPlayer player) {
        PlotMergeDirection dir = context.getResult(1);
        return dir != null ? dir : PlotMergeDirection.fromYaw(player.getLocation().yaw());
    }

    private CommandResult doClaim(CommandContext context, EntityPlayer player, PlotContext pc) {
        Plot existing = pc.plot();
        if (existing != null && existing.isClaimed()) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_ALREADY_CLAIMED));
            return context.fail();
        }

        PlotWorldConfig wc = pc.world().getConfig();

        int maxPlots = wc.maxPlotsPerPlayer();
        if (maxPlots > 0) {
            int owned = plotService.countOwnedPlots(pc.world(), player.getUniqueId());
            if (owned >= maxPlots) {
                context.addOutput(messages.render(player, LangKeys.MESSAGE_TOO_MANY_PLOTS, String.valueOf(maxPlots)));
                return context.fail();
            }
        }

        if (shouldChargeOnClaim(player, wc)) {
            BigDecimal price = BigDecimal.valueOf(wc.claimPrice());
            if (!withdraw(player.getUniqueId(), price)) {
                context.addOutput(messages.render(player, LangKeys.MESSAGE_NOT_ENOUGH_MONEY, price.toPlainString()));
                return context.fail();
            }
        }

        Plot claimed = plotService.claimPlot(pc.world(), pc.plotId(), player.getUniqueId(), resolveOwnerName(player));
        claimed.getDenied().remove(player.getUniqueId());

        new PlotClaimEvent(player, pc.world(), claimed).call();

        if (wc.teleportOnClaim()) {
            teleportToPlot(player, pc.world(), pc.plotId());
        }

        context.addOutput(messages.render(player, LangKeys.MESSAGE_CLAIM_SUCCESS));
        return context.success();
    }

    private static String resolveOwnerName(EntityPlayer player) {
        return player.getController() != null
                ? player.getController().getOriginName()
                : player.getDisplayName();
    }

    private Currency resolveCurrency(Logger logger) {
        String name = config.economy().currency();
        if (name.isBlank()) return economyApi.getDefaultCurrency();

        Currency resolved = economyApi.getCurrency(name);
        if (resolved == null) {
            logger.warn("Economy currency '{}' not found; using default currency.", name);
            return economyApi.getDefaultCurrency();
        }
        return resolved;
    }

    private boolean shouldChargeOnClaim(EntityPlayer player, PlotWorldConfig wc) {
        return economyEnabled
               && wc.claimPrice() > 0
               && !player.hasPermission(Permissions.ECONOMY_BYPASS).asBoolean();
    }

    private boolean shouldRefundOnDelete(EntityPlayer player, PlotWorldConfig wc) {
        return economyEnabled
               && wc.sellRefund() > 0
               && !player.hasPermission(Permissions.ECONOMY_BYPASS).asBoolean();
    }

    private boolean withdraw(UUID uuid, BigDecimal amount) {
        Account account = economyApi.getOrCreateAccount(uuid);
        if (account.getBalance(currency).compareTo(amount) < 0) return false;
        return account.withdraw(currency, amount);
    }

    private void deposit(UUID uuid, BigDecimal amount) {
        economyApi.getOrCreateAccount(uuid).deposit(currency, amount);
    }

    private CommandResult teleportHome(CommandContext context, EntityPlayer player, UUID targetId, String targetName) {
        PlotService.PlotLocation location = plotService.findHomePlot(targetId);
        if (location == null) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_HOME_NOT_FOUND, targetName));
            return context.fail();
        }

        Plot plot = location.plot();
        if (plot != null
            && !plot.canEnter(player.getUniqueId())
            && !player.hasPermission(Permissions.ADMIN_BYPASS).asBoolean()) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_ENTER_DENIED));
            return context.fail();
        }

        teleportToPlot(player, location.world(), location.id());
        context.addOutput(messages.render(
                player,
                LangKeys.MESSAGE_HOME_TELEPORT,
                location.id().x(),
                location.id().z(),
                location.world().getConfig().worldName()
        ));
        return context.success();
    }

    private void teleportToPlot(EntityPlayer player, PlotWorld world, PlotId plotId) {
        var bounds = world.getMergedPlotBounds(plotId);

        double x = bounds.minX() + ((bounds.maxX() - bounds.minX() + 1) / 2.0);
        double z = bounds.minZ() + ((bounds.maxZ() - bounds.minZ() + 1) / 2.0);
        double y = world.getConfig().groundY() + 1.0;

        var targetWorld = Server.getInstance().getWorldPool().getWorld(world.getConfig().worldName());
        var dimension = targetWorld != null ? targetWorld.getOverWorld() : player.getDimension();

        player.teleport(new Location3d(
                x, y, z,
                player.getLocation().pitch(),
                player.getLocation().yaw(),
                dimension
        ));
    }

    private boolean isOwnerOrAdminDelete(EntityPlayer player, Plot plot) {
        return plot.isOwner(player.getUniqueId()) || player.hasPermission(Permissions.ADMIN_DELETE).asBoolean();
    }

    private String renderFlags(Plot plot) {
        StringBuilder builder = new StringBuilder();
        for (PlotFlag flag : PlotFlag.values()) {
            if (!builder.isEmpty()) builder.append(", ");
            builder.append(flag.getLowerCaseName())
                    .append("=")
                    .append(PlotFlagValue.format(plot.getFlag(flag)));
        }
        return builder.toString();
    }

    private record PlotTarget(PlotWorld world, PlotId plotId, EntityPlayer target) {}

    private record PlotContext(PlotWorld world, PlotId plotId) {
        Plot plot() {
            return world.getPlot(plotId);
        }
    }
}

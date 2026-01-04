package me.daoge.allayplots.command;

import me.daoge.allayplots.Permissions;
import me.daoge.allayplots.config.PlotWorldConfig;
import me.daoge.allayplots.config.PluginConfig;
import me.daoge.allayplots.event.PlotClaimEvent;
import me.daoge.allayplots.i18n.LangKeys;
import me.daoge.allayplots.i18n.MessageService;
import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import me.daoge.allayplots.plot.PlotFlag;
import me.daoge.allayplots.plot.PlotFlagValue;
import me.daoge.allayplots.plot.PlotMergeDirection;
import me.daoge.allayplots.plot.PlotService;
import me.daoge.allayplots.plot.PlotWorld;
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
    private final EconomyAPI economyApi;
    private final Currency currency;
    private final boolean economyEnabled;

    public PlotCommand(PlotService plotService, PluginConfig config, MessageService messages, Logger logger) {
        super("plot", LangKeys.COMMAND_PLOT_DESCRIPTION, Permissions.COMMAND_PLOT);
        this.plotService = plotService;
        this.config = config;
        this.messages = messages;
        this.economyEnabled = config.economy().enabled();
        this.economyApi = EconomyAPI.getAPI();
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
        root.key("merge").enumClass("direction", PlotMergeDirection.class).optional().exec(this::handleMerge, SenderType.PLAYER);
        root.key("unmerge").enumClass("direction", PlotMergeDirection.class).optional().exec(this::handleUnmerge, SenderType.PLAYER);
        root.key("info").exec(this::handleInfo, SenderType.PLAYER);
        root.key("home").playerTarget("player").optional().exec(this::handleHomeOther, SenderType.PLAYER);
        root.key("sethome").exec(this::handleSetHome, SenderType.PLAYER);
        root.key("trust").playerTarget("player").exec(this::handleTrust, SenderType.PLAYER);
        root.key("untrust").playerTarget("player").exec(this::handleUntrust, SenderType.PLAYER);
        root.key("deny").playerTarget("player").exec(this::handleDeny, SenderType.PLAYER);
        root.key("undeny").playerTarget("player").exec(this::handleUndeny, SenderType.PLAYER);
        root.key("flag").enumClass("flag", PlotFlag.class).optional().str("value").optional().exec(this::handleFlag, SenderType.PLAYER);
    }

    private CommandResult sendHelp(CommandContext context) {
        EntityPlayer player = context.getSender() instanceof EntityPlayer sender ? sender : null;
        context.addOutput(messages.renderInline(player, LangKeys.COMMAND_PLOT_HELP));
        return context.success();
    }

    private CommandResult handleClaim(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        return handleClaim(context, player, plotContext, false);
    }

    private CommandResult handleAuto(CommandContext context, EntityPlayer player) {
        PlotWorld world = plotService.getPlotWorld(player.getDimension());
        if (world == null) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_NOT_PLOT_WORLD));
            return context.fail();
        }
        PlotId nextId = plotService.findNextFreePlotId(world);
        PlotContext plotContext = new PlotContext(world, nextId, world.getPlot(nextId));
        return handleClaim(context, player, plotContext, true);
    }

    private CommandResult handleDelete(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolveClaimedPlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        Plot plot = plotContext.plot();
        if (!plot.isOwner(player.getUniqueId()) && !player.hasPermission(Permissions.ADMIN_DELETE).asBoolean()) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_NOT_OWNER));
            return context.fail();
        }

        PlotWorldConfig worldConfig = plotContext.world().getConfig();
        if (economyEnabled
                && worldConfig.sellRefund() > 0
                && !player.hasPermission(Permissions.ECONOMY_BYPASS).asBoolean()) {
            deposit(plot.getOwner() != null ? plot.getOwner() : player.getUniqueId(),
                    BigDecimal.valueOf(worldConfig.sellRefund()));
        }

        plotService.deletePlot(plotContext.world(), plotContext.plotId());
        context.addOutput(messages.render(player, LangKeys.MESSAGE_DELETE_SUCCESS));
        return context.success();
    }

    private CommandResult handleMerge(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        Plot plot = requireOwnedPlot(context, player, plotContext);
        if (plot == null) {
            return context.fail();
        }
        PlotMergeDirection direction = resolveMergeDirection(context, player);
        PlotId targetId = plotContext.world().getAdjacentPlotId(plotContext.plotId(), direction);
        Plot targetPlot = plotContext.world().getPlot(targetId);
        if (targetPlot == null || !targetPlot.isClaimed()) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_MERGE_TARGET_UNCLAIMED));
            return context.fail();
        }
        if (!Objects.equals(plot.getOwner(), targetPlot.getOwner())) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_MERGE_NOT_SAME_OWNER));
            return context.fail();
        }
        if (plotContext.world().isMerged(plotContext.plotId(), direction)) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_MERGE_ALREADY));
            return context.fail();
        }
        if (!plotContext.world().setMerged(plotContext.plotId(), direction, true)) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_MERGE_FAILED));
            return context.fail();
        }
        plotService.syncPlotSettings(plotContext.world(), plotContext.plotId(), plot);
        plotService.updateMergeRoads(plotContext.world(), plotContext.plotId(), direction);
        context.addOutput(messages.render(player, LangKeys.MESSAGE_MERGE_SUCCESS, targetId.x(), targetId.z()));
        return context.success();
    }

    private CommandResult handleUnmerge(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        if (requireOwnedPlot(context, player, plotContext) == null) {
            return context.fail();
        }
        PlotMergeDirection direction = resolveMergeDirection(context, player);
        if (!plotContext.world().isMerged(plotContext.plotId(), direction)) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_UNMERGE_NOT_MERGED));
            return context.fail();
        }
        plotContext.world().setMerged(plotContext.plotId(), direction, false);
        plotService.updateMergeRoads(plotContext.world(), plotContext.plotId(), direction);
        PlotId targetId = plotContext.world().getAdjacentPlotId(plotContext.plotId(), direction);
        context.addOutput(messages.render(player, LangKeys.MESSAGE_UNMERGE_SUCCESS, targetId.x(), targetId.z()));
        return context.success();
    }

    private CommandResult handleInfo(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        Plot plot = plotContext.plot();
        String ownerText = messages.renderInline(player, LangKeys.MESSAGE_UNCLAIMED_INFO);
        if (plot != null && plot.isClaimed()) {
            String ownerName = plotService.resolvePlayerName(plot.getOwner());
            ownerText = messages.renderInline(player, LangKeys.MESSAGE_CLAIMED_INFO, ownerName);
        }
        context.addOutput(messages.renderInline(
                player,
                LangKeys.COMMAND_PLOT_INFO_HEADER,
                plotContext.plotId().x(),
                plotContext.plotId().z(),
                plotContext.world().getConfig().worldName()
        ));
        context.addOutput(ownerText);
        if (plot != null && plot.isClaimed()) {
            context.addOutput(messages.renderInline(player, LangKeys.COMMAND_PLOT_INFO_ACCESS,
                    String.valueOf(plot.getTrusted().size()),
                    String.valueOf(plot.getDenied().size())));
        }
        return context.success();
    }

    private CommandResult handleHomeOther(CommandContext context, EntityPlayer player) {
        List<EntityPlayer> targets = context.getResult(1);
        if (targets == null || targets.isEmpty()) {
            return teleportHome(context, player, player.getUniqueId(), player.getDisplayName());
        }
        if (targets.size() > 1) {
            context.addTooManyTargetsError();
            return context.fail();
        }
        EntityPlayer target = targets.getFirst();
        if (!target.getUniqueId().equals(player.getUniqueId())
                && !player.hasPermission(Permissions.ADMIN_BYPASS).asBoolean()) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_NO_PERMISSION));
            return context.fail();
        }
        return teleportHome(context, player, target.getUniqueId(), target.getDisplayName());
    }

    private CommandResult handleSetHome(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        if (requireOwnedPlot(context, player, plotContext) == null) {
            return context.fail();
        }
        plotService.setHomePlot(player.getUniqueId(), plotContext.world(), plotContext.plotId());
        context.addOutput(messages.render(
                player,
                LangKeys.MESSAGE_HOME_SET,
                plotContext.plotId().x(),
                plotContext.plotId().z(),
                plotContext.world().getConfig().worldName()
        ));
        return context.success();
    }

    private CommandResult handleTrust(CommandContext context, EntityPlayer player) {
        PlotTarget plotTarget = resolveOwnedPlotTarget(context, player);
        if (plotTarget == null) {
            return context.fail();
        }
        EntityPlayer target = plotTarget.target();
        plotService.applyToMergeGroup(plotTarget.world(), plotTarget.plotId(), plot -> {
            plot.addTrusted(target.getUniqueId());
            plot.getDenied().remove(target.getUniqueId());
        });
        context.addOutput(messages.render(player, LangKeys.MESSAGE_TRUST_ADDED, target.getDisplayName()));
        return context.success();
    }

    private CommandResult handleUntrust(CommandContext context, EntityPlayer player) {
        PlotTarget plotTarget = resolveOwnedPlotTarget(context, player);
        if (plotTarget == null) {
            return context.fail();
        }
        EntityPlayer target = plotTarget.target();
        plotService.applyToMergeGroup(plotTarget.world(), plotTarget.plotId(), plot -> {
            plot.removeTrusted(target.getUniqueId());
        });
        context.addOutput(messages.render(player, LangKeys.MESSAGE_TRUST_REMOVED, target.getDisplayName()));
        return context.success();
    }

    private CommandResult handleDeny(CommandContext context, EntityPlayer player) {
        PlotTarget plotTarget = resolveOwnedPlotTarget(context, player);
        if (plotTarget == null) {
            return context.fail();
        }
        EntityPlayer target = plotTarget.target();
        plotService.applyToMergeGroup(plotTarget.world(), plotTarget.plotId(), plot -> {
            plot.addDenied(target.getUniqueId());
            plot.getTrusted().remove(target.getUniqueId());
        });
        context.addOutput(messages.render(player, LangKeys.MESSAGE_DENY_ADDED, target.getDisplayName()));
        return context.success();
    }

    private CommandResult handleUndeny(CommandContext context, EntityPlayer player) {
        PlotTarget plotTarget = resolveOwnedPlotTarget(context, player);
        if (plotTarget == null) {
            return context.fail();
        }
        EntityPlayer target = plotTarget.target();
        plotService.applyToMergeGroup(plotTarget.world(), plotTarget.plotId(), plot -> {
            plot.removeDenied(target.getUniqueId());
        });
        context.addOutput(messages.render(player, LangKeys.MESSAGE_DENY_REMOVED, target.getDisplayName()));
        return context.success();
    }

    private CommandResult handleFlagList(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolveClaimedPlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        Plot plot = plotContext.plot();
        context.addOutput(messages.render(player, LangKeys.MESSAGE_FLAG_LIST, renderFlags(plot)));
        return context.success();
    }

    private CommandResult handleFlag(CommandContext context, EntityPlayer player) {
        PlotFlag flag = context.getResult(1);
        if (flag == null) {
            return handleFlagList(context, player);
        }
        String rawValue = context.getResult(2);
        if (rawValue == null || rawValue.isBlank()) {
            return handleFlagShow(context, player);
        }
        return handleFlagSet(context, player);
    }

    private CommandResult handleFlagShow(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolveClaimedPlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        Plot plot = plotContext.plot();
        PlotFlag flag = context.getResult(1);
        context.addOutput(messages.render(player, LangKeys.MESSAGE_FLAG_VALUE, flag.getLowerCaseName(),
                PlotFlagValue.format(plot.getFlag(flag))));
        return context.success();
    }

    private CommandResult handleFlagSet(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        if (requireOwnedPlot(context, player, plotContext) == null) {
            return context.fail();
        }
        PlotFlag flag = context.getResult(1);
        String rawValue = context.getResult(2);
        if (PlotFlagValue.isReset(rawValue)) {
            plotService.applyToMergeGroup(plotContext.world(), plotContext.plotId(),
                    target -> target.removeFlag(flag.getLowerCaseName()));
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
        plotService.applyToMergeGroup(plotContext.world(), plotContext.plotId(),
                target -> target.setFlag(flag, parsed));
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
        return new PlotContext(world, plotId, world.getPlot(plotId));
    }

    private PlotContext resolveClaimedPlotContext(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return null;
        }
        Plot plot = plotContext.plot();
        if (plot == null || !plot.isClaimed()) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_PLOT_UNCLAIMED));
            return null;
        }
        return plotContext;
    }

    private Plot requireOwnedPlot(CommandContext context, EntityPlayer player, PlotContext plotContext) {
        Plot plot = plotContext.plot();
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

    private PlotTarget resolveOwnedPlotTarget(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return null;
        }
        Plot plot = requireOwnedPlot(context, player, plotContext);
        if (plot == null) {
            return null;
        }
        EntityPlayer target = resolveSingleTarget(context, 1);
        if (target == null) {
            return null;
        }
        return new PlotTarget(plotContext.world(), plotContext.plotId(), plot, target);
    }

    private PlotMergeDirection resolveMergeDirection(CommandContext context, EntityPlayer player) {
        PlotMergeDirection direction = context.getResult(1);
        if (direction != null) {
            return direction;
        }
        return PlotMergeDirection.fromYaw(player.getLocation().yaw());
    }

    private CommandResult handleClaim(CommandContext context, EntityPlayer player, PlotContext plotContext, boolean auto) {
        Plot plot = plotContext.plot();
        if (plot != null && plot.isClaimed()) {
            context.addOutput(messages.render(player, LangKeys.MESSAGE_ALREADY_CLAIMED));
            return context.fail();
        }

        PlotWorldConfig worldConfig = plotContext.world().getConfig();
        int maxPlots = worldConfig.maxPlotsPerPlayer();
        if (maxPlots > 0) {
            int owned = plotService.countOwnedPlots(plotContext.world(), player.getUniqueId());
            if (owned >= maxPlots) {
                context.addOutput(messages.render(player, LangKeys.MESSAGE_TOO_MANY_PLOTS, String.valueOf(maxPlots)));
                return context.fail();
            }
        }

        if (economyEnabled
                && worldConfig.claimPrice() > 0
                && !player.hasPermission(Permissions.ECONOMY_BYPASS).asBoolean()) {
            BigDecimal price = BigDecimal.valueOf(worldConfig.claimPrice());
            if (!withdraw(player.getUniqueId(), price)) {
                context.addOutput(messages.render(player, LangKeys.MESSAGE_NOT_ENOUGH_MONEY, price.toPlainString()));
                return context.fail();
            }
        }

        String ownerName = player.getController() != null
                ? player.getController().getOriginName()
                : player.getDisplayName();
        Plot claimed = plotService.claimPlot(plotContext.world(), plotContext.plotId(), player.getUniqueId(), ownerName);
        claimed.getDenied().remove(player.getUniqueId());
        new PlotClaimEvent(player, plotContext.world(), plotContext.plotId(), claimed, auto).call();
        if (worldConfig.teleportOnClaim()) {
            teleportToPlot(player, plotContext.world(), plotContext.plotId());
        }
        context.addOutput(messages.render(player, LangKeys.MESSAGE_CLAIM_SUCCESS));
        return context.success();
    }

    private Currency resolveCurrency(Logger logger) {
        if (config.economy().currency().isBlank()) {
            return economyApi.getDefaultCurrency();
        }
        Currency resolved = economyApi.getCurrency(config.economy().currency());
        if (resolved == null) {
            logger.warn("Economy currency '{}' not found; using default currency.", config.economy().currency());
            return economyApi.getDefaultCurrency();
        }
        return resolved;
    }

    private boolean withdraw(UUID uuid, BigDecimal amount) {
        Account account = economyApi.getOrCreateAccount(uuid);
        if (account.getBalance(currency).compareTo(amount) < 0) {
            return false;
        }
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

    private String renderFlags(Plot plot) {
        StringBuilder builder = new StringBuilder();
        for (PlotFlag flag : PlotFlag.values()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(flag.getLowerCaseName()).append("=").append(PlotFlagValue.format(plot.getFlag(flag)));
        }
        return builder.toString();
    }

    private void teleportToPlot(EntityPlayer player, PlotWorld world, PlotId plotId) {
        var bounds = world.getMergedPlotBounds(plotId);
        double x = bounds.minX() + ((bounds.maxX() - bounds.minX() + 1) / 2.0);
        double z = bounds.minZ() + ((bounds.maxZ() - bounds.minZ() + 1) / 2.0);
        double y = world.getConfig().groundY() + 1.0;
        var targetWorld = Server.getInstance().getWorldPool().getWorld(world.getConfig().worldName());
        var dimension = targetWorld != null ? targetWorld.getOverWorld() : player.getDimension();
        player.teleport(new Location3d(
                x,
                y,
                z,
                player.getLocation().pitch(),
                player.getLocation().yaw(),
                dimension
        ));
    }

    private record PlotTarget(PlotWorld world, PlotId plotId, Plot plot, EntityPlayer target) {
    }

    private record PlotContext(PlotWorld world, PlotId plotId, Plot plot) {
    }
}

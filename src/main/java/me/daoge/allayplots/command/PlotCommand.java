package me.daoge.allayplots.command;

import me.daoge.allayplots.Permissions;
import me.daoge.allayplots.config.PlotWorldConfig;
import me.daoge.allayplots.config.PluginConfig;
import me.daoge.allayplots.i18n.LangKeys;
import me.daoge.allayplots.i18n.MessageService;
import me.daoge.allayplots.plot.Plot;
import me.daoge.allayplots.plot.PlotId;
import me.daoge.allayplots.plot.PlotService;
import me.daoge.allayplots.plot.PlotWorld;
import org.allaymc.api.command.Command;
import org.allaymc.api.command.CommandResult;
import org.allaymc.api.command.SenderType;
import org.allaymc.api.command.tree.CommandContext;
import org.allaymc.api.command.tree.CommandTree;
import org.allaymc.api.entity.interfaces.EntityPlayer;
import org.allaymc.api.permission.OpPermissionCalculator;
import org.allaymc.economyapi.Account;
import org.allaymc.economyapi.Currency;
import org.allaymc.economyapi.EconomyAPI;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.util.List;
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
        this.economyEnabled = config.economyEnabled();
        this.economyApi = EconomyAPI.getAPI();
        this.currency = economyEnabled ? resolveCurrency(logger) : economyApi.getDefaultCurrency();
        aliases.addAll(List.of("plots", "p"));
        OpPermissionCalculator.NON_OP_PERMISSIONS.addAll(this.permissions);
    }

    @Override
    public void prepareCommandTree(CommandTree tree) {
        tree.getRoot()
                .key("help")
                .exec(this::sendHelp)
                .root()
                .key("claim")
                .exec(this::handleClaim, SenderType.PLAYER)
                .root()
                .key("auto")
                .exec(this::handleAuto, SenderType.PLAYER)
                .root()
                .key("delete")
                .exec(this::handleDelete, SenderType.PLAYER)
                .root()
                .key("info")
                .exec(this::handleInfo, SenderType.PLAYER)
                .root()
                .key("trust")
                .playerTarget("player")
                .exec(this::handleTrust, SenderType.PLAYER)
                .root()
                .key("untrust")
                .playerTarget("player")
                .exec(this::handleUntrust, SenderType.PLAYER)
                .root()
                .key("deny")
                .playerTarget("player")
                .exec(this::handleDeny, SenderType.PLAYER)
                .root()
                .key("undeny")
                .playerTarget("player")
                .exec(this::handleUndeny, SenderType.PLAYER);
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
        return handleClaim(context, player, plotContext);
    }

    private CommandResult handleAuto(CommandContext context, EntityPlayer player) {
        PlotWorld world = plotService.getPlotWorld(player.getDimension());
        if (world == null) {
            context.addOutput(messages.render(player, config.messages().notPlotWorld()));
            return context.fail();
        }
        PlotId nextId = plotService.findNextFreePlotId(world);
        PlotContext plotContext = new PlotContext(world, nextId, world.getPlot(nextId));
        return handleClaim(context, player, plotContext);
    }

    private CommandResult handleDelete(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        Plot plot = plotContext.plot();
        if (plot == null || !plot.isClaimed()) {
            context.addOutput(messages.render(player, config.messages().plotUnclaimed()));
            return context.fail();
        }
        if (!plot.isOwner(player.getUniqueId()) && !player.hasPermission(Permissions.ADMIN_DELETE).asBoolean()) {
            context.addOutput(messages.render(player, config.messages().notOwner()));
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
        context.addOutput(messages.render(player, config.messages().deleteSuccess()));
        return context.success();
    }

    private CommandResult handleInfo(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        Plot plot = plotContext.plot();
        String ownerText = messages.renderInline(player, config.messages().unclaimedInfo());
        if (plot != null && plot.isClaimed()) {
            String ownerName = plotService.resolvePlayerName(plot.getOwner());
            ownerText = messages.renderInline(player, config.messages().claimedInfo(), ownerName);
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

    private CommandResult handleTrust(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        Plot plot = requireOwnedPlot(context, player, plotContext);
        if (plot == null) {
            return context.fail();
        }
        EntityPlayer target = resolveSingleTarget(context, 0);
        if (target == null) {
            return context.fail();
        }
        plot.addTrusted(target.getUniqueId());
        plot.getDenied().remove(target.getUniqueId());
        context.addOutput(messages.render(player, config.messages().trustAdded(), target.getDisplayName()));
        return context.success();
    }

    private CommandResult handleUntrust(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        Plot plot = requireOwnedPlot(context, player, plotContext);
        if (plot == null) {
            return context.fail();
        }
        EntityPlayer target = resolveSingleTarget(context, 0);
        if (target == null) {
            return context.fail();
        }
        plot.removeTrusted(target.getUniqueId());
        context.addOutput(messages.render(player, config.messages().trustRemoved(), target.getDisplayName()));
        return context.success();
    }

    private CommandResult handleDeny(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        Plot plot = requireOwnedPlot(context, player, plotContext);
        if (plot == null) {
            return context.fail();
        }
        EntityPlayer target = resolveSingleTarget(context, 0);
        if (target == null) {
            return context.fail();
        }
        plot.addDenied(target.getUniqueId());
        plot.getTrusted().remove(target.getUniqueId());
        context.addOutput(messages.render(player, config.messages().denyAdded(), target.getDisplayName()));
        return context.success();
    }

    private CommandResult handleUndeny(CommandContext context, EntityPlayer player) {
        PlotContext plotContext = resolvePlotContext(context, player);
        if (plotContext == null) {
            return context.fail();
        }
        Plot plot = requireOwnedPlot(context, player, plotContext);
        if (plot == null) {
            return context.fail();
        }
        EntityPlayer target = resolveSingleTarget(context, 0);
        if (target == null) {
            return context.fail();
        }
        plot.removeDenied(target.getUniqueId());
        context.addOutput(messages.render(player, config.messages().denyRemoved(), target.getDisplayName()));
        return context.success();
    }

    private PlotContext resolvePlotContext(CommandContext context, EntityPlayer player) {
        PlotWorld world = plotService.getPlotWorld(player.getDimension());
        if (world == null) {
            context.addOutput(messages.render(player, config.messages().notPlotWorld()));
            return null;
        }
        int x = (int) Math.floor(player.getLocation().x());
        int z = (int) Math.floor(player.getLocation().z());
        PlotId plotId = world.getPlotIdAt(x, z);
        if (plotId == null) {
            context.addOutput(messages.render(player, config.messages().notInPlot()));
            return null;
        }
        return new PlotContext(world, plotId, world.getPlot(plotId));
    }

    private Plot requireOwnedPlot(CommandContext context, EntityPlayer player, PlotContext plotContext) {
        Plot plot = plotContext.plot();
        if (plot == null || !plot.isClaimed()) {
            context.addOutput(messages.render(player, config.messages().plotUnclaimed()));
            return null;
        }
        if (!plot.isOwner(player.getUniqueId()) && !player.hasPermission(Permissions.ADMIN_BYPASS).asBoolean()) {
            context.addOutput(messages.render(player, config.messages().notOwner()));
            return null;
        }
        return plot;
    }

    @SuppressWarnings("unchecked")
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
        return targets.get(0);
    }

    private CommandResult handleClaim(CommandContext context, EntityPlayer player, PlotContext plotContext) {
        Plot plot = plotContext.plot();
        if (plot != null && plot.isClaimed()) {
            context.addOutput(messages.render(player, config.messages().alreadyClaimed()));
            return context.fail();
        }

        PlotWorldConfig worldConfig = plotContext.world().getConfig();
        int maxPlots = worldConfig.maxPlotsPerPlayer();
        if (maxPlots > 0) {
            int owned = plotService.countOwnedPlots(plotContext.world(), player.getUniqueId());
            if (owned >= maxPlots) {
                context.addOutput(messages.render(player, config.messages().tooManyPlots(), String.valueOf(maxPlots)));
                return context.fail();
            }
        }

        if (economyEnabled
                && worldConfig.claimPrice() > 0
                && !player.hasPermission(Permissions.ECONOMY_BYPASS).asBoolean()) {
            BigDecimal price = BigDecimal.valueOf(worldConfig.claimPrice());
            if (!withdraw(player.getUniqueId(), price)) {
                context.addOutput(messages.render(player, config.messages().notEnoughMoney(), price.toPlainString()));
                return context.fail();
            }
        }

        String ownerName = player.getController() != null
                ? player.getController().getOriginName()
                : player.getDisplayName();
        Plot claimed = plotService.claimPlot(plotContext.world(), plotContext.plotId(), player.getUniqueId(), ownerName);
        claimed.getDenied().remove(player.getUniqueId());
        if (worldConfig.teleportOnClaim()) {
            teleportToPlot(player, plotContext.world(), plotContext.plotId());
        }
        context.addOutput(messages.render(player, config.messages().claimSuccess()));
        return context.success();
    }

    private Currency resolveCurrency(Logger logger) {
        if (config.economyCurrency().isBlank()) {
            return economyApi.getDefaultCurrency();
        }
        Currency resolved = economyApi.getCurrency(config.economyCurrency());
        if (resolved == null) {
            logger.warn("Economy currency '{}' not found; using default currency.", config.economyCurrency());
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

    private void teleportToPlot(EntityPlayer player, PlotWorld world, PlotId plotId) {
        var bounds = world.getPlotBounds(plotId);
        double x = bounds.minX() + (world.getConfig().plotSize() / 2.0);
        double z = bounds.minZ() + (world.getConfig().plotSize() / 2.0);
        double y = world.getConfig().groundY() + 1.0;
        player.teleport(new org.allaymc.api.math.location.Location3d(
                x,
                y,
                z,
                player.getLocation().pitch(),
                player.getLocation().yaw(),
                player.getDimension()
        ));
    }

    private record PlotContext(PlotWorld world, PlotId plotId, Plot plot) {
    }
}

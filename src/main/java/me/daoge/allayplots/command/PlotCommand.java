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
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

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

    private static String resolveOwnerName(EntityPlayer player) {
        return player.getController() != null
                ? player.getController().getOriginName()
                : player.getDisplayName();
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

        root.key("list").exec(this::handleList, SenderType.PLAYER);

        var visit = root.key("visit");
        visit.playerTarget("player")
                .exec(this::handleVisitPlayer, SenderType.PLAYER);
        visit.intNum("x").intNum("z")
                .exec(this::handleVisitCoords, SenderType.PLAYER);

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
        EntityPlayer player = context.getSender().isPlayer() ? context.getSender().asPlayer() : null;
        context.getSender().sendMessage(messages.renderInline(player, LangKeys.COMMAND_PLOT_HELP));
        return context.success();
    }

    private CommandResult handleClaim(CommandContext context, EntityPlayer player) {
        return withPlotContext(context, player, pc -> doClaim(context, player, pc));
    }

    private CommandResult handleAuto(CommandContext context, EntityPlayer player) {
        PlotWorld world = plotService.getPlotWorld(player.getDimension());
        if (world == null) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_NOT_PLOT_WORLD));
            return context.fail();
        }

        PlotId nextId = plotService.findNextFreePlotId(world);
        return doClaim(context, player, new PlotContext(world, nextId));
    }

    private CommandResult handleDelete(CommandContext context, EntityPlayer player) {
        return withPlotContext(context, player, pc -> {
            Plot plot = pc.plot();
            boolean bypass = player.hasPermission(Permissions.ADMIN_DELETE).asBoolean();
            PlotService.OwnerActionResult result = plotService.deletePlot(
                    pc.world(),
                    pc.plotId(),
                    player.getUniqueId(),
                    bypass
            );
            if (!handleOwnerResult(player, result)) return context.fail();

            PlotWorldConfig wc = pc.world().getConfig();
            if (plot != null && shouldRefundOnDelete(player, wc)) {
                UUID receiver = plot.getOwner() != null ? plot.getOwner() : player.getUniqueId();
                deposit(receiver, BigDecimal.valueOf(wc.sellRefund()));
            }

            player.sendMessage(messages.render(player, LangKeys.MESSAGE_DELETE_SUCCESS));
            return context.success();
        });
    }

    private CommandResult handleMerge(CommandContext context, EntityPlayer player) {
        return withPlotContext(context, player, pc -> {
            PlotMergeDirection dir = resolveMergeDirection(context, player);
            PlotId targetId = pc.world().getAdjacentPlotId(pc.plotId(), dir);

            boolean bypass = hasAdminBypass(player);
            PlotService.MergeResult result = plotService.mergePlots(
                    pc.world(),
                    pc.plotId(),
                    dir,
                    player.getUniqueId(),
                    bypass
            );
            return switch (result) {
                case SUCCESS -> {
                    player.sendMessage(messages.render(player, LangKeys.MESSAGE_MERGE_SUCCESS, targetId.x(), targetId.z()));
                    yield context.success();
                }
                case UNCLAIMED -> {
                    player.sendMessage(messages.render(player, LangKeys.MESSAGE_PLOT_UNCLAIMED));
                    yield context.fail();
                }
                case NOT_OWNER -> {
                    player.sendMessage(messages.render(player, LangKeys.MESSAGE_NOT_OWNER));
                    yield context.fail();
                }
                case TARGET_UNCLAIMED -> {
                    player.sendMessage(messages.render(player, LangKeys.MESSAGE_MERGE_TARGET_UNCLAIMED));
                    yield context.fail();
                }
                case NOT_SAME_OWNER -> {
                    player.sendMessage(messages.render(player, LangKeys.MESSAGE_MERGE_NOT_SAME_OWNER));
                    yield context.fail();
                }
                case ALREADY_MERGED -> {
                    player.sendMessage(messages.render(player, LangKeys.MESSAGE_MERGE_ALREADY));
                    yield context.fail();
                }
                case FAILED -> {
                    player.sendMessage(messages.render(player, LangKeys.MESSAGE_MERGE_FAILED));
                    yield context.fail();
                }
            };
        });
    }

    private CommandResult handleUnmerge(CommandContext context, EntityPlayer player) {
        return withPlotContext(context, player, pc -> {
            PlotMergeDirection dir = resolveMergeDirection(context, player);
            boolean bypass = hasAdminBypass(player);
            PlotService.UnmergeResult result = plotService.unmergePlots(
                    pc.world(),
                    pc.plotId(),
                    dir,
                    player.getUniqueId(),
                    bypass
            );
            switch (result) {
                case SUCCESS -> {
                }
                case NOT_MERGED, FAILED -> {
                    player.sendMessage(messages.render(player, LangKeys.MESSAGE_UNMERGE_NOT_MERGED));
                    return context.fail();
                }
                case UNCLAIMED -> {
                    player.sendMessage(messages.render(player, LangKeys.MESSAGE_PLOT_UNCLAIMED));
                    return context.fail();
                }
                case NOT_OWNER -> {
                    player.sendMessage(messages.render(player, LangKeys.MESSAGE_NOT_OWNER));
                    return context.fail();
                }
            }

            PlotId targetId = pc.world().getAdjacentPlotId(pc.plotId(), dir);
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_UNMERGE_SUCCESS, targetId.x(), targetId.z()));
            return context.success();
        });
    }

    private CommandResult handleInfo(CommandContext context, EntityPlayer player) {
        return withPlotContext(context, player, pc -> {
            Plot plot = pc.world().getPlot(pc.plotId());

            String ownerLine = messages.renderInline(player, LangKeys.MESSAGE_UNCLAIMED_INFO);
            if (plot != null && plot.isClaimed()) {
                ownerLine = messages.renderInline(player, LangKeys.MESSAGE_CLAIMED_INFO, plot.getOwnerNameOrUUID());
            }

            player.sendMessage(messages.renderInline(
                    player,
                    LangKeys.COMMAND_PLOT_INFO_HEADER,
                    pc.plotId().x(),
                    pc.plotId().z(),
                    pc.world().getConfig().worldName()
            ));

            player.sendMessage(ownerLine);

            if (plot != null && plot.isClaimed()) {
                player.sendMessage(messages.renderInline(
                        player,
                        LangKeys.COMMAND_PLOT_INFO_ACCESS,
                        String.valueOf(plot.getTrusted().size()),
                        String.valueOf(plot.getDenied().size())
                ));
            }

            return context.success();
        });
    }

    private CommandResult handleList(CommandContext context, EntityPlayer player) {
        UUID ownerId = player.getUniqueId();
        boolean found = false;

        for (String worldName : config.worlds().keySet()) {
            PlotWorld world = plotService.getPlotWorld(worldName);
            if (world == null) continue;

            for (Plot plot : world.getPlots().values()) {
                if (!plot.isOwner(ownerId)) continue;

                player.sendMessage(messages.renderInline(
                        player,
                        LangKeys.COMMAND_PLOT_INFO_HEADER,
                        plot.getId().x(),
                        plot.getId().z(),
                        world.getConfig().worldName()
                ));
                found = true;
            }
        }

        if (!found) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_HOME_NOT_FOUND, player.getDisplayName()));
            return context.fail();
        }

        return context.success();
    }

    private CommandResult handleVisitPlayer(CommandContext context, EntityPlayer player) {
        EntityPlayer target = resolveSingleTarget(context, 1);
        if (target == null) return context.fail();

        boolean same = target.getUniqueId().equals(player.getUniqueId());
        if (!same && !hasAdminBypass(player)) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_NO_PERMISSION));
            return context.fail();
        }

        return teleportHome(context, player, target.getUniqueId(), target.getDisplayName());
    }

    private CommandResult handleVisitCoords(CommandContext context, EntityPlayer player) {
        Integer x = context.getResult(1);
        Integer z = context.getResult(2);
        PlotService.PlotLocation location = plotService.resolvePlot(player.getDimension(), new PlotId(x, z));
        if (location == null) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_NOT_PLOT_WORLD));
            return context.fail();
        }

        Plot plot = location.plot();
        if (plot == null || !plot.isClaimed()) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_PLOT_UNCLAIMED));
            return context.fail();
        }

        if (!plot.canEnter(player.getUniqueId()) && !hasAdminBypass(player)) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_ENTER_DENIED));
            return context.fail();
        }

        teleportToPlot(player, location.world(), location.id());
        player.sendMessage(messages.render(
                player,
                LangKeys.MESSAGE_HOME_TELEPORT,
                location.id().x(),
                location.id().z(),
                location.world().getConfig().worldName()
        ));
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
        if (!same && !hasAdminBypass(player)) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_NO_PERMISSION));
            return context.fail();
        }

        return teleportHome(context, player, target.getUniqueId(), target.getDisplayName());
    }

    private CommandResult handleSetHome(CommandContext context, EntityPlayer player) {
        return withPlotContext(context, player, pc -> {
            PlotService.OwnerActionResult result = plotService.setHomePlot(player.getUniqueId(), pc.world(), pc.plotId());
            if (!handleOwnerResult(player, result)) return context.fail();

            player.sendMessage(messages.render(
                    player,
                    LangKeys.MESSAGE_HOME_SET,
                    pc.plotId().x(),
                    pc.plotId().z(),
                    pc.world().getConfig().worldName()
            ));
            return context.success();
        });
    }

    private CommandResult handleSetOwner(CommandContext context, EntityPlayer player) {
        return withPlotContext(context, player, pc -> {
            EntityPlayer target = resolveSingleTarget(context, 1);
            if (target == null) return context.fail();

            boolean bypass = hasAdminBypass(player);
            PlotService.OwnerActionResult result = plotService.setPlotOwner(
                    pc.world(),
                    pc.plotId(),
                    player.getUniqueId(),
                    bypass,
                    target.getUniqueId(),
                    resolveOwnerName(target)
            );
            if (!handleOwnerResult(player, result)) return context.fail();

            player.sendMessage(messages.render(
                    player,
                    LangKeys.MESSAGE_OWNER_SET,
                    target.getDisplayName(),
                    pc.plotId().x(),
                    pc.plotId().z(),
                    pc.world().getConfig().worldName()
            ));
            return context.success();
        });
    }

    private CommandResult handleTrust(CommandContext context, EntityPlayer player) {
        return withPlotTarget(context, player, pt -> {
            EntityPlayer target = pt.target();
            return handleAccessUpdate(
                    context,
                    player,
                    pt,
                    plot -> plot.withTrustedAdded(target.getUniqueId())
                            .withDeniedRemoved(target.getUniqueId()),
                    LangKeys.MESSAGE_TRUST_ADDED
            );
        });
    }

    private CommandResult handleUntrust(CommandContext context, EntityPlayer player) {
        return withPlotTarget(context, player, pt -> {
            EntityPlayer target = pt.target();
            return handleAccessUpdate(
                    context,
                    player,
                    pt,
                    plot -> plot.withTrustedRemoved(target.getUniqueId()),
                    LangKeys.MESSAGE_TRUST_REMOVED
            );
        });
    }

    private CommandResult handleDeny(CommandContext context, EntityPlayer player) {
        return withPlotTarget(context, player, pt -> {
            EntityPlayer target = pt.target();
            return handleAccessUpdate(
                    context,
                    player,
                    pt,
                    plot -> plot.withDeniedAdded(target.getUniqueId())
                            .withTrustedRemoved(target.getUniqueId()),
                    LangKeys.MESSAGE_DENY_ADDED
            );
        });
    }

    private CommandResult handleUndeny(CommandContext context, EntityPlayer player) {
        return withPlotTarget(context, player, pt -> {
            EntityPlayer target = pt.target();
            return handleAccessUpdate(
                    context,
                    player,
                    pt,
                    plot -> plot.withDeniedRemoved(target.getUniqueId()),
                    LangKeys.MESSAGE_DENY_REMOVED
            );
        });
    }

    private CommandResult handleFlag(CommandContext context, EntityPlayer player) {
        PlotFlag flag = context.getResult(1);
        if (flag == null) return handleFlagList(context, player);

        String rawValue = context.getResult(2);
        if (rawValue == null || rawValue.isBlank()) return handleFlagShow(context, player, flag);

        return handleFlagSet(context, player, flag, rawValue);
    }

    private CommandResult handleFlagList(CommandContext context, EntityPlayer player) {
        return withClaimedPlotContext(context, player, pc -> {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_FLAG_LIST, renderFlags(pc.plot())));
            return context.success();
        });
    }

    private CommandResult handleFlagShow(CommandContext context, EntityPlayer player, PlotFlag flag) {
        return withClaimedPlotContext(context, player, pc -> {
            player.sendMessage(messages.render(
                    player,
                    LangKeys.MESSAGE_FLAG_VALUE,
                    flag.getLowerCaseName(),
                    PlotFlagValue.format(pc.plot().getFlag(flag))
            ));
            return context.success();
        });
    }

    private CommandResult handleFlagSet(CommandContext context, EntityPlayer player, PlotFlag flag, String rawValue) {
        return withPlotContext(context, player, pc -> {
            if (PlotFlagValue.isReset(rawValue)) {
                PlotService.OwnerActionResult result = plotService.updateMergeGroupOwned(
                        pc.world(),
                        pc.plotId(),
                        player.getUniqueId(),
                        hasAdminBypass(player),
                        p -> p.withoutFlag(flag.getLowerCaseName())
                );
                if (!handleOwnerResult(player, result)) return context.fail();
                player.sendMessage(messages.render(
                        player,
                        LangKeys.MESSAGE_FLAG_RESET,
                        flag.getLowerCaseName(),
                        PlotFlagValue.format(flag.defaultValue())
                ));
                return context.success();
            }

            Boolean parsed = PlotFlagValue.parseBoolean(rawValue);
            if (parsed == null) {
                player.sendMessage(messages.render(player, LangKeys.MESSAGE_FLAG_INVALID_VALUE));
                return context.fail();
            }

            PlotService.OwnerActionResult result = plotService.updateMergeGroupOwned(
                    pc.world(),
                    pc.plotId(),
                    player.getUniqueId(),
                    hasAdminBypass(player),
                    p -> p.withFlag(flag, parsed)
            );
            if (!handleOwnerResult(player, result)) return context.fail();
            player.sendMessage(messages.render(
                    player,
                    LangKeys.MESSAGE_FLAG_SET,
                    flag.getLowerCaseName(),
                    PlotFlagValue.format(parsed)
            ));
            return context.success();
        });
    }

    private CommandResult withPlotContext(
            CommandContext context,
            EntityPlayer player,
            Function<PlotContext, CommandResult> action
    ) {
        PlotContext pc = resolvePlotContext(player);
        if (pc == null) return context.fail();
        return action.apply(pc);
    }

    private CommandResult withClaimedPlotContext(
            CommandContext context,
            EntityPlayer player,
            Function<PlotContext, CommandResult> action
    ) {
        PlotContext pc = resolveClaimedPlotContext(player);
        if (pc == null) return context.fail();
        return action.apply(pc);
    }

    private CommandResult withPlotTarget(
            CommandContext context,
            EntityPlayer player,
            Function<PlotTarget, CommandResult> action
    ) {
        PlotTarget pt = resolvePlotTarget(context, player);
        if (pt == null) return context.fail();
        return action.apply(pt);
    }

    private PlotContext resolvePlotContext(EntityPlayer player) {
        PlotWorld world = plotService.getPlotWorld(player.getDimension());
        if (world == null) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_NOT_PLOT_WORLD));
            return null;
        }

        int x = (int) Math.floor(player.getLocation().x());
        int z = (int) Math.floor(player.getLocation().z());
        PlotId plotId = world.getPlotIdAt(x, z);

        if (plotId == null) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_NOT_IN_PLOT));
            return null;
        }

        return new PlotContext(world, plotId);
    }

    private PlotContext resolveClaimedPlotContext(EntityPlayer player) {
        PlotContext pc = resolvePlotContext(player);
        if (pc == null) return null;

        Plot plot = pc.plot();
        if (plot == null || !plot.isClaimed()) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_PLOT_UNCLAIMED));
            return null;
        }
        return pc;
    }

    private PlotTarget resolvePlotTarget(CommandContext context, EntityPlayer player) {
        PlotContext pc = resolvePlotContext(player);
        if (pc == null) return null;

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
        PlotWorldConfig wc = pc.world().getConfig();
        int maxPlots = wc.maxPlotsPerPlayer();

        BigDecimal price = BigDecimal.valueOf(wc.claimPrice());
        boolean charged = false;
        if (shouldChargeOnClaim(player, wc)) {
            if (!withdraw(player.getUniqueId(), price)) {
                player.sendMessage(messages.render(player, LangKeys.MESSAGE_NOT_ENOUGH_MONEY, price.toPlainString()));
                return context.fail();
            }
            charged = true;
        }

        PlotService.ClaimResult result = plotService.claimPlot(
                pc.world(),
                pc.plotId(),
                player.getUniqueId(),
                resolveOwnerName(player),
                maxPlots
        );
        if (result != PlotService.ClaimResult.SUCCESS) {
            if (charged) {
                deposit(player.getUniqueId(), price);
            }
            switch (result) {
                case ALREADY_CLAIMED -> player.sendMessage(messages.render(player, LangKeys.MESSAGE_ALREADY_CLAIMED));
                case TOO_MANY -> player.sendMessage(messages.render(
                        player,
                        LangKeys.MESSAGE_TOO_MANY_PLOTS,
                        String.valueOf(maxPlots)
                ));
                case FAILED -> player.sendMessage(messages.render(player, LangKeys.MESSAGE_CLAIM_FAILED));
            }
            return context.fail();
        }

        Plot claimed = pc.world().getPlot(pc.plotId());
        if (claimed == null) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_CLAIM_FAILED));
            return context.fail();
        }

        new PlotClaimEvent(player, pc.world(), claimed).call();

        if (wc.teleportOnClaim()) {
            teleportToPlot(player, pc.world(), pc.plotId());
        }

        player.sendMessage(messages.render(player, LangKeys.MESSAGE_CLAIM_SUCCESS));
        return context.success();
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
               && !hasEconomyBypass(player);
    }

    private boolean shouldRefundOnDelete(EntityPlayer player, PlotWorldConfig wc) {
        return economyEnabled
               && wc.sellRefund() > 0
               && !hasEconomyBypass(player);
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
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_HOME_NOT_FOUND, targetName));
            return context.fail();
        }

        Plot plot = location.plot();
        if (plot != null
            && !plot.canEnter(player.getUniqueId())
            && !hasAdminBypass(player)) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_ENTER_DENIED));
            return context.fail();
        }

        teleportToPlot(player, location.world(), location.id());
        player.sendMessage(messages.render(
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

    private boolean handleOwnerResult(EntityPlayer player, PlotService.OwnerActionResult result) {
        if (result == PlotService.OwnerActionResult.SUCCESS) return true;
        if (result == PlotService.OwnerActionResult.UNCLAIMED) {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_PLOT_UNCLAIMED));
        } else {
            player.sendMessage(messages.render(player, LangKeys.MESSAGE_NOT_OWNER));
        }
        return false;
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

    private CommandResult handleAccessUpdate(
            CommandContext context,
            EntityPlayer player,
            PlotTarget target,
            UnaryOperator<Plot> updater,
            String messageKey
    ) {
        PlotService.OwnerActionResult result = plotService.updateMergeGroupOwned(
                target.world(),
                target.plotId(),
                player.getUniqueId(),
                hasAdminBypass(player),
                updater
        );
        if (!handleOwnerResult(player, result)) return context.fail();

        player.sendMessage(messages.render(player, messageKey, target.target().getDisplayName()));
        return context.success();
    }

    private boolean hasAdminBypass(EntityPlayer player) {
        return player.hasPermission(Permissions.ADMIN_BYPASS).asBoolean();
    }

    private boolean hasEconomyBypass(EntityPlayer player) {
        return player.hasPermission(Permissions.ECONOMY_BYPASS).asBoolean();
    }

    private record PlotTarget(PlotWorld world, PlotId plotId, EntityPlayer target) {
    }

    private record PlotContext(PlotWorld world, PlotId plotId) {
        Plot plot() {
            return world.getPlot(plotId);
        }
    }
}

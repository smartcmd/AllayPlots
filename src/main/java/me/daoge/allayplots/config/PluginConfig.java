package me.daoge.allayplots.config;

import me.daoge.allayplots.i18n.LangKeys;
import org.allaymc.api.utils.config.Config;
import org.allaymc.api.utils.config.ConfigSection;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class PluginConfig {
    private final Map<String, PlotWorldConfig> worlds;
    private final boolean economyEnabled;
    private final String economyCurrency;
    private final boolean protectRoads;
    private final int autoSaveIntervalTicks;
    private final boolean useActionBar;
    private final Messages messages;

    private PluginConfig(
            Map<String, PlotWorldConfig> worlds,
            boolean economyEnabled,
            String economyCurrency,
            boolean protectRoads,
            int autoSaveIntervalTicks,
            boolean useActionBar,
            Messages messages
    ) {
        this.worlds = worlds;
        this.economyEnabled = economyEnabled;
        this.economyCurrency = economyCurrency;
        this.protectRoads = protectRoads;
        this.autoSaveIntervalTicks = autoSaveIntervalTicks;
        this.useActionBar = useActionBar;
        this.messages = messages;
    }

    public static PluginConfig load(Path dataFolder, Logger logger) {
        ConfigSection defaults = new ConfigSection();
        defaults.set("version", 1);
        defaults.set("economy.enabled", false);
        defaults.set("economy.currency", "");
        defaults.set("settings.protect-roads", true);
        defaults.set("settings.auto-save-interval-ticks", 6000);
        defaults.set("settings.use-action-bar", true);
        defaults.set("messages.prefix", LangKeys.MESSAGE_PREFIX);
        defaults.set("messages.enter", LangKeys.MESSAGE_ENTER);
        defaults.set("messages.leave", LangKeys.MESSAGE_LEAVE);
        defaults.set("messages.enter-denied", LangKeys.MESSAGE_ENTER_DENIED);
        defaults.set("messages.build-denied", LangKeys.MESSAGE_BUILD_DENIED);
        defaults.set("messages.not-in-plot", LangKeys.MESSAGE_NOT_IN_PLOT);
        defaults.set("messages.not-plot-world", LangKeys.MESSAGE_NOT_PLOT_WORLD);
        defaults.set("messages.already-claimed", LangKeys.MESSAGE_ALREADY_CLAIMED);
        defaults.set("messages.claim-success", LangKeys.MESSAGE_CLAIM_SUCCESS);
        defaults.set("messages.delete-success", LangKeys.MESSAGE_DELETE_SUCCESS);
        defaults.set("messages.plot-unclaimed", LangKeys.MESSAGE_PLOT_UNCLAIMED);
        defaults.set("messages.not-owner", LangKeys.MESSAGE_NOT_OWNER);
        defaults.set("messages.too-many-plots", LangKeys.MESSAGE_TOO_MANY_PLOTS);
        defaults.set("messages.not-enough-money", LangKeys.MESSAGE_NOT_ENOUGH_MONEY);
        defaults.set("messages.trust-added", LangKeys.MESSAGE_TRUST_ADDED);
        defaults.set("messages.trust-removed", LangKeys.MESSAGE_TRUST_REMOVED);
        defaults.set("messages.deny-added", LangKeys.MESSAGE_DENY_ADDED);
        defaults.set("messages.deny-removed", LangKeys.MESSAGE_DENY_REMOVED);
        defaults.set("messages.unclaimed-info", LangKeys.MESSAGE_UNCLAIMED_INFO);
        defaults.set("messages.claimed-info", LangKeys.MESSAGE_CLAIMED_INFO);
        defaults.set("worlds.plotworld.plot-size", 35);
        defaults.set("worlds.plotworld.road-size", 7);
        defaults.set("worlds.plotworld.ground-y", 64);
        defaults.set("worlds.plotworld.max-plots-per-player", 2);
        defaults.set("worlds.plotworld.claim-price", 100.0);
        defaults.set("worlds.plotworld.sell-refund", 50.0);
        defaults.set("worlds.plotworld.teleport-on-claim", true);
        defaults.set("worlds.plotworld.road-edge-block", "minecraft:smooth_stone_slab");
        defaults.set("worlds.plotworld.road-corner-block", "minecraft:smooth_stone_slab");

        Config config = new Config(dataFolder.resolve("config.yml").toFile(), Config.YAML, defaults);
        boolean economyEnabled = config.getBoolean("economy.enabled", false);
        String economyCurrency = config.getString("economy.currency", "");
        boolean protectRoads = config.getBoolean("settings.protect-roads",
                config.getBoolean("settings.protectRoads", true));
        int autoSaveIntervalTicks = config.getInt("settings.auto-save-interval-ticks",
                config.getInt("settings.autoSaveIntervalTicks", 6000));
        boolean useActionBar = config.getBoolean("settings.use-action-bar",
                config.getBoolean("settings.useActionBar", true));

        Messages messages = new Messages(
                config.getString("messages.prefix", LangKeys.MESSAGE_PREFIX),
                config.getString("messages.enter", LangKeys.MESSAGE_ENTER),
                config.getString("messages.leave", LangKeys.MESSAGE_LEAVE),
                config.getString("messages.enter-denied",
                        config.getString("messages.enterDenied", LangKeys.MESSAGE_ENTER_DENIED)),
                config.getString("messages.build-denied",
                        config.getString("messages.buildDenied", LangKeys.MESSAGE_BUILD_DENIED)),
                config.getString("messages.not-in-plot",
                        config.getString("messages.notInPlot", LangKeys.MESSAGE_NOT_IN_PLOT)),
                config.getString("messages.not-plot-world",
                        config.getString("messages.notPlotWorld", LangKeys.MESSAGE_NOT_PLOT_WORLD)),
                config.getString("messages.already-claimed",
                        config.getString("messages.alreadyClaimed", LangKeys.MESSAGE_ALREADY_CLAIMED)),
                config.getString("messages.claim-success",
                        config.getString("messages.claimSuccess", LangKeys.MESSAGE_CLAIM_SUCCESS)),
                config.getString("messages.delete-success",
                        config.getString("messages.deleteSuccess", LangKeys.MESSAGE_DELETE_SUCCESS)),
                config.getString("messages.plot-unclaimed",
                        config.getString("messages.plotUnclaimed", LangKeys.MESSAGE_PLOT_UNCLAIMED)),
                config.getString("messages.not-owner",
                        config.getString("messages.notOwner", LangKeys.MESSAGE_NOT_OWNER)),
                config.getString("messages.too-many-plots",
                        config.getString("messages.tooManyPlots", LangKeys.MESSAGE_TOO_MANY_PLOTS)),
                config.getString("messages.not-enough-money",
                        config.getString("messages.notEnoughMoney", LangKeys.MESSAGE_NOT_ENOUGH_MONEY)),
                config.getString("messages.trust-added",
                        config.getString("messages.trustAdded", LangKeys.MESSAGE_TRUST_ADDED)),
                config.getString("messages.trust-removed",
                        config.getString("messages.trustRemoved", LangKeys.MESSAGE_TRUST_REMOVED)),
                config.getString("messages.deny-added",
                        config.getString("messages.denyAdded", LangKeys.MESSAGE_DENY_ADDED)),
                config.getString("messages.deny-removed",
                        config.getString("messages.denyRemoved", LangKeys.MESSAGE_DENY_REMOVED)),
                config.getString("messages.unclaimed-info",
                        config.getString("messages.unclaimedInfo", LangKeys.MESSAGE_UNCLAIMED_INFO)),
                config.getString("messages.claimed-info",
                        config.getString("messages.claimedInfo", LangKeys.MESSAGE_CLAIMED_INFO))
        );

        Map<String, PlotWorldConfig> worlds = new HashMap<>();
        ConfigSection worldSection = config.getSection("worlds");
        for (String worldName : worldSection.getKeys(false)) {
            ConfigSection entry = worldSection.getSection(worldName);
            int plotSize = Math.max(1, entry.getInt("plot-size", entry.getInt("plotSize", 35)));
            int roadSize = Math.max(0, entry.getInt("road-size", entry.getInt("roadSize", 7)));
            int groundY = entry.getInt("ground-y", entry.getInt("groundY", 64));
            int maxPlotsPerPlayer = entry.getInt("max-plots-per-player", entry.getInt("maxPlotsPerPlayer", 2));
            double claimPrice = entry.getDouble("claim-price", entry.getDouble("claimPrice", 0.0));
            double sellRefund = entry.getDouble("sell-refund", entry.getDouble("sellRefund", 0.0));
            boolean teleportOnClaim = entry.getBoolean("teleport-on-claim",
                    entry.getBoolean("teleportOnClaim", true));
            String roadEdgeBlock = entry.getString("road-edge-block", "minecraft:smooth_stone_slab");
            String roadCornerBlock = entry.getString("road-corner-block", "minecraft:smooth_stone_slab");
            worlds.put(worldName, new PlotWorldConfig(
                    worldName,
                    plotSize,
                    roadSize,
                    groundY,
                    maxPlotsPerPlayer,
                    claimPrice,
                    sellRefund,
                    teleportOnClaim,
                    roadEdgeBlock,
                    roadCornerBlock
            ));
        }

        if (worlds.isEmpty()) {
            logger.warn("No plot worlds configured. Add entries under worlds in config.yml.");
        }

        return new PluginConfig(
                worlds,
                economyEnabled,
                economyCurrency,
                protectRoads,
                autoSaveIntervalTicks,
                useActionBar,
                messages
        );
    }

    public Map<String, PlotWorldConfig> worlds() {
        return worlds;
    }

    public PlotWorldConfig world(String worldName) {
        return worlds.get(worldName);
    }

    public boolean economyEnabled() {
        return economyEnabled;
    }

    public String economyCurrency() {
        return economyCurrency;
    }

    public boolean protectRoads() {
        return protectRoads;
    }

    public int autoSaveIntervalTicks() {
        return autoSaveIntervalTicks;
    }

    public boolean useActionBar() {
        return useActionBar;
    }

    public Messages messages() {
        return messages;
    }

    public record Messages(
            String prefix,
            String enter,
            String leave,
            String enterDenied,
            String buildDenied,
            String notInPlot,
            String notPlotWorld,
            String alreadyClaimed,
            String claimSuccess,
            String deleteSuccess,
            String plotUnclaimed,
            String notOwner,
            String tooManyPlots,
            String notEnoughMoney,
            String trustAdded,
            String trustRemoved,
            String denyAdded,
            String denyRemoved,
            String unclaimedInfo,
            String claimedInfo
    ) {
    }
}

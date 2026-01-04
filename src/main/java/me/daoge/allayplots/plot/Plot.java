package me.daoge.allayplots.plot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Plot {
    private final String worldName;
    private final PlotId id;
    private UUID owner;
    private String ownerName;
    private boolean home;
    private final Set<UUID> trusted = new HashSet<>();
    private final Set<UUID> denied = new HashSet<>();
    private final Map<String, String> flags = new HashMap<>();
    private final Set<PlotMergeDirection> mergedDirections = new HashSet<>();

    public Plot(String worldName, PlotId id) {
        this.worldName = worldName;
        this.id = id;
    }

    public String getWorldName() {
        return worldName;
    }

    public PlotId getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner, String ownerName) {
        UUID previousOwner = this.owner;
        this.owner = owner;
        if (!Objects.equals(previousOwner, owner)) {
            trusted.clear();
            denied.clear();
            flags.clear();
            mergedDirections.clear();
            home = false;
        }
        if (owner == null) {
            this.ownerName = null;
        } else {
            this.ownerName = ownerName;
        }
    }

    public void setOwner(UUID owner) {
        setOwner(owner, null);
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        if (owner != null) {
            this.ownerName = ownerName;
        }
    }

    public boolean isClaimed() {
        return owner != null;
    }

    public boolean isOwner(UUID playerId) {
        return owner != null && owner.equals(playerId);
    }

    public Set<UUID> getTrusted() {
        return trusted;
    }

    public Set<UUID> getDenied() {
        return denied;
    }

    public boolean isHome() {
        return home;
    }

    public void setHome(boolean home) {
        this.home = home;
    }

    public Map<String, String> getFlags() {
        return flags;
    }

    public Set<PlotMergeDirection> getMergedDirections() {
        return mergedDirections;
    }

    public boolean isMerged(PlotMergeDirection direction) {
        return mergedDirections.contains(direction);
    }

    public void addMergedDirection(PlotMergeDirection direction) {
        if (direction != null) {
            mergedDirections.add(direction);
        }
    }

    public void removeMergedDirection(PlotMergeDirection direction) {
        if (direction != null) {
            mergedDirections.remove(direction);
        }
    }

    public void clearMergedDirections() {
        mergedDirections.clear();
    }

    public boolean getFlag(PlotFlag flag) {
        String raw = flags.get(flag.getLowerCaseName());
        Boolean parsed = PlotFlagValue.parseBoolean(raw);
        return parsed != null ? parsed : flag.defaultValue();
    }

    public String getFlagRaw(String key) {
        return flags.get(key);
    }

    public void setFlagRaw(String key, String value) {
        if (key == null || key.isBlank()) {
            return;
        }
        if (value == null || value.isBlank()) {
            flags.remove(key);
            return;
        }
        flags.put(key, value);
    }

    public void setFlag(PlotFlag flag, boolean value) {
        if (value == flag.defaultValue()) {
            flags.remove(flag.getLowerCaseName());
            return;
        }
        flags.put(flag.getLowerCaseName(), value ? "true" : "false");
    }

    public void removeFlag(String key) {
        flags.remove(key);
    }

    public boolean addTrusted(UUID playerId) {
        return trusted.add(playerId);
    }

    public boolean removeTrusted(UUID playerId) {
        return trusted.remove(playerId);
    }

    public boolean addDenied(UUID playerId) {
        return denied.add(playerId);
    }

    public boolean removeDenied(UUID playerId) {
        return denied.remove(playerId);
    }

    public boolean canEnter(UUID playerId) {
        if (denied.contains(playerId)) {
            return false;
        }
        if (owner == null) {
            return true;
        }
        return owner.equals(playerId) || trusted.contains(playerId) || getFlag(PlotFlag.ENTRY);
    }

    public boolean canBuild(UUID playerId) {
        // Deny list always wins to keep access control predictable.
        if (denied.contains(playerId)) {
            return false;
        }
        if (owner == null) {
            return false;
        }
        return owner.equals(playerId) || trusted.contains(playerId) || getFlag(PlotFlag.BUILD);
    }

    public boolean isDefault() {
        return owner == null && trusted.isEmpty() && denied.isEmpty() && flags.isEmpty()
                && mergedDirections.isEmpty() && !home;
    }
}

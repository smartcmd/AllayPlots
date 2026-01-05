package me.daoge.allayplots.plot;

import java.util.*;

public final class Plot {
    private final String worldName;
    private final PlotId id;
    private final UUID owner;
    private final String ownerName;
    private final boolean home;
    private final Set<UUID> trusted;
    private final Set<UUID> denied;
    private final Map<String, String> flags;
    private final Set<PlotMergeDirection> mergedDirections;

    public Plot(String worldName, PlotId id) {
        this(worldName, id, null, null, false, Set.of(), Set.of(), Map.of(), Set.of());
    }

    private Plot(
            String worldName,
            PlotId id,
            UUID owner,
            String ownerName,
            boolean home,
            Set<UUID> trusted,
            Set<UUID> denied,
            Map<String, String> flags,
            Set<PlotMergeDirection> mergedDirections
    ) {
        this.worldName = Objects.requireNonNull(worldName, "worldName");
        this.id = Objects.requireNonNull(id, "id");
        this.owner = owner;
        this.ownerName = ownerName;
        this.home = home;
        this.trusted = Set.copyOf(trusted);
        this.denied = Set.copyOf(denied);
        this.flags = Map.copyOf(flags);
        this.mergedDirections = Set.copyOf(mergedDirections);
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

    public Plot withOwner(UUID owner, String ownerName) {
        if (!Objects.equals(this.owner, owner)) {
            if (owner == null) {
                return new Plot(worldName, id, null, null, false, Set.of(), Set.of(), Map.of(), Set.of());
            }
            return new Plot(worldName, id, owner, ownerName, false, Set.of(), Set.of(), Map.of(), Set.of());
        }
        if (owner == null) {
            return this;
        }
        if (Objects.equals(this.ownerName, ownerName)) {
            return this;
        }
        return new Plot(worldName, id, owner, ownerName, home, trusted, denied, flags, mergedDirections);
    }

    public Plot withOwner(UUID owner) {
        return withOwner(owner, null);
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getOwnerNameOrUUID() {
        return ownerName != null ? ownerName : owner.toString();
    }

    public Plot withOwnerName(String ownerName) {
        if (owner == null || Objects.equals(this.ownerName, ownerName)) {
            return this;
        }
        return new Plot(worldName, id, owner, ownerName, home, trusted, denied, flags, mergedDirections);
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

    public Plot withHome(boolean home) {
        if (this.home == home) {
            return this;
        }
        return new Plot(worldName, id, owner, ownerName, home, trusted, denied, flags, mergedDirections);
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

    public Plot withMergedDirectionAdded(PlotMergeDirection direction) {
        if (direction == null || mergedDirections.contains(direction)) {
            return this;
        }
        Set<PlotMergeDirection> updated = new HashSet<>(mergedDirections);
        updated.add(direction);
        return new Plot(worldName, id, owner, ownerName, home, trusted, denied, flags, updated);
    }

    public Plot withMergedDirectionRemoved(PlotMergeDirection direction) {
        if (direction == null || !mergedDirections.contains(direction)) {
            return this;
        }
        Set<PlotMergeDirection> updated = new HashSet<>(mergedDirections);
        updated.remove(direction);
        return new Plot(worldName, id, owner, ownerName, home, trusted, denied, flags, updated);
    }

    public Plot withMergedDirectionsCleared() {
        if (mergedDirections.isEmpty()) {
            return this;
        }
        return new Plot(worldName, id, owner, ownerName, home, trusted, denied, flags, Set.of());
    }

    public boolean getFlag(PlotFlag flag) {
        String raw = flags.get(flag.getLowerCaseName());
        Boolean parsed = PlotFlagValue.parseBoolean(raw);
        return parsed != null ? parsed : flag.defaultValue();
    }

    public String getFlagRaw(String key) {
        return flags.get(key);
    }

    public Plot withFlagRaw(String key, String value) {
        if (key == null || key.isBlank()) {
            return this;
        }
        Map<String, String> updated = new HashMap<>(flags);
        if (value == null || value.isBlank()) {
            if (!updated.containsKey(key)) {
                return this;
            }
            updated.remove(key);
        } else {
            if (value.equals(updated.get(key))) {
                return this;
            }
            updated.put(key, value);
        }
        return new Plot(worldName, id, owner, ownerName, home, trusted, denied, updated, mergedDirections);
    }

    public Plot withFlag(PlotFlag flag, boolean value) {
        if (value == flag.defaultValue()) {
            return withoutFlag(flag.getLowerCaseName());
        }
        String key = flag.getLowerCaseName();
        String raw = value ? "true" : "false";
        if (raw.equals(flags.get(key))) {
            return this;
        }
        Map<String, String> updated = new HashMap<>(flags);
        updated.put(key, raw);
        return new Plot(worldName, id, owner, ownerName, home, trusted, denied, updated, mergedDirections);
    }

    public Plot withoutFlag(String key) {
        if (key == null || key.isBlank() || !flags.containsKey(key)) {
            return this;
        }
        Map<String, String> updated = new HashMap<>(flags);
        updated.remove(key);
        return new Plot(worldName, id, owner, ownerName, home, trusted, denied, updated, mergedDirections);
    }

    public Plot withTrustedAdded(UUID playerId) {
        if (playerId == null || trusted.contains(playerId)) {
            return this;
        }
        Set<UUID> updated = new HashSet<>(trusted);
        updated.add(playerId);
        return new Plot(worldName, id, owner, ownerName, home, updated, denied, flags, mergedDirections);
    }

    public Plot withTrustedRemoved(UUID playerId) {
        if (playerId == null || !trusted.contains(playerId)) {
            return this;
        }
        Set<UUID> updated = new HashSet<>(trusted);
        updated.remove(playerId);
        return new Plot(worldName, id, owner, ownerName, home, updated, denied, flags, mergedDirections);
    }

    public Plot withDeniedAdded(UUID playerId) {
        if (playerId == null || denied.contains(playerId)) {
            return this;
        }
        Set<UUID> updated = new HashSet<>(denied);
        updated.add(playerId);
        return new Plot(worldName, id, owner, ownerName, home, trusted, updated, flags, mergedDirections);
    }

    public Plot withDeniedRemoved(UUID playerId) {
        if (playerId == null || !denied.contains(playerId)) {
            return this;
        }
        Set<UUID> updated = new HashSet<>(denied);
        updated.remove(playerId);
        return new Plot(worldName, id, owner, ownerName, home, trusted, updated, flags, mergedDirections);
    }

    public Plot withSettingsFrom(Plot source) {
        if (source == null) {
            return this;
        }
        if (trusted.equals(source.trusted) && denied.equals(source.denied) && flags.equals(source.flags)) {
            return this;
        }
        return new Plot(worldName, id, owner, ownerName, home, source.trusted, source.denied, source.flags, mergedDirections);
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

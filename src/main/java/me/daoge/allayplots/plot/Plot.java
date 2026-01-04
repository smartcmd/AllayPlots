package me.daoge.allayplots.plot;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Plot {
    private final String worldName;
    private final PlotId id;
    private volatile UUID owner;
    private volatile String ownerName;
    private final Set<UUID> trusted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> denied = ConcurrentHashMap.newKeySet();

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
        this.owner = owner;
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

    public boolean canBuild(UUID playerId) {
        // Deny list always wins to keep access control predictable.
        if (denied.contains(playerId)) {
            return false;
        }
        if (owner == null) {
            return false;
        }
        return owner.equals(playerId) || trusted.contains(playerId);
    }

    public boolean isDefault() {
        return owner == null && trusted.isEmpty() && denied.isEmpty();
    }
}

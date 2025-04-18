package com.michael.mmorpg.deathchest;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class DeathChest {
    private final UUID id;
    private final UUID ownerUUID;
    private final String ownerName;
    private final Location location;
    private Location signLocation;
    private final long creationTime;
    private final long expirationTime;
    private boolean claimed;
    private ItemStack[] contents;

    public DeathChest(Player owner, Location location, long protectionTime) {
        this.id = UUID.randomUUID();
        this.ownerUUID = owner.getUniqueId();
        this.ownerName = owner.getName();
        this.location = location;
        this.creationTime = System.currentTimeMillis();
        this.expirationTime = creationTime + (protectionTime * 1000);
        this.claimed = false;
        this.signLocation = null;
    }

    public DeathChest(UUID id, UUID ownerUUID, String ownerName, Location location,
                      long creationTime, long expirationTime, boolean claimed, Location signLocation) {
        this.id = id;
        this.ownerUUID = ownerUUID;
        this.ownerName = ownerName;
        this.location = location;
        this.creationTime = creationTime;
        this.expirationTime = expirationTime;
        this.claimed = claimed;
        this.signLocation = signLocation;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Location getLocation() {
        return location;
    }

    public Location getSignLocation() {
        return signLocation;
    }

    public void setSignLocation(Location signLocation) {
        this.signLocation = signLocation;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public ItemStack[] getContents() {
        return contents;
    }

    public void setContents(ItemStack[] contents) {
        this.contents = contents;
    }

    public boolean isLocked() {
        return System.currentTimeMillis() < expirationTime && !claimed;
    }

    public long getRemainingProtectionTime() {
        long remaining = expirationTime - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    public String getFormattedRemainingTime() {
        long remainingSeconds = getRemainingProtectionTime() / 1000;
        if (remainingSeconds <= 0) {
            return "Unlocked";
        }

        long minutes = remainingSeconds / 60;
        long seconds = remainingSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public boolean canAccess(Player player) {
        return player.getUniqueId().equals(ownerUUID) || !isLocked();
    }
}
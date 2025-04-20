package com.michael.mmorpg.guild;

import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GuildHall {
    private final UUID id;
    private final UUID guildId;
    private final String name;
    private final String world;
    private final Set<ChunkPosition> claimedChunks;
    private static final int MAX_CHUNKS = 15;
    private Location centerLocation;

    public GuildHall(UUID id, UUID guildId, String name, String world, Location centerLocation) {
        this.id = id;
        this.guildId = guildId;
        this.name = name;
        this.world = world;
        this.claimedChunks = new HashSet<>();
        this.centerLocation = centerLocation;

        // Add the initial chunk
        addChunk(centerLocation.getChunk().getX(), centerLocation.getChunk().getZ());
    }

    public UUID getId() {
        return id;
    }

    public UUID getGuildId() {
        return guildId;
    }

    public String getName() {
        return name;
    }

    public String getWorld() {
        return world;
    }

    public Location getCenterLocation() {
        return centerLocation;
    }

    public Set<ChunkPosition> getClaimedChunks() {
        return new HashSet<>(claimedChunks);
    }

    public int getChunkCount() {
        return claimedChunks.size();
    }

    public boolean isFull() {
        return claimedChunks.size() >= MAX_CHUNKS;
    }

    public boolean addChunk(int x, int z) {
        if (isFull()) {
            return false;
        }

        return claimedChunks.add(new ChunkPosition(x, z));
    }

    public boolean removeChunk(int x, int z) {
        return claimedChunks.remove(new ChunkPosition(x, z));
    }

    public boolean isChunkClaimed(int x, int z) {
        return claimedChunks.contains(new ChunkPosition(x, z));
    }

    public boolean isChunkClaimed(Chunk chunk) {
        return isChunkClaimed(chunk.getX(), chunk.getZ());
    }

    public boolean isAdjacentToClaimedChunk(int x, int z) {
        return claimedChunks.contains(new ChunkPosition(x + 1, z)) ||
                claimedChunks.contains(new ChunkPosition(x - 1, z)) ||
                claimedChunks.contains(new ChunkPosition(x, z + 1)) ||
                claimedChunks.contains(new ChunkPosition(x, z - 1));
    }

    public static class ChunkPosition {
        private final int x;
        private final int z;

        public ChunkPosition(int x, int z) {
            this.x = x;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkPosition that = (ChunkPosition) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }

        @Override
        public String toString() {
            return "(" + x + ", " + z + ")";
        }
    }
}
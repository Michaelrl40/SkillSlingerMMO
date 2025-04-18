package com.michael.mmorpg.graveyard;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class Graveyard {
    private final UUID id;
    private final String name;
    private final Location location;

    public Graveyard(String name, Location location) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.location = location;
    }

    public Graveyard(UUID id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

}
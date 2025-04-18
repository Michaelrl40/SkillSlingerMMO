package com.michael.mmorpg.skills.windwaker;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class Gust extends Skill {
    private final double dashDistance = 2.0; // 2 blocks

    public Gust(ConfigurationSection config) {
        super(config);
    }

    @Override
    protected void performSkill(Player player) {
        // Get player's direction (excluding Y to keep dash horizontal)
        Vector direction = player.getLocation().getDirection();
        direction.setY(0);
        direction.normalize();

        // Calculate the dash vector
        Vector dashVector = direction.multiply(dashDistance);

        // Add a small upward boost to prevent getting stuck on small heights
        dashVector.setY(0.2);

        // Apply the dash
        player.setVelocity(dashVector);

        // Play effects
        Location loc = player.getLocation();

        // Particle trail
        player.getWorld().spawnParticle(Particle.CLOUD,
                loc.add(0, 1, 0), // Spawn at player's torso
                20, // Amount of particles
                0.2, 0.2, 0.2, // Spread
                0.1); // Speed

        // Sound effect
        player.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.5f);
    }
}
package com.michael.mmorpg.skills.hunter;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import com.michael.mmorpg.skills.Skill;


public class DisengageSkill extends Skill {
    private final double distance;
    private final double heightMultiplier;


    public DisengageSkill(ConfigurationSection config) {
        super(config);
        this.distance = config.getDouble("distance", 15.0);
        this.heightMultiplier = config.getDouble("heightMultiplier", 0.8);
    }

    @Override
    protected void performSkill(Player player) {
        // Get the direction the player is facing
        Vector direction = player.getLocation().getDirection();

        // Calculate the backward direction (opposite of where player faces)
        Vector backwardDirection = direction.multiply(-1).setY(0).normalize();


        // Calculate the initial velocity for the arc jump
        double horizontalSpeed = distance / 20.0; // Adjust for desired travel time
        double verticalSpeed = Math.sqrt(heightMultiplier * 0.98); // Calculate vertical velocity for desired arc height

        Vector velocity = backwardDirection.multiply(horizontalSpeed).setY(verticalSpeed);
        player.setVelocity(velocity);

        // Apply fall damage immunity
        applyFallDamageImmunity(player);

        // Play the disengage sound effect
        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_RABBIT_JUMP,
                1.0f,
                0.8f
        );

        setSkillSuccess(true);
    }

    private void applyFallDamageImmunity(Player player) {
        // Add metadata to track immunity
        player.setMetadata("disengage_immunity", new FixedMetadataValue(plugin, true));

        // Remove immunity after 2 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && player.hasMetadata("disengage_immunity")) {
                    player.removeMetadata("disengage_immunity", plugin);
                }
            }
        }.runTaskLater(plugin, 40L); // 40 ticks = 2 seconds
    }

}
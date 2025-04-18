package com.michael.mmorpg.skills.ninja;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.util.Vector;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class BackflipSkill extends Skill {
    private final double distance;  // How far back to flip
    private final double height;    // Height of the flip
    private final double particleDensity;  // Controls particle trail density
    private final long immunityDuration;   // Duration of fall damage immunity in ticks

    public BackflipSkill(ConfigurationSection config) {
        super(config);
        this.distance = config.getDouble("distance", 4.0);
        this.height = config.getDouble("height", 1.0);
        this.particleDensity = config.getDouble("particleDensity", 2.0);
        // Convert immunity duration from config seconds to ticks (20 ticks = 1 second)
        this.immunityDuration = (long)(config.getDouble("immunityduration", 1.5) * 20);
    }

    @Override
    protected void performSkill(Player player) {
        // Store original direction to flip backwards
        Vector direction = player.getLocation().getDirection().multiply(-1);

        // Calculate the jump vector with both backwards and upwards components
        Vector jumpVector = direction.multiply(distance)
                .setY(height);

        // Apply the velocity to the player
        player.setVelocity(jumpVector);

        // Apply fall damage immunity
        applyFallImmunity(player);

        // Create particle trail effect
        createFlipTrail(player);

        // Play flip sound
        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_BAT_TAKEOFF,
                0.5f,
                1.2f
        );

        setSkillSuccess(true);
    }

    private void applyFallImmunity(Player player) {
        // Apply immunity metadata
        player.setMetadata("disengage_immunity", new FixedMetadataValue(plugin, true));

        // Remove immunity after duration
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && player.hasMetadata("disengage_immunity")) {
                    player.removeMetadata("disengage_immunity", plugin);
                }
            }
        }.runTaskLater(plugin, immunityDuration);
    }

    private void createFlipTrail(Player player) {
        new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = 10;  // Duration of trail effect

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    cancel();
                    return;
                }

                // Create particle trail at player's location
                Location particleLoc = player.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(
                        Particle.CLOUD,
                        particleLoc,
                        5,  // Number of particles
                        0.2, 0.2, 0.2,  // Spread
                        0.02  // Particle speed
                );

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
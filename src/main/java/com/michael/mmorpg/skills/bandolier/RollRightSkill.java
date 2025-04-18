package com.michael.mmorpg.skills.bandolier;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class RollRightSkill extends Skill {
    private final double velocity;
    private final double height;
    private final double invulnFrames;
    private final int duration;

    public RollRightSkill(ConfigurationSection config) {
        super(config);
        this.velocity = config.getDouble("velocity", 1.2);
        this.height = config.getDouble("height", 0.2);
        this.invulnFrames = config.getDouble("invulnframes", 0.5);
        this.duration = config.getInt("duration", 10);
    }

    @Override
    protected void performSkill(Player player) {
        if (player.isSneaking()) {
            setSkillSuccess(false);
            player.sendMessage("§c✦ Cannot roll while sneaking!");
            return;
        }

        // Calculate right direction relative to player's facing
        Vector direction = player.getLocation().getDirection().crossProduct(new Vector(0, 1, 0))
                .normalize().multiply(velocity);

        // Add slight upward velocity for initial "hop"
        direction.setY(height);

        // Apply the initial velocity
        player.setVelocity(direction);

        // Add brief invulnerability
        player.setInvulnerable(true);

        // Play roll start sound
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.2f);

        // Prevent fall damage during roll
        player.setFallDistance(0f);

        // Create the rolling effect
        new BukkitRunnable() {
            int ticks = 0;
            Location lastLocation = player.getLocation();

            @Override
            public void run() {
                if (ticks >= duration || !player.isOnline()) {
                    player.setInvulnerable(false);
                    this.cancel();
                    return;
                }

                // Create rolling particles between last location and current location
                Location currentLoc = player.getLocation();
                Vector between = currentLoc.toVector().subtract(lastLocation.toVector()).normalize().multiply(0.5);
                for (double d = 0; d <= lastLocation.distance(currentLoc); d += 0.5) {
                    Location particleLoc = lastLocation.clone().add(between.clone().multiply(d));
                    player.getWorld().spawnParticle(
                            Particle.CLOUD,
                            particleLoc,
                            2, 0.1, 0.1, 0.1, 0.02
                    );
                }

                lastLocation = currentLoc;
                ticks++;

                // Keep player low to ground during roll
                if (player.getVelocity().getY() > height) {
                    Vector newVel = player.getVelocity();
                    newVel.setY(height);
                    player.setVelocity(newVel);
                }

                // Maintain horizontal momentum
                if (ticks < duration / 2) {
                    Vector currentVel = player.getVelocity();
                    if (currentVel.length() < velocity) {
                        currentVel.normalize().multiply(velocity);
                        currentVel.setY(player.getVelocity().getY());
                        player.setVelocity(currentVel);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Remove invulnerability after duration
        new BukkitRunnable() {
            @Override
            public void run() {
                player.setInvulnerable(false);
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 0.8f);
            }
        }.runTaskLater(plugin, (long)(invulnFrames * 20));

        setSkillSuccess(true);
    }
}
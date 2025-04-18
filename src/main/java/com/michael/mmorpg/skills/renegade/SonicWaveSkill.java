package com.michael.mmorpg.skills.renegade;

import com.michael.mmorpg.party.Party;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class SonicWaveSkill extends Skill {
    private final double damage;
    private final double range;
    private final double projectileSpeed;
    private final double markDuration;
    private final double width;

    public SonicWaveSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 8.0);
        this.range = config.getDouble("range", 20.0);
        this.projectileSpeed = config.getDouble("projectilespeed", 1.5);
        this.markDuration = config.getDouble("markDuration", 6.0);
        this.width = config.getDouble("width", 2.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Launch wind charge projectile
        Location startLoc = player.getEyeLocation();
        WindCharge windCharge = (WindCharge) player.getWorld().spawnEntity(startLoc, EntityType.WIND_CHARGE);
        windCharge.setShooter(player);

        // Set velocity
        Vector direction = player.getLocation().getDirection();
        windCharge.setVelocity(direction.multiply(projectileSpeed));

        // Play launch sound
        player.getWorld().playSound(startLoc, "entity.wind_charge.throw", 1.0f, 1.0f);

        // Track the projectile
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!windCharge.isValid() || ticks > 100 || windCharge.getLocation().distance(startLoc) > range) {
                    windCharge.remove();
                    cancel();
                    return;
                }

                // Check for entity collisions
                for (Entity entity : windCharge.getNearbyEntities(width, width, width)) {
                    if (!(entity instanceof LivingEntity) || entity == player) continue;
                    if (entity.hasMetadata("sonic_wave_marked")) continue;

                    LivingEntity target = (LivingEntity) entity;

                    // Check party protection
                    if (target instanceof Player) {
                        Party casterParty = plugin.getPartyManager().getParty(player);
                        if (casterParty != null && casterParty.isMember((Player) target)) {
                            continue;
                        }
                    }

                    // Apply damage
                    target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
                    target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
                    target.damage(damage, player);
                    target.setMetadata("sonic_wave_marked", new FixedMetadataValue(plugin, true));

                    // Play hit sound
                    player.getWorld().playSound(startLoc, "entity.wind_charge.wind_burst", 1.0f, 1.0f);

                    // Remove mark after duration
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (target.isValid()) {
                                target.removeMetadata("sonic_wave_marked", plugin);
                            }
                        }
                    }.runTaskLater(plugin, (long)(markDuration * 20));
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }
}
package com.michael.mmorpg.skills.frostmage;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.party.Party;

import java.util.List;
import java.util.stream.Collectors;

public class BlizzardSkill extends Skill {
    private final double range;
    private final double damage;
    private final double aoesize;
    private final int duration;
    private final int snowballsPerTick;
    private final int slowduration;
    private final int slowintensity;

    public BlizzardSkill(ConfigurationSection config) {
        super(config);
        this.range = config.getDouble("range", 20.0);
        this.damage = config.getDouble("damage", 5.0);
        this.aoesize = config.getDouble("aoesize", 10.0);
        this.duration = config.getInt("duration", 10);
        this.snowballsPerTick = config.getInt("snowballspertick", 2);
        this.slowduration = config.getInt("slowduration", 3);
        this.slowintensity = config.getInt("slowintensity", 2);
    }

    @Override
    protected void performSkill(Player player) {
        Location targetLocation = player.getTargetBlock(null, (int) range).getLocation();
        if (targetLocation == null) {
            targetLocation = player.getLocation().add(player.getLocation().getDirection().multiply(range));
        }

        Party casterParty = plugin.getPartyManager().getParty(player);

        // Show initial cast effect
        targetLocation.getWorld().playSound(targetLocation, Sound.BLOCK_SNOW_BREAK, 1.0f, 0.5f);
        showAoEIndicator(targetLocation);

        final Location finalTarget = targetLocation.clone().add(0, 0.5, 0);

        // Start the blizzard
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }

                // Get valid targets (excluding party members)
                List<Entity> targets = finalTarget.getWorld().getNearbyEntities(finalTarget, aoesize/2, 5, aoesize/2)
                        .stream()
                        .filter(e -> e instanceof LivingEntity && !e.equals(player))
                        .filter(e -> !(e instanceof Player) ||
                                casterParty == null ||
                                !casterParty.shouldPreventInteraction(player, e, true))
                        .collect(Collectors.toList());

                // Spawn multiple snowballs per tick
                for (int i = 0; i < snowballsPerTick; i++) {
                    Location snowballSpawn;
                    Vector direction;

                    if (!targets.isEmpty() && Math.random() < 0.9) {
                        Entity target = targets.get((int)(Math.random() * targets.size()));

                        double offsetX = (Math.random() - 0.5) * 2.0;
                        double offsetZ = (Math.random() - 0.5) * 2.0;
                        snowballSpawn = target.getLocation().clone().add(offsetX, 15, offsetZ);

                        direction = target.getLocation().clone().add(
                                (Math.random() - 0.5) * 0.5,
                                0,
                                (Math.random() - 0.5) * 0.5
                        ).subtract(snowballSpawn).toVector().normalize();
                    } else {
                        double offsetX = (Math.random() * aoesize) - (aoesize / 2);
                        double offsetZ = (Math.random() * aoesize) - (aoesize / 2);
                        snowballSpawn = finalTarget.clone().add(offsetX, 15, offsetZ);
                        direction = new Vector(0, -1, 0);
                    }

                    Snowball snowball = finalTarget.getWorld().spawn(snowballSpawn, Snowball.class);
                    snowball.setShooter(player);
                    snowball.setVelocity(direction);
// Update damage metadata to match new system
                    snowball.setMetadata("skill_damage", new FixedMetadataValue(getPlugin(), player));
                    snowball.setMetadata("skill_damage_amount", new FixedMetadataValue(getPlugin(), damage));
                    snowball.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
// Keep other metadata
                    snowball.setMetadata("blizzard_slow", new FixedMetadataValue(getPlugin(), true));
                    snowball.setMetadata("blizzard_duration", new FixedMetadataValue(getPlugin(), slowduration));
                    snowball.setMetadata("blizzard_intensity", new FixedMetadataValue(getPlugin(), slowintensity));
                    snowball.setMetadata("skill_shooter", new FixedMetadataValue(getPlugin(), player));
                    // Add party check metadata
                    if (casterParty != null) {
                        snowball.setMetadata("caster_party", new FixedMetadataValue(getPlugin(), casterParty));
                    }

                    spawnSnowballEffects(snowball.getLocation());
                }

                ticks++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        // Create ongoing blizzard particle effects
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }

                for (int i = 0; i < 5; i++) {
                    double angle = Math.random() * 2 * Math.PI;
                    double radius = Math.random() * (aoesize/2);
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;

                    Location particleLoc = finalTarget.clone().add(x, Math.random() * 5, z);
                    particleLoc.getWorld().spawnParticle(Particle.SNOWFLAKE, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                    particleLoc.getWorld().spawnParticle(Particle.CLOUD, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                }

                ticks++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private void spawnSnowballEffects(Location location) {
        location.getWorld().spawnParticle(Particle.SNOWFLAKE, location, 3, 0.1, 0.1, 0.1, 0);
        location.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, location, 2, 0.1, 0.1, 0.1, 0);
    }

    private void showAoEIndicator(Location center) {
        double halfSize = aoesize / 2;
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 20) {
                    cancel();
                    return;
                }

                for (double x = -halfSize; x <= halfSize; x += 0.5) {
                    for (double z = -halfSize; z <= halfSize; z += 0.5) {
                        if (Math.abs(x) >= halfSize - 0.5 || Math.abs(z) >= halfSize - 0.5 ||
                                (ticks % 5 == 0 && Math.random() < 0.1)) {
                            Location particleLoc = center.clone().add(x, 0.1, z);
                            center.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, particleLoc, 1, 0, 0, 0, 0);
                            center.getWorld().spawnParticle(Particle.SNOWFLAKE, particleLoc.add(0, 0.5, 0), 1, 0, 0, 0, 0);
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }
}
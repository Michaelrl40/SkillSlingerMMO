package com.michael.mmorpg.skills.elementalranger;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class TsunamiCallSkill extends Skill {
    private final double radius;
    private final double knockbackStrength;
    private final double damage;
    private final int slowDuration;
    private final int slowAmplifier;
    private final double waveSpeed;
    private final int waveHeight;

    public TsunamiCallSkill(ConfigurationSection config) {
        super(config);
        this.radius = config.getDouble("radius", 8.0);
        this.knockbackStrength = config.getDouble("knockbackstrength", 1.5);
        this.damage = config.getDouble("damage", 5.0);
        this.slowDuration = config.getInt("slowduration", 3) * 20;
        this.slowAmplifier = config.getInt("slowamplifier", 1);
        this.waveSpeed = config.getDouble("wavespeed", 0.5);
        this.waveHeight = config.getInt("waveheight", 3);
    }

    @Override
    protected void performSkill(Player player) {
        // Cancel if player doesn't have a bow
        if (!(player.getInventory().getItemInMainHand().getType() == Material.BOW ||
                player.getInventory().getItemInMainHand().getType() == Material.CROSSBOW)) {
            player.sendMessage("§c✦ You must be holding a bow or crossbow!");
            setSkillSuccess(false);
            return;
        }

        if (player.hasMetadata("enhanced_arrow")) {
            String activeSkill = player.getMetadata("enhanced_arrow").get(0).value().toString();
            player.sendMessage("§c✦ You already have " + activeSkill + " active!");
            setSkillSuccess(false);
            return;
        }

        // Mark the player as having tsunami ready
        player.setMetadata("enhanced_arrow", new FixedMetadataValue(plugin, "Tsunami Call"));
        player.setMetadata("tsunami_ready", new FixedMetadataValue(plugin, true));

        // Visual effect to show skill is ready
        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (!player.hasMetadata("tsunami_ready") || ticks++ > 100) { // 5 second timeout
                    this.cancel();
                    if (player.hasMetadata("enhanced_arrow")) {
                        player.removeMetadata("enhanced_arrow", plugin);
                    }
                    return;
                }

                // Spiral water effect around player
                Location loc = player.getLocation();
                angle += Math.PI / 8;
                double x = Math.cos(angle) * 0.5;
                double z = Math.sin(angle) * 0.5;
                loc.add(x, 1, z);

                player.getWorld().spawnParticle(
                        Particle.SPLASH,
                        loc,
                        2, 0, 0, 0, 0
                );

                player.getWorld().spawnParticle(
                        Particle.DRIPPING_DRIPSTONE_WATER,
                        loc,
                        1, 0, 0, 0, 0
                );

                loc.subtract(x, 1, z);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.sendMessage("§b✦ Tsunami Call ready! Shoot an arrow to summon a wave!");
        setSkillSuccess(true);
    }

    public void createWave(Location center, Vector direction, Player caster) {
        World world = center.getWorld();
        if (world == null) return;

        // Initial wave sound
        world.playSound(center, Sound.AMBIENT_UNDERWATER_LOOP, 1.0f, 1.0f);

        // Calculate perpendicular vector for wave width
        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();

        // Create advancing wave
        new BukkitRunnable() {
            double distance = 0;

            @Override
            public void run() {
                if (distance >= radius) {
                    this.cancel();
                    return;
                }

                // Calculate current wave position
                Location waveLoc = center.clone().add(direction.clone().multiply(distance));

                // Create wave particles
                for (double offset = -radius/2; offset <= radius/2; offset += 0.5) {
                    Location particleLoc = waveLoc.clone().add(right.clone().multiply(offset));

                    // Create wave height
                    for (int y = 0; y < waveHeight; y++) {
                        // Main wave particles
                        world.spawnParticle(
                                Particle.SPLASH,
                                particleLoc.clone().add(0, y, 0),
                                3, 0.2, 0.2, 0.2, 0
                        );

                        // Additional water effects
                        if (y == waveHeight - 1) {
                            world.spawnParticle(
                                    Particle.DRIPPING_DRIPSTONE_WATER,
                                    particleLoc.clone().add(0, y, 0),
                                    1, 0.1, 0.1, 0.1, 0
                            );
                        }

                        // Blue particle trail
                        world.spawnParticle(
                                Particle.DUST,
                                particleLoc.clone().add(0, y, 0),
                                0, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(0, 150, 255), 1.0f)
                        );
                    }
                }

                // Affect entities
                world.getNearbyEntities(waveLoc, radius/2, waveHeight, radius/2).forEach(entity -> {
                    if (entity instanceof LivingEntity && entity != caster) {
                        LivingEntity target = (LivingEntity) entity;

                        // Apply knockback
                        Vector knockback = direction.clone().multiply(knockbackStrength);
                        target.setVelocity(knockback.add(new Vector(0, 0.3, 0)));

                        // Apply slow
                        target.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS,
                                slowDuration,
                                slowAmplifier
                        ));

                        // Apply damage
                        target.damage(damage, caster);
                    }
                });

                // Wave sound
                if (distance % 2 == 0) {
                    world.playSound(waveLoc, Sound.BLOCK_WATER_AMBIENT, 0.5f, 1.0f);
                }

                distance += waveSpeed;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
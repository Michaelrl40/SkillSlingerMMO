package com.michael.mmorpg.skills.elementalranger;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class NetherShotSkill extends Skill {
    private final double radius;
    private final double damage;
    private final double knockbackStrength;

    public NetherShotSkill(ConfigurationSection config) {
        super(config);
        this.radius = config.getDouble("radius", 4.0);
        this.damage = config.getDouble("damage", 12.0);
        this.knockbackStrength = config.getDouble("knockbackstrength", 0.5);
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

        player.setMetadata("enhanced_arrow", new FixedMetadataValue(plugin, "Nether Shot"));
        player.setMetadata("nether_shot_ready", new FixedMetadataValue(plugin, true));

        // Visual effect to show skill is ready
        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (!player.hasMetadata("nether_shot_ready") || ticks++ > 100) { // 5 second timeout
                    this.cancel();
                    if (player.hasMetadata("enhanced_arrow")) {
                        player.removeMetadata("enhanced_arrow", plugin);
                    }
                    return;
                }

                // Flame spiral effect around player
                Location loc = player.getLocation();
                angle += Math.PI / 8;
                double x = Math.cos(angle) * 0.5;
                double z = Math.sin(angle) * 0.5;
                loc.add(x, 1, z);

                player.getWorld().spawnParticle(
                        Particle.FLAME,
                        loc,
                        1, 0, 0, 0, 0
                );

                loc.subtract(x, 1, z);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.sendMessage("§c✦ Nether Shot ready! Shoot an arrow to unleash explosive power!");
        setSkillSuccess(true);
    }

    public void createExplosion(Location center, Player caster) {
        World world = center.getWorld();
        if (world == null) return;

        // Don't create real explosion, just effects
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);

        // Create expanding fire ring effect
        new BukkitRunnable() {
            double size = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (size >= radius || ticks++ > 10) {
                    this.cancel();
                    return;
                }

                // Create circle of particles
                for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
                    double x = Math.cos(angle) * size;
                    double z = Math.sin(angle) * size;
                    Location particleLoc = center.clone().add(x, 0.2, z);

                    // Flame particles
                    world.spawnParticle(
                            Particle.FLAME,
                            particleLoc,
                            1, 0.1, 0.1, 0.1, 0.02
                    );

                    // Nether particles
                    world.spawnParticle(
                            Particle.CRIMSON_SPORE,
                            particleLoc,
                            1, 0.1, 0.1, 0.1, 0
                    );

                    // Smoke effect
                    if (Math.random() < 0.3) {
                        world.spawnParticle(
                                Particle.SMOKE,
                                particleLoc.add(0, 0.5, 0),
                                1, 0.1, 0.1, 0.1, 0.02
                        );
                    }
                }

                // Deal damage to entities in range
                world.getNearbyEntities(center, size, size, size).forEach(entity -> {
                    if (entity instanceof LivingEntity && entity != caster) {
                        LivingEntity target = (LivingEntity) entity;

                        // Calculate damage falloff based on distance
                        double distance = target.getLocation().distance(center);
                        double damageMultiplier = 1 - (distance / radius);
                        if (damageMultiplier > 0) {
                            double finalDamage = damage * damageMultiplier;
                            target.damage(finalDamage, caster);

                            // Knock back entities from center
                            Vector knockback = target.getLocation().subtract(center).toVector()
                                    .normalize()
                                    .multiply(knockbackStrength)
                                    .setY(0.2);
                            target.setVelocity(target.getVelocity().add(knockback));

                            // Set target on fire briefly
                            target.setFireTicks(40); // 2 seconds
                        }
                    }
                });

                size += 0.5;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Additional fire particles rising
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 20) {
                    this.cancel();
                    return;
                }

                for (int i = 0; i < 3; i++) {
                    Location particleLoc = center.clone().add(
                            Math.random() * radius * 2 - radius,
                            Math.random() * 2,
                            Math.random() * radius * 2 - radius
                    );

                    world.spawnParticle(
                            Particle.FLAME,
                            particleLoc,
                            0, 0, 0.1, 0, 0
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
package com.michael.mmorpg.skills.skyknight;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;

public class WindSpearSkill extends Skill {
    private final double baseDamage;
    private final double glidingDamageMultiplier;
    private final double knockbackStrength;
    private final double skillShotRange;
    private final double skillShotWidth;
    private final double skillShotSpeed;

    public WindSpearSkill(ConfigurationSection config) {
        super(config);
        this.baseDamage = config.getDouble("basedamage", 8.0);
        this.glidingDamageMultiplier = config.getDouble("glidingmultiplier", 1.75);
        this.knockbackStrength = config.getDouble("knockbackstrength", 0.5);
        this.skillShotRange = config.getDouble("skillshotrange", 5.0);
        this.skillShotWidth = config.getDouble("skillshotwidth", 0.8);
        this.skillShotSpeed = config.getDouble("skillshotspeed", 0.5);
    }

    @Override
    protected void performSkill(Player player) {
        Vector direction = player.getLocation().getDirection();
        Location startLoc = player.getLocation().add(0, 1, 0);

        // Calculate damage based on gliding state
        double damage = baseDamage;
        if (player.isGliding()) {
            damage *= glidingDamageMultiplier;
        }

        final double finalDamage = damage;

        // Create the skill shot projectile effect
        new BukkitRunnable() {
            private Location currentLoc = startLoc.clone();
            private double distanceTraveled = 0;
            private boolean hasHit = false;

            @Override
            public void run() {
                if (hasHit || distanceTraveled >= skillShotRange) {
                    this.cancel();
                    return;
                }

                // Move the projectile forward
                currentLoc.add(direction.clone().multiply(skillShotSpeed));
                distanceTraveled += skillShotSpeed;

                // Check for entities in the path
                for (Entity entity : currentLoc.getWorld().getNearbyEntities(currentLoc, skillShotWidth, skillShotWidth, skillShotWidth)) {
                    if (!(entity instanceof LivingEntity) || entity == player) continue;
                    LivingEntity target = (LivingEntity) entity;

                    // Skip if not a valid target
                    if (validateTarget(player, target)) continue;

                    // Hit effect
                    onHit(player, target, finalDamage);
                    hasHit = true;
                    this.cancel();
                    return;
                }

                // Visual effects
                if (player.isGliding()) {
                    playGlidingProjectileEffects(currentLoc, direction);
                } else {
                    playNormalProjectileEffects(currentLoc, direction);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    private void onHit(Player player, LivingEntity target, double damage) {
        // Apply damage
        target.setMetadata("skill_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new org.bukkit.metadata.FixedMetadataValue(plugin, damage));
        target.damage(damage, player);

        // Apply knockback
        Vector knockback = target.getLocation().subtract(player.getLocation()).toVector()
                .normalize()
                .multiply(knockbackStrength)
                .setY(0.2);
        target.setVelocity(knockback);

        // Effects
        if (player.isGliding()) {
            playGlidingHitEffects(target.getLocation());
            player.sendMessage("§6✦ Aerial Wind Spear strike! §e(+" +
                    String.format("%.0f", (glidingDamageMultiplier - 1) * 100) + "% damage)");
        } else {
            playNormalHitEffects(target.getLocation());
        }
    }

    private void playGlidingProjectileEffects(Location loc, Vector direction) {
        double angle = loc.getWorld().getTime() * 0.5;
        Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

        // Spiral effect
        Location spiral1 = loc.clone().add(perpendicular.clone().multiply(Math.sin(angle) * 0.3))
                .add(0, Math.cos(angle) * 0.3, 0);
        Location spiral2 = loc.clone().add(perpendicular.clone().multiply(-Math.sin(angle) * 0.3))
                .add(0, -Math.cos(angle) * 0.3, 0);

        loc.getWorld().spawnParticle(Particle.CLOUD, spiral1, 1, 0, 0, 0, 0);
        loc.getWorld().spawnParticle(Particle.CLOUD, spiral2, 1, 0, 0, 0, 0);
    }

    private void playNormalProjectileEffects(Location loc, Vector direction) {
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 1, 0.1, 0.1, 0.1, 0);
    }

    private void playGlidingHitEffects(Location loc) {
        loc.getWorld().spawnParticle(Particle.FLASH, loc.add(0, 1, 0), 1, 0, 0, 0, 0);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 15, 0.3, 0.3, 0.3, 0.2);
        loc.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_SWOOP, 1.0f, 1.5f);
    }

    private void playNormalHitEffects(Location loc) {
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.add(0, 1, 0), 1, 0, 0, 0, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
    }
}
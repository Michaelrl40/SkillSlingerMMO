package com.michael.mmorpg.skills.guardian;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class RamSkill extends Skill {
    private final double damage;
    private final double knockbackStrength;
    private final double chargeSpeed;
    private final int maxChargeTicks;

    public RamSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 12.0);
        this.knockbackStrength = config.getDouble("knockbackstrength", 2.5);
        this.chargeSpeed = config.getDouble("chargespeed", 1.5);
        this.maxChargeTicks = config.getInt("maxchargeticks", 40);
    }

    @Override
    protected void performSkill(Player player) {
        LivingEntity target = getTargetEntity(player, targetRange);

        if (target == null || validateTarget(player, target)) {
            player.sendMessage("§c✦ No valid target in range!");
            setSkillSuccess(false);
            return;
        }

        currentTarget = target;
        Location startLoc = player.getLocation();
        Location targetLoc = target.getLocation();
        Vector direction = targetLoc.subtract(startLoc).toVector().normalize();

        // Start charging
        chargeTowardsTarget(player, target, direction);

        // Set the cooldown as soon as skill is successfully started
        plugin.getSkillManager().setCooldown(player, getName(), getCooldown());
        setSkillSuccess(true);
    }


    private void chargeTowardsTarget(Player player, LivingEntity target, Vector initialDirection) {
        // Initial charge sound
        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_IRON_GOLEM_ATTACK,
                1.0f,
                0.8f
        );

        // Prevent fall damage
        player.setFallDistance(0);

        // Set player as charging
        player.setMetadata("charging", new FixedMetadataValue(plugin, true));

        new BukkitRunnable() {
            int ticks = 0;
            boolean hasHit = false;
            Vector currentDirection = initialDirection.clone();

            @Override
            public void run() {
                // Check if charge should end
                if (ticks >= maxChargeTicks || hasHit || !player.isValid() || !target.isValid()) {
                    endCharge(player);
                    cancel();
                    return;
                }

                // Update direction to target each tick for better tracking
                currentDirection = target.getLocation().subtract(player.getLocation()).toVector().normalize();

                // Apply velocity towards target
                Vector velocity = currentDirection.multiply(chargeSpeed);
                player.setVelocity(velocity);

                // Create trail effect
                player.getWorld().spawnParticle(
                        Particle.CLOUD,
                        player.getLocation().add(0, 1, 0),
                        5, 0.2, 0.2, 0.2, 0.05
                );

                // Check for collision with target
                if (isColliding(player, target)) {
                    handleImpact(player, target, currentDirection);
                    hasHit = true;
                    endCharge(player);
                    cancel();
                    return;
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean isColliding(Player player, LivingEntity target) {
        // Check if entities are very close to each other
        return player.getLocation().distanceSquared(target.getLocation()) < 2.25; // 1.5 blocks radius
    }

    private void handleImpact(Player player, LivingEntity target, Vector chargeDirection) {
        // Calculate knockback
        Vector knockback = chargeDirection.clone().multiply(knockbackStrength).setY(0.5);

        // Apply knockback next tick to ensure it works
        new BukkitRunnable() {
            @Override
            public void run() {
                target.setVelocity(knockback);
            }
        }.runTaskLater(plugin, 1L);

        // Apply damage
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.damage(damage, player);

        // Impact effects
        Location impactLoc = target.getLocation().add(0, 1, 0);
        World world = impactLoc.getWorld();

        // Explosion effect
        world.spawnParticle(
                Particle.EXPLOSION_EMITTER,
                impactLoc,
                3, 0.2, 0.2, 0.2, 0
        );

        // Impact particles
        world.spawnParticle(
                Particle.CRIT,
                impactLoc,
                30, 0.5, 0.5, 0.5, 0.3
        );

        // Shockwave effect
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
            double x = Math.cos(angle) * 1.5;
            double z = Math.sin(angle) * 1.5;
            world.spawnParticle(
                    Particle.LARGE_SMOKE,
                    impactLoc.clone().add(x, 0, z),
                    1, 0.1, 0.1, 0.1, 0.05
            );
        }

        // Impact sound
        world.playSound(
                impactLoc,
                Sound.ENTITY_IRON_GOLEM_DAMAGE,
                1.0f,
                0.6f
        );

        // Clean up damage metadata
        new BukkitRunnable() {
            @Override
            public void run() {
                target.removeMetadata("skill_damage", plugin);
                target.removeMetadata("skill_damage_amount", plugin);
            }
        }.runTaskLater(plugin, 1L);
    }

    private void endCharge(Player player) {
        player.setFallDistance(0);
        player.removeMetadata("charging", plugin);
    }
}
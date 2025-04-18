package com.michael.mmorpg.skills.skyknight;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class DiveBombSkill extends Skill {
    private final double baseDamage;
    private final double heightMultiplier;
    private final double maxHeightBonus;
    private final double radius;
    private final double knockbackStrength;
    private final double diveSpeed;

    public DiveBombSkill(ConfigurationSection config) {
        super(config);
        this.baseDamage = config.getDouble("basedamage", 10.0);
        this.heightMultiplier = config.getDouble("heightmultiplier", 0.5);
        this.maxHeightBonus = config.getDouble("maxheightbonus", 20.0);
        this.radius = config.getDouble("radius", 4.0);
        this.knockbackStrength = config.getDouble("knockbackstrength", 1.0);
        this.diveSpeed = config.getDouble("divespeed", 2.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Check if player is gliding
        if (!player.isGliding()) {
            player.sendMessage("§c✦ You must be gliding to use Dive Bomb!");
            setSkillSuccess(false);
            return;
        }

        // Check if high enough
        if (player.getLocation().getY() <= player.getLocation().getWorld().getHighestBlockYAt(player.getLocation()) + 3) {
            player.sendMessage("§c✦ You need to be higher to use Dive Bomb!");
            setSkillSuccess(false);
            return;
        }

        // Store initial height for damage calculation
        Location startLoc = player.getLocation();

        // Start the dive animation and tracking
        startDive(player, startLoc.getY());
        setSkillSuccess(true);
    }

    private void startDive(Player player, double startHeight) {
        // Initial dive effect
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_SWOOP, 1.0f, 0.5f);
        spawnDiveEffects(player);

        // Create dive task
        BukkitTask diveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isValid()) {
                return;
            }

            // Force more vertical movement
            Vector velocity = new Vector(0, -2.0, 0);  // Straight down with high speed
            player.setVelocity(velocity);

            // Visual effects during dive
            spawnDiveEffects(player);

            // Check for ground impact
            Location checkLoc = player.getLocation().clone().subtract(0, 0.1, 0);
            if (!checkLoc.getBlock().isPassable() || player.isOnGround()) {
                handleImpact(player, startHeight);
                plugin.getServer().getScheduler().cancelTask(player.getMetadata("divebomb_task").get(0).asInt());
                player.removeMetadata("divebomb_task", plugin);
                player.setGliding(false); // Stop gliding on impact
            }
        }, 0L, 1L);

        // Store task ID for cleanup
        player.setMetadata("divebomb_task", new org.bukkit.metadata.FixedMetadataValue(plugin, diveTask.getTaskId()));
    }

    private void handleImpact(Player player, double startHeight) {
        Location impactLoc = player.getLocation();

        // Calculate height-based damage
        double heightDiff = Math.min(startHeight - impactLoc.getY(), maxHeightBonus);
        double bonusDamage = heightDiff * heightMultiplier;
        double totalDamage = baseDamage + bonusDamage;

        // Debug the damage calculation
        player.sendMessage(String.format("§6✦ Height fallen: %.1f blocks (Bonus: %.1f damage)",
                heightDiff, bonusDamage));

        // Get nearby entities
        for (Entity entity : player.getWorld().getNearbyEntities(impactLoc, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity) || entity == player) continue;

            LivingEntity target = (LivingEntity) entity;

            // Skip party members
            if (entity instanceof Player) {
                Player targetPlayer = (Player) entity;
                if (plugin.getPartyManager().getParty(player) != null &&
                        plugin.getPartyManager().getParty(player).isMember(targetPlayer)) {
                    continue;
                }
            }

            // Set skill damage metadata
            target.setMetadata("skill_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, player));
            target.setMetadata("skill_damage_amount", new org.bukkit.metadata.FixedMetadataValue(plugin, totalDamage));

            // Apply damage through the entity damage event system
            target.damage(totalDamage, player);

            // Clear metadata after damage
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                target.removeMetadata("skill_damage", plugin);
                target.removeMetadata("skill_damage_amount", plugin);
            }, 1L);

            // Enhanced knockback
            Vector knockback = target.getLocation().toVector()
                    .subtract(impactLoc.toVector())
                    .normalize()
                    .multiply(knockbackStrength)
                    .setY(0.8); // More upward force
            target.setVelocity(knockback);
        }

        // Enhanced visual and sound effects
        player.getWorld().spawnParticle(
                Particle.EXPLOSION_EMITTER,
                impactLoc,
                10, 1.0, 0.1, 1.0, 0.1
        );

        // Ring of particles
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 12) {
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particleLoc = impactLoc.clone().add(x, 0.1, z);
            player.getWorld().spawnParticle(
                    Particle.CLOUD,
                    particleLoc,
                    5, 0.2, 0.1, 0.2, 0.05
            );
        }

        // Ground crack effect
        player.getWorld().spawnParticle(
                Particle.BLOCK_CRUMBLE,
                impactLoc,
                50, 2.0, 0.1, 2.0, 0,
                impactLoc.clone().subtract(0, 1, 0).getBlock().getBlockData()
        );

        // Multiple layered sounds for more impact
        player.getWorld().playSound(
                impactLoc,
                Sound.ENTITY_GENERIC_EXPLODE,
                1.0f,
                0.5f
        );
        player.getWorld().playSound(
                impactLoc,
                Sound.BLOCK_ANVIL_LAND,
                1.0f,
                0.8f
        );

        // Send feedback based on bonus damage
        if (bonusDamage > maxHeightBonus/2) {
            player.sendMessage("§6✦ Perfect Dive Bomb! +" + String.format("%.1f", bonusDamage) + " bonus damage!");
        } else if (bonusDamage > 0) {
            player.sendMessage("§6✦ Dive Bomb landed! +" + String.format("%.1f", bonusDamage) + " bonus damage!");
        }
    }

    private void spawnDiveEffects(Player player) {
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(
                Particle.CLOUD,
                loc,
                3, 0.2, 0.2, 0.2, 0.0
        );
        player.getWorld().spawnParticle(
                Particle.FIREWORK,
                loc,
                1, 0.1, 0.1, 0.1, 0.0
        );
    }

    private boolean hasBlockBelow(Location location) {
        return !location.subtract(0, 0.1, 0).getBlock().isPassable();
    }

    // Cleanup method
    public static void cleanup(Player player) {
        if (player.hasMetadata("divebomb_task")) {
            plugin.getServer().getScheduler().cancelTask(
                    player.getMetadata("divebomb_task").get(0).asInt()
            );
            player.removeMetadata("divebomb_task", plugin);
        }
    }
}
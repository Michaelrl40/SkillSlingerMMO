package com.michael.mmorpg.skills.skyknight;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class SpiralPierceSkill extends Skill {
    private final double damage;
    private final double glidingDamageMultiplier;
    private final double spiralRadius;
    private final int riptideDuration;

    public SpiralPierceSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 12.0);
        this.glidingDamageMultiplier = config.getDouble("glidingmultiplier", 1.5);
        this.spiralRadius = config.getDouble("spiralradius", 0.3);
        this.riptideDuration = config.getInt("riptideDuration", 10); // Duration in ticks (default: 0.5 seconds)
    }

    @Override
    protected void performSkill(Player player) {
        // Get target
        LivingEntity target = getTargetEntity(player, targetRange);

        if (target == null) {
            player.sendMessage("§c✦ No valid target in range!");
            setSkillSuccess(false);
            return;
        }

        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Set current target for skill systems
        this.currentTarget = target;

        // Mark player as doing this skill to prevent movement/other actions
        player.setMetadata("spiral_pierce_active", new FixedMetadataValue(plugin, true));

        // Record target location for impact
        final Location targetLocation = target.getLocation().clone();

        // Start riptide animation - using the native method
        // The float parameter is the attack strength (doesn't actually matter for our use case)
        // null parameter means we don't specify an attack item
        player.startRiptideAttack(riptideDuration, 0.0f, null);

        // Initial sound effect
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 2.0f);

        // Add directional momentum toward target
        Vector direction = target.getLocation().clone().subtract(player.getLocation()).toVector().normalize();
        player.setVelocity(direction.multiply(1.5)); // Adjust multiplier to control speed

        // Schedule the impact after the riptide animation
        new BukkitRunnable() {
            @Override
            public void run() {
                // Clear the metadata
                if (player.hasMetadata("spiral_pierce_active")) {
                    player.removeMetadata("spiral_pierce_active", plugin);
                }

                // Check if player and target are still valid
                if (!player.isValid() || !target.isValid() || !player.isOnline() || target.isDead()) {
                    return;
                }

                // Handle impact
                handleImpact(player, target, direction);
            }
        }.runTaskLater(plugin, riptideDuration + 5); // Give a few extra ticks for the animation
    }

    private void handleImpact(Player player, LivingEntity target, Vector direction) {
        // Calculate final damage
        double finalDamage = player.isGliding() ? damage * glidingDamageMultiplier : damage;

        // Apply damage with skill metadata
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, finalDamage));
        target.damage(0.1, player); // Trigger damage event

        // Knockback effect
        target.setVelocity(direction.multiply(0.5));

        // Impact effects
        Location impactLoc = target.getLocation();

        // Explosion particle
        player.getWorld().spawnParticle(
                Particle.EXPLOSION,
                impactLoc,
                1, 0, 0, 0, 0
        );

        // Cloud and water burst for riptide theme
        player.getWorld().spawnParticle(
                Particle.CLOUD,
                impactLoc,
                15, 0.3, 0.3, 0.3, 0.2
        );

        player.getWorld().spawnParticle(
                Particle.SPLASH,
                impactLoc,
                20, 0.3, 0.3, 0.3, 0.5
        );

        // Impact sounds
        player.getWorld().playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
        player.getWorld().playSound(impactLoc, Sound.BLOCK_ANVIL_LAND, 0.5f, 2.0f);
        player.getWorld().playSound(impactLoc, Sound.ITEM_TRIDENT_HIT, 1.0f, 1.0f);

        // Send feedback if gliding bonus applied
        if (player.isGliding()) {
            player.sendMessage("§6✦ Aerial Spiral Pierce! §e(+" +
                    String.format("%.0f", (glidingDamageMultiplier - 1) * 100) + "% damage)");
        }
    }
}
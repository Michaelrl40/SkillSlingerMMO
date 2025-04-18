package com.michael.mmorpg.skills.windwaker;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.metadata.FixedMetadataValue;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.party.Party;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class GaleForceSkill extends Skill {
    private final double damage;
    private final double range;           // How far the blast travels
    private final double knockbackForce;  // Knockback strength
    private final double coneSpread;      // How wide the cone gets

    public GaleForceSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 15.0);
        this.range = config.getDouble("range", 12.0);
        this.knockbackForce = config.getDouble("knockbackforce", 2.5);
        this.coneSpread = config.getDouble("conespread", 0.3);
    }

    @Override
    protected void performSkill(Player player) {
        Party casterParty = plugin.getPartyManager().getParty(player);
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection();

        // Create the advancing wind tunnel
        new BukkitRunnable() {
            double t = 0;
            final Set<UUID> hitEntities = new HashSet<>(); // Track hit entities

            @Override
            public void run() {
                if (t >= range) {
                    // Clean up hit markers when skill ends
                    for (Entity entity : player.getWorld().getNearbyEntities(start, range, range, range)) {
                        if (entity instanceof LivingEntity) {
                            entity.removeMetadata("galeforce_hit", plugin);
                        }
                    }
                    this.cancel();
                    return;
                }

                // Calculate current point and cone width
                double coneWidth = t * coneSpread;
                Location point = start.clone().add(direction.clone().multiply(t));

                // Create minimal tunnel outline (reduced particle count)
                for (double theta = 0; theta < Math.PI * 2; theta += Math.PI / 4) { // Increased step size
                    double x = coneWidth * Math.cos(theta);
                    double y = coneWidth * Math.sin(theta);

                    Vector perpendicular = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();
                    Vector upward = direction.clone().crossProduct(perpendicular).normalize();

                    Location particleLoc = point.clone().add(
                                    perpendicular.clone().multiply(x))
                            .add(upward.clone().multiply(y));

                    // Reduced particle effects
                    if (t % 2 == 0) { // Only spawn particles every other step
                        point.getWorld().spawnParticle(Particle.CLOUD, particleLoc, 1, 0, 0, 0, 0);
                        if (Math.random() < 0.2) { // Reduced chance for additional particles
                            point.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
                        }
                    }
                }

                // Check for entities in the cone
                for (Entity entity : player.getWorld().getNearbyEntities(point, coneWidth/2, coneWidth/2, coneWidth/2)) {
                    if (!(entity instanceof LivingEntity) || entity == player) continue;
                    LivingEntity target = (LivingEntity) entity;

                    // Skip party members
                    if (target instanceof Player && casterParty != null &&
                            casterParty.shouldPreventInteraction(player, target, true)) {
                        continue;
                    }

                    // Check if already hit using UUID tracking
                    if (hitEntities.contains(target.getUniqueId())) continue;

                    // Apply damage and knockback
                    target.setMetadata("skill_damage", new FixedMetadataValue(getPlugin(), player));
                    target.setMetadata("skill_damage_amount", new FixedMetadataValue(getPlugin(), damage));
                    target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
                    target.damage(0.1, player);

                    // Add knockback in the direction of the cone
                    Vector knockback = direction.clone().multiply(knockbackForce);
                    knockback.setY(knockback.getY() * 0.5); // Reduce vertical knockback
                    target.setVelocity(knockback);

                    // Track hit entity
                    hitEntities.add(target.getUniqueId());

                    // Minimal impact effect
                    target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation().add(0, 1, 0),
                            5, 0.2, 0.2, 0.2, 0.1);
                }

                // Reduced sound frequency
                if (t % 4 == 0) { // Play sound less frequently
                    point.getWorld().playSound(point, Sound.ENTITY_PHANTOM_FLAP, 0.3f, 1.5f);
                }

                t += 0.5;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        // Initial cast sound
        player.getWorld().playSound(start, Sound.ENTITY_PHANTOM_SWOOP, 1.0f, 0.8f);

        broadcastLocalSkillMessage(player, "§7[Windwaker] " + player.getName() + " unleashes a devastating gale force!");
        setSkillSuccess(true);
    }
}
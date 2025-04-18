package com.michael.mmorpg.skills.windwaker;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.party.Party;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class TornadoSkill extends Skill {
    private final double radius = 3.0;       // Tornado radius
    private final int duration = 5;          // Duration in seconds
    private final double damage = 2.0;       // Damage per tick
    private final double heightOffset = 2.5;  // How tall the tornado is
    private final double finalLaunchPower = 2.0; // How strong the final launch is

    public TornadoSkill(ConfigurationSection config) {
        super(config);
    }

    @Override
    protected void performSkill(Player player) {
        Location center = player.getLocation();
        Party playerParty = plugin.getPartyManager().getParty(player);
        Set<LivingEntity> caughtEntities = new HashSet<>();

        // Create the tornado effect
        new BukkitRunnable() {
            private double currentAngle = 0;
            private int ticks = 0;
            private final int maxTicks = duration * 20; // Convert seconds to ticks

            @Override
            public void run() {

                // Create tornado particles
                createTornadoParticles(center, currentAngle);

                // Handle entity effects
                handleNearbyEntities(player, playerParty, center, caughtEntities, currentAngle);

                // Play sound effects
                if (ticks % 5 == 0) { // Every 5 ticks
                    center.getWorld().playSound(center, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.5f);
                }

                currentAngle += 0.5; // Rotate the tornado
                ticks++;
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);

        // Broadcast skill use message
        broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " + player.getName() + " summoned a Tornado!");
    }

    private void createTornadoParticles(Location center, double currentAngle) {
        for (double height = 0; height <= heightOffset; height += 0.2) {
            double particleRadius = Math.min(radius, height);
            for (int i = 0; i < 3; i++) { // Create multiple particle rings
                double angle = currentAngle + (i * Math.PI * 2 / 3);
                double x = center.getX() + particleRadius * Math.cos(angle + height * 2);
                double z = center.getZ() + particleRadius * Math.sin(angle + height * 2);

                Location particleLoc = new Location(center.getWorld(), x, center.getY() + height, z);

                // Spawn more detailed particle effects
                center.getWorld().spawnParticle(Particle.CLOUD, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                center.getWorld().spawnParticle(Particle.SMOKE, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                if (Math.random() < 0.3) { // Occasional white swirl particles
                    center.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 1, 0.1, 0.1, 0.1, 0.02);
                }
            }
        }
    }

    private void handleNearbyEntities(Player caster, Party casterParty, Location center,
                                      Set<LivingEntity> caughtEntities, double currentAngle) {
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, heightOffset, radius)) {
            if (!(entity instanceof LivingEntity) || entity == caster) continue;

            LivingEntity target = (LivingEntity) entity;

            // Check party membership
            if (entity instanceof Player) {
                if (casterParty != null && casterParty.shouldPreventInteraction(caster, entity, true)) {
                    continue;
                }
            }

            // Add to caught entities set
            caughtEntities.add(target);

            // Apply damage with proper metadata
            target.setMetadata("skill_damage", new FixedMetadataValue(getPlugin(), caster));
            target.setMetadata("skill_damage_amount", new FixedMetadataValue(getPlugin(), damage));
            target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
            target.damage(0.1, caster);

            // Calculate swirling position
            double height = Math.min(heightOffset, target.getLocation().getY() - center.getY() + 1);
            double swirlingRadius = Math.max(1, radius - height/2);
            double x = center.getX() + swirlingRadius * Math.cos(currentAngle);
            double z = center.getZ() + swirlingRadius * Math.sin(currentAngle);

            // Move entity to swirling position
            Location targetLoc = new Location(center.getWorld(), x, center.getY() + height, z);
            Vector velocity = targetLoc.toVector().subtract(target.getLocation().toVector()).multiply(0.3);
            velocity.setY(0.1); // Small upward force to keep them airborne
            target.setVelocity(velocity);

            // Visual effect for caught entities
            target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(),
                    5, 0.2, 0.2, 0.2, 0.05);
        }
    }
}
package com.michael.mmorpg.skills.chainwarden;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import com.michael.mmorpg.party.Party;

import java.util.HashSet;
import java.util.Set;

public class DeathFlailSkill extends Skill {
    private final double damage;
    private final double radius;
    private final double chainSpacing;
    private final int chainCount;
    private final double verticalRange; // Height of the flail effect

    public DeathFlailSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 12.0);
        this.radius = config.getDouble("radius", 5.0);
        this.chainSpacing = config.getDouble("chainspacing", 0.5);
        this.chainCount = config.getInt("chaincount", 24);
        this.verticalRange = 4.0; // 4 blocks up and down from the player
    }

    @Override
    protected void performSkill(Player player) {
        Location centerLoc = player.getLocation();
        World world = player.getWorld();
        Party casterParty = plugin.getPartyManager().getParty(player);
        Set<LivingEntity> hitTargets = new HashSet<>();

        // Initial cast effect with more menacing sounds
        world.playSound(centerLoc, Sound.BLOCK_CHAIN_BREAK, 1.0f, 0.5f);
        world.playSound(centerLoc, Sound.ENTITY_WITHER_SHOOT, 0.5f, 0.8f);
        world.spawnParticle(Particle.WITCH, centerLoc, 50, 0.5, 0.1, 0.5, 0.1);

        // Create the flail effect
        new BukkitRunnable() {
            double currentAngle = 0;
            final double MAX_ANGLE = Math.toRadians(350);
            final double ANGLE_PER_CHAIN = MAX_ANGLE / chainCount;
            int chainsFired = 0;

            @Override
            public void run() {
                if (chainsFired >= chainCount) {
                    this.cancel();
                    return;
                }

                // Calculate flail direction in horizontal plane
                double x = Math.cos(currentAngle);
                double z = Math.sin(currentAngle);
                Vector baseDirection = new Vector(x, 0, z);

                // Create a vertical sweep of flail particles
                for (double height = -verticalRange; height <= verticalRange; height += chainSpacing) {
                    Vector direction = baseDirection.clone();

                    // Create flail particles along the radius
                    for (double d = 0; d < radius; d += chainSpacing) {
                        Location particleLoc = centerLoc.clone()
                                .add(direction.clone().multiply(d))
                                .add(0, height, 0);

                        // Main chain particles
                        world.spawnParticle(Particle.CRIT, particleLoc, 1, 0, 0, 0, 0);

                        // Dark aesthetic particles
                        if (Math.random() < 0.3) {
                            world.spawnParticle(Particle.SMOKE, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                        }

                        // Check for targets in a small radius around the particle
                        for (Entity entity : world.getNearbyEntities(particleLoc, 0.8, 0.8, 0.8)) {
                            if (!(entity instanceof LivingEntity) || entity == player) continue;
                            LivingEntity target = (LivingEntity) entity;

                            if (hitTargets.contains(target)) continue;
                            if (casterParty != null && casterParty.shouldPreventInteraction(player, target, true)) continue;

                            // Apply damage and effects
                            hitTargets.add(target);
                            target.damage(damage, player);

                            // Hit effects
                            Location hitLoc = target.getLocation().add(0, 1, 0);
                            world.spawnParticle(Particle.CRIT, hitLoc, 10, 0.2, 0.2, 0.2, 0.2);
                            world.spawnParticle(Particle.LARGE_SMOKE, hitLoc, 5, 0.2, 0.2, 0.2, 0.1);
                            world.playSound(hitLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.5f);

                            // Additional visual feedback for vertical hits
                            world.spawnParticle(Particle.WITCH, hitLoc, 5, 0.2, 0.2, 0.2, 0.05);
                        }
                    }

                    // Enhanced flail end effect at each height
                    Location endLoc = centerLoc.clone()
                            .add(direction.multiply(radius))
                            .add(0, height, 0);
                    world.spawnParticle(Particle.WITCH, endLoc, 3, 0.1, 0.1, 0.1, 0.05);
                    world.spawnParticle(Particle.LARGE_SMOKE, endLoc, 1, 0.1, 0.1, 0.1, 0.05);
                }

                currentAngle += ANGLE_PER_CHAIN;
                chainsFired++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Broadcast skill usage with enhanced message
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!hitTargets.isEmpty()) {
                    String targets = hitTargets.size() == 1 ? "victim" : "victims";
                    broadcastLocalSkillMessage(player, "§c[" + getPlayerClass(player) + "] " +
                            player.getName() + " unleashes Death Flail, claiming " +
                            hitTargets.size() + " " + targets + "!");
                }
            }
        }.runTaskLater(plugin, chainCount + 5);

        setSkillSuccess(true);
    }
}
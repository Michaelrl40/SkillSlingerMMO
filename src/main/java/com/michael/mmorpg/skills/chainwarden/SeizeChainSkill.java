package com.michael.mmorpg.skills.chainwarden;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import com.michael.mmorpg.party.Party;

public class SeizeChainSkill extends Skill {
    private final double projectileSpeed;
    private final double damage;
    private final double pullSpeed;
    private final double chainVisualsSpacing;
    private final double HOVER_HEIGHT = 0.5;
    private final double HIT_DETECTION_RADIUS = 1.0;
    private final int MAX_FLIGHT_TICKS = 60;

    public SeizeChainSkill(ConfigurationSection config) {
        super(config);
        this.projectileSpeed = config.getDouble("projectilespeed", 1.0);
        this.damage = config.getDouble("damage", 5.0);
        this.pullSpeed = config.getDouble("pullspeed", 0.4);
        this.chainVisualsSpacing = config.getDouble("chainvisualsspacing", 0.5);
    }

    @Override
    protected void performSkill(Player player) {
        Location startLoc = player.getEyeLocation();
        Vector direction = startLoc.getDirection();

        Snowball chainProjectile = player.launchProjectile(Snowball.class);
        chainProjectile.setVelocity(direction.multiply(projectileSpeed));
        chainProjectile.setGravity(false);

        chainProjectile.setMetadata("seize_chain", new FixedMetadataValue(plugin, true));
        chainProjectile.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        chainProjectile.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));

        Party casterParty = plugin.getPartyManager().getParty(player);

        new BukkitRunnable() {
            final World world = player.getWorld();
            final Location castLocation = player.getLocation().clone();
            LivingEntity target = null;
            Location targetStartLoc = null;
            Location pullDestination = null;
            double progressToDestination = 0;
            boolean hasHitSomething = false;
            int flightTicks = 0;

            @Override
            public void run() {
                // Time and range limit checks for projectile
                if (!hasHitSomething) {
                    // Check if chain has exceeded maximum range
                    if (chainProjectile.getLocation().distance(castLocation) > targetRange) {
                        chainProjectile.remove();
                        world.playSound(chainProjectile.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.5f, 0.8f);
                        world.spawnParticle(Particle.CRIT, chainProjectile.getLocation(),
                                10, 0.2, 0.2, 0.2, 0.05);
                        player.sendMessage("§c✦ Chain reached its maximum range!");
                        this.cancel();
                        return;
                    }

                    // Check flight time limit
                    if (flightTicks++ > MAX_FLIGHT_TICKS) {
                        chainProjectile.remove();
                        world.playSound(chainProjectile.getLocation(), Sound.BLOCK_CHAIN_BREAK, 0.5f, 0.8f);
                        world.spawnParticle(Particle.SMOKE, chainProjectile.getLocation(),
                                10, 0.2, 0.2, 0.2, 0.05);
                        this.cancel();
                        return;
                    }
                }

                // Search for target if we haven't found one
                if (target == null && !hasHitSomething) {
                    if (chainProjectile.isDead() || !chainProjectile.isValid()) {
                        this.cancel();
                        return;
                    }

                    // Enhanced hit detection
                    for (Entity entity : chainProjectile.getNearbyEntities(HIT_DETECTION_RADIUS, HIT_DETECTION_RADIUS, HIT_DETECTION_RADIUS)) {
                        if (!(entity instanceof LivingEntity) || entity == player) continue;
                        if (casterParty != null && casterParty.shouldPreventInteraction(player, entity, true)) continue;

                        // Hit confirmed - initialize pull sequence
                        hasHitSomething = true;
                        target = (LivingEntity) entity;
                        chainProjectile.remove();

                        targetStartLoc = target.getLocation();
                        pullDestination = player.getLocation();

                        // Play hit effects
                        world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0),
                                20, 0.5, 0.5, 0.5, 0.1);
                        world.playSound(target.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.0f);

                        // Broadcast the successful hit
                        broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                                player.getName() + " seizes " + (target instanceof Player ? target.getName() : "their target") + "!");

                        target.setGravity(false);
                        break;
                    }

                    // Draw chain particles while searching
                    drawChainParticles(player.getEyeLocation(), chainProjectile.getLocation(), world);
                }
                // Handle controlled pull movement
                else if (target != null && target.isValid()) {
                    pullDestination = player.getLocation();

                    if (progressToDestination >= 1.0 || !player.isValid()) {
                        target.setGravity(true);
                        this.cancel();
                        return;
                    }

                    progressToDestination += pullSpeed;
                    progressToDestination = Math.min(progressToDestination, 1.0);

                    Vector pullPath = pullDestination.toVector()
                            .subtract(targetStartLoc.toVector());
                    Location newPosition = targetStartLoc.clone().add(
                            pullPath.multiply(progressToDestination));

                    newPosition.add(0, HOVER_HEIGHT, 0);

                    target.teleport(newPosition);
                    target.setVelocity(new Vector(0, 0, 0));
                    target.setFallDistance(0);

                    drawChainParticles(player.getEyeLocation(), newPosition.clone().add(0, 1, 0), world);
                    if ((int)(progressToDestination * 10) % 2 == 0) {
                        world.playSound(newPosition, Sound.BLOCK_CHAIN_STEP, 0.5f, 1.2f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    private void drawChainParticles(Location start, Location end, World world) {
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize();

        for (double d = 0; d < distance; d += chainVisualsSpacing) {
            Location particleLoc = start.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.CRIT, particleLoc, 1, 0, 0, 0, 0);
        }
    }
}
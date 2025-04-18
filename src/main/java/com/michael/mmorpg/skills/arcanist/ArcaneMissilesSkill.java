package com.michael.mmorpg.skills.arcanist;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

public class ArcaneMissilesSkill extends Skill {
    private final double range;
    private final double missileDamage;
    private final double projectileSpeed;
    private final double trackingStrength;
    private int missilesFired = 0;
    private final int totalMissiles = 3;
    private final Set<LivingEntity> hitByCurrentMissile = new HashSet<>();
    private BukkitRunnable missileFiringTask;
    private int castLockTaskId = -1;

    public ArcaneMissilesSkill(ConfigurationSection config) {
        super(config);
        this.range = config.getDouble("range", 20.0);
        this.missileDamage = config.getDouble("damage", 12.0);
        this.projectileSpeed = config.getDouble("projectilespeed", 1.0); // Increased speed
        this.trackingStrength = config.getDouble("trackingstrength", 0.3); // How strongly missiles track targets
    }

    @Override
    public void execute(Player caster) {
        // Get target using built-in targeting
        currentTarget = getTargetEntity(caster, range);

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No valid target in range!");
            setSkillSuccess(false);
            return;
        }

        if (hasCastTime) {
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player player) {
        // Reset missile counter
        missilesFired = 0;

        // Apply a "casting lock" metadata to prevent other skills
        player.setMetadata("arcane_missiles_lock", new FixedMetadataValue(getPlugin(), true));

        // Visual effect to show player is channeling
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);

        // Start firing sequence - faster firing rate (10 ticks = 0.5 seconds between missiles)
        missileFiringTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (missilesFired >= totalMissiles || !player.isOnline() || player.isDead()
                        || !currentTarget.isValid() || currentTarget.isDead()) {
                    // Clean up if interrupted
                    endChanneling(player);
                    cancel();
                    return;
                }

                fireMissile(player);
                missilesFired++;

                if (missilesFired >= totalMissiles) {
                    // Schedule removal of lock after last missile is fired
                    // but give enough time for missile to reach target
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            endChanneling(player);
                        }
                    }.runTaskLater(getPlugin(), 10L); // 2 seconds after last missile

                    cancel();
                }
            }
        };

        missileFiringTask.runTaskTimer(getPlugin(), 10L, 10L); // Fire every 0.5 seconds

        // Apply a subtle slowness effect while channeling
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 0, false, false, true));

        // Visual indicator for channeling
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.hasMetadata("arcane_missiles_lock") || !player.isOnline()) {
                    cancel();
                    return;
                }

                // Orbit particles around the player while channeling
                Location playerLoc = player.getLocation().add(0, 1.2, 0);
                double angle = (System.currentTimeMillis() % 1000) / 1000.0 * Math.PI * 2;
                for (int i = 0; i < 3; i++) {
                    double angleOffset = angle + (i * (Math.PI * 2 / 3));
                    double x = Math.cos(angleOffset) * 0.7;
                    double z = Math.sin(angleOffset) * 0.7;
                    player.getWorld().spawnParticle(Particle.WITCH,
                            playerLoc.clone().add(x, 0, z),
                            1, 0.05, 0.05, 0.05, 0);
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 2L);

        setSkillSuccess(true);
    }

    private void endChanneling(Player player) {
        if (player.hasMetadata("arcane_missiles_lock")) {
            player.removeMetadata("arcane_missiles_lock", getPlugin());
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f);
        }
    }

    private void fireMissile(Player caster) {
        Location missileLocation = caster.getEyeLocation();
        Vector initialDirection = currentTarget.getLocation().add(0, 1, 0)
                .subtract(missileLocation).toVector().normalize();

        // Play launch sound
        caster.getWorld().playSound(missileLocation, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.5f);

        // Clear hit tracking for new missile
        hitByCurrentMissile.clear();

        new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = 100; // Maximum travel time
            private final Location startLoc = missileLocation.clone();
            private Location currentLoc = missileLocation.clone();
            private Vector currentDirection = initialDirection.clone();
            private boolean hasHitTarget = false;

            @Override
            public void run() {
                if (ticks++ > maxTicks || hasHitTarget || !caster.isOnline() ||
                        !currentTarget.isValid() || currentTarget.isDead()) {
                    cancel();
                    return;
                }

                // Get vector to target for heat-seeking
                Vector toTarget = currentTarget.getLocation().add(0, 1, 0)
                        .subtract(currentLoc).toVector().normalize();

                // Gradually adjust direction toward target (heat-seeking)
                currentDirection = currentDirection.multiply(1 - trackingStrength)
                        .add(toTarget.multiply(trackingStrength)).normalize();

                // Update missile position
                currentLoc.add(currentDirection.clone().multiply(projectileSpeed));

                // Display missile particles
                caster.getWorld().spawnParticle(Particle.WITCH, currentLoc,
                        5, 0.1, 0.1, 0.1, 0.02);
                caster.getWorld().spawnParticle(Particle.END_ROD, currentLoc,
                        2, 0.1, 0.1, 0.1, 0.02);

                // Trail particles
                if (ticks % 2 == 0) {
                    caster.getWorld().spawnParticle(Particle.INSTANT_EFFECT, currentLoc,
                            1, 0.1, 0.1, 0.1, 0);
                }

                // Check for collisions with terrain
                if (!currentLoc.getBlock().isPassable()) {
                    playImpactEffects(currentLoc);
                    cancel();
                    return;
                }

                // Check for entity hits with increased precision
                for (Entity entity : currentLoc.getWorld().getNearbyEntities(currentLoc, 1.2, 1.2, 1.2)) {
                    if (!(entity instanceof LivingEntity) || entity == caster ||
                            hitByCurrentMissile.contains(entity)) continue;

                    // Target specifically the intended target with high priority
                    if (entity.equals(currentTarget) || !isHarmfulSkill) {
                        LivingEntity target = (LivingEntity) entity;

                        // Party check
                        if (entity instanceof Player) {
                            Player playerTarget = (Player) entity;
                            if (getPlugin().getPartyManager().getParty(caster) != null &&
                                    getPlugin().getPartyManager().getParty(caster).isMember(playerTarget)) {
                                continue;
                            }
                        }

                        // Apply damage
                        target.setMetadata("skill_damage", new FixedMetadataValue(getPlugin(), caster));
                        target.setMetadata("skill_damage_amount", new FixedMetadataValue(getPlugin(), missileDamage));
                        target.setMetadata("magic_damage", new FixedMetadataValue(getPlugin(), true));

                        target.damage(0.1, caster); // Small damage to trigger event

                        // Enter combat
                        getPlugin().getCombatManager().enterCombat(caster, target);
                        if (target instanceof Player) {
                            getPlugin().getCombatManager().enterCombat((Player) target, caster);
                        }

                        // Clean up metadata after a tick
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (target.isValid()) {
                                    target.removeMetadata("skill_damage", getPlugin());
                                    target.removeMetadata("skill_damage_amount", getPlugin());
                                    target.removeMetadata("magic_damage", getPlugin());
                                }
                            }
                        }.runTaskLater(getPlugin(), 1L);

                        // Track hit and play effects
                        hitByCurrentMissile.add(target);
                        playImpactEffects(currentLoc);
                        hasHitTarget = true;
                        break;
                    }
                }

                // Check range limit
                if (startLoc.distance(currentLoc) > range * 1.5) { // Allow extra range for tracking
                    cancel();
                }
            }
        }.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private void playImpactEffects(Location location) {
        location.getWorld().spawnParticle(Particle.WITCH, location,
                15, 0.2, 0.2, 0.2, 0.1);
        location.getWorld().spawnParticle(Particle.CRIT, location,
                20, 0.2, 0.2, 0.2, 0.2);
        location.getWorld().spawnParticle(Particle.FLASH, location,
                1, 0.1, 0.1, 0.1, 0);
        location.getWorld().playSound(location, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.0f, 1.2f);
    }

    // Handle skill cancellation if needed
    @Override
    public void cancelCast(Player player) {
        super.cancelCast(player);

        // Also cancel the firing sequence if it's running
        if (missileFiringTask != null) {
            missileFiringTask.cancel();
        }

        // Remove the casting lock
        endChanneling(player);
    }
}
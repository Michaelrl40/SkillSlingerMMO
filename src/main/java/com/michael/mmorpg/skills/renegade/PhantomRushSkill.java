package com.michael.mmorpg.skills.renegade;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class PhantomRushSkill extends Skill {
    private final double shieldAmount;
    private final int shieldDuration;

    public PhantomRushSkill(ConfigurationSection config) {
        super(config);
        this.shieldAmount = config.getDouble("shieldamount", 30.0);
        this.shieldDuration = config.getInt("shieldduration", 5);
    }

    @Override
    public void execute(Player caster) {
        // Check if player is in a party
        if (plugin.getPartyManager().getParty(caster) == null) {
            // No party, self-cast only
            caster.sendMessage("§5✦ No party found, shielding yourself!");
            currentTarget = null;
            if (hasCastTime) {
                startCasting(caster);
                return;
            }
            performSkill(caster);
            return;
        }

        // Get target using built-in targeting
        currentTarget = getTargetEntity(caster, config.getDouble("range", 15.0));
        Player finalTarget = null;

        if (currentTarget instanceof Player) {
            Player potentialTarget = (Player) currentTarget;
            // Check if target is in party
            if (plugin.getPartyManager().getParty(caster).isMember(potentialTarget)) {
                finalTarget = potentialTarget;
                caster.sendMessage("§5✦ Targeting " + finalTarget.getName() + " with Phantom Rush!");
            } else {
                caster.sendMessage("§5✦ You can only shield party members!");
                currentTarget = null;
            }
        } else {
            caster.sendMessage("§5✦ No party member targeted, shielding yourself!");
        }

        // Store target for cast time
        if (finalTarget != null) {
            caster.setMetadata("phantom_rush_target", new FixedMetadataValue(plugin, finalTarget.getUniqueId()));
        }

        // Start casting if has cast time
        if (hasCastTime) {
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        Player finalTarget = null;

        // Get stored target if exists
        if (caster.hasMetadata("phantom_rush_target")) {
            UUID targetUUID = (UUID) caster.getMetadata("phantom_rush_target").get(0).value();
            Player storedTarget = Bukkit.getPlayer(targetUUID);
            // Verify target is still valid and in party
            if (storedTarget != null && storedTarget.isOnline() &&
                    plugin.getPartyManager().getParty(caster) != null &&
                    plugin.getPartyManager().getParty(caster).isMember(storedTarget)) {
                finalTarget = storedTarget;
            }
        }

        // Default to self-cast if no valid target
        if (finalTarget == null) {
            finalTarget = caster;
        }

        // Remove targeting metadata
        if (caster.hasMetadata("phantom_rush_target")) {
            caster.removeMetadata("phantom_rush_target", plugin);
        }

        // Handle self-cast
        if (finalTarget == caster) {
            applyShield(caster);
            caster.sendMessage("§5✦ You've shielded yourself!");
        } else {
            // Enhanced dash mechanics
            double dashSpeed = 2.5;  // Increased from 1.5
            double upwardBoost = 0.5; // Add upward momentum
            org.bukkit.util.Vector direction = finalTarget.getLocation()
                    .subtract(caster.getLocation()).toVector().normalize();

            // Add upward component and boost
            direction.setY(upwardBoost).normalize().multiply(dashSpeed);
            caster.setVelocity(direction);

            // Enhanced dash effects
            caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 0.8f);
            caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.2f);

            // Enhanced particle trail
            new BukkitRunnable() {
                private int ticks = 0;
                private final Location startLoc = caster.getLocation().clone();

                @Override
                public void run() {
                    if (ticks++ >= 10) {
                        this.cancel();
                        return;
                    }

                    // Main dash trail
                    caster.getWorld().spawnParticle(
                            Particle.WITCH,
                            caster.getLocation().add(0, 1, 0),
                            8, 0.3, 0.3, 0.3, 0.05
                    );

                    // Additional effects
                    if (ticks % 2 == 0) {
                        caster.getWorld().spawnParticle(
                                Particle.END_ROD,
                                caster.getLocation().add(0, 1, 0),
                                3, 0.1, 0.1, 0.1, 0.05
                        );
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);

            // Apply shields after dash with enhanced timing
            final Player target = finalTarget;
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Impact effect at arrival
                    target.getWorld().spawnParticle(
                            Particle.FLASH,
                            caster.getLocation(),
                            1, 0, 0, 0, 0
                    );
                    target.getWorld().playSound(
                            caster.getLocation(),
                            Sound.ENTITY_PHANTOM_SWOOP,
                            1.0f,
                            1.2f
                    );

                    applyShield(caster);
                    applyShield(target);
                    createConnectingEffect(caster, target);

                    caster.sendMessage("§5✦ You've shielded " + target.getName() + "!");
                    target.sendMessage("§5✦ " + caster.getName() + " has shielded you!");
                }
            }.runTaskLater(plugin, 5L);
        }

        setSkillSuccess(true);
    }

    private void applyShield(Player player) {
        // Set shield metadata
        player.setMetadata("phantom_shield_amount", new FixedMetadataValue(plugin, shieldAmount));

        // Shield application effect
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.2f);
        player.getWorld().spawnParticle(
                Particle.WITCH,
                player.getLocation().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1
        );

        // Shield duration effect
        new BukkitRunnable() {
            private int ticks = 0;
            private final int maxTicks = shieldDuration * 20;
            private double angle = 0;

            @Override
            public void run() {
                // Check if shield is broken
                if (!player.hasMetadata("phantom_shield_amount")) {
                    this.cancel();
                    return;
                }

                // Check if duration expired
                if (ticks >= maxTicks || !player.isOnline()) {
                    if (player.isOnline()) {
                        removeShield(player);
                    }
                    this.cancel();
                    return;
                }

                // Rotating shield particles
                angle += Math.PI / 8;
                Location loc = player.getLocation().add(0, 1, 0);

                for (double i = 0; i < Math.PI * 2; i += Math.PI / 2) {
                    double x = Math.cos(angle + i) * 1;
                    double z = Math.sin(angle + i) * 1;
                    loc.add(x, 0, z);

                    player.getWorld().spawnParticle(
                            Particle.WITCH,
                            loc,
                            1, 0, 0, 0, 0
                    );

                    loc.subtract(x, 0, z);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // Remove this method as it's handled by ShieldHandler now
    /*
    public static boolean processShieldDamage(Player player, double damage) {
        // This functionality is now in ShieldHandler
    }
    */

    private static void removeShield(Player player) {
        if (!player.hasMetadata("phantom_shield_amount")) return;

        player.removeMetadata("phantom_shield_amount", plugin);
        player.sendMessage("§c✦ Your phantom shield has shattered!");

        // Shield break effect
        player.getWorld().spawnParticle(
                Particle.WITCH,
                player.getLocation().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1
        );
        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_PHANTOM_DEATH,
                0.5f,
                1.2f
        );
    }

    private void createConnectingEffect(Player source, Player target) {
        new BukkitRunnable() {
            private int ticks = 0;
            private final int duration = 20;

            @Override
            public void run() {
                if (ticks++ >= duration || !source.isOnline() || !target.isOnline()) {
                    this.cancel();
                    return;
                }

                Location sourceLoc = source.getLocation().add(0, 1, 0);
                Location targetLoc = target.getLocation().add(0, 1, 0);
                double distance = sourceLoc.distance(targetLoc);

                for (double d = 0; d <= distance; d += 0.5) {
                    double progress = d / distance;
                    double x = sourceLoc.getX() + (targetLoc.getX() - sourceLoc.getX()) * progress;
                    double y = sourceLoc.getY() + (targetLoc.getY() - sourceLoc.getY()) * progress;
                    double z = sourceLoc.getZ() + (targetLoc.getZ() - sourceLoc.getZ()) * progress;

                    Location particleLoc = new Location(source.getWorld(), x, y, z);
                    source.getWorld().spawnParticle(
                            Particle.WITCH,
                            particleLoc,
                            1, 0, 0, 0, 0
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
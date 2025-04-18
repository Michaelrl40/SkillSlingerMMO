package com.michael.mmorpg.skills.chainwarden;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;
import com.michael.mmorpg.party.Party;

public class LashSkill extends Skill {
    private final double damage;
    private final double coneAngle;
    private final int disarmDuration;
    private final int chainCount;
    private final double chainSpacing;

    public LashSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 8.0);
        this.coneAngle = config.getDouble("coneangle", 60.0);
        this.disarmDuration = config.getInt("disarmduration", 40);
        this.chainCount = config.getInt("chaincount", 5);
        this.chainSpacing = config.getDouble("chainspacing", 0.5);
    }

    @Override
    protected void performSkill(Player player) {
        Location playerLoc = player.getEyeLocation();
        Vector direction = playerLoc.getDirection();
        Party casterParty = plugin.getPartyManager().getParty(player);
        World world = player.getWorld();
        java.util.Set<LivingEntity> hitTargets = new java.util.HashSet<>();

        // Initial effect
        world.playSound(playerLoc, Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.2f);
        world.spawnParticle(Particle.SWEEP_ATTACK, playerLoc.add(direction.clone().multiply(1)), 5, 0.2, 0.2, 0.2, 0);

        // Create the chains visual effect
        for (int i = 0; i < chainCount; i++) {
            double angleOffset = (coneAngle / (chainCount - 1)) * i - (coneAngle / 2);
            Vector chainDir = rotateVector(direction.clone(), angleOffset);
            Location startLoc = playerLoc.clone();

            // Check for targets along the chain path immediately
            for (double distance = 0; distance <= targetRange; distance += chainSpacing) {
                Location checkLoc = startLoc.clone().add(chainDir.clone().multiply(distance));

                // Spawn chain particles
                world.spawnParticle(Particle.CRIT, checkLoc, 1, 0, 0, 0, 0);
                if (Math.random() < 0.3) {
                    world.spawnParticle(Particle.SMOKE, checkLoc, 1, 0.05, 0.05, 0.05, 0);
                }

                // Check for targets
                for (Entity entity : world.getNearbyEntities(checkLoc, 1.2, 1.2, 1.2)) {
                    if (!(entity instanceof LivingEntity) || entity == player) continue;
                    LivingEntity target = (LivingEntity) entity;

                    if (hitTargets.contains(target)) continue;
                    if (casterParty != null && casterParty.shouldPreventInteraction(player, target, true)) continue;

                    hitTargets.add(target);

                    if (target instanceof Player) {
                        Player targetPlayer = (Player) target;
                        final Location hitLoc = target.getLocation().add(0, 1, 0);

                        // Apply effects in sequence
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                // First apply disarm
                                plugin.getStatusEffectManager().applyEffect(targetPlayer,
                                        new StatusEffect(CCType.DISARM, disarmDuration, player, 1));

                                // Visual and sound effects
                                world.spawnParticle(Particle.CRIT, hitLoc, 10, 0.2, 0.2, 0.2, 0.2);
                                world.spawnParticle(Particle.LARGE_SMOKE, hitLoc, 5, 0.2, 0.2, 0.2, 0.05);
                                world.playSound(hitLoc, Sound.BLOCK_CHAIN_HIT, 0.8f, 1.0f);
                                world.playSound(hitLoc, Sound.ITEM_SHIELD_BREAK, 0.5f, 1.2f);

                                // Schedule damage after disarm
                                new BukkitRunnable() {
                                    @Override
                                    public void run() {
                                        if (damage > 0) {
                                            target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
                                            target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
                                            target.damage(0.1, player);
                                        }
                                    }
                                }.runTaskLater(plugin, 2L);
                            }
                        }.runTask(plugin);
                    } else {
                        // Apply direct damage to non-players
                        if (damage > 0) {
                            target.damage(damage, player);
                        }
                    }
                }
            }
        }

        // Broadcast the skill message
        if (!hitTargets.isEmpty()) {
            String targets = hitTargets.size() == 1 ? "target" : "targets";
            broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                    player.getName() + " lashes out with chains, disarming " +
                    hitTargets.size() + " " + targets + "!");
        }

        setSkillSuccess(true);
    }

    private Vector rotateVector(Vector vector, double degrees) {
        double rad = Math.toRadians(degrees);
        double x = vector.getX();
        double y = vector.getY();
        double z = vector.getZ();

        double cosTheta = Math.cos(rad);
        double sinTheta = Math.sin(rad);

        double newX = x * cosTheta - z * sinTheta;
        double newZ = x * sinTheta + z * cosTheta;

        return new Vector(newX, y, newZ).normalize();
    }
}
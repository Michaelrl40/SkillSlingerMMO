package com.michael.mmorpg.skills.buccaneer;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.metadata.FixedMetadataValue;

public class PeckSkill extends Skill {
    private final long blindDuration;
    private final int blindIntensity;
    private final double damage;
    private final double parrotSpeed;
    private final double range;

    public PeckSkill(ConfigurationSection config) {
        super(config);
        this.blindDuration = config.getLong("blindDuration", 3000);
        this.blindIntensity = config.getInt("blindIntensity", 1);
        this.damage = config.getDouble("damage", 5.0);
        this.parrotSpeed = config.getDouble("parrotSpeed", 1.0);
        this.range = config.getDouble("range", 15.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Get target using base targeting system
        LivingEntity target = getTargetEntity(player, range);
        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Store target for validation and messages
        currentTarget = target;

        // If target validation fails, don't proceed
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Spawn a temporary parrot
        Location spawnLoc = player.getLocation().add(0, 2, 0);
        Parrot parrot = (Parrot) player.getWorld().spawnEntity(spawnLoc, EntityType.PARROT);

        // Set parrot properties
        parrot.setVariant(Parrot.Variant.values()[(int)(Math.random() * Parrot.Variant.values().length)]);
        parrot.setInvulnerable(true);
        parrot.setGravity(false);

        // Calculate attack vectors
        Location startLoc = spawnLoc.clone();
        Location targetLoc = target.getLocation().add(0, 1.5, 0);
        Vector toTarget = targetLoc.clone().subtract(startLoc).toVector().normalize();

        // Initial effect
        player.getWorld().playSound(startLoc, Sound.ENTITY_PARROT_FLY, 1.0f, 1.2f);

        new BukkitRunnable() {
            private double progress = 0;
            private boolean returning = false;
            private boolean hasHit = false;

            @Override
            public void run() {
                if (!parrot.isValid() || progress > 2) {
                    parrot.remove();
                    cancel();
                    return;
                }

                // Update progress
                progress += 0.1 * parrotSpeed;

                if (!returning && progress >= 1) {
                    // Hit the target
                    if (!hasHit) {
                        onHitTarget(target, player);
                        hasHit = true;
                    }
                    returning = true;
                    progress = 0;
                }

                // Calculate current position
                Location currentPos;
                if (!returning) {
                    // Going to target
                    currentPos = startLoc.clone().add(toTarget.clone().multiply(progress * range));
                } else {
                    // Returning to start
                    Vector returnVector = startLoc.clone().subtract(targetLoc).toVector().normalize();
                    currentPos = targetLoc.clone().add(returnVector.clone().multiply(progress * range));
                }

                // Move parrot
                parrot.teleport(currentPos);

                // Face the direction of movement
                float yaw = returning ?
                        currentPos.clone().setDirection(startLoc.clone().subtract(currentPos).toVector()).getYaw() :
                        currentPos.clone().setDirection(targetLoc.clone().subtract(currentPos).toVector()).getYaw();
                Location rotatedLoc = currentPos.clone();
                rotatedLoc.setYaw(yaw);
                parrot.teleport(rotatedLoc);

                // Particle trail
                parrot.getWorld().spawnParticle(Particle.CLOUD, currentPos, 2, 0.1, 0.1, 0.1, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    private void onHitTarget(LivingEntity target, Player caster) {
        // Apply blind effect if target is a player
        if (target instanceof Player) {
            plugin.getStatusEffectManager().applyEffect(
                    (Player) target,
                    new StatusEffect(CCType.BLIND, blindDuration, caster, blindIntensity)
            );
        }

        // Mark for Dead Man Walking
        caster.setMetadata("last_peck_target", new FixedMetadataValue(plugin, target));

        // Clean up metadata after 30 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                if (caster.isValid() && caster.hasMetadata("last_peck_target")) {
                    caster.removeMetadata("last_peck_target", plugin);
                }
            }
        }.runTaskLater(plugin, 600L);

        // Apply physical damage
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.damage(0.1, caster);

        // Effects
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PARROT_HURT, 1.0f, 1.5f);
        target.getWorld().spawnParticle(
                Particle.CLOUD,
                target.getLocation().add(0, 1.5, 0),
                10, 0.3, 0.3, 0.3, 0.1
        );
    }
}
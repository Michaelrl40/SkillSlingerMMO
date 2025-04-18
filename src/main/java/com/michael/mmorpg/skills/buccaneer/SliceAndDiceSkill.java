package com.michael.mmorpg.skills.buccaneer;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class SliceAndDiceSkill extends Skill {
    private final double meleeRange;
    private final double damagePerHit;
    private final int numberOfSlices;
    private final long timeBetweenSlices;

    public SliceAndDiceSkill(ConfigurationSection config) {
        super(config);
        this.hasCastTime = true;
        this.castTime = 0.5;
        this.isMeleeSkill = true;
        this.isHarmfulSkill = true;

        this.meleeRange = config.getDouble("range", 3.0);
        this.damagePerHit = config.getDouble("damagePerHit", 3.0);
        this.numberOfSlices = config.getInt("numberOfSlices", 5);
        // Increased time between slices to ensure damage registration
        this.timeBetweenSlices = config.getLong("timeBetweenSlices", 7);
    }

    @Override
    protected void performSkill(Player player) {
        LivingEntity target = getMeleeTarget(player, meleeRange);

        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        currentTarget = target;

        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Start the slicing sequence
        class SliceSequence {
            int slicesDelivered = 0;
        }

        SliceSequence sequence = new SliceSequence();

        // Create a delayed sequence of slices
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!target.isValid() || target.isDead() || sequence.slicesDelivered >= numberOfSlices) {
                    cancel();
                    return;
                }

                // Schedule the actual damage application separately from the visual effects
                final int currentSlice = sequence.slicesDelivered;

                // Apply the damage first
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (target.isValid() && !target.isDead()) {
                            applySliceDamage(player, target, currentSlice);
                        }
                    }
                }.runTask(plugin);

                // Then show the visual effects
                showSliceEffects(player, target, currentSlice);

                plugin.getLogger().info("Slice " + (sequence.slicesDelivered + 1) +
                        " of " + numberOfSlices + " delivered");

                sequence.slicesDelivered++;
            }
        }.runTaskTimer(plugin, 0L, timeBetweenSlices);

        setSkillSuccess(true);
    }

    private void applySliceDamage(Player player, LivingEntity target, int sliceNumber) {
        // Create unique metadata for each slice
        String sliceId = "slice_" + System.currentTimeMillis() + "_" + sliceNumber;

        // Apply damage with unique metadata for this slice
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damagePerHit));
        target.setMetadata(sliceId, new FixedMetadataValue(plugin, true));

        // Apply the damage
        target.damage(0.1, player);

        // Clean up the metadata after a short delay
        new BukkitRunnable() {
            @Override
            public void run() {
                if (target.isValid()) {
                    target.removeMetadata(sliceId, plugin);
                }
            }
        }.runTaskLater(plugin, 2L);
    }

    private void showSliceEffects(Player player, LivingEntity target, int sliceNumber) {
        // Calculate slice position
        double angle = (360.0 / numberOfSlices) * sliceNumber;
        double radius = 1.5;
        double radian = Math.toRadians(angle);

        Location center = target.getLocation();
        Location sliceStart = center.clone().add(
                radius * Math.cos(radian),
                1.0,
                radius * Math.sin(radian)
        );

        Vector sliceVector = center.clone().subtract(sliceStart).toVector().normalize();
        double sliceLength = 2.0;

        World world = target.getWorld();

        // Sound effects with varying pitch for each slice
        float pitch = 1.0f + ((float)sliceNumber * 0.1f);
        world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, pitch);
        world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.4f, pitch + 0.2f);

        // Visual effects
        for (double d = 0; d < sliceLength; d += 0.1) {
            Location particleLoc = sliceStart.clone().add(sliceVector.clone().multiply(d));

            // Slice trail
            world.spawnParticle(
                    Particle.SWEEP_ATTACK,
                    particleLoc,
                    1, 0.1, 0.1, 0.1, 0
            );

            // Additional spark effects
            if (Math.random() < 0.3) {
                world.spawnParticle(
                        Particle.CRIT,
                        particleLoc,
                        1, 0.1, 0.1, 0.1, 0.1
                );
            }
        }

        // Impact effects
        world.spawnParticle(
                Particle.BLOCK_CRUMBLE,
                target.getLocation().add(0, 1, 0),
                10, 0.3, 0.3, 0.3, 0,
                Material.REDSTONE_BLOCK.createBlockData()
        );

        world.spawnParticle(
                Particle.CRIT,
                target.getLocation().add(0, 1, 0),
                5, 0.2, 0.2, 0.2, 0.1
        );
    }
}
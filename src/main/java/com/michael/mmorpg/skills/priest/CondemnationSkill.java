package com.michael.mmorpg.skills.priest;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class CondemnationSkill extends Skill {
    private final double initialDamage;
    private final double tickDamage;
    private final int duration;
    private final int tickRate;
    private final double range;
    private static final String DOT_METADATA = "divine_condemnation_dot";

    public CondemnationSkill(ConfigurationSection config) {
        super(config);
        this.initialDamage = config.getDouble("initialdamage", 5.0);
        this.tickDamage = config.getDouble("tickdamage", 3.0);
        this.duration = config.getInt("duration", 8);
        this.tickRate = config.getInt("tickrate", 1);
        this.range = config.getDouble("range", 10.0);
        this.isHarmfulSkill = true;  // This is a damaging skill
    }

    @Override
    public void execute(Player caster) {
        // Get target using normal targeting system
        currentTarget = getTargetEntity(caster, range);

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Validate target (this will prevent targeting party members since isHarmfulSkill is true)
        if (validateTarget(caster, currentTarget)) {
            setSkillSuccess(false);
            return;
        }

        // Handle cast time
        if (hasCastTime) {
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        // Apply initial damage
        if (initialDamage > 0) {
            applyHolyDamage(caster, currentTarget, initialDamage);
        }

        // Start DoT effect
        final LivingEntity target = currentTarget;

        // Store DoT info in metadata for visual effects
        target.setMetadata(DOT_METADATA, new FixedMetadataValue(plugin, caster.getUniqueId()));

        // Start damage over time
        new BukkitRunnable() {
            int ticksElapsed = 0;
            final int totalTicks = duration * 20;
            final int ticksBetweenDamage = tickRate * 20;

            @Override
            public void run() {
                if (ticksElapsed >= totalTicks || !target.isValid() || target.isDead()) {
                    target.removeMetadata(DOT_METADATA, plugin);
                    this.cancel();
                    return;
                }

                if (ticksElapsed % ticksBetweenDamage == 0) {
                    applyHolyDamage(caster, target, tickDamage);
                    playDotEffect(target.getLocation());
                }

                // Play persistent effect every few ticks
                if (ticksElapsed % 4 == 0) {
                    playPersistentEffect(target.getLocation());
                }

                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 1L, 1L);

        // Play initial application effect
        playApplicationEffect(currentTarget.getLocation());

        // Broadcast the skill use
        broadcastLocalSkillMessage(caster, "§e✦ Divine light sears " +
                (currentTarget instanceof Player ? ((Player)currentTarget).getName() : "the target") +
                " with holy judgment!");

        setSkillSuccess(true);
    }

    private void applyHolyDamage(Player caster, LivingEntity target, double damage) {
        // Set damage as magic damage that bypasses armor
        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));

        target.damage(damage, caster);
    }

    private void playApplicationEffect(Location location) {
        // Pillar of light effect
        for (double y = 0; y < 2.5; y += 0.2) {
            Location particleLoc = location.clone().add(0, y, 0);

            // Inner holy beam
            location.getWorld().spawnParticle(
                    Particle.END_ROD,
                    particleLoc,
                    2, 0.1, 0, 0.1, 0.01
            );
        }

        // Ground effect
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
            double radius = 0.8;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particleLoc = location.clone().add(x, 0.1, z);

            location.getWorld().spawnParticle(
                    Particle.END_ROD,
                    particleLoc,
                    1, 0, 0, 0, 0
            );
        }

        // Holy sound effect
        location.getWorld().playSound(
                location,
                Sound.BLOCK_BEACON_ACTIVATE,
                0.5f,
                1.5f
        );
        location.getWorld().playSound(
                location,
                Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                0.7f,
                1.2f
        );
    }

    private void playDotEffect(Location location) {
        // Small burst of holy particles
        location.getWorld().spawnParticle(
                Particle.END_ROD,
                location.add(0, 1, 0),
                3, 0.2, 0.3, 0.2, 0.02
        );

        // Damage tick sound
        location.getWorld().playSound(
                location,
                Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                0.3f,
                1.5f
        );
    }

    private void playPersistentEffect(Location location) {
        // Subtle persistent holy effect
        location.getWorld().spawnParticle(
                Particle.END_ROD,
                location.add(0, 1, 0),
                1, 0.2, 0.3, 0.2, 0
        );
    }
}
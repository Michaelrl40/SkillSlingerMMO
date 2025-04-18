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

public class SoulWardSkill extends Skill {
    private final double shieldAmount;
    private final int shieldDuration;
    private final int immunityPeriod;
    private final double range;
    private static final String SHIELD_METADATA = "soul_ward_shield";
    private static final String IMMUNITY_METADATA = "soul_ward_immunity";

    public SoulWardSkill(ConfigurationSection config) {
        super(config);
        this.shieldAmount = config.getDouble("shieldamount", 40.0);
        this.shieldDuration = config.getInt("shieldduration", 30);
        this.immunityPeriod = config.getInt("immunityperiod", 15);
        this.range = config.getDouble("range", 10.0);
        this.isHarmfulSkill = false;  // Allow targeting party members
    }

    @Override
    public void execute(Player healer) {
        // Get target using normal targeting system
        currentTarget = getTargetEntity(healer, range);

        // Default to self if no target found
        if (currentTarget == null) {
            currentTarget = healer;
        }

        // Let validateTarget handle all party/player validation
        if (validateTarget(healer, currentTarget)) {
            return;  // Target validation failed, message already sent
        }

        // We know target is a Player because validateTarget ensures this for beneficial skills
        Player target = (Player) currentTarget;

        // Check immunity (this is specific to shield mechanic)
        if (target.hasMetadata(IMMUNITY_METADATA)) {
            healer.sendMessage("§c✦ Target cannot receive another soul ward yet!");
            setSkillSuccess(false);
            return;
        }

        if (hasCastTime) {
            startCasting(healer);
            return;
        }

        performSkill(healer);
    }

    @Override
    protected void performSkill(Player healer) {
        // We can safely cast to Player because validateTarget ensures this
        Player target = (Player) currentTarget;

        // Apply the shield
        applyShield(healer, target);
        setSkillSuccess(true);
    }


    private void applyShield(Player caster, Player target) {
        // Store shield value in metadata
        target.setMetadata(SHIELD_METADATA,
                new FixedMetadataValue(plugin, shieldAmount));

        // Apply immunity after shield breaks/expires
        target.setMetadata(IMMUNITY_METADATA,
                new FixedMetadataValue(plugin, true));

        // Start shield duration tracker
        new BukkitRunnable() {
            @Override
            public void run() {
                if (target.hasMetadata(SHIELD_METADATA)) {
                    removeShield(target);
                    target.sendMessage("§7✦ Your soul ward fades away...");
                }
            }
        }.runTaskLater(plugin, shieldDuration * 20L);

        // Start immunity duration tracker
        new BukkitRunnable() {
            @Override
            public void run() {
                if (target.hasMetadata(IMMUNITY_METADATA)) {
                    target.removeMetadata(IMMUNITY_METADATA, plugin);
                    target.sendMessage("§a✦ You can now receive a new soul ward.");
                }
            }
        }.runTaskLater(plugin, (shieldDuration + immunityPeriod) * 20L);

        // Visual and audio effects
        playShieldEffects(target.getLocation());

        // Broadcast shield application
        if (target.equals(caster)) {
            broadcastLocalSkillMessage(caster, "§b✦ " + caster.getName() +
                    " surrounds themselves with a soul ward!");
        } else {
            broadcastLocalSkillMessage(caster, "§b✦ " + caster.getName() +
                    " protects " + target.getName() + " with a soul ward!");
        }
    }

    private void removeShield(Player target) {
        if (target.hasMetadata(SHIELD_METADATA)) {
            target.removeMetadata(SHIELD_METADATA, plugin);
        }
    }

    private void playShieldEffects(Location location) {
        // Create soul particles spiraling upward
        for (double y = 0; y < 2; y += 0.2) {
            double radius = 1.0 - (y / 2);
            double angle = y * Math.PI * 2;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;

            Location particleLoc = location.clone().add(x, y, z);

            // Soul particle effect
            location.getWorld().spawnParticle(
                    Particle.SOUL,
                    particleLoc,
                    1, 0, 0, 0, 0
            );
        }

        // Shield formation sound
        location.getWorld().playSound(
                location,
                Sound.BLOCK_SOUL_SAND_BREAK,
                0.5f,
                1.2f
        );
        location.getWorld().playSound(
                location,
                Sound.BLOCK_BEACON_ACTIVATE,
                0.3f,
                1.5f
        );
    }
}
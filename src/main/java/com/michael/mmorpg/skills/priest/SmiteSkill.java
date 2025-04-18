package com.michael.mmorpg.skills.priest;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class SmiteSkill extends Skill {
    private final double damage;
    private final double knockbackForce;
    private final double range;

    public SmiteSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 12.0);
        this.knockbackForce = config.getDouble("knockbackforce", 0.5);
        this.range = config.getDouble("range", 10.0);
        // This is a harmful skill since it deals damage
        this.isHarmfulSkill = true;
    }

    @Override
    protected void performSkill(Player caster) {
        // Get the target entity in range
        LivingEntity target = getTargetEntity(caster, range);

        if (target == null) {
            caster.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Validate target (checks for party members, etc)
        if (validateTarget(caster, target)) {
            setSkillSuccess(false);
            return;
        }

        // Store target for messaging
        currentTarget = target;

        // Apply magic damage
        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.damage(damage, caster);

        // Calculate knockback direction
        Vector knockbackDirection = target.getLocation().subtract(caster.getLocation()).toVector();
        // Normalize for consistent knockback distance and add slight upward force
        knockbackDirection.normalize().setY(0.2).multiply(knockbackForce);

        // Apply knockback
        target.setVelocity(knockbackDirection);

        // Play visual and sound effects
        playSmiteEffects(target.getLocation());

        setSkillSuccess(true);
    }

    private void playSmiteEffects(Location targetLoc) {
        // Create a pillar of divine light particles
        for (double y = 0; y < 2.5; y += 0.2) {
            Location particleLoc = targetLoc.clone().add(0, y, 0);

            // Inner bright beam
            targetLoc.getWorld().spawnParticle(
                    Particle.END_ROD,
                    particleLoc,
                    2, 0.1, 0, 0.1, 0.02
            );

            // Outer impact particles at the base
            if (y < 0.4) {
                targetLoc.getWorld().spawnParticle(
                        Particle.WAX_OFF,
                        targetLoc.clone().add(0, 0.2, 0),
                        4, 0.2, 0.1, 0.2, 0.01
                );
            }
        }

        // Divine strike sound combination
        targetLoc.getWorld().playSound(
                targetLoc,
                Sound.ENTITY_LIGHTNING_BOLT_IMPACT,
                0.5f,  // Lower volume to not be overwhelming
                1.5f   // Higher pitch for a divine feel
        );

        // Secondary sound for layered effect
        targetLoc.getWorld().playSound(
                targetLoc,
                Sound.BLOCK_BEACON_DEACTIVATE,
                0.3f,
                2.0f
        );
    }
}
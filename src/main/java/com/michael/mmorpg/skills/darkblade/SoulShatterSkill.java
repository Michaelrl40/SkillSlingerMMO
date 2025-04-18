package com.michael.mmorpg.skills.darkblade;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

public class SoulShatterSkill extends Skill {
    private final double baseDamage;
    private final double missingHealthPercent;
    private final double threshold;

    public SoulShatterSkill(ConfigurationSection config) {
        super(config);
        this.baseDamage = config.getDouble("basedamage", 15.0);
        this.missingHealthPercent = config.getDouble("missinghealthpercent", 0.3);
        this.threshold = config.getDouble("threshold", 0.4);
    }

    @Override
    public void execute(Player caster) {
        // Get target using built-in targeting
        currentTarget = getTargetEntity(caster, targetRange);

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No valid target in range!");
            return;
        }

        // Start casting if has cast time
        if (hasCastTime) {
            caster.sendMessage("§5✦ Channeling soul-shattering energy at " + currentTarget.getName() + "...");
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        LivingEntity target = currentTarget;

        // Calculate damage based on missing health
        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH).getValue();
        double currentHealth = target.getHealth();
        double missingHealth = maxHealth - currentHealth;

        // Calculate final damage
        double damage = baseDamage + (missingHealth * missingHealthPercent);

        // Check if target is below threshold for enhanced effects
        boolean isExecuteRange = (currentHealth / maxHealth) <= threshold;

        if (isExecuteRange) {
            damage *= 1.5; // 50% more damage in execute range
            playExecuteEffect(target.getLocation());
        } else {
            playNormalEffect(target.getLocation());
        }

        // Apply damage
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, caster));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
        target.damage(0.1, caster);

        // Set cooldown
        plugin.getSkillManager().setCooldown(caster, getName(), getCooldown());

        // Send messages
        if (isExecuteRange) {
            caster.sendMessage("§5§l✦ You SHATTER " + target.getName() + "'s soul!");
            if (target instanceof Player) {
                ((Player)target).sendMessage("§5§l✦ " + caster.getName() + " SHATTERS your soul!");
            }
        } else {
            caster.sendMessage("§5✦ You strike " + target.getName() + "'s soul!");
            if (target instanceof Player) {
                ((Player)target).sendMessage("§5✦ " + caster.getName() + " strikes your soul!");
            }
        }

        setSkillSuccess(true);
    }

    private void playExecuteEffect(Location location) {
        // Dramatic execute effect
        location.getWorld().spawnParticle(
                Particle.SOUL,
                location.clone().add(0, 1, 0),
                30, 0.3, 0.5, 0.3, 0.1
        );

        // Purple soul fragments
        location.getWorld().spawnParticle(
                Particle.DUST,
                location.clone().add(0, 1, 0),
                40, 0.3, 0.5, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(75, 0, 130), 2)
        );

        // Dramatic sounds
        location.getWorld().playSound(
                location,
                Sound.ENTITY_WITHER_BREAK_BLOCK,
                1.0f,
                0.5f
        );
        location.getWorld().playSound(
                location,
                Sound.ENTITY_ELDER_GUARDIAN_CURSE,
                1.0f,
                2.0f
        );
    }

    private void playNormalEffect(Location location) {
        // Normal hit effect
        location.getWorld().spawnParticle(
                Particle.WITCH,
                location.clone().add(0, 1, 0),
                15, 0.3, 0.5, 0.3, 0.1
        );

        location.getWorld().spawnParticle(
                Particle.DUST,
                location.clone().add(0, 1, 0),
                20, 0.3, 0.5, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(75, 0, 130), 1.5f)
        );

        // Normal hit sound
        location.getWorld().playSound(
                location,
                Sound.ENTITY_WITHER_HURT,
                1.0f,
                1.5f
        );
    }
}
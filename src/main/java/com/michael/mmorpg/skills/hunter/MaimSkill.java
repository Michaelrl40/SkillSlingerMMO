package com.michael.mmorpg.skills.hunter;

import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class MaimSkill extends Skill {
    private final double damage;
    private final int slowDuration;
    private final int slowIntensity;

    public MaimSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 12.0);
        this.slowDuration = config.getInt("slowduration", 4) * 1000; // Convert to milliseconds
        this.slowIntensity = config.getInt("slowintensity", 2);
    }

    @Override
    protected void performSkill(Player player) {
        // Since this is a melee skill, use getMeleeTarget
        LivingEntity target = getMeleeTarget(player, targetRange);

        if (target == null) {
            player.sendMessage("§c✦ No valid target in range!");
            setSkillSuccess(false);
            return;
        }

        // Validate the target (checks for party members, etc)
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Store current target for messaging
        currentTarget = target;

        // Apply damage
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.damage(damage, player);

        // Apply slow effect only if target is a player
        if (target instanceof Player) {
            StatusEffect slowEffect = new StatusEffect(CCType.SLOW, slowDuration, player, slowIntensity);
            plugin.getStatusEffectManager().applyEffect((Player) target, slowEffect);
        } else {
            // For non-player entities, apply a vanilla Minecraft slow effect
            if (target instanceof LivingEntity) {
                ((LivingEntity) target).addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.SLOWNESS,
                                slowDuration / 50, // Convert from milliseconds to ticks (20 ticks = 1 second)
                                slowIntensity - 1, // Minecraft potion levels are 0-based
                                false,
                                true
                        )
                );
            }
        }

        // Visual and sound effects
        playEffects(player, target);

        setSkillSuccess(true);
    }

    private void playEffects(Player player, LivingEntity target) {
        Location targetLoc = target.getLocation();

        // Blood particles around target
        target.getWorld().spawnParticle(
                Particle.BLOCK_CRUMBLE,
                targetLoc.add(0, 1, 0),
                15, 0.5, 0.5, 0.5, 0.1,
                org.bukkit.Material.REDSTONE_BLOCK.createBlockData()
        );

        // Sound effects
        target.getWorld().playSound(
                targetLoc,
                Sound.ENTITY_PLAYER_ATTACK_CRIT,
                1.0f,
                0.8f
        );
        target.getWorld().playSound(
                targetLoc,
                Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK,
                1.0f,
                0.5f
        );
    }
}
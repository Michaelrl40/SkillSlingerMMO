package com.michael.mmorpg.skills.berserker;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class HeadButtSkill extends Skill {
    private final double damage;
    private final int slowDuration;
    private final int slowIntensity;
    private final int stunDuration;
    private final double healthThreshold;

    public HeadButtSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 8.0);
        this.slowDuration = config.getInt("slowduration", 60); // 3 seconds
        this.slowIntensity = config.getInt("slowintensity", 2); // Slowness II
        this.stunDuration = config.getInt("stunduration", 40); // 2 seconds
        this.healthThreshold = config.getDouble("healththreshold", 0.5); // 50%
    }

    @Override
    protected void performSkill(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        // Check rage cost
        if (!playerData.useRage(rageCost)) {
            player.sendMessage("§c✦ Not enough rage!");
            setSkillSuccess(false);
            return;
        }

        // Get target in front of player
        LivingEntity target = getMeleeTarget(player, targetRange);
        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Validate target
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Calculate target's health percentage
        double targetHealthPercent = target instanceof Player ?
                ((Player) target).getHealth() / ((Player) target).getMaxHealth() :
                target.getHealth() / target.getMaxHealth();

        // Apply damage
        target.setMetadata("skill_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new org.bukkit.metadata.FixedMetadataValue(plugin, damage));
        target.damage(damage, player);

        // Apply CC based on target's health
        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            if (targetHealthPercent > healthThreshold) {
                // Above threshold - apply slow
                StatusEffect slowEffect = new StatusEffect(CCType.SLOW, slowDuration * 50L, player, slowIntensity);
                plugin.getStatusEffectManager().applyEffect(targetPlayer, slowEffect);

                // Play slow effect sound
                target.getWorld().playSound(
                        target.getLocation(),
                        Sound.BLOCK_ANVIL_LAND,
                        0.5f,
                        1.2f
                );
            } else {
                // Below threshold - apply stun
                StatusEffect stunEffect = new StatusEffect(CCType.STUN, stunDuration * 50L, player, 1);
                plugin.getStatusEffectManager().applyEffect(targetPlayer, stunEffect);

                // Play stun effect sound
                target.getWorld().playSound(
                        target.getLocation(),
                        Sound.ENTITY_IRON_GOLEM_DAMAGE,
                        1.0f,
                        0.8f
                );
            }
        }

        // Visual effects
        Location targetLoc = target.getLocation();
        target.getWorld().spawnParticle(
                Particle.CRIT,
                targetLoc.clone().add(0, 1, 0),
                15, 0.3, 0.3, 0.3, 0.2
        );

        // Additional particles based on effect
        target.getWorld().spawnParticle(
                targetHealthPercent > healthThreshold ? Particle.CLOUD : Particle.EXPLOSION,
                targetLoc.clone().add(0, 1, 0),
                10, 0.3, 0.3, 0.3, 0.05
        );

        setSkillSuccess(true);
    }
}
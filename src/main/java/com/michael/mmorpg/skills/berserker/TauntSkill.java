package com.michael.mmorpg.skills.berserker;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.StatusEffect;
import com.michael.mmorpg.status.CCType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

public class TauntSkill extends Skill {
    private final int tauntDuration;

    public TauntSkill(ConfigurationSection config) {
        super(config);
        this.tauntDuration = config.getInt("tauntduration", 60); // 3 seconds in ticks
    }

    @Override
    protected void performSkill(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        // Get melee target
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

        // Apply taunt effect
        if (target instanceof Player) {
            // If target is a player, apply charm CC
            StatusEffect tauntEffect = new StatusEffect(CCType.CHARM, tauntDuration * 50L, player, 1);
            plugin.getStatusEffectManager().applyEffect((Player) target, tauntEffect);

            // Play effects for player target
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 1.2f);
            ((Player) target).sendMessage("§c✦ You have been taunted by " + player.getName() + "!");
        } else {
            // For mobs, just set them to target the player
            target.setMetadata("taunted_by", new FixedMetadataValue(plugin, player.getUniqueId()));
        }

        // Visual effects
        Location effectLoc = target.getLocation().add(0, 1, 0);
        target.getWorld().spawnParticle(
                Particle.ANGRY_VILLAGER,
                effectLoc,
                8, 0.3, 0.3, 0.3, 0.1
        );
        target.getWorld().spawnParticle(
                Particle.FLAME,
                effectLoc,
                15, 0.2, 0.2, 0.2, 0.05
        );

        // Play taunt sound
        target.getWorld().playSound(
                target.getLocation(),
                Sound.ENTITY_VILLAGER_NO,
                1.0f,
                0.5f
        );

        player.sendMessage("§6✦ You taunt " + (target instanceof Player ? target.getName() : "the target") + "!");

        setSkillSuccess(true);
    }
}
package com.michael.mmorpg.skills.healer;

import com.michael.mmorpg.managers.DamageDisplayManager;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class HealSkill extends Skill {

    private final double healAmount;

    public HealSkill(ConfigurationSection config) {
        super(config);
        this.healAmount = config.getDouble("healAmount", 10.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Get target (either self or another player)
        LivingEntity target;

        if (isTargetedSkill) {
            target = getTargetEntity(player, targetRange);

            if (target == null) {
                // If no target found, heal self instead
                target = player;
                player.sendMessage("§e✦ No target found, healing yourself instead.");
            }
        } else {
            // If not targeted, always heal self
            target = player;
        }

        // Store target for reference (used in broadcastLocalSkillMessage)
        currentTarget = target;

        // Check if target is valid (party member or self)
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Only players can be healed
        if (!(target instanceof Player targetPlayer)) {
            player.sendMessage("§c✦ You can only heal players!");
            setSkillSuccess(false);
            return;
        }

        // Get target's player data
        PlayerData targetData = plugin.getPlayerManager().getPlayerData(targetPlayer);
        if (targetData == null) {
            setSkillSuccess(false);
            return;
        }

        // Check if target is at full health
        if (targetData.getCurrentHealth() >= targetData.getMaxHealth()) {
            if (target == player) {
                player.sendMessage("§c✦ You are already at full health!");
            } else {
                player.sendMessage("§c✦ Target is already at full health!");
            }
            setSkillSuccess(false);
            return;
        }

        // Apply healing
        double actualHeal = Math.min(healAmount, targetData.getMaxHealth() - targetData.getCurrentHealth());
        targetData.regenHealth(actualHeal);

        // Visual and sound effects
        Location targetLoc = target.getLocation().add(0, 1, 0);
        target.getWorld().spawnParticle(Particle.HEART, targetLoc, 8, 0.5, 0.5, 0.5, 0.1);
        target.getWorld().playSound(targetLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

        // Spawn healing display
        plugin.getDamageDisplayManager().spawnDamageDisplay(
                targetLoc,
                actualHeal,
                DamageDisplayManager.DamageType.HEALING
        );

        // Send messages
        if (target == player) {
            player.sendMessage("§a✦ You healed yourself for " + String.format("%.1f", actualHeal) + " health!");
        } else {
            player.sendMessage("§a✦ You healed " + targetPlayer.getName() + " for " + String.format("%.1f", actualHeal) + " health!");
            targetPlayer.sendMessage("§a✦ " + player.getName() + " healed you for " + String.format("%.1f", actualHeal) + " health!");
        }

        setSkillSuccess(true);
    }
}
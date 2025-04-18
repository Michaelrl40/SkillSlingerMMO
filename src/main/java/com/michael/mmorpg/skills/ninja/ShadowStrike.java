package com.michael.mmorpg.skills.ninja;

import com.michael.mmorpg.managers.DamageDisplayManager;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.metadata.FixedMetadataValue;

public class ShadowStrike extends Skill {
    private final double damage;
    private final long stunDuration;
    private final int stunIntensity;

    public ShadowStrike(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 20.0);
        // Convert stun duration from seconds to milliseconds
        this.stunDuration = (long)(config.getDouble("stunduration", 1.5) * 1000);
        this.stunIntensity = config.getInt("stunintensity", 1);
    }

    @Override
    protected void performSkill(Player player) {
        // Check if player is stealthed (either from Vanish or SmokeBomb)
        if (!player.hasMetadata("vanished") && !player.hasMetadata("smokebomb")) {
            player.sendMessage("§c✦ ShadowStrike can only be used while stealthed!");
            setSkillSuccess(false);
            return;
        }

        // Get target in melee range
        LivingEntity target = getMeleeTarget(player, targetRange);

        // Validate target
        if (target == null || validateTarget(player, target)) {
            player.sendMessage("§c✦ No valid target in range!");
            setSkillSuccess(false);
            return;
        }

        // Store current target for messages
        currentTarget = target;

        // Apply damage
        applyDamage(target);

        // Apply stun if target is a player
        if (target instanceof Player) {
            Player playerTarget = (Player) target;
            StatusEffect stun = new StatusEffect(CCType.STUN, stunDuration, player, stunIntensity);
            plugin.getStatusEffectManager().applyEffect(playerTarget, stun);
        }

        // Break stealth after the strike
        breakStealth(player);

        // Play effects
        playShadowStrikeEffects(player, target);

        setSkillSuccess(true);
    }

    private void applyDamage(LivingEntity target) {
        // Set damage metadata
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, plugin));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));

        // Apply the damage
        target.damage(damage);

        // Clean up metadata after a tick
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                target.removeMetadata("skill_damage", plugin);
                target.removeMetadata("skill_damage_amount", plugin);
            }
        }.runTaskLater(plugin, 1L);

        // Show damage number
        plugin.getDamageDisplayManager().spawnDamageDisplay(
                target.getLocation(),
                damage,
                DamageDisplayManager.DamageType.CRITICAL
        );
    }

    private void playShadowStrikeEffects(Player player, LivingEntity target) {
        target.getWorld().spawnParticle(
                Particle.SMOKE,  // Using SMOKE_NORMAL instead of DUST
                target.getLocation().add(0, 1, 0),
                15, 0.2, 0.2, 0.2, 0.02
        );

        // Strike sound
        target.getWorld().playSound(
                target.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                1.0f,
                0.5f  // Lower pitch for a heavier sound
        );

    }

    private void breakStealth(Player player) {
        // Handle breaking both types of stealth
        if (player.hasMetadata("vanished")) {
            // Get the Vanish skill instance and break it
            Skill vanishSkill = plugin.getSkillManager().getSkillInstance("Vanish");
            if (vanishSkill instanceof VanishSkill) {
                // Break the vanish effect
                ((VanishSkill) vanishSkill).breakVanish(player, "Your stealth breaks as you strike from the shadows!");

                // Force Vanish on cooldown - we get the cooldown from the Vanish skill itself
                // This ensures we're using the correct cooldown value
                plugin.getSkillManager().setCooldown(player, "Vanish", vanishSkill.getCooldown());
            }
        } else if (player.hasMetadata("smokebomb")) {
            // Get the SmokeBomb skill instance and break it
            Skill smokeBombSkill = plugin.getSkillManager().getSkillInstance("SmokeBomb");
            if (smokeBombSkill instanceof SmokeBombSkill) {
                ((SmokeBombSkill) smokeBombSkill).removeSmokeBombEffects(player);
                player.sendMessage("§c✦ Your smoke bomb effect breaks as you strike!");

                // Note: We don't put SmokeBomb on cooldown since it's already a duration-based ability
            }
        }
    }
}
package com.michael.mmorpg.skills.arcanist;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.MetadataValue;

public class CounterspellSkill extends Skill {
    private final long silenceDuration;
    private final int silenceIntensity;

    public CounterspellSkill(ConfigurationSection config) {
        super(config);
        // Convert seconds to milliseconds for silence duration
        this.silenceDuration = (long)(config.getDouble("silenceduration", 3.0) * 1000);
        this.silenceIntensity = config.getInt("silenceintensity", 1);
    }

    @Override
    protected void performSkill(Player player) {
        // Get target in range
        LivingEntity target = getTargetEntity(player, targetRange);

        // Validate target
        if (target == null || validateTarget(player, target)) {
            player.sendMessage("§c✦ No valid target in range!");
            setSkillSuccess(false);
            return;
        }

        // Store current target for messages
        currentTarget = target;

        // Only affect players (since only they can cast spells)
        if (target instanceof Player) {
            Player playerTarget = (Player) target;

            // Check if target is casting
            if (playerTarget.hasMetadata("casting")) {
                // Get the skill name being cast
                String skillName = "a spell";
                for (MetadataValue meta : playerTarget.getMetadata("casting")) {
                    if (meta.value() instanceof Skill) {
                        Skill skill = (Skill) meta.value();
                        skillName = skill.getName();
                        skill.cancelCast(playerTarget);
                    }
                }

                // Success messages
                playerTarget.sendMessage("§c✦ Your " + skillName + " was countered by " + player.getName() + "!");
                player.sendMessage("§6✦ Successfully countered " + playerTarget.getName() + "'s " + skillName + "!");

                // Apply silence effect
                StatusEffect silence = new StatusEffect(CCType.SILENCE, silenceDuration, player, silenceIntensity);
                plugin.getStatusEffectManager().applyEffect(playerTarget, silence);

                // Play success effects
                playCounterspellEffects(player, playerTarget, true);
                setSkillSuccess(true);
            } else {
                // Miss messages
                player.sendMessage("§c✦ " + playerTarget.getName() + " wasn't casting anything!");
                playCounterspellEffects(player, playerTarget, false);
                setSkillSuccess(false);
            }
        } else {
            player.sendMessage("§c✦ You can only counter player spells!");
            setSkillSuccess(false);
        }
    }

    private void playCounterspellEffects(Player caster, Player target, boolean success) {
        Location targetLoc = target.getLocation().add(0, 1, 0);

        if (success) {
            // Success effects - arcane disruption
            target.getWorld().spawnParticle(
                    Particle.WITCH,
                    targetLoc,
                    30, 0.3, 0.5, 0.3, 0.1
            );
            target.getWorld().spawnParticle(
                    Particle.INSTANT_EFFECT,
                    targetLoc,
                    15, 0.3, 0.3, 0.3, 0
            );
            target.getWorld().playSound(
                    targetLoc,
                    Sound.ENTITY_ELDER_GUARDIAN_CURSE,
                    1.0f,
                    2.0f
            );
            target.getWorld().playSound(
                    targetLoc,
                    Sound.BLOCK_BEACON_DEACTIVATE,
                    1.0f,
                    1.5f
            );
        } else {
            // Miss effects - fizzle
            target.getWorld().spawnParticle(
                    Particle.SMOKE,
                    targetLoc,
                    10, 0.3, 0.3, 0.3, 0.05
            );
            target.getWorld().playSound(
                    targetLoc,
                    Sound.BLOCK_FIRE_EXTINGUISH,
                    0.5f,
                    1.2f
            );
        }
    }
}
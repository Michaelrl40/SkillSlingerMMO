package com.michael.mmorpg.skills.toxicologist;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class ToxicStrikeSkill extends Skill {
    private final double damage;
    private final long stunDuration;

    public ToxicStrikeSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 5.0);
        this.stunDuration = config.getLong("stunDuration", 40); // 2 seconds (40 ticks)
    }

    @Override
    protected void performSkill(Player player) {
        // Get player data for toxin checking
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) {
            setSkillSuccess(false);
            return;
        }

        // Check if player has enough toxin
        if (playerData.getCurrentToxin() < getToxinCost()) {
            player.sendMessage("§c☠ Need " + getToxinCost() + " toxin to use Toxic Strike!");
            setSkillSuccess(false);
            return;
        }

        // Use base melee targeting system
        LivingEntity target = getMeleeTarget(player, targetRange);
        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Store current target for messages
        currentTarget = target;

        // Play skill effect before damage
        playToxicStrikeEffect(player, target);

        // Apply damage
        target.damage(damage, player);


        // Apply stun effect
        if (target instanceof Player) {
            StatusEffect stun = new StatusEffect(CCType.STUN, stunDuration * 50, player, 1);
            plugin.getStatusEffectManager().applyEffect((Player)target, stun);
        } else {
            // For non-player entities
            target.setAI(false);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (target.isValid() && !target.isDead()) {
                        target.setAI(true);
                    }
                }
            }.runTaskLater(plugin, stunDuration);
        }

        // Play hit effect
        playHitEffect(target.getLocation());

        // Consume toxin on successful hit
        playerData.useToxin(getToxinCost());

        setSkillSuccess(true);
    }

    private void playToxicStrikeEffect(Player player, LivingEntity target) {
        Location start = player.getLocation().add(0, 1, 0);
        Location end = target.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        // Create toxic strike effect
        for (double progress = 0; progress <= 1.0; progress += 0.1) {
            Location point = start.clone().add(
                    end.clone().subtract(start).toVector().multiply(progress)
            );

            // Add some randomization for a more organic effect
            point.add(
                    Math.random() * 0.3 - 0.15,
                    Math.random() * 0.3 - 0.15,
                    Math.random() * 0.3 - 0.15
            );

            world.spawnParticle(Particle.SNEEZE, point, 1, 0.1, 0.1, 0.1, 0);
        }

        // Play sound effects
        world.playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
        world.playSound(start, Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 2.0f);
    }

    private void playHitEffect(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Create toxic explosion effect
        world.spawnParticle(Particle.SNEEZE, location, 20, 0.3, 0.3, 0.3, 0.2);
        world.playSound(location, Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.5f);
    }
}
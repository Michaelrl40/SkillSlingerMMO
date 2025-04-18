package com.michael.mmorpg.skills.berserker;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ChargeSkill extends Skill {
    private final double rageGain;
    private final double movementSpeed;

    public ChargeSkill(ConfigurationSection config) {
        super(config);
        this.rageGain = config.getDouble("ragegain", 20.0);
        this.movementSpeed = config.getDouble("movementspeed", 1.0);
    }

    @Override
    protected void performSkill(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        // Check if player is in combat
        if (plugin.getCombatManager().isInCombat(player)) {
            player.sendMessage("§c✦ Cannot use Charge while in combat!");
            setSkillSuccess(false);
            return;
        }

        // Get target
        LivingEntity target = getTargetEntity(player, targetRange);
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

        // Store initial positions
        Location startLoc = player.getLocation();
        Location targetLoc = target.getLocation();
        Vector direction = targetLoc.toVector().subtract(startLoc.toVector()).normalize();

        // Calculate distance and total ticks needed
        double distance = startLoc.distance(targetLoc);
        int totalTicks = Math.max(5, (int)(distance / movementSpeed)); // At least 5 ticks for very short distances

        // Initial charge effect
        player.getWorld().playSound(startLoc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.8f);

        new BukkitRunnable() {
            private int ticks = 0;
            private final Location lastParticleLoc = startLoc.clone();

            @Override
            public void run() {
                if (!player.isOnline() || target.isDead() || ticks >= totalTicks) {
                    if (player.isOnline()) {
                        // Add rage upon completion
                        playerData.addRage(rageGain);

                        // Play arrival effects
                        player.getWorld().playSound(player.getLocation(),
                                Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.8f, 1.2f);
                        player.getWorld().spawnParticle(
                                Particle.EXPLOSION,
                                player.getLocation(),
                                10, 0.3, 0.2, 0.3, 0.05
                        );

                        // Show rage gain message
                        player.sendMessage(String.format("§c⚔ +%.0f Rage", rageGain));
                    }
                    cancel();
                    return;
                }

                // Calculate current position
                double progress = (double) ticks / totalTicks;
                Vector movement = direction.clone().multiply(distance / totalTicks * movementSpeed);

                // Move player
                player.setVelocity(movement);

                // Particle trail
                for (int i = 0; i < 2; i++) {
                    lastParticleLoc.add(movement.clone().multiply(0.5));
                    player.getWorld().spawnParticle(
                            Particle.SMOKE,
                            lastParticleLoc,
                            1, 0.1, 0.1, 0.1, 0.05
                    );
                }

                // Rush sound effect every few ticks
                if (ticks % 4 == 0) {
                    player.getWorld().playSound(
                            player.getLocation(),
                            Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                            0.3f,
                            1.0f
                    );
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }
}
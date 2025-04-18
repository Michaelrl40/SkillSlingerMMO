package com.michael.mmorpg.skills.chainwarden;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ChainShieldSkill extends Skill {
    private final double damageReduction;
    private final double reflectChance;
    private final double chainRadius;
    private final int particleCount;

    public ChainShieldSkill(ConfigurationSection config) {
        super(config);
        this.damageReduction = config.getDouble("damagereduction", 0.25);
        this.reflectChance = config.getDouble("reflectchance", 0.3);
        this.chainRadius = config.getDouble("chainradius", 2.0);
        this.particleCount = config.getInt("particlecount", 20);
    }

    @Override
    protected void performSkill(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        // Handle deactivation
        if (isToggleActive(player)) {
            deactivateToggle(player);
            return;
        }

        // Check initial resource cost
        if (!plugin.getSkillManager().checkResources(player, playerData, this)) {
            return;
        }

        // Start the chain shield effect
        new BukkitRunnable() {
            double angle = 0;

            @Override
            public void run() {
                // Check if toggle is still active
                if (!isToggleActive(player) || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                // Check stamina drain
                if (!playerData.useStamina(staminaCost)) {
                    // Not enough stamina to maintain the shield
                    deactivateToggle(player);
                    player.sendMessage("§c✦ Not enough stamina to maintain Chain Shield!");
                    this.cancel();
                    return;
                }

                // Create orbiting chain particles
                for (int i = 0; i < 2; i++) {
                    double currentAngle = angle + (Math.PI * i);

                    for (int j = 0; j < particleCount/2; j++) {
                        double particleAngle = currentAngle + ((2 * Math.PI * j) / (particleCount/2));

                        double x = Math.cos(particleAngle) * chainRadius;
                        double z = Math.sin(particleAngle) * chainRadius;
                        double y = Math.sin(particleAngle * 2) * 0.5;

                        Location particleLoc = player.getLocation().add(x, 1 + y, z);
                        player.getWorld().spawnParticle(
                                Particle.CRIT,
                                particleLoc,
                                1, 0, 0, 0, 0
                        );
                    }
                }

                // Rotate the chains
                angle += 0.2;
                if (angle > 2 * Math.PI) {
                    angle = 0;
                }

                // Play chain sound occasionally
                if (Math.random() < 0.1) {
                    player.getWorld().playSound(
                            player.getLocation(),
                            Sound.BLOCK_CHAIN_STEP,
                            0.3f,
                            1.2f
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Apply defensive metadata
        player.setMetadata("chain_shield", new FixedMetadataValue(plugin, damageReduction));
        player.setMetadata("chain_reflect", new FixedMetadataValue(plugin, reflectChance));

        // Consume initial resources and start the toggle
        plugin.getSkillManager().consumeResources(playerData, this);
        setSkillSuccess(true);

        // Broadcast activation
        broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                player.getName() + " used " + name + "!");

        // Play activation sound
        player.getWorld().playSound(
                player.getLocation(),
                Sound.BLOCK_CHAIN_BREAK,
                1.0f,
                0.8f
        );
    }

    @Override
    protected void onToggleDeactivate(Player player) {
        // Clean up metadata
        player.removeMetadata("chain_shield", plugin);
        player.removeMetadata("chain_reflect", plugin);

        // Broadcast deactivation
        broadcastLocalSkillMessage(player, "§c[" + getPlayerClass(player) + "] " +
                player.getName() + "'s " + name + " fades!");

        // Play deactivation sound
        player.getWorld().playSound(
                player.getLocation(),
                Sound.BLOCK_CHAIN_BREAK,
                1.0f,
                0.5f
        );
    }
}
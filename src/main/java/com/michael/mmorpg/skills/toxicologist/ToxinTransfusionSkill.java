package com.michael.mmorpg.skills.toxicologist;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.managers.DamageDisplayManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ToxinTransfusionSkill extends Skill {
    private final Map<UUID, BukkitRunnable> drainTasks = new HashMap<>();
    private final double healAmount;  // Adding configurable heal amount

    public ToxinTransfusionSkill(ConfigurationSection config) {
        super(config);
        this.isTargetedSkill = false;  // Self-cast only
        this.healAmount = config.getDouble("healAmount", 1.0);  // Default to 1.0 if not specified
    }

    @Override
    protected void performSkill(Player player) {
        if (!isToggleActive(player)) {
            activateTransfusion(player);
            setSkillSuccess(true);
        }
    }

    private void activateTransfusion(Player player) {
        // Apply initial effects
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.2f);
        player.getWorld().spawnParticle(
                Particle.WITCH,
                player.getLocation().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1
        );

        // Apply debuffs
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 1, false, true));  // Slowness 2
        player.sendMessage("§2☠ Toxin Transfusion activated! Converting toxin to healing...");

        startDrainTask(player);
    }

    private void startDrainTask(Player player) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isToggleActive(player)) {
                    cancel();
                    return;
                }

                PlayerData data = getPlugin().getPlayerManager().getPlayerData(player);
                if (data == null || data.getCurrentToxin() < toxinDrainPerTick) {
                    player.sendMessage("§c☠ Toxin Transfusion ended - Not enough toxin!");
                    deactivateToggle(player);
                    return;
                }

                // Use toxin and heal
                if (data.getCurrentToxin() >= toxinDrainPerTick) {
                    data.setCurrentToxin(data.getCurrentToxin() - toxinDrainPerTick);

                    // Only heal if not at max health
                    if (player.getHealth() < player.getMaxHealth()) {
                        double currentHealth = player.getHealth();
                        double newHealth = Math.min(currentHealth + healAmount, player.getMaxHealth());
                        double actualHealing = newHealth - currentHealth;

                        player.setHealth(newHealth);

                        // Show healing numbers if actual healing occurred
                        if (actualHealing > 0) {
                            plugin.getDamageDisplayManager().spawnDamageDisplay(
                                    player.getLocation(),
                                    actualHealing,
                                    DamageDisplayManager.DamageType.HEALING
                            );
                        }

                        // Healing particles
                        player.getWorld().spawnParticle(
                                Particle.HEART,
                                player.getLocation().add(0, 1, 0),
                                1, 0.5, 0.5, 0.5, 0
                        );
                    }

                } else {
                    deactivateToggle(player);
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 1L);
        drainTasks.put(player.getUniqueId(), task);
    }

    @Override
    protected void onToggleDeactivate(Player player) {
        // Remove debuffs
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        // Cancel drain task
        BukkitRunnable task = drainTasks.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        // Deactivation effects
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.2f);
        player.getWorld().spawnParticle(
                Particle.WITCH,
                player.getLocation().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1
        );

        player.sendMessage("§c☠ Toxin Transfusion deactivated!");
    }
}
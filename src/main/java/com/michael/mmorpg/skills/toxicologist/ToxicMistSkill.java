package com.michael.mmorpg.skills.toxicologist;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

public class ToxicMistSkill extends Skill implements Listener {
    private final Map<UUID, BukkitRunnable> activeEffects = new HashMap<>();
    private final int mistRadius = 5; // Creates a 10x10 square area

    public ToxicMistSkill(ConfigurationSection config) {
        super(config);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void performSkill(Player player) {
        if (!isToggleActive(player)) {
            activateMist(player);
        } else {
            deactivateToggle(player);
        }
        setSkillSuccess(true);
    }

    private void activateMist(Player player) {
        // Initial effect
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0F, 0.5F);

        // Start toxin drain
        PlayerData data = getPlugin().getPlayerManager().getPlayerData(player);
        if (data != null) {
            data.addToxinDrain(name, toxinDrainPerTick);
        }

        // Create recurring effect
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isToggleActive(player)) {
                    this.cancel();
                    return;
                }

                // Check toxin levels
                PlayerData data = getPlugin().getPlayerManager().getPlayerData(player);
                if (data == null || !data.useToxin(toxinDrainPerTick)) {
                    player.sendMessage("§c☠ Toxic Mist ended - Not enough toxin!");
                    deactivateToggle(player);
                    return;
                }

                // Create visible mist effect
                createMistEffect(player);

                // Apply debuffs to enemies in range
                applyMistEffects(player);
            }
        };

        task.runTaskTimer(plugin, 0L, 20L); // Run every second
        activeEffects.put(player.getUniqueId(), task);

        player.sendMessage("§2☠ Toxic mist surrounds you! Use again to deactivate.");
    }

    private void createMistEffect(Player player) {
        Location center = player.getLocation();

        // Create a square area of particles
        for (int x = -mistRadius; x <= mistRadius; x++) {
            for (int z = -mistRadius; z <= mistRadius; z++) {
                // Only spawn particles at the edge and randomly inside
                if (Math.abs(x) == mistRadius || Math.abs(z) == mistRadius || Math.random() < 0.1) {
                    Location particleLoc = center.clone().add(x, 0.2, z);
                    player.getWorld().spawnParticle(
                            Particle.SNEEZE,
                            particleLoc,
                            1, 0.3, 0.3, 0.3, 0
                    );

                    // Add some particles at different heights
                    if (Math.random() < 0.3) {
                        particleLoc = center.clone().add(x, 1 + Math.random(), z);
                        player.getWorld().spawnParticle(
                                Particle.SNEEZE,
                                particleLoc,
                                1, 0.1, 0.1, 0.1, 0
                        );
                    }
                }
            }
        }
    }

    private void applyMistEffects(Player player) {
        Location center = player.getLocation();

        // Get all entities in range
        for (Entity entity : player.getWorld().getNearbyEntities(center, mistRadius, mistRadius, mistRadius)) {
            if (!(entity instanceof Player) || entity == player) continue;

            Player target = (Player) entity;

            // Skip allies (party members)
            if (plugin.getPartyManager().getParty(player) != null &&
                    plugin.getPartyManager().getParty(player).isMember(target)) {
                continue;
            }

            // Apply debuffs to enemies
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3)); // Slowness II
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 1)); // Mining Fatigue II
            target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0));// Nausea
        }
    }


    @Override
    protected void onToggleDeactivate(Player player) {
        player.removeMetadata("in_toxic_mist", plugin);

        // Stop toxin drain
        PlayerData data = getPlugin().getPlayerManager().getPlayerData(player);
        if (data != null) {
            data.removeToxinDrain(name);
        }

        // Cancel effect task
        BukkitRunnable task = activeEffects.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }

        player.sendMessage("§c☠ Toxic Mist dissipates!");
    }
}
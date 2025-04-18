package com.michael.mmorpg.skills.berserker;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class GutsSkill extends Skill implements Listener {
    private final int duration;

    public GutsSkill(ConfigurationSection config) {
        super(config);
        this.duration = config.getInt("duration", 40); // 2 seconds at 20 ticks/sec
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

        // Apply guts effect
        player.setMetadata("guts_active", new FixedMetadataValue(plugin, true));

        // Register the listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Initial activation effects
        Location loc = player.getLocation();
        player.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
        player.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_REPAIR, 1.0f, 1.2f);

        // Initial particles
        player.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                loc.add(0, 1, 0),
                50, 0.5, 0.5, 0.5, 0.5
        );
        player.getWorld().spawnParticle(
                Particle.FLAME,
                loc,
                30, 0.3, 0.5, 0.3, 0.1
        );

        player.sendMessage("§6✦ GUTS activated! You cannot die for 2 seconds!");

        // Duration tracker
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!player.isValid() || ticks >= duration) {
                    endEffect();
                    cancel();
                    return;
                }

                ticks++;

                // Ambient effects every 5 ticks
                if (ticks % 5 == 0) {
                    Location playerLoc = player.getLocation().add(0, 1, 0);
                    player.getWorld().spawnParticle(
                            Particle.FLAME,
                            playerLoc,
                            3, 0.2, 0.2, 0.2, 0.05
                    );
                }

                // Warning sound near end
                if (ticks == duration - 10) { // 0.5 seconds before end
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                }
            }

            private void endEffect() {
                if (player.isValid()) {
                    player.removeMetadata("guts_active", plugin);
                    HandlerList.unregisterAll(GutsSkill.this); // Unregister the listener
                    player.sendMessage("§c✦ GUTS effect has worn off!");

                    // End effect particles
                    Location endLoc = player.getLocation().add(0, 1, 0);
                    player.getWorld().spawnParticle(
                            Particle.FLASH,
                            endLoc,
                            2, 0.1, 0.1, 0.1, 0
                    );
                    player.getWorld().playSound(endLoc, Sound.ENTITY_IRON_GOLEM_HURT, 0.5f, 0.8f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        if (player.hasMetadata("guts_active")) {
            double finalDamage = event.getFinalDamage();
            double currentHealth = player.getHealth();

            if (currentHealth - finalDamage <= 1.0) {
                event.setDamage(currentHealth - 1.0);

                // Visual feedback
                player.getWorld().spawnParticle(
                        Particle.TOTEM_OF_UNDYING,
                        player.getLocation().add(0, 1, 0),
                        15, 0.3, 0.5, 0.3, 0.1
                );
                player.getWorld().playSound(
                        player.getLocation(),
                        Sound.ITEM_TOTEM_USE,
                        0.5f,
                        1.2f
                );
            }
        }
    }
}
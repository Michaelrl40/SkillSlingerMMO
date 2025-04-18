package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class SoulWardListener implements Listener {
    private final MinecraftMMORPG plugin;

    public SoulWardListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        // Only handle player damage
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (!player.hasMetadata("soul_ward_shield")) {
            return;
        }

        // Get shield and damage values
        double shieldAmount = player.getMetadata("soul_ward_shield").get(0).asDouble();
        double damage = event.getDamage();

        // Determine who damaged the player for messaging
        String damagerName = getDamagerName(event);

        // Calculate remaining shield and damage
        if (shieldAmount >= damage) {
            // Shield absorbs all damage
            event.setCancelled(true);
            shieldAmount -= damage;

            // Update or remove shield
            if (shieldAmount > 0) {
                player.setMetadata("soul_ward_shield",
                        new FixedMetadataValue(plugin, shieldAmount));

                // Broadcast shield remaining
                broadcastShieldStatus(player, shieldAmount, false);
            } else {
                // Shield breaks
                removeShield(player, damagerName);
            }
        } else {
            // Shield breaks and remaining damage goes through
            event.setDamage(damage - shieldAmount);
            removeShield(player, damagerName);
        }

        // Play visual and sound effects
        playShieldImpactEffects(player.getLocation());
    }

    private String getDamagerName(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
            if (damager instanceof Player) {
                return ((Player) damager).getName();
            } else if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
                return ((Player) ((Projectile) damager).getShooter()).getName();
            } else {
                return formatEntityName(damager.getType().toString());
            }
        }
        return "damage";
    }

    private void removeShield(Player player, String damagerName) {
        player.removeMetadata("soul_ward_shield", plugin);

        // Broadcast shield break
        broadcastLocalMessage(player,
                "§c✦ " + player.getName() + "'s soul ward breaks from " + damagerName + "!");

        // Schedule immunity removal notification
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && player.hasMetadata("soul_ward_immunity")) {
                    player.removeMetadata("soul_ward_immunity", plugin);
                    broadcastLocalMessage(player,
                            "§a✦ " + player.getName() + " can receive a new soul ward!");
                }
            }
        }.runTaskLater(plugin, 300L); // 15 seconds by default
    }

    private void broadcastShieldStatus(Player player, double amount, boolean breaking) {
        String message;
        if (breaking) {
            message = "§c✦ " + player.getName() + "'s soul ward breaks!";
        } else {
            message = "§e✦ " + player.getName() + "'s soul ward absorbs " +
                    String.format("%.1f", amount) + " damage!";
        }
        broadcastLocalMessage(player, message);
    }

    private void broadcastLocalMessage(Player player, String message) {
        // Broadcast to nearby players (30 block radius)
        player.getWorld().getNearbyEntities(player.getLocation(), 30, 30, 30).forEach(entity -> {
            if (entity instanceof Player) {
                ((Player) entity).sendMessage(message);
            }
        });
    }

    private void playShieldImpactEffects(Location location) {
        // Shield impact particles
        location.getWorld().spawnParticle(
                Particle.SOUL,
                location.add(0, 1, 0),
                5, 0.3, 0.3, 0.3, 0.02
        );

        // Shield impact sound
        location.getWorld().playSound(
                location,
                Sound.BLOCK_SOUL_SAND_HIT,
                0.3f,
                1.0f
        );
    }

    private String formatEntityName(String entityType) {
        return entityType.toLowerCase()
                .replace('_', ' ')
                .substring(0, 1).toUpperCase() +
                entityType.toLowerCase().replace('_', ' ').substring(1);
    }
}
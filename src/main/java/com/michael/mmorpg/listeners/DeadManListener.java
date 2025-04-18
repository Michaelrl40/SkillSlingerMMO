package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class DeadManListener implements Listener {
    private final MinecraftMMORPG plugin;

    public DeadManListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Get the actual attacker
        Player attacker = getAttacker(event);
        if (attacker == null) return;

        // Check if they have an active target
        if (!plugin.getDeadManManager().hasValidTarget(attacker)) {
            return;
        }

        // Get their marked target
        LivingEntity markedTarget = plugin.getDeadManManager().getMarkedTarget(attacker);
        if (markedTarget == null) return;

        // If not attacking their marked target, cancel the damage
        if (!event.getEntity().equals(markedTarget)) {
            event.setCancelled(true);
            attacker.sendMessage("§c✦ You can only damage your marked target!");
            return;
        }
    }

    private Player getAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                return (Player) projectile.getShooter();
            }
        }
        return null;
    }
}
package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.ninja.BackstabPassive;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class BackstabListener implements Listener {
    private final MinecraftMMORPG plugin;

    public BackstabListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Check if attacker is a player
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player attacker = (Player) event.getDamager();

        // Check if player is a Ninja
        if (!plugin.getPlayerManager().getPlayerData(attacker).getGameClass().getName().equalsIgnoreCase("Ninja")) {
            return;
        }

        // Check if this is a backstab
        if (BackstabPassive.isBackstab(attacker, event.getEntity())) {
            // Double the damage
            event.setDamage(event.getDamage() * 2.0);

            // Visual and sound effects for backstab
            event.getEntity().getWorld().spawnParticle(
                    Particle.CRIT,
                    event.getEntity().getLocation().add(0, 1, 0),
                    15, 0.3, 0.3, 0.3, 0.2
            );

            event.getEntity().getWorld().playSound(
                    event.getEntity().getLocation(),
                    Sound.ENTITY_PLAYER_ATTACK_CRIT,
                    1.0f,
                    1.5f
            );

        }
    }
}
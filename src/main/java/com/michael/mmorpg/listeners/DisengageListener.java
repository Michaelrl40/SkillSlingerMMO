package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.skyknight.CloudBounceSkill;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public class DisengageListener implements Listener {
    private final MinecraftMMORPG plugin;

    public DisengageListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFallDamage(EntityDamageEvent event) {
        // Check if it's fall damage and the entity is a player
        if (event.getEntityType() != EntityType.PLAYER ||
                event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        Player player = (Player) event.getEntity();

        // Check for disengage immunity
        if (player.hasMetadata("disengage_immunity")) {
            event.setCancelled(true);
        }

        // Check for CloudBounce immunity
        if (CloudBounceSkill.hasBounceFallImmunity(player)) {
            event.setCancelled(true);
        }
    }
}
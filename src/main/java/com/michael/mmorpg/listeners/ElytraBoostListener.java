package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class ElytraBoostListener implements Listener {
    private final MinecraftMMORPG plugin;

    public ElytraBoostListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFireworkBoost(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Check if player is gliding
        if (player.isGliding()) {
            ItemStack item = event.getItem();

            // Check if they're trying to use a firework
            if (item != null && item.getType().toString().equals("FIREWORK_ROCKET")) {
                event.setCancelled(true);
                player.sendMessage("§c✦ Firework boosting is disabled!");
            }
        }
    }
}
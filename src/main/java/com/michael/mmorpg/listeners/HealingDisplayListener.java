package com.michael.mmorpg.listeners;


import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.managers.DamageDisplayManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;

public class HealingDisplayListener implements Listener {
    private final MinecraftMMORPG plugin;

    public HealingDisplayListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHealthRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getAmount() <= 0) return;

        Player player = (Player) event.getEntity();

        // Skip Goredrinker healing since it already has its own display
        if (player.hasMetadata("goredrinker_active")) return;

        // Use the exact same display call that worked in DesperatePrayerSkill
        plugin.getDamageDisplayManager().spawnDamageDisplay(
                player.getLocation(),
                event.getAmount(),
                DamageDisplayManager.DamageType.HEALING
        );
    }
}
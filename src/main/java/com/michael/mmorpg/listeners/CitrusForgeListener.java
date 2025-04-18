package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import java.util.Iterator;

public class CitrusForgeListener implements Listener {
    private final MinecraftMMORPG plugin;

    public CitrusForgeListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (plugin.getCitrusForgeManager().isUndropable(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✦ This item cannot be dropped!");
        }
    }

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();

        // Check if this is a magic fruit
        String fruitId = plugin.getCitrusForgeManager().getFruitId(item);
        if (fruitId == null) return;

        // Cancel vanilla consumption
        event.setCancelled(true);

        // Try to consume the fruit
        plugin.getCitrusForgeManager().consumeFruit(player, item);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Iterator<ItemStack> iterator = event.getDrops().iterator();

        // Remove citrus forge items from drops
        while (iterator.hasNext()) {
            ItemStack item = iterator.next();
            if (plugin.getCitrusForgeManager().getFruitId(item) != null) {
                iterator.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();

        // Prevent planting if it's a citrus forge item
        if (plugin.getCitrusForgeManager().getFruitId(item) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✦ This magical fruit cannot be planted!");
        }
    }
}
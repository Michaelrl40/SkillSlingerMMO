package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

public class EconomyInventoryListener implements Listener {
    private final MinecraftMMORPG plugin;

    public EconomyInventoryListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();

        // Handle bank inventory
        if (title.startsWith("§6✦ Bank Vault:")) {
            // Allow normal inventory interaction, but handle upgrade button
            if (event.getCurrentItem() != null &&
                    event.getCurrentItem().hasItemMeta() &&
                    event.getCurrentItem().getItemMeta().getDisplayName().contains("Upgrade Bank")) {

                event.setCancelled(true);
                plugin.getBankManager().upgradeBank(player);
            }
        }
        // Handle auction house inventory
        else if (title.startsWith("§6✦ Auction House:")) {
            event.setCancelled(true);
            plugin.getAuctionHouseManager().handleInventoryClick(player, event.getRawSlot(), inventory);
        }
        // Handle purchase confirmation
        else if (title.equals("§6✦ Confirm Purchase")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 11 || event.getRawSlot() == 15) { // Confirm or cancel button
                plugin.getAuctionHouseManager().handleConfirmPurchase(player, event.getRawSlot());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();

        // Handle bank inventory closing
        if (title.startsWith("§6✦ Bank Vault:")) {
            plugin.getBankManager().handleInventoryClose(player, inventory);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Clean up any pending bank sessions
        plugin.getBankManager().unloadPlayerBank(player);
    }
}
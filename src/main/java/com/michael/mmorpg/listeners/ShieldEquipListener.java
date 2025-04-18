package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class ShieldEquipListener implements Listener {
    private final MinecraftMMORPG plugin;

    public ShieldEquipListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        startShieldCheckTask();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

        if (playerData == null || !playerData.hasClass()) return;

        // Check if not a Guardian
        boolean isGuardian = playerData.getGameClass().getName().equalsIgnoreCase("Guardian");

        // Check both current item and cursor for shields
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Check for shield movement
        if ((clickedItem != null && clickedItem.getType() == Material.SHIELD) ||
                (cursorItem != null && cursorItem.getType() == Material.SHIELD)) {

            if (!isGuardian) {
                // Check if trying to move to hotbar or offhand slot
                if (isMovingToHotbar(event.getSlot(), event.getRawSlot(), event.isShiftClick()) ||
                        event.getRawSlot() == 40) { // 40 is the offhand slot
                    event.setCancelled(true);
                    player.sendMessage("§c✦ Only Guardians can equip shields!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

        if (playerData == null || !playerData.hasClass()) return;

        // If either item is a shield and player isn't a Guardian
        if ((event.getMainHandItem() != null && event.getMainHandItem().getType() == Material.SHIELD ||
                event.getOffHandItem() != null && event.getOffHandItem().getType() == Material.SHIELD) &&
                !playerData.getGameClass().getName().equalsIgnoreCase("Guardian")) {
            event.setCancelled(true);
            player.sendMessage("§c✦ Only Guardians can use shields!");
        }
    }

    private boolean isMovingToHotbar(int slot, int rawSlot, boolean isShiftClick) {
        // Check if the destination is hotbar (slots 0-8)
        return (rawSlot >= 0 && rawSlot <= 8) ||
                (isShiftClick && slot >= 9 && slot <= 35); // Inventory to hotbar shift-click
    }

    private void startShieldCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                    if (playerData == null || !playerData.hasClass()) continue;

                    // Skip if player is a Guardian
                    if (playerData.getGameClass().getName().equalsIgnoreCase("Guardian")) continue;

                    // Check hotbar for shields
                    for (int i = 0; i < 9; i++) {
                        ItemStack item = player.getInventory().getItem(i);
                        if (item != null && item.getType() == Material.SHIELD) {
                            // Find first empty slot in main inventory
                            int emptySlot = findEmptyMainInventorySlot(player);
                            if (emptySlot != -1) {
                                // Move shield to main inventory
                                player.getInventory().setItem(emptySlot, item);
                                player.getInventory().setItem(i, null);
                                player.sendMessage("§c✦ Shields have been moved to your inventory!");
                            }
                        }
                    }

                    // Check and clear offhand if it contains a shield
                    ItemStack offhandItem = player.getInventory().getItemInOffHand();
                    if (offhandItem != null && offhandItem.getType() == Material.SHIELD) {
                        // Find empty slot and move shield there
                        int emptySlot = findEmptyMainInventorySlot(player);
                        if (emptySlot != -1) {
                            player.getInventory().setItem(emptySlot, offhandItem);
                            player.getInventory().setItemInOffHand(null);
                            player.sendMessage("§c✦ Shield has been moved from your offhand to inventory!");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second
    }

    private int findEmptyMainInventorySlot(Player player) {
        for (int i = 9; i < 36; i++) {
            if (player.getInventory().getItem(i) == null) {
                return i;
            }
        }
        return -1;
    }
}
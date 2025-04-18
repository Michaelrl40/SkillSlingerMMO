package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryType;

public class ArmorEquipListener implements Listener {
    private final MinecraftMMORPG plugin;

    public ArmorEquipListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        startArmorCheckTask();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

        if (playerData == null || !playerData.hasClass()) return;

        // Check if the click involves armor slots
        if (event.getSlotType() == InventoryType.SlotType.ARMOR) {
            ItemStack item = event.getCursor();
            if (item != null && isArmor(item)) {
                if (!canUseArmor(player, item)) {
                    event.setCancelled(true);
                    player.sendMessage("§cYour class cannot use this type of armor!");
                }
            }
        }

        // Check if player is trying to shift-click armor into armor slots
        if (event.isShiftClick() && event.getCurrentItem() != null && isArmor(event.getCurrentItem())) {
            if (!canUseArmor(player, event.getCurrentItem())) {
                event.setCancelled(true);
                player.sendMessage("§cYour class cannot use this type of armor!");
            }
        }
    }

    private void startArmorCheckTask() {
        // Run every 2 seconds (40 ticks)
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                checkAndRemoveInvalidArmor(player);
            }
        }, 40L, 40L);
    }

    private void checkAndRemoveInvalidArmor(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null || !playerData.hasClass()) return;

        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item != null && isArmor(item) && !canUseArmor(player, item)) {
                // Move the armor to inventory if there's space, otherwise drop it
                if (player.getInventory().firstEmpty() != -1) {
                    // Remove from armor slot
                    if (item.equals(player.getInventory().getHelmet())) {
                        player.getInventory().setHelmet(null);
                    } else if (item.equals(player.getInventory().getChestplate())) {
                        player.getInventory().setChestplate(null);
                    } else if (item.equals(player.getInventory().getLeggings())) {
                        player.getInventory().setLeggings(null);
                    } else if (item.equals(player.getInventory().getBoots())) {
                        player.getInventory().setBoots(null);
                    }

                    // Add to inventory
                    player.getInventory().addItem(item);
                    player.sendMessage("§c✦ You cannot wear this type of armor with your class!");
                } else {
                    // Drop the item if inventory is full
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    player.sendMessage("§c✦ You cannot wear this type of armor with your class! The item has been dropped.");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item != null && isArmor(item)) {
                Player player = event.getPlayer();
                if (!canUseArmor(player, item)) {
                    event.setCancelled(true);
                    player.sendMessage("§cYour class cannot use this type of armor!");
                }
            }
        }
    }

    private boolean isArmor(ItemStack item) {
        String type = item.getType().toString().toUpperCase();
        return type.endsWith("_HELMET") ||
                type.endsWith("_CHESTPLATE") ||
                type.endsWith("_LEGGINGS") ||
                type.endsWith("_BOOTS");
    }

    private boolean canUseArmor(Player player, ItemStack armor) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null || !playerData.hasClass()) return false;

        String armorType = armor.getType().toString();
        return playerData.getGameClass().getAllowedArmor().contains(armorType);
    }
}

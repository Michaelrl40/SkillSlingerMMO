package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.skyknight.CloudbornSkill;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class CloudbornListener implements Listener {
    private final MinecraftMMORPG plugin;

    public CloudbornListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        startElytraCheckTask();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Add a short delay to ensure PlayerData is loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
            if (playerData != null && playerData.hasClass() &&
                    playerData.getGameClass() != null &&
                    playerData.getGameClass().getName().equalsIgnoreCase("SkyKnight")) {

                // Double check they don't already have an elytra
                if (player.getInventory().getChestplate() == null ||
                        player.getInventory().getChestplate().getType() != Material.ELYTRA) {
                    CloudbornSkill.initializePassive(player);
                    player.sendMessage("§b✦ Cloudborn passive activated!");
                }
            }
        }, 5L); // 5 tick delay (0.25 seconds)
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up when player leaves
        CloudbornSkill.removePassive(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

        if (playerData != null && playerData.hasClass() &&
                playerData.getGameClass().getName().equalsIgnoreCase("SkyKnight")) {
            // Re-equip elytra after respawn
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                CloudbornSkill.initializePassive(player);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Check if the interaction involves an elytra (either clicked or cursor)
        boolean isElytraInvolved = (clickedItem != null && clickedItem.getType() == Material.ELYTRA) ||
                (cursorItem != null && cursorItem.getType() == Material.ELYTRA);

        if (isElytraInvolved) {
            // If not a SkyKnight, prevent any elytra interaction
            if (playerData == null || !playerData.hasClass() ||
                    !playerData.getGameClass().getName().equalsIgnoreCase("SkyKnight")) {
                event.setCancelled(true);
                player.sendMessage("§c✦ Only SkyKnights can use elytras!");
                return;
            }

            // For SkyKnights, prevent removing their Cloudborn elytra in survival/adventure
            if (event.getSlot() == 38 && // Chestplate slot in player inventory
                    (player.getGameMode() == GameMode.SURVIVAL ||
                            player.getGameMode() == GameMode.ADVENTURE)) {
                event.setCancelled(true);
                player.sendMessage("§c✦ You cannot remove the Cloudborn elytra!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && item.getType() == Material.ELYTRA) {
            PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
            if (playerData == null || !playerData.hasClass() ||
                    !playerData.getGameClass().getName().equalsIgnoreCase("SkyKnight")) {
                event.setCancelled(true);
                player.sendMessage("§c✦ Only SkyKnights can use elytras!");
            }
        }
    }

    private void startElytraCheckTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                ItemStack chestplate = player.getInventory().getChestplate();

                if (chestplate != null && chestplate.getType() == Material.ELYTRA) {
                    if (playerData == null || !playerData.hasClass() ||
                            !playerData.getGameClass().getName().equalsIgnoreCase("SkyKnight")) {
                        // Simply remove the elytra
                        player.getInventory().setChestplate(null);
                        player.sendMessage("§c✦ You cannot wear an elytra as a non-SkyKnight!");
                    }
                }
            }
        }, 20L, 20L); // Check every second
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorEquip(org.bukkit.event.block.BlockDispenseEvent event) {
        // Prevent dispensers from equipping elytras on non-SkyKnights
        if (event.getBlock().getType() == Material.DISPENSER) {
            ItemStack item = event.getItem();
            if (item != null && item.getType() == Material.ELYTRA) {
                event.setCancelled(true);
            }
        }
    }

    // Add prevention for dropping elytras
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (item.getType() == Material.ELYTRA) {
            Player player = event.getPlayer();
            PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

            if (playerData == null || !playerData.hasClass() ||
                    !playerData.getGameClass().getName().equalsIgnoreCase("SkyKnight")) {
                event.setCancelled(true);
                player.sendMessage("§c✦ Only SkyKnights can interact with elytras!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

        // Check if player is a SkyKnight
        if (playerData != null && playerData.hasClass() &&
                playerData.getGameClass().getName().equalsIgnoreCase("SkyKnight")) {

            // Create a list to store items that should remain
            List<ItemStack> remainingItems = new ArrayList<>();

            // Check all drops
            for (ItemStack item : event.getDrops()) {
                // Skip the wings
                if (item != null && item.getType() == Material.ELYTRA) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == 10015) {
                        // This is our SkyKnight wing - don't add to remaining items
                        continue;
                    }

                    // Also check for the tag if they don't have custom model data
                    if (meta != null) {
                        NamespacedKey key = new NamespacedKey(plugin, "skyknight_wings");
                        if (meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                            continue;
                        }
                    }
                }

                // Keep all other items
                remainingItems.add(item);
            }

            // Update the drops to only include remaining items
            event.getDrops().clear();
            event.getDrops().addAll(remainingItems);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

        if (playerData != null && playerData.hasClass() &&
                playerData.getGameClass().getName().equalsIgnoreCase("SkyKnight")) {

            // If trying to start gliding
            if (event.isGliding()) {
                // Check if player has remaining flight time
                if (!CloudbornSkill.hasRemainingFlightTime(player)) {
                    event.setCancelled(true);
                    player.sendMessage("§c✦ You must land to reset your flight time!");
                    return;
                }
            }
        }
    }
}
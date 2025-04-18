package com.michael.mmorpg.deathchest;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DeathChestListener implements Listener {
    private final MinecraftMMORPG plugin;

    public DeathChestListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        plugin.getLogger().info("Player died: " + player.getName());
        plugin.getLogger().info("Tracking death for: " + player.getName());

        // Don't create death chests for creative mode players
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        // Only create a death chest if player has at least one item
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        if (drops.isEmpty()) {
            return;
        }

        // Create death chest
        DeathChest chest = plugin.getDeathChestManager().createDeathChest(player, drops);

        if (chest != null) {
            // Clear drops since they're now in the chest
            event.getDrops().clear();

            // Notify player
            player.sendMessage("§6Your items have been stored in a death chest at §f" +
                    formatLocation(chest.getLocation()) + "§6.");
            player.sendMessage("§6Use §f/deathchest §6to see its location.");
            player.sendMessage("§6Right-click the sign to quick loot your items!");
        }
    }

    @EventHandler
    public void onChestOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();

        // Check if inventory is from a death chest
        if (event.getInventory().getHolder() instanceof org.bukkit.block.Chest) {
            org.bukkit.block.Chest chest = (org.bukkit.block.Chest) event.getInventory().getHolder();
            Location chestLoc = chest.getLocation();

            DeathChest deathChest = plugin.getDeathChestManager().getDeathChestAt(chestLoc);

            if (deathChest != null && !deathChest.canAccess(player)) {
                event.setCancelled(true);
                player.sendMessage("§cThis death chest is locked by " + deathChest.getOwnerName() +
                        " for " + deathChest.getFormattedRemainingTime() + ".");
            }
        }
    }

    @EventHandler
    public void onChestBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.CHEST) {
            DeathChest deathChest = plugin.getDeathChestManager().getDeathChestAt(block.getLocation());

            if (deathChest != null) {
                Player player = event.getPlayer();

                // Only the owner can break the chest while it's locked
                if (deathChest.isLocked() && !deathChest.getOwnerUUID().equals(player.getUniqueId())) {
                    event.setCancelled(true);
                    player.sendMessage("§cThis death chest is locked by " + deathChest.getOwnerName() +
                            " for " + deathChest.getFormattedRemainingTime() + ".");
                } else {
                    // Mark chest as claimed when broken
                    deathChest.setClaimed(true);

                    // Also remove the sign if it exists
                    if (deathChest.getSignLocation() != null) {
                        Block signBlock = deathChest.getSignLocation().getBlock();
                        if (signBlock.getType().name().contains("SIGN")) {
                            signBlock.setType(Material.AIR);
                        }
                    }

                    plugin.getDeathChestManager().removeDeathChest(deathChest);

                    // Give player a chest back if it's the owner
                    if (deathChest.getOwnerUUID().equals(player.getUniqueId())) {
                        player.getInventory().addItem(new ItemStack(Material.CHEST, 1));
                        player.sendMessage("§aYou've broken your death chest and received a chest back.");
                    }
                }
            }
        } else if (block.getType().name().contains("SIGN")) {
            // Prevent breaking the death chest sign directly
            Location signLoc = block.getLocation();
            for (DeathChest chest : plugin.getDeathChestManager().getAllDeathChests()) {
                if (chest.getSignLocation() != null &&
                        isSameLocation(chest.getSignLocation(), signLoc)) {
                    Player player = event.getPlayer();
                    // Only allow the owner to break the sign (which will break the chest too)
                    if (!chest.getOwnerUUID().equals(player.getUniqueId())) {
                        event.setCancelled(true);
                        player.sendMessage("§cYou cannot break this death chest sign.");
                    } else {
                        // If owner breaks sign, remove the whole chest too
                        Block chestBlock = chest.getLocation().getBlock();
                        if (chestBlock.getType() == Material.CHEST) {
                            chest.setClaimed(true);
                            plugin.getDeathChestManager().removeDeathChest(chest);
                            chestBlock.setType(Material.AIR);
                            player.getInventory().addItem(new ItemStack(Material.CHEST, 1));
                            player.sendMessage("§aYou've broken your death chest sign and received a chest back.");
                        }
                    }
                    break;
                }
            }
        }
    }

    // Prevent sign editing
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        if (block.getType().name().contains("SIGN")) {
            Location signLoc = block.getLocation();
            for (DeathChest chest : plugin.getDeathChestManager().getAllDeathChests()) {
                if (chest.getSignLocation() != null &&
                        isSameLocation(chest.getSignLocation(), signLoc)) {
                    event.setCancelled(true);
                    Player player = event.getPlayer();
                    player.sendMessage("§cYou cannot edit death chest signs.");

                    // Restore the original sign text
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            Block signBlock = signLoc.getBlock();
                            if (signBlock.getState() instanceof Sign) {
                                Sign sign = (Sign) signBlock.getState();
                                sign.setLine(0, "§6Death Chest");
                                sign.setLine(1, "§f" + chest.getOwnerName());
                                sign.setLine(2, ""); // Date was here, but we'll skip for now
                                sign.setLine(3, chest.isLocked() ?
                                        "§cLocked: " + chest.getFormattedRemainingTime() :
                                        "§aUnlocked");
                                sign.update(true);
                            }
                        }
                    }.runTaskLater(plugin, 1L);
                    break;
                }
            }
        }
    }

    // Handle all interactions with signs and chests
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        // First, check if this is a death chest sign
        if (block.getType().name().contains("SIGN")) {
            Location signLoc = block.getLocation();

            for (DeathChest chest : plugin.getDeathChestManager().getAllDeathChests()) {
                if (chest.getSignLocation() != null &&
                        isSameLocation(chest.getSignLocation(), signLoc)) {
                    // This is a death chest sign - cancel normal interaction
                    event.setCancelled(true);

                    Player player = event.getPlayer();

                    // Only the owner can quick loot
                    if (chest.getOwnerUUID().equals(player.getUniqueId())) {
                        player.sendMessage("§aQuick looting your death chest...");
                        quickLootChest(player, chest);
                    } else {
                        // Non-owners just get a message
                        if (chest.isLocked()) {
                            player.sendMessage("§cThis death chest is locked by " + chest.getOwnerName() +
                                    " for " + chest.getFormattedRemainingTime() + ".");
                        } else {
                            player.sendMessage("§7This death chest belongs to " + chest.getOwnerName() +
                                    " but is now unlocked.");
                        }
                    }
                    return;
                }
            }
        }

        // Also check if this is a death chest (not the sign)
        if (block.getType() == Material.CHEST) {
            DeathChest deathChest = plugin.getDeathChestManager().getDeathChestAt(block.getLocation());
            if (deathChest != null && deathChest.getOwnerUUID().equals(event.getPlayer().getUniqueId())) {
                // Owner clicking their own chest directly
                Player player = event.getPlayer();

                // If the player is sneaking (shift-clicking), quick loot instead of opening
                if (player.isSneaking()) {
                    event.setCancelled(true);
                    player.sendMessage("§aQuick looting your death chest...");
                    quickLootChest(player, deathChest);
                }
            }
        }
    }

    // Quick loot the chest contents
    private void quickLootChest(Player player, DeathChest deathChest) {
        plugin.getLogger().info("Starting quick loot process for player: " + player.getName());

        Block block = deathChest.getLocation().getBlock();
        if (block.getType() != Material.CHEST) {
            player.sendMessage("§cThe death chest no longer exists.");
            plugin.getLogger().info("Chest block not found at location");
            return;
        }

        Chest chest = (Chest) block.getState();
        Inventory chestInv = chest.getInventory();

        boolean allItemsLooted = true;
        int itemsLooted = 0;

        // Try to add all items to player inventory
        for (ItemStack item : chestInv.getContents()) {
            if (item != null) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    allItemsLooted = false;
                    plugin.getLogger().info("Player inventory full, couldn't loot all items");
                    player.sendMessage("§cYour inventory is full! Some items remain in the chest.");
                    break;
                } else {
                    itemsLooted++;
                }
            }
        }

        plugin.getLogger().info("Items looted: " + itemsLooted);

        if (allItemsLooted) {
            player.sendMessage("§aAll items have been looted!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

            // Clear chest inventory
            chestInv.clear();

            // Mark as claimed and remove
            deathChest.setClaimed(true);

            // Give player a chest back
            player.getInventory().addItem(new ItemStack(Material.CHEST, 1));
            plugin.getLogger().info("Giving chest back to player");

            // Remove the chest and sign
            if (deathChest.getSignLocation() != null) {
                Block signBlock = deathChest.getSignLocation().getBlock();
                if (signBlock.getType().name().contains("SIGN")) {
                    signBlock.setType(Material.AIR);
                    plugin.getLogger().info("Removed sign block");
                }
            }
            block.setType(Material.AIR);
            plugin.getLogger().info("Removed chest block");

            // Remove the death chest from manager
            plugin.getDeathChestManager().removeDeathChest(deathChest);
            plugin.getLogger().info("Death chest removed from manager");
        } else {
            // Open chest inventory normally if not all items could be looted
            player.openInventory(chestInv);
            plugin.getLogger().info("Opening chest inventory for manual looting");
        }
    }

    private boolean isSameLocation(Location loc1, Location loc2) {
        return loc1.getWorld().equals(loc2.getWorld()) &&
                loc1.getBlockX() == loc2.getBlockX() &&
                loc1.getBlockY() == loc2.getBlockY() &&
                loc1.getBlockZ() == loc2.getBlockZ();
    }

    private String formatLocation(Location location) {
        return String.format("x:%d, y:%d, z:%d",
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}
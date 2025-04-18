package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShopManager implements Listener {
    private final MinecraftMMORPG plugin;
    private final Map<String, UUID> shopOwners;
    private final Map<String, String> shopTypes; // Location -> "BUY" or "SELL"
    private final int SHOP_CREATE_COST = 1000;
    private final BlockFace[] BLOCK_FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
    private final Map<String, Boolean> adminShops = new HashMap<>(); // Location -> isAdmin
    private static final UUID SERVER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000"); // Special UUID for server shops

    public ShopManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.shopOwners = new HashMap<>();
        this.shopTypes = new HashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onSignCreate(SignChangeEvent event) {
        if (!event.getLine(0).equalsIgnoreCase("[shop]") && !event.getLine(0).equalsIgnoreCase("[adminshop]")) {
            return;
        }

        Player player = event.getPlayer();
        boolean isAdminShop = event.getLine(0).equalsIgnoreCase("[adminshop]");

        // Check permission for admin shop
        if (isAdminShop && !player.isOp() && !player.hasPermission("skillslinger.shop.admin")) {
            player.sendMessage("§c✦ You don't have permission to create admin shops!");
            event.setCancelled(true);
            return;
        }

        // Regular shop permission check
        if (!isAdminShop && !player.hasPermission("skillslinger.shop.create")) {
            player.sendMessage("§c✦ You don't have permission to create shops!");
            event.setCancelled(true);
            return;
        }

        // Only charge for regular shops
        if (!isAdminShop) {
            // Check if they can afford it
            PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
            if (!playerData.hasEnoughCoins(SHOP_CREATE_COST)) {
                player.sendMessage("§c✦ You need " + SHOP_CREATE_COST + " coins to create a shop!");
                event.setCancelled(true);
                return;
            }
        }

        // Check for chest
        Block signBlock = event.getBlock();
        Block chestBlock = getAttachedChest(signBlock);

        if (chestBlock == null || !(chestBlock.getState() instanceof Chest)) {
            player.sendMessage("§c✦ Shop signs must be placed on or above a chest!");
            event.setCancelled(true);
            return;
        }

        try {
            String itemName = event.getLine(1).toUpperCase();
            String shopType = event.getLine(2).toUpperCase();
            int price;

            // Validate shop type
            if (!shopType.equals("BUY") && !shopType.equals("SELL")) {
                throw new IllegalArgumentException("Shop type must be BUY or SELL");
            }

            // Parse item and price
            Material material = Material.valueOf(itemName);
            try {
                price = Integer.parseInt(event.getLine(3));
                if (price <= 0) throw new IllegalArgumentException();
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid price");
            }

            // Charge player if not admin shop
            if (!isAdminShop) {
                PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
                playerData.removeCoins(SHOP_CREATE_COST);
            }

            // Format sign based on shop type and admin status
            String header;
            if (isAdminShop) {
                header = shopType.equals("BUY") ? "§b§l[A-BUY]" : "§d§l[A-SELL]";
            } else {
                header = shopType.equals("BUY") ? "§a§l[BUY]" : "§c§l[SELL]";
            }

            event.setLine(0, header);
            event.setLine(1, "§e" + material.toString());
            event.setLine(2, "§6Price: §f" + price);
            event.setLine(3, isAdminShop ? "§7SERVER" : "§7" + player.getName());

            // Store shop data
            String locationKey = locationToString(signBlock.getLocation());
            shopOwners.put(locationKey, isAdminShop ? SERVER_UUID : player.getUniqueId());
            shopTypes.put(locationKey, shopType);
            adminShops.put(locationKey, isAdminShop);

            // Success message
            player.sendMessage("");
            player.sendMessage("§6§l=== Shop Created! ===");
            player.sendMessage("§eType: §f" + (isAdminShop ? "Admin " : "") + shopType + " Shop");
            player.sendMessage("§eItem: §f" + material.toString());
            player.sendMessage("§ePrice: §f" + price + " coins");
            if (!isAdminShop) {
                player.sendMessage("§7Cost: §f" + SHOP_CREATE_COST + " coins");
            }
            if (shopType.equals("BUY")) {
                player.sendMessage("§7Players can buy items from this shop");
            } else {
                player.sendMessage("§7Players can sell items to this shop");
            }
            player.sendMessage("§6§l===================");
            player.sendMessage("");

            // Play success sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);

        } catch (IllegalArgumentException e) {
            player.sendMessage("§c✦ Invalid shop format! Use:");
            player.sendMessage("§7[shop] or [adminshop]");
            player.sendMessage("§7<item>");
            player.sendMessage("§7BUY or SELL");
            player.sendMessage("§7<price>");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Check if breaking a shop sign
        if (block.getState() instanceof Sign) {
            Sign sign = (Sign) block.getState();
            if (sign.getLine(0).contains("[BUY]") || sign.getLine(0).contains("[SELL]")) {
                String locationKey = locationToString(block.getLocation());
                UUID ownerUUID = shopOwners.get(locationKey);

                if (ownerUUID != null && !player.getUniqueId().equals(ownerUUID) && !player.hasPermission("skillslinger.shop.admin")) {
                    event.setCancelled(true);
                    player.sendMessage("§c✦ You cannot break someone else's shop!");
                    return;
                }

                // Remove shop data if broken
                shopOwners.remove(locationKey);
                shopTypes.remove(locationKey);
                player.sendMessage("§a✦ Shop removed!");
            }
        }

        // Check if breaking a shop chest
        else if (block.getState() instanceof Chest) {
            Block signBlock = getAttachedSign(block);
            if (signBlock != null && signBlock.getState() instanceof Sign) {
                Sign sign = (Sign) signBlock.getState();
                if (sign.getLine(0).contains("[BUY]") || sign.getLine(0).contains("[SELL]")) {
                    String locationKey = locationToString(signBlock.getLocation());
                    UUID ownerUUID = shopOwners.get(locationKey);

                    if (ownerUUID != null && !player.getUniqueId().equals(ownerUUID) && !player.hasPermission("skillslinger.shop.admin")) {
                        event.setCancelled(true);
                        player.sendMessage("§c✦ You cannot break someone else's shop chest!");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onChestOpen(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (!(block.getState() instanceof Chest)) return;

        // Check if this chest is part of a shop
        Block signBlock = getAttachedSign(block);
        if (signBlock != null && signBlock.getState() instanceof Sign) {
            Sign sign = (Sign) signBlock.getState();
            if (sign.getLine(0).contains("[BUY]") || sign.getLine(0).contains("[SELL]") ||
                    sign.getLine(0).contains("[A-BUY]") || sign.getLine(0).contains("[A-SELL]")) {
                String locationKey = locationToString(signBlock.getLocation());
                UUID ownerUUID = shopOwners.get(locationKey);
                Player player = event.getPlayer();
                boolean isAdminShop = SERVER_UUID.equals(ownerUUID);

                // If it's an admin shop, only ops/admins can open it
                if (isAdminShop && !player.isOp() && !player.hasPermission("skillslinger.shop.admin")) {
                    event.setCancelled(true);
                    player.sendMessage("§c✦ You can't open server shop chests!");
                    return;
                }

                // If it's a regular shop and they're not the owner or admin, cancel
                if (!isAdminShop && !player.getUniqueId().equals(ownerUUID) &&
                        !player.hasPermission("skillslinger.shop.admin")) {
                    event.setCancelled(true);
                    player.sendMessage("§c✦ You can't open someone else's shop chest!");
                    return;
                }

                // Skip item checks for admin shops
                if (!isAdminShop && player.getUniqueId().equals(ownerUUID)) {
                    // Get the shop's item type
                    try {
                        Material shopItem = Material.valueOf(sign.getLine(1).substring(2)); // Remove color code
                        Chest chest = (Chest) block.getState();

                        // Check existing items in chest
                        for (ItemStack item : chest.getInventory().getContents()) {
                            if (item != null && item.getType() != shopItem) {
                                player.sendMessage("§c✦ This shop chest can only contain " + shopItem.toString() + "!");
                                event.setCancelled(true);
                                return;
                            }
                        }

                        // If they're holding an item, check if it matches
                        ItemStack heldItem = player.getInventory().getItemInMainHand();
                        if (!heldItem.getType().isAir() && heldItem.getType() != shopItem) {
                            player.sendMessage("§c✦ You can only put " + shopItem.toString() + " in this shop!");
                            event.setCancelled(true);
                            return;
                        }
                    } catch (Exception e) {
                        player.sendMessage("§c✦ There was an error with this shop's configuration!");
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onShopInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (!(block.getState() instanceof Sign)) return;

        Sign sign = (Sign) block.getState();
        String firstLine = sign.getLine(0);
        if (!firstLine.contains("[BUY]") && !firstLine.contains("[SELL]")) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        Block chestBlock = getAttachedChest(block);

        if (chestBlock == null || !(chestBlock.getState() instanceof Chest)) {
            player.sendMessage("§c✦ This shop's chest is missing!");
            return;
        }

        Chest chest = (Chest) chestBlock.getState();
        String locationKey = locationToString(block.getLocation());
        UUID ownerUUID = shopOwners.get(locationKey);
        String shopType = shopTypes.get(locationKey);

        try {
            Material material = Material.valueOf(sign.getLine(1).substring(2)); // Remove color code
            int price = Integer.parseInt(sign.getLine(2).split(": §f")[1]);

            if (shopType.equals("BUY")) {
                handleBuy(player, material, price, chest, ownerUUID);
            } else {
                handleSell(player, material, price, chest, ownerUUID);
            }
        } catch (Exception e) {
            player.sendMessage("§c✦ This shop is not configured correctly!");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof Chest)) return;

        Chest chest = (Chest) event.getInventory().getHolder();
        Block signBlock = getAttachedSign(chest.getBlock());

        if (signBlock != null && signBlock.getState() instanceof Sign) {
            Sign sign = (Sign) signBlock.getState();
            if (sign.getLine(0).contains("[BUY]") || sign.getLine(0).contains("[SELL]")) {
                try {
                    Material shopItem = Material.valueOf(sign.getLine(1).substring(2));
                    ItemStack draggedItem = event.getOldCursor();

                    if (draggedItem.getType() != shopItem) {
                        event.setCancelled(true);
                        Player player = (Player) event.getWhoClicked();
                        player.sendMessage("§c✦ You can only put " + shopItem.toString() + " in this shop!");
                    }
                } catch (Exception e) {
                    event.setCancelled(true);
                }
            }
        }
    }

    private void handleBuy(Player player, Material material, int price, Chest chest, UUID ownerUUID) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        boolean isAdminShop = SERVER_UUID.equals(ownerUUID);

        if (!playerData.hasEnoughCoins(price)) {
            player.sendMessage("§c✦ You don't have enough coins! Required: " + price);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // For admin shops, skip inventory check
        if (!isAdminShop && !chest.getInventory().containsAtLeast(new ItemStack(material), 1)) {
            player.sendMessage("§c✦ This shop is out of stock!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c✦ Your inventory is full!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // For admin shops, don't actually remove from chest
        if (!isAdminShop) {
            chest.getInventory().removeItem(new ItemStack(material));
        }

        player.getInventory().addItem(new ItemStack(material));
        playerData.removeCoins(price);

        // Only notify non-admin shop owners
        if (!isAdminShop && ownerUUID != null) {
            Player owner = plugin.getServer().getPlayer(ownerUUID);
            if (owner != null) {
                PlayerData ownerData = plugin.getPlayerManager().getPlayerData(owner);
                ownerData.addCoins(price);
                owner.sendMessage("§a✦ " + player.getName() + " bought " + material.toString() + " for " + price + " coins");
                owner.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            }
        }

        player.sendMessage("§a✦ Bought 1 " + material.toString() + " for " + price + " coins");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    private void handleSell(Player player, Material material, int price, Chest chest, UUID ownerUUID) {
        boolean isAdminShop = SERVER_UUID.equals(ownerUUID);

        if (!isAdminShop && player.getUniqueId().equals(ownerUUID)) {
            player.sendMessage("§c✦ You cannot sell to your own shop!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ItemStack itemToSell = new ItemStack(material);
        if (!player.getInventory().containsAtLeast(itemToSell, 1)) {
            player.sendMessage("§c✦ You don't have any " + material.toString() + " to sell!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // For admin shops, skip chest space check
        if (!isAdminShop && chest.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c✦ This shop is full!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // For non-admin shops, check if owner can afford it (if online)
        if (!isAdminShop && ownerUUID != null) {
            Player owner = plugin.getServer().getPlayer(ownerUUID);
            if (owner != null) {
                PlayerData ownerData = plugin.getPlayerManager().getPlayerData(owner);
                if (!ownerData.hasEnoughCoins(price)) {
                    player.sendMessage("§c✦ Shop owner cannot afford to buy your items!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                ownerData.removeCoins(price);
            }
        }

        player.getInventory().removeItem(itemToSell);

        // For admin shops, don't actually add to chest
        if (!isAdminShop) {
            chest.getInventory().addItem(itemToSell);
        }

        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        playerData.addCoins(price);

        // Notify owner if online and not admin shop
        if (!isAdminShop && ownerUUID != null) {
            Player owner = plugin.getServer().getPlayer(ownerUUID);
            if (owner != null) {
                owner.sendMessage("§a✦ " + player.getName() + " sold " + material.toString() + " for " + price + " coins");
                owner.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            }
        }

        player.sendMessage("§a✦ Sold 1 " + material.toString() + " for " + price + " coins");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }


    private Block getAttachedChest(Block signBlock) {
        // Check below
        Block below = signBlock.getLocation().subtract(0, 1, 0).getBlock();
        if (below.getState() instanceof Chest) {
            return below;
        }

        // Check adjacent blocks
        for (BlockFace face : BLOCK_FACES) {
            Block relative = signBlock.getRelative(face);
            if (relative.getState() instanceof Chest) {
                return relative;
            }
        }

        return null;
    }

    private Block getAttachedSign(Block chestBlock) {
        // Check above
        Block above = chestBlock.getLocation().add(0, 1, 0).getBlock();
        if (above.getState() instanceof Sign) {
            return above;
        }

        // Check adjacent blocks
        for (BlockFace face : BLOCK_FACES) {
            Block relative = chestBlock.getRelative(face);
            if (relative.getState() instanceof Sign) {
                return relative;
            }
        }

        return null;
    }

    private String locationToString(org.bukkit.Location location) {
        return location.getWorld().getName() + "," +
                location.getBlockX() + "," +
                location.getBlockY() + "," +
                location.getBlockZ();
    }
}
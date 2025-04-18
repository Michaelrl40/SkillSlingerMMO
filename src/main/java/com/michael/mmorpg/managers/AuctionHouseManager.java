package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AuctionHouseManager {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, Integer> viewingPage = new HashMap<>();
    private final Map<UUID, AuctionListingType> viewingType = new HashMap<>();

    // Constants
    private static final int ITEMS_PER_PAGE = 45;
    private static final int LISTING_FEE_PERCENTAGE = 5; // 5% fee to list an item
    private static final int LISTING_DURATION_DAYS = 7; // Listings expire after 7 days

    public AuctionHouseManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        initDatabase();
        startCleanupTask();
    }

    private void initDatabase() {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            // Create auction_listings table
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS auction_listings (" +
                            "listing_id TEXT PRIMARY KEY, " +
                            "seller_id TEXT NOT NULL, " +
                            "price INTEGER NOT NULL, " +
                            "item_data TEXT NOT NULL, " +  // Serialized item data
                            "creation_time INTEGER NOT NULL, " +
                            "expiration_time INTEGER NOT NULL, " +
                            "sold BOOLEAN NOT NULL DEFAULT 0" +
                            ")"
            )) {
                stmt.executeUpdate();
            }

            // Create auction_completed table for sales history
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS auction_completed (" +
                            "listing_id TEXT PRIMARY KEY, " +
                            "seller_id TEXT NOT NULL, " +
                            "buyer_id TEXT, " +  // Null if expired unsold
                            "price INTEGER NOT NULL, " +
                            "item_data TEXT NOT NULL, " +
                            "sale_time INTEGER NOT NULL, " +
                            "expired BOOLEAN NOT NULL DEFAULT 0" +
                            ")"
            )) {
                stmt.executeUpdate();
            }

            plugin.getLogger().info("Auction house database tables initialized!");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize auction house database", e);
        }
    }

    private void startCleanupTask() {
        // Run auction cleanup task every hour
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanupExpiredListings,
                1200L, // 1 minute delay
                72000L); // 1 hour (20 ticks/sec * 60 sec * 60 min)
    }

    private void cleanupExpiredListings() {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            // Find expired listings
            List<AuctionListing> expiredListings = new ArrayList<>();
            long now = Instant.now().getEpochSecond();

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM auction_listings WHERE expiration_time < ? AND sold = 0"
            )) {
                stmt.setLong(1, now);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    String listingId = rs.getString("listing_id");
                    UUID sellerId = UUID.fromString(rs.getString("seller_id"));
                    int price = rs.getInt("price");
                    String itemData = rs.getString("item_data");
                    long creationTime = rs.getLong("creation_time");
                    long expirationTime = rs.getLong("expiration_time");

                    ItemStack item = deserializeItemStack(itemData);
                    if (item != null) {
                        AuctionListing listing = new AuctionListing(
                                listingId, sellerId, price, item, creationTime, expirationTime
                        );
                        expiredListings.add(listing);
                    }
                }
            }

            if (!expiredListings.isEmpty()) {
                boolean autoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);

                try {
                    // Move to completed with expired flag
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO auction_completed (listing_id, seller_id, buyer_id, price, item_data, sale_time, expired) " +
                                    "VALUES (?, ?, NULL, ?, ?, ?, 1)"
                    )) {
                        for (AuctionListing listing : expiredListings) {
                            stmt.setString(1, listing.getListingId());
                            stmt.setString(2, listing.getSellerId().toString());
                            stmt.setInt(3, listing.getPrice());
                            stmt.setString(4, serializeItemStack(listing.getItem()));
                            stmt.setLong(5, now);
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }

                    // Delete from active listings
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "DELETE FROM auction_listings WHERE listing_id = ?"
                    )) {
                        for (AuctionListing listing : expiredListings) {
                            stmt.setString(1, listing.getListingId());
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }

                    // Return items to sellers
                    for (AuctionListing listing : expiredListings) {
                        returnExpiredItemToSeller(listing);
                    }

                    conn.commit();
                    plugin.getLogger().info("Cleaned up " + expiredListings.size() + " expired auction listings");

                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(autoCommit);
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error cleaning up expired auction listings", e);
        }
    }

    private void returnExpiredItemToSeller(AuctionListing listing) {
        // Store the item in a table of unclaimed items
        // This example just puts a message for the player when they log in
        Player seller = Bukkit.getPlayer(listing.getSellerId());
        if (seller != null && seller.isOnline()) {
            // If player is online, give item directly
            ItemStack returnedItem = listing.getItem().clone();

            // Add to inventory or drop if full
            if (seller.getInventory().firstEmpty() != -1) {
                seller.getInventory().addItem(returnedItem);
                seller.sendMessage("§e✦ Your auction listing has expired and the item has been returned to your inventory.");
            } else {
                seller.getWorld().dropItem(seller.getLocation(), returnedItem);
                seller.sendMessage("§e✦ Your auction listing has expired and the item has been dropped at your feet.");
            }
        } else {
            // TODO: Implement an unclaimed items system for offline players
            // For now, we'll just save them somewhere for admin to handle
            plugin.getLogger().info("Item returned from expired listing for offline player: " + listing.getSellerId());
        }
    }

    public void openAuctionHouse(Player player) {
        // Default to first page of buy interface
        viewingPage.put(player.getUniqueId(), 0);
        viewingType.put(player.getUniqueId(), AuctionListingType.BUY);
        openAuctionHousePage(player);
    }

    public void openAuctionHousePage(Player player) {
        UUID playerId = player.getUniqueId();
        int page = viewingPage.getOrDefault(playerId, 0);
        AuctionListingType type = viewingType.getOrDefault(playerId, AuctionListingType.BUY);

        if (type == AuctionListingType.BUY) {
            openBuyInterface(player, page);
        } else if (type == AuctionListingType.MY_LISTINGS) {
            openMyListingsInterface(player, page);
        }
    }

    private void openBuyInterface(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(player, 54, "§6✦ Auction House: Browse");

        // Fetch listings for this page
        List<AuctionListing> listings = getActiveListings(page, ITEMS_PER_PAGE);

        // Add listings to the inventory
        for (int i = 0; i < Math.min(listings.size(), ITEMS_PER_PAGE); i++) {
            AuctionListing listing = listings.get(i);
            ItemStack displayItem = listing.getItem().clone();

            // Add price information to lore
            ItemMeta meta = displayItem.getItemMeta();
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add("§7Price: §6" + listing.getPrice() + " coins");
            lore.add("§7Seller: §f" + getSellerName(listing.getSellerId()));
            lore.add("");
            lore.add("§eClick to purchase!");
            meta.setLore(lore);
            displayItem.setItemMeta(meta);

            inventory.setItem(i, displayItem);
        }

        // Navigation buttons
        addNavigationButtons(inventory, page, getActiveListingsCount(), ITEMS_PER_PAGE);

        // Mode buttons
        addModeButtons(inventory, AuctionListingType.BUY);

        player.openInventory(inventory);
    }

    public void openMyListings(Player player) {
        UUID playerId = player.getUniqueId();
        viewingType.put(playerId, AuctionListingType.MY_LISTINGS);
        viewingPage.put(playerId, 0);
        openMyListingsInterface(player, 0);
    }

    private void openMyListingsInterface(Player player, int page) {
        UUID playerId = player.getUniqueId();
        Inventory inventory = Bukkit.createInventory(player, 54, "§6✦ Auction House: My Listings");

        // Fetch listings for this page
        List<AuctionListing> listings = getMyListings(playerId, page, ITEMS_PER_PAGE);

        // Add listings to the inventory
        for (int i = 0; i < Math.min(listings.size(), ITEMS_PER_PAGE); i++) {
            AuctionListing listing = listings.get(i);
            ItemStack displayItem = listing.getItem().clone();

            // Add price information to lore
            ItemMeta meta = displayItem.getItemMeta();
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add("§7Price: §6" + listing.getPrice() + " coins");

            // Calculate expiration
            long timeLeft = listing.getExpirationTime() - Instant.now().getEpochSecond();
            String timeLeftStr;
            if (timeLeft > 86400) {
                timeLeftStr = (timeLeft / 86400) + " days";
            } else if (timeLeft > 3600) {
                timeLeftStr = (timeLeft / 3600) + " hours";
            } else {
                timeLeftStr = (timeLeft / 60) + " minutes";
            }

            lore.add("§7Expires in: §f" + timeLeftStr);
            lore.add("");
            lore.add("§eClick to cancel listing!");
            meta.setLore(lore);
            displayItem.setItemMeta(meta);

            inventory.setItem(i, displayItem);
        }

        // Navigation buttons
        addNavigationButtons(inventory, page, getMyListingsCount(playerId), ITEMS_PER_PAGE);

        // Mode buttons
        addModeButtons(inventory, AuctionListingType.MY_LISTINGS);

        player.openInventory(inventory);
    }

    private void addNavigationButtons(Inventory inventory, int currentPage, int totalItems, int itemsPerPage) {
        int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);

        // Previous page button
        if (currentPage > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta meta = prevButton.getItemMeta();
            meta.setDisplayName("§a← Previous Page");
            prevButton.setItemMeta(meta);
            inventory.setItem(45, prevButton);
        }

        // Current page indicator
        ItemStack pageIndicator = new ItemStack(Material.PAPER);
        ItemMeta meta = pageIndicator.getItemMeta();
        meta.setDisplayName("§ePage " + (currentPage + 1) + " of " + Math.max(1, totalPages));
        pageIndicator.setItemMeta(meta);
        inventory.setItem(49, pageIndicator);

        // Next page button
        if (currentPage < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            meta.setDisplayName("§aNext Page →");
            nextButton.setItemMeta(meta);
            inventory.setItem(53, nextButton);
        }
    }

    private void addModeButtons(Inventory inventory, AuctionListingType currentType) {
        // Buy button
        ItemStack buyButton = new ItemStack(Material.CHEST);
        ItemMeta meta = buyButton.getItemMeta();
        meta.setDisplayName("§6Browse Items");
        List<String> lore = new ArrayList<>();
        lore.add("§7Browse items for sale");
        meta.setLore(lore);
        buyButton.setItemMeta(meta);

        // My listings button
        ItemStack myListingsButton = new ItemStack(Material.WRITABLE_BOOK);
        meta = myListingsButton.getItemMeta();
        meta.setDisplayName("§6My Listings");
        lore = new ArrayList<>();
        lore.add("§7View your active listings");
        meta.setLore(lore);
        myListingsButton.setItemMeta(meta);

        inventory.setItem(46, buyButton);
        inventory.setItem(48, myListingsButton);
    }

    public void handleInventoryClick(Player player, int slot, Inventory inventory) {
        UUID playerId = player.getUniqueId();
        int page = viewingPage.getOrDefault(playerId, 0);
        AuctionListingType type = viewingType.getOrDefault(playerId, AuctionListingType.BUY);

        // Check if navigation button was clicked
        if (slot == 45 && page > 0) {  // Previous page
            viewingPage.put(playerId, page - 1);
            openAuctionHousePage(player);
            return;
        } else if (slot == 53) {  // Next page
            int totalItems = type == AuctionListingType.BUY ?
                    getActiveListingsCount() :
                    getMyListingsCount(playerId);
            int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);

            if (page < totalPages - 1) {
                viewingPage.put(playerId, page + 1);
                openAuctionHousePage(player);
            }
            return;
        }

        // Check if mode button was clicked
        if (slot == 46) {  // Buy mode
            viewingType.put(playerId, AuctionListingType.BUY);
            viewingPage.put(playerId, 0);
            openAuctionHousePage(player);
            return;
        } else if (slot == 48) {  // My listings mode
            viewingType.put(playerId, AuctionListingType.MY_LISTINGS);
            viewingPage.put(playerId, 0);
            openAuctionHousePage(player);
            return;
        }

        // Handle other clicks based on current mode
        if (type == AuctionListingType.BUY && slot < ITEMS_PER_PAGE) {
            handleBuyClick(player, slot, page);
        } else if (type == AuctionListingType.MY_LISTINGS && slot < ITEMS_PER_PAGE) {
            handleMyListingClick(player, slot, page);
        }
    }

    private void handleBuyClick(Player player, int slot, int page) {
        List<AuctionListing> listings = getActiveListings(page, ITEMS_PER_PAGE);

        if (slot < listings.size()) {
            AuctionListing listing = listings.get(slot);

            // Confirm purchase (or open a confirmation GUI)
            confirmPurchase(player, listing);
        }
    }

    private void confirmPurchase(Player player, AuctionListing listing) {
        // Create confirmation inventory
        Inventory inventory = Bukkit.createInventory(player, 27, "§6✦ Confirm Purchase");

        // Add listing item
        inventory.setItem(13, listing.getItem());

        // Add price info
        ItemStack priceInfo = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = priceInfo.getItemMeta();
        meta.setDisplayName("§6Price: " + listing.getPrice() + " coins");
        priceInfo.setItemMeta(meta);
        inventory.setItem(4, priceInfo);

        // Add confirm button
        ItemStack confirmButton = new ItemStack(Material.LIME_WOOL);
        meta = confirmButton.getItemMeta();
        meta.setDisplayName("§a✦ Confirm Purchase");
        List<String> lore = new ArrayList<>();
        lore.add("§7Click to purchase this item for");
        lore.add("§6" + listing.getPrice() + " coins");
        meta.setLore(lore);
        confirmButton.setItemMeta(meta);
        inventory.setItem(11, confirmButton);

        // Add cancel button
        ItemStack cancelButton = new ItemStack(Material.RED_WOOL);
        meta = cancelButton.getItemMeta();
        meta.setDisplayName("§c✦ Cancel");
        cancelButton.setItemMeta(meta);
        inventory.setItem(15, cancelButton);

        // Store listing ID somewhere (e.g. in a map)
        // For this example, we'll use a persistent data container
        // In a real implementation, you'd want a more robust solution
        player.setMetadata("auction_listing_id", new org.bukkit.metadata.FixedMetadataValue(plugin, listing.getListingId()));

        player.openInventory(inventory);
    }

    public void handleConfirmPurchase(Player player, int slot) {
        if (slot == 11) { // Confirm button
            // Get listing ID from player metadata
            if (!player.hasMetadata("auction_listing_id")) {
                player.sendMessage("§c✦ Error: Could not find listing information!");
                player.closeInventory();
                return;
            }

            String listingId = player.getMetadata("auction_listing_id").get(0).asString();
            player.removeMetadata("auction_listing_id", plugin);

            // Process purchase
            if (completePurchase(player, listingId)) {
                player.sendMessage("§a✦ Purchase successful!");
                player.closeInventory();

                // Refresh auction house view
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    openAuctionHousePage(player);
                }, 5L);
            } else {
                player.sendMessage("§c✦ Purchase failed. The item may have been sold or you don't have enough coins.");
                player.closeInventory();
            }
        } else if (slot == 15) { // Cancel button
            player.removeMetadata("auction_listing_id", plugin);

            // Return to auction house
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                openAuctionHousePage(player);
            }, 1L);
        }
    }

    private boolean completePurchase(Player buyer, String listingId) {
        AuctionListing listing = getListingById(listingId);
        if (listing == null) {
            return false;
        }

        EconomyManager economy = plugin.getEconomyManager();

        // Check if buyer has enough coins
        if (!economy.hasEnough(buyer, listing.getPrice())) {
            buyer.sendMessage("§c✦ You don't have enough coins!");
            return false;
        }

        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // Check if item is still available
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT sold FROM auction_listings WHERE listing_id = ?"
                )) {
                    stmt.setString(1, listingId);
                    ResultSet rs = stmt.executeQuery();

                    if (!rs.next() || rs.getBoolean("sold")) {
                        return false; // Listing not found or already sold
                    }
                }

                // Remove coins from buyer
                if (!economy.removeCoins(buyer, listing.getPrice())) {
                    return false;
                }

                // Mark item as sold
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE auction_listings SET sold = 1 WHERE listing_id = ?"
                )) {
                    stmt.setString(1, listingId);
                    stmt.executeUpdate();
                }

                // Record completed sale
                long saleTime = Instant.now().getEpochSecond();
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO auction_completed (listing_id, seller_id, buyer_id, price, item_data, sale_time, expired) " +
                                "VALUES (?, ?, ?, ?, ?, ?, 0)"
                )) {
                    stmt.setString(1, listingId);
                    stmt.setString(2, listing.getSellerId().toString());
                    stmt.setString(3, buyer.getUniqueId().toString());
                    stmt.setInt(4, listing.getPrice());
                    stmt.setString(5, serializeItemStack(listing.getItem()));
                    stmt.setLong(6, saleTime);
                    stmt.executeUpdate();
                }

                // Delete from active listings
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM auction_listings WHERE listing_id = ?"
                )) {
                    stmt.setString(1, listingId);
                    stmt.executeUpdate();
                }

                conn.commit();

                // Add item to buyer's inventory
                ItemStack boughtItem = listing.getItem().clone();
                if (buyer.getInventory().firstEmpty() != -1) {
                    buyer.getInventory().addItem(boughtItem);
                } else {
                    buyer.getWorld().dropItem(buyer.getLocation(), boughtItem);
                    buyer.sendMessage("§e✦ Your inventory was full so the item was dropped at your feet.");
                }

                // Send coins to seller (minus fee)
                int fee = 0; // No fee on purchase
                int sellerAmount = listing.getPrice() - fee;
                sendCoinsToSeller(listing.getSellerId(), sellerAmount, buyer.getName());

                return true;

            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().log(Level.SEVERE, "Error completing auction purchase", e);
                return false;
            } finally {
                conn.setAutoCommit(autoCommit);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Database error during auction purchase", e);
            return false;
        }
    }

    private void sendCoinsToSeller(UUID sellerId, int amount, String buyerName) {
        Player seller = Bukkit.getPlayer(sellerId);

        if (seller != null && seller.isOnline()) {
            // If seller is online, give coins directly
            plugin.getEconomyManager().addCoins(seller, amount);
            seller.sendMessage("§a✦ Your item sold for " + amount + " coins to " + buyerName + "!");
        } else {
            // Store coins to give next time they login
            // In a real plugin, you'd want a more robust offline coin storage system
            try {
                // This is just a simple example - you'd want a proper table for this
                Connection conn = plugin.getDatabaseManager().getConnection();
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE players SET coins = coins + ? WHERE uuid = ?"
                )) {
                    stmt.setInt(1, amount);
                    stmt.setString(2, sellerId.toString());
                    stmt.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Error storing seller payment", e);
            }
        }
    }

    private void handleMyListingClick(Player player, int slot, int page) {
        List<AuctionListing> listings = getMyListings(player.getUniqueId(), page, ITEMS_PER_PAGE);

        if (slot < listings.size()) {
            AuctionListing listing = listings.get(slot);

            // Cancel listing
            cancelListing(player, listing);
        }
    }

    private void cancelListing(Player player, AuctionListing listing) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            // Check if listing exists and belongs to player
            boolean canCancel = false;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT listing_id FROM auction_listings WHERE listing_id = ? AND seller_id = ? AND sold = 0"
            )) {
                stmt.setString(1, listing.getListingId());
                stmt.setString(2, player.getUniqueId().toString());
                ResultSet rs = stmt.executeQuery();
                canCancel = rs.next();
            }

            if (canCancel) {
                // Delete listing
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM auction_listings WHERE listing_id = ?"
                )) {
                    stmt.setString(1, listing.getListingId());
                    stmt.executeUpdate();
                }

                // Return item to player
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(listing.getItem());
                    player.sendMessage("§a✦ Your listing has been cancelled and the item returned to your inventory.");
                } else {
                    player.getWorld().dropItem(player.getLocation(), listing.getItem());
                    player.sendMessage("§a✦ Your listing has been cancelled, but your inventory was full. The item was dropped at your feet.");
                }

                // Refresh inventory
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    openAuctionHousePage(player);
                }, 1L);

            } else {
                player.sendMessage("§c✦ This listing cannot be cancelled. It may have already been sold or expired.");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error cancelling auction listing", e);
            player.sendMessage("§c✦ An error occurred while cancelling your listing. Please try again later.");
        }
    }

    /**
     * Sell an item directly from the player's hand
     * @param player The player selling the item
     * @param itemInHand The item to sell
     * @param price The price in coins
     * @return true if successful
     */
    public boolean sellItemFromHand(Player player, ItemStack itemInHand, int price) {
        // Calculate listing fee
        int listingFee = (int) Math.ceil((price * LISTING_FEE_PERCENTAGE) / 100.0);

        // Check if player has enough coins for the fee
        if (!plugin.getEconomyManager().hasEnough(player, listingFee)) {
            player.sendMessage("§c✦ You need " + listingFee + " coins to list this item (listing fee).");
            return false;
        }

        // Deduct listing fee
        if (!plugin.getEconomyManager().removeCoins(player, listingFee)) {
            player.sendMessage("§c✦ Failed to deduct listing fee.");
            return false;
        }

        // Create a copy of the item to save (don't modify the original yet)
        ItemStack itemCopy = itemInHand.clone();

        // Create the listing
        return createListing(player, itemCopy, price);
    }

    private boolean createListing(Player player, ItemStack item, int price) {
        String listingId = UUID.randomUUID().toString();
        long creationTime = Instant.now().getEpochSecond();
        long expirationTime = creationTime + (LISTING_DURATION_DAYS * 86400); // Days to seconds

        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO auction_listings (listing_id, seller_id, price, item_data, creation_time, expiration_time) " +
                            "VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                stmt.setString(1, listingId);
                stmt.setString(2, player.getUniqueId().toString());
                stmt.setInt(3, price);
                stmt.setString(4, serializeItemStack(item));
                stmt.setLong(5, creationTime);
                stmt.setLong(6, expirationTime);
                stmt.executeUpdate();
            }

            player.sendMessage("§a✦ Your " + getItemName(item) + " has been listed for " + price + " coins.");
            return true;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating auction listing", e);
            player.sendMessage("§c✦ An error occurred while creating your listing. Please try again later.");

            // Refund listing fee
            int listingFee = (int) Math.ceil((price * LISTING_FEE_PERCENTAGE) / 100.0);
            plugin.getEconomyManager().addCoins(player, listingFee);

            // Return item
            player.getInventory().addItem(item);
            return false;
        }
    }

    private List<AuctionListing> getActiveListings(int page, int limit) {
        List<AuctionListing> listings = new ArrayList<>();

        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM auction_listings WHERE sold = 0 ORDER BY creation_time DESC LIMIT ? OFFSET ?"
            )) {
                stmt.setInt(1, limit);
                stmt.setInt(2, page * limit);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    String listingId = rs.getString("listing_id");
                    UUID sellerId = UUID.fromString(rs.getString("seller_id"));
                    int price = rs.getInt("price");
                    String itemData = rs.getString("item_data");
                    long creationTime = rs.getLong("creation_time");
                    long expirationTime = rs.getLong("expiration_time");

                    ItemStack item = deserializeItemStack(itemData);
                    if (item != null) {
                        listings.add(new AuctionListing(
                                listingId, sellerId, price, item, creationTime, expirationTime
                        ));
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error fetching auction listings", e);
        }

        return listings;
    }

    private List<AuctionListing> getMyListings(UUID playerId, int page, int limit) {
        List<AuctionListing> listings = new ArrayList<>();

        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM auction_listings WHERE seller_id = ? AND sold = 0 ORDER BY creation_time DESC LIMIT ? OFFSET ?"
            )) {
                stmt.setString(1, playerId.toString());
                stmt.setInt(2, limit);
                stmt.setInt(3, page * limit);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    String listingId = rs.getString("listing_id");
                    int price = rs.getInt("price");
                    String itemData = rs.getString("item_data");
                    long creationTime = rs.getLong("creation_time");
                    long expirationTime = rs.getLong("expiration_time");

                    ItemStack item = deserializeItemStack(itemData);
                    if (item != null) {
                        listings.add(new AuctionListing(
                                listingId, playerId, price, item, creationTime, expirationTime
                        ));
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error fetching player's auction listings", e);
        }

        return listings;
    }

    private int getActiveListingsCount() {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) AS count FROM auction_listings WHERE sold = 0"
            )) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error counting auction listings", e);
        }

        return 0;
    }

    private int getMyListingsCount(UUID playerId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) AS count FROM auction_listings WHERE seller_id = ? AND sold = 0"
            )) {
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error counting player's auction listings", e);
        }

        return 0;
    }

    private AuctionListing getListingById(String listingId) {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT * FROM auction_listings WHERE listing_id = ? AND sold = 0"
            )) {
                stmt.setString(1, listingId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    UUID sellerId = UUID.fromString(rs.getString("seller_id"));
                    int price = rs.getInt("price");
                    String itemData = rs.getString("item_data");
                    long creationTime = rs.getLong("creation_time");
                    long expirationTime = rs.getLong("expiration_time");

                    ItemStack item = deserializeItemStack(itemData);
                    if (item != null) {
                        return new AuctionListing(
                                listingId, sellerId, price, item, creationTime, expirationTime
                        );
                    }
                }
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error fetching auction listing by ID", e);
        }

        return null;
    }

    // Helper methods
    private String getSellerName(UUID sellerId) {
        // This would be better with a proper player name cache
        Player seller = Bukkit.getPlayer(sellerId);
        return seller != null ? seller.getName() : "Unknown";
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        return item.getType().toString().toLowerCase().replace('_', ' ');
    }

    /**
     * Serialize an ItemStack to Base64 string for storage
     * This method preserves ALL item data including custom model data, NBT tags, etc.
     *
     * @param item The ItemStack to serialize
     * @return Base64 encoded string representation of the item
     */
    private String serializeItemStack(ItemStack item) {
        try {
            // Convert ItemStack to a byte stream
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write the full ItemStack
            dataOutput.writeObject(item);
            dataOutput.close();

            // Encode the byte stream to Base64 and return
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error serializing ItemStack", e);
            return null;
        }
    }

    /**
     * Deserialize an ItemStack from Base64 string
     * This method restores ALL item data including custom model data, NBT tags, etc.
     *
     * @param data Base64 encoded string representation of the item
     * @return The restored ItemStack with all properties
     */
    private ItemStack deserializeItemStack(String data) {
        try {
            // Decode Base64 string to byte array
            byte[] bytes = Base64.getDecoder().decode(data);

            // Convert byte array to ItemStack
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            // Read the serialized ItemStack
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();

            return item;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deserialize ItemStack", e);
            return null;
        }
    }

    // Inner classes
    public enum AuctionListingType {
        BUY,
        MY_LISTINGS
    }

    public static class AuctionListing {
        private final String listingId;
        private final UUID sellerId;
        private final int price;
        private final ItemStack item;
        private final long creationTime;
        private final long expirationTime;

        public AuctionListing(String listingId, UUID sellerId, int price, ItemStack item, long creationTime, long expirationTime) {
            this.listingId = listingId;
            this.sellerId = sellerId;
            this.price = price;
            this.item = item;
            this.creationTime = creationTime;
            this.expirationTime = expirationTime;
        }

        public String getListingId() {
            return listingId;
        }

        public UUID getSellerId() {
            return sellerId;
        }

        public int getPrice() {
            return price;
        }

        public ItemStack getItem() {
            return item;
        }

        public long getCreationTime() {
            return creationTime;
        }

        public long getExpirationTime() {
            return expirationTime;
        }
    }
}
package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public class BankManager {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, BankAccount> playerBanks = new HashMap<>();

    // Bank upgrade tiers with their costs and slot sizes
    private final Map<Integer, BankTier> bankTiers = new HashMap<>();

    public BankManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        initBankTiers();
        initDatabase();
    }

    private void initBankTiers() {
        // Tier 0 is free starter bank (9 slots - small chest)
        bankTiers.put(0, new BankTier(0, 0, 9, "Small Chest"));

        // Subsequent tiers with increasing cost and size
        bankTiers.put(1, new BankTier(1, 1000, 18, "Medium Chest"));
        bankTiers.put(2, new BankTier(2, 5000, 27, "Large Chest"));
        bankTiers.put(3, new BankTier(3, 15000, 36, "Double Chest"));
        bankTiers.put(4, new BankTier(4, 50000, 45, "Ender Chest"));
        bankTiers.put(5, new BankTier(5, 100000, 54, "Vault"));
    }

    private void initDatabase() {
        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            // Create bank_accounts table
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS bank_accounts (" +
                            "player_id TEXT PRIMARY KEY, " +
                            "tier INTEGER NOT NULL DEFAULT 0" +
                            ")"
            )) {
                stmt.executeUpdate();
            }

            // Create bank_items table
            try (PreparedStatement stmt = conn.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS bank_items (" +
                            "player_id TEXT, " +
                            "slot INTEGER, " +
                            "item_data TEXT, " +  // Serialized item data
                            "PRIMARY KEY (player_id, slot)" +
                            ")"
            )) {
                stmt.executeUpdate();
            }

            plugin.getLogger().info("Bank database tables initialized!");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize bank database", e);
        }
    }

    public void loadPlayerBank(Player player) {
        UUID playerId = player.getUniqueId();
        if (playerBanks.containsKey(playerId)) {
            return; // Already loaded
        }

        try {
            Connection conn = plugin.getDatabaseManager().getConnection();

            // Load bank tier
            int tier = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT tier FROM bank_accounts WHERE player_id = ?"
            )) {
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    tier = rs.getInt("tier");
                } else {
                    // Create new account for player
                    try (PreparedStatement insertStmt = conn.prepareStatement(
                            "INSERT INTO bank_accounts (player_id, tier) VALUES (?, 0)"
                    )) {
                        insertStmt.setString(1, playerId.toString());
                        insertStmt.executeUpdate();
                    }
                }
            }

            // Create bank account with appropriate tier
            BankTier bankTier = bankTiers.get(tier);
            BankAccount account = new BankAccount(playerId, tier, bankTier.getSlots());

            // Load items
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT slot, item_data FROM bank_items WHERE player_id = ?"
            )) {
                stmt.setString(1, playerId.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    int slot = rs.getInt("slot");
                    String itemData = rs.getString("item_data");

                    if (itemData != null && !itemData.isEmpty()) {
                        // Deserialize item from string (using a helper method)
                        ItemStack item = deserializeItemStack(itemData);
                        if (item != null) {
                            account.setItem(slot, item);
                        }
                    }
                }
            }

            playerBanks.put(playerId, account);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load player bank for " + player.getName(), e);
        }
    }

    public void savePlayerBank(UUID playerId) {
        BankAccount account = playerBanks.get(playerId);
        if (account == null) {
            return; // Nothing to save
        }

        try {
            Connection conn = plugin.getDatabaseManager().getConnection();
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                // Update tier
                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE bank_accounts SET tier = ? WHERE player_id = ?"
                )) {
                    stmt.setInt(1, account.getTier());
                    stmt.setString(2, playerId.toString());
                    stmt.executeUpdate();
                }

                // Delete existing items
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM bank_items WHERE player_id = ?"
                )) {
                    stmt.setString(1, playerId.toString());
                    stmt.executeUpdate();
                }

                // Insert current items
                try (PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO bank_items (player_id, slot, item_data) VALUES (?, ?, ?)"
                )) {
                    for (Map.Entry<Integer, ItemStack> entry : account.getItems().entrySet()) {
                        int slot = entry.getKey();
                        ItemStack item = entry.getValue();

                        if (item != null) {
                            stmt.setString(1, playerId.toString());
                            stmt.setInt(2, slot);
                            stmt.setString(3, serializeItemStack(item));
                            stmt.addBatch();
                        }
                    }
                    stmt.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommit);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player bank for " + playerId, e);
        }
    }

    public void unloadPlayerBank(Player player) {
        UUID playerId = player.getUniqueId();
        if (playerBanks.containsKey(playerId)) {
            savePlayerBank(playerId);
            playerBanks.remove(playerId);
        }
    }

    public void openBankInterface(Player player) {
        loadPlayerBank(player);
        BankAccount account = playerBanks.get(player.getUniqueId());

        if (account == null) {
            player.sendMessage("§c✦ Could not load your bank account!");
            return;
        }

        // Create inventory based on bank tier size
        int slots = bankTiers.get(account.getTier()).getSlots();
        String title = "§6✦ Bank Vault: " + bankTiers.get(account.getTier()).getName();
        Inventory inventory = Bukkit.createInventory(player, slots, title);

        // Fill inventory with stored items
        for (Map.Entry<Integer, ItemStack> entry : account.getItems().entrySet()) {
            int slot = entry.getKey();
            ItemStack item = entry.getValue();

            if (slot < slots && item != null) {
                inventory.setItem(slot, item);
            }
        }

        // Check if player can upgrade
        if (account.getTier() < 5) { // 5 is max tier
            BankTier nextTier = bankTiers.get(account.getTier() + 1);

            // Add upgrade button
            ItemStack upgradeItem = new ItemStack(Material.EMERALD);
            ItemMeta meta = upgradeItem.getItemMeta();
            meta.setDisplayName("§a✦ Upgrade Bank");
            List<String> lore = new ArrayList<>();
            lore.add("§7Current tier: §6" + bankTiers.get(account.getTier()).getName());
            lore.add("§7Next tier: §6" + nextTier.getName());
            lore.add("§7Cost: §6" + nextTier.getCost() + " coins");
            lore.add("§7New slots: §6" + nextTier.getSlots());
            lore.add("");
            lore.add("§eClick to upgrade!");
            meta.setLore(lore);
            upgradeItem.setItemMeta(meta);

            // Place upgrade button in last slot
            inventory.setItem(slots - 1, upgradeItem);
        }

        player.openInventory(inventory);
    }

    public boolean upgradeBank(Player player) {
        UUID playerId = player.getUniqueId();
        BankAccount account = playerBanks.get(playerId);

        if (account == null) {
            player.sendMessage("§c✦ Could not load your bank account!");
            return false;
        }

        // Check if already at max tier
        if (account.getTier() >= 5) { // 5 is max tier
            player.sendMessage("§c✦ Your bank is already at the highest tier!");
            return false;
        }

        // Get next tier
        BankTier nextTier = bankTiers.get(account.getTier() + 1);
        int cost = nextTier.getCost();

        // Check if player has enough coins
        if (!plugin.getEconomyManager().hasEnough(player, cost)) {
            player.sendMessage("§c✦ You need " + cost + " coins to upgrade your bank!");
            return false;
        }

        // Deduct coins and upgrade
        if (plugin.getEconomyManager().removeCoins(player, cost)) {
            account.setTier(account.getTier() + 1);
            savePlayerBank(playerId);
            player.sendMessage("§a✦ Your bank has been upgraded to " + nextTier.getName() + "!");
            return true;
        } else {
            player.sendMessage("§c✦ Failed to upgrade your bank!");
            return false;
        }
    }

    // Helper method to handle inventory update when player closes bank
    public void handleInventoryClose(Player player, Inventory inventory) {
        UUID playerId = player.getUniqueId();
        BankAccount account = playerBanks.get(playerId);

        if (account == null) {
            return;
        }

        // Clear current items
        account.clearItems();

        // Store items from inventory
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);

            // Skip upgrade button
            if (item != null && item.getType() == Material.EMERALD) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.getDisplayName().contains("Upgrade Bank")) {
                    continue;
                }
            }

            if (item != null) {
                account.setItem(i, item);
            }
        }

        // Save changes
        savePlayerBank(playerId);
    }

    // Helper methods for serialization (these need implementation)
    private String serializeItemStack(ItemStack item) {
        // Placeholder - you would need to implement proper serialization
        // Could use Bukkit's ConfigurationSerializable methods or custom JSON serialization
        return item.getType().toString() + ":" + item.getAmount();
    }

    private ItemStack deserializeItemStack(String data) {
        // Placeholder - you would need to implement proper deserialization
        try {
            String[] parts = data.split(":");
            Material material = Material.valueOf(parts[0]);
            int amount = Integer.parseInt(parts[1]);
            return new ItemStack(material, amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deserialize item: " + data, e);
            return null;
        }
    }

    // Inner classes for bank data
    public static class BankAccount {
        private final UUID playerId;
        private int tier;
        private final Map<Integer, ItemStack> items = new HashMap<>();

        public BankAccount(UUID playerId, int tier, int slots) {
            this.playerId = playerId;
            this.tier = tier;
        }

        public int getTier() {
            return tier;
        }

        public void setTier(int tier) {
            this.tier = tier;
        }

        public void setItem(int slot, ItemStack item) {
            if (item == null) {
                items.remove(slot);
            } else {
                items.put(slot, item);
            }
        }

        public Map<Integer, ItemStack> getItems() {
            return items;
        }

        public void clearItems() {
            items.clear();
        }
    }

    public static class BankTier {
        private final int tier;
        private final int cost;
        private final int slots;
        private final String name;

        public BankTier(int tier, int cost, int slots, String name) {
            this.tier = tier;
            this.cost = cost;
            this.slots = slots;
            this.name = name;
        }

        public int getTier() {
            return tier;
        }

        public int getCost() {
            return cost;
        }

        public int getSlots() {
            return slots;
        }

        public String getName() {
            return name;
        }
    }
}
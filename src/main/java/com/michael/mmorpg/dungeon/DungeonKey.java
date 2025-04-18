package com.michael.mmorpg.dungeon;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DungeonKey {
    private final MinecraftMMORPG plugin;
    private static final String KEY_PREFIX = "dungeon_key_";

    // Cache of dungeon keys (dungeonName -> ItemStack)
    private final Map<String, ItemStack> dungeonKeyTemplates = new HashMap<>();
    private final File keysFile;

    public DungeonKey(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.keysFile = new File(plugin.getDataFolder(), "dungeon_keys.yml");
        loadKeys();
    }

    /**
     * Converts an existing item into a dungeon key and saves it as a template
     */
    public ItemStack convertToKey(ItemStack item, String dungeonName, String keyName, String lore) {
        if (item == null) return null;

        // Create a clone to avoid modifying the original
        ItemStack keyItem = item.clone();
        ItemMeta meta = keyItem.getItemMeta();

        if (meta == null) return keyItem;

        // Set the new display name
        meta.setDisplayName("§6" + keyName);

        // Store dungeon name in persistent data container
        NamespacedKey nameKey = new NamespacedKey(plugin, KEY_PREFIX + "name");
        meta.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, dungeonName);

        // Add key lore to existing lore (if any)
        List<String> existingLore = meta.getLore();
        List<String> newLore = new ArrayList<>();

        if (existingLore != null) {
            newLore.addAll(existingLore);
        }

        // Add key-specific lore
        newLore.add("§7Use at the dungeon entrance to enter");
        newLore.add("§7" + lore);
        newLore.add("§e§oDungeon: " + dungeonName);

        meta.setLore(newLore);

        // Apply changes back to the item
        keyItem.setItemMeta(meta);

        // Save this key as a template for this dungeon
        saveKeyTemplate(dungeonName, keyItem);

        return keyItem;
    }

    /**
     * Gets a copy of the key template for a dungeon
     */
    public ItemStack getKeyForDungeon(String dungeonName) {
        ItemStack template = dungeonKeyTemplates.get(dungeonName.toLowerCase());
        if (template == null) {
            plugin.getLogger().warning("No key template found for dungeon: " + dungeonName);
            return null;
        }

        return template.clone();
    }

    /**
     * Saves a key as the template for a dungeon
     */
    private void saveKeyTemplate(String dungeonName, ItemStack key) {
        // Store in memory cache
        dungeonKeyTemplates.put(dungeonName.toLowerCase(), key.clone());

        // Save to file
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(keysFile);
            config.set("keys." + dungeonName.toLowerCase(), key);
            config.save(keysFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save key template for dungeon: " + dungeonName);
            e.printStackTrace();
        }
    }

    /**
     * Loads all saved key templates
     */
    private void loadKeys() {
        dungeonKeyTemplates.clear();

        if (!keysFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(keysFile);
        if (!config.contains("keys")) {
            return;
        }

        for (String dungeonName : config.getConfigurationSection("keys").getKeys(false)) {
            ItemStack key = config.getItemStack("keys." + dungeonName);
            if (key != null) {
                dungeonKeyTemplates.put(dungeonName.toLowerCase(), key);
                plugin.getLogger().info("Loaded key template for dungeon: " + dungeonName);
            }
        }
    }

    /**
     * Checks if an item is a dungeon key and returns the dungeon name
     */
    public String getDungeonNameFromKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey nameKey = new NamespacedKey(plugin, KEY_PREFIX + "name");

        if (container.has(nameKey, PersistentDataType.STRING)) {
            return container.get(nameKey, PersistentDataType.STRING);
        }

        return null;
    }

    /**
     * Checks if a player has a key for the specified dungeon
     */
    public boolean hasKeyForDungeon(Player player, String dungeonName) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            String keyDungeon = getDungeonNameFromKey(item);
            if (dungeonName.equalsIgnoreCase(keyDungeon)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Consumes a key from the player's inventory
     */
    public boolean consumeKey(Player player, String dungeonName) {
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

            String keyDungeon = getDungeonNameFromKey(item);
            if (dungeonName.equalsIgnoreCase(keyDungeon)) {
                // Remove one from stack or remove stack if only one
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }

                player.updateInventory();
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a key template exists for a dungeon
     */
    public boolean hasKeyTemplate(String dungeonName) {
        return dungeonKeyTemplates.containsKey(dungeonName.toLowerCase());
    }
}
package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.bukkit.attribute.Attribute.ATTACK_SPEED;

public class WeaponManager {
    private final MinecraftMMORPG plugin;
    private final Map<String, WeaponData> weaponData;
    private final Map<String, NamespacedKey> recipeKeys;

    public WeaponManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.weaponData = new HashMap<>();
        this.recipeKeys = new HashMap<>();
        loadWeaponConfig();
    }

    private static class WeaponData {
        final double damage;
        final double projectileDamage;
        final String name;
        final int modelData;
        final List<String> lore;
        final Material material;
        final ConfigurationSection crafting;
        final double attackSpeed;

        WeaponData(double damage, double projectileDamage, String name, int modelData,
                   List<String> lore, Material material, ConfigurationSection crafting,
                   double attackSpeed) {
            this.damage = damage;
            this.projectileDamage = projectileDamage;
            this.name = name;
            this.modelData = modelData;
            this.lore = lore;
            this.material = material;
            this.crafting = crafting;
            this.attackSpeed = attackSpeed;
        }
    }



    private double getBaseAttackSpeed(Material material) {
        // Default base speeds for different weapon types
        if (material.name().endsWith("_SWORD")) return 1.6;
        if (material.name().endsWith("_AXE")) return 1.0;
        if (material.name().endsWith("_HOE")) return 1.0;
        if (material.name().endsWith("_SHOVEL")) return 1.0;
        return 4.0; // Default speed for other items
    }

    private void loadWeaponConfig() {
        ConfigurationSection weapons = plugin.getConfig().getConfigurationSection("weapons");
        if (weapons == null) {
            plugin.getLogger().warning("No weapons section found in config!");
            return;
        }

        for (String weaponKey : weapons.getKeys(false)) {
            ConfigurationSection weaponConfig = weapons.getConfigurationSection(weaponKey);
            if (weaponConfig != null) {
                double meleeDamage = weaponConfig.getDouble("meleeDamage", 1.0);
                double projectileDamage = weaponConfig.getDouble("projectileDamage", meleeDamage);
                String name = weaponConfig.getString("name", weaponKey);
                int modelData = weaponConfig.getInt("model-data", 0);
                List<String> lore = weaponConfig.getStringList("lore");
                double attackSpeed = weaponConfig.getDouble("attackSpeed", 4.0);

                Material material;
                String materialStr = weaponConfig.getString("material");
                if (materialStr != null) {
                    material = Material.valueOf(materialStr.toUpperCase());
                } else {
                    material = Material.valueOf(weaponKey);
                }

                ConfigurationSection crafting = weaponConfig.getConfigurationSection("crafting");

                weaponData.put(weaponKey.toUpperCase(),
                        new WeaponData(meleeDamage, projectileDamage, name, modelData,
                                lore, material, crafting, attackSpeed));
            }
        }
    }

    /**
     * Gets the identifier for a weapon based on its type and custom model data.
     * This is used to check if a player's class can use this specific weapon.
     *
     * @param weapon The ItemStack to identify
     * @return The weapon identifier string or null if not registered
     */
    public String getCustomWeaponIdentifier(ItemStack weapon) {
        if (weapon == null || !weapon.hasItemMeta() || !weapon.getItemMeta().hasCustomModelData()) {
            return null;
        }

        Material material = weapon.getType();
        int modelData = weapon.getItemMeta().getCustomModelData();

        // Search through weapon data to find a match
        for (String key : weaponData.keySet()) {
            WeaponData data = weaponData.get(key);
            if (data.material == material && data.modelData == modelData) {
                return key;
            }
        }

        return null;
    }

    public double getWeaponDamage(ItemStack weapon) {
        String weaponType = weapon.getType().toString().toUpperCase();

        // If it has custom model data, check for custom weapon first
        if (weapon.hasItemMeta() && weapon.getItemMeta().hasCustomModelData()) {
            int modelData = weapon.getItemMeta().getCustomModelData();
            for (Map.Entry<String, WeaponData> entry : weaponData.entrySet()) {
                WeaponData data = entry.getValue();
                if (data.material == weapon.getType() &&
                        data.modelData == modelData) {
                    return data.damage;
                }
            }
        }

        // Just get the configured damage directly from weaponData
        WeaponData data = weaponData.get(weaponType);
        return data != null ? data.damage : 0;
    }


    // Get projectile damage for a weapon type
    public double getProjectileDamage(String projectileType) {
        WeaponData data = weaponData.get(projectileType.toUpperCase());
        return data != null ? data.projectileDamage : 0;
    }

    /**
     * Checks if an ItemStack weapon is configured in the weapon system,
     * considering both its material type and custom model data.
     *
     * @param weapon The ItemStack to check
     * @return true if this weapon is configured in the system
     */
    /**
     * Checks if an ItemStack weapon is configured in the weapon system
     * considering both its material type and custom model data.
     */
    public boolean isConfiguredWeapon(ItemStack weapon) {
        if (weapon == null) return false;

        String weaponType = weapon.getType().toString().toUpperCase();

        // First check if this is a standard weapon type without custom model data
        if (weaponData.containsKey(weaponType) &&
                (!weapon.hasItemMeta() || !weapon.getItemMeta().hasCustomModelData())) {
            return true;
        }

        // If it has custom model data, check if it's a custom weapon
        if (weapon.hasItemMeta() && weapon.getItemMeta().hasCustomModelData()) {
            int modelData = weapon.getItemMeta().getCustomModelData();

            // Check if this is a registered custom weapon
            for (Map.Entry<String, WeaponData> entry : weaponData.entrySet()) {
                WeaponData data = entry.getValue();
                if (data.material == weapon.getType() &&
                        data.modelData == modelData) {
                    return true;
                }
            }
        }

        return false;
    }

    // Check if weapon is configured
    public boolean isConfiguredWeapon(String weaponType) {
        return weaponData.containsKey(weaponType.toUpperCase());
    }

    // Clean up recipes when disabling plugin
    public void cleanup() {
        for (NamespacedKey key : recipeKeys.values()) {
            Bukkit.removeRecipe(key);
        }
    }
}
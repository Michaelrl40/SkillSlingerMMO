package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

public class PotionCraftingManager {
    private final MinecraftMMORPG plugin;
    private final CustomPotionManager customPotionManager;

    public PotionCraftingManager(MinecraftMMORPG plugin, CustomPotionManager customPotionManager) {
        this.plugin = plugin;
        this.customPotionManager = customPotionManager;
        loadRecipes();
    }

    private void loadRecipes() {
        ConfigurationSection potionSection = plugin.getConfig().getConfigurationSection("potion-system.custom-potions");
        if (potionSection == null) {
            plugin.getLogger().warning("No potion-system.custom-potions section found in config!");
            return;
        }

        plugin.getLogger().info("Loading custom potion recipes...");

        for (String potionId : potionSection.getKeys(false)) {
            ConfigurationSection potionConfig = potionSection.getConfigurationSection(potionId);
            if (potionConfig == null) continue;

            ConfigurationSection craftingConfig = potionConfig.getConfigurationSection("crafting");
            if (craftingConfig == null) {
                plugin.getLogger().warning("No crafting section found for potion: " + potionId);
                continue;
            }

            String mainIngredient = craftingConfig.getString("ingredient");
            if (mainIngredient == null) {
                plugin.getLogger().warning("No ingredient specified for potion: " + potionId);
                continue;
            }

            Material ingredient = Material.matchMaterial(mainIngredient);
            if (ingredient == null) {
                plugin.getLogger().warning("Invalid material name for potion " + potionId + ": " + mainIngredient);
                continue;
            }

            // Create the potion item
            ItemStack potionItem = customPotionManager.createCustomPotion(potionId);
            if (potionItem == null) continue;

            // Create a new shaped recipe
            NamespacedKey key = new NamespacedKey(plugin, potionId.toLowerCase());
            ShapedRecipe recipe = new ShapedRecipe(key, potionItem);

            // Set the shape
            recipe.shape("GBG", "BIB", "GBG");

            // Set the ingredients
            recipe.setIngredient('I', ingredient); // Main ingredient in center
            recipe.setIngredient('B', Material.GLASS_BOTTLE); // Glass bottles around
            recipe.setIngredient('G', Material.GLOWSTONE_DUST); // Glowstone in corners

            // Register the recipe
            try {
                Bukkit.addRecipe(recipe);
                plugin.getLogger().info("Registered crafting recipe for: " + potionId);
            } catch (IllegalStateException e) {
                plugin.getLogger().warning("Failed to register recipe for: " + potionId);
            }
        }
    }
}
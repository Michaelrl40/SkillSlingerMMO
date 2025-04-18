package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class CitrusForgeManager {
    private final MinecraftMMORPG plugin;
    private final Map<String, MagicFruit> magicFruits = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final NamespacedKey magicFruitKey;

    public CitrusForgeManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.magicFruitKey = new NamespacedKey(plugin, "magic_fruit_id");
        loadFruits();
    }

    private void loadFruits() {
        ConfigurationSection fruitConfig = plugin.getConfig().getConfigurationSection("citrus-forge.fruits");
        if (fruitConfig == null) {
            // Load default if no config exists
            createDefaultFruit();
            return;
        }

        for (String fruitId : fruitConfig.getKeys(false)) {
            ConfigurationSection fruit = fruitConfig.getConfigurationSection(fruitId);
            if (fruit == null) continue;

            magicFruits.put(fruitId, new MagicFruit(
                    fruitId,
                    fruit.getString("name", "§6Magical Orange"),
                    Material.SWEET_BERRIES,
                    Arrays.asList(
                            "§7" + fruit.getString("description", "A citrus fruit infused with healing energy"),
                            "§7Instantly restores §c" + fruit.getDouble("healAmount", 10.0) + "❤§7 health",
                            "§8Cooldown: " + fruit.getInt("cooldown", 30) + " seconds",
                            "§8Cannot be dropped"
                    ),
                    fruit.getDouble("healAmount", 10.0),
                    fruit.getLong("cooldown", 30) * 1000, // Convert to milliseconds
                    Color.ORANGE
            ));
        }
    }

    private void createDefaultFruit() {
        magicFruits.put("healing_orange", new MagicFruit(
                "healing_orange",
                "§6Sunforged Orange",
                Material.SWEET_BERRIES,
                Arrays.asList(
                        "§7A citrus fruit infused with solar energy",
                        "§7Instantly restores §c10❤§7 health",
                        "§8Cooldown: 30 seconds",
                        "§8Cannot be dropped"
                ),
                10.0,
                30000,
                Color.ORANGE
        ));
    }


    public ItemStack createMagicFruit(String fruitId) {
        MagicFruit fruit = magicFruits.get(fruitId);
        if (fruit == null) return null;

        ItemStack item = new ItemStack(fruit.material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        // Set custom name and lore
        meta.setDisplayName(fruit.name);
        meta.setLore(fruit.lore);

        // Add glow effect
        meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Add flags to prevent normal interactions
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);

        // Store fruit ID and mark as undropable
        meta.getPersistentDataContainer().set(magicFruitKey, PersistentDataType.STRING, fruitId);
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "undropable"),
                PersistentDataType.BYTE,
                (byte) 1
        );

        item.setItemMeta(meta);
        return item;
    }

    public boolean consumeFruit(Player player, ItemStack item) {
        String fruitId = getFruitId(item);
        if (fruitId == null) return false;

        MagicFruit fruit = magicFruits.get(fruitId);
        if (fruit == null) return false;

        // Check cooldown
        if (isOnCooldown(player, fruitId)) {
            long remainingSeconds = getRemainingCooldown(player, fruitId) / 1000;
            player.sendMessage("§c✦ You must wait " + remainingSeconds + " seconds before eating another " + fruit.name + "!");
            return false;
        }

        // Apply healing
        double currentHealth = player.getHealth();
        double maxHealth = player.getMaxHealth();
        double newHealth = Math.min(maxHealth, currentHealth + fruit.healingAmount);
        player.setHealth(newHealth);

        // Apply cooldown
        setCooldown(player, fruitId, fruit.cooldown);

        // Play effects
        player.getWorld().spawnParticle(
                org.bukkit.Particle.HEART,
                player.getLocation().add(0, 1, 0),
                5, 0.5, 0.5, 0.5, 0
        );
        player.playSound(
                player.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP,
                0.5f, 2.0f
        );

        // Remove the consumed fruit
        item.setAmount(item.getAmount() - 1);

        return true;
    }

    public String getFruitId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(magicFruitKey, PersistentDataType.STRING);
    }

    public boolean isUndropable(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte undropable = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "undropable"), PersistentDataType.BYTE);
        return undropable != null && undropable == 1;
    }

    private boolean isOnCooldown(Player player, String fruitId) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return false;

        Long cooldownEnd = playerCooldowns.get(fruitId);
        return cooldownEnd != null && cooldownEnd > System.currentTimeMillis();
    }

    private long getRemainingCooldown(Player player, String fruitId) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;

        Long cooldownEnd = playerCooldowns.get(fruitId);
        if (cooldownEnd == null) return 0;

        return Math.max(0, cooldownEnd - System.currentTimeMillis());
    }

    private void setCooldown(Player player, String fruitId, long duration) {
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(fruitId, System.currentTimeMillis() + duration);
    }

    private static class MagicFruit {
        final String id;
        final String name;
        final Material material;
        final List<String> lore;
        final double healingAmount;
        final long cooldown;
        final Color color;

        MagicFruit(String id, String name, Material material, List<String> lore,
                   double healingAmount, long cooldown, Color color) {
            this.id = id;
            this.name = name;
            this.material = material;
            this.lore = lore;
            this.healingAmount = healingAmount;
            this.cooldown = cooldown;
            this.color = color;
        }
    }
}
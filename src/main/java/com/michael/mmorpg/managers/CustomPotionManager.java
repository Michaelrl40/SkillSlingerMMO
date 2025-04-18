package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class CustomPotionManager {
    private final MinecraftMMORPG plugin;
    private final Map<String, CustomPotion> customPotions = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final NamespacedKey customPotionKey;

    public CustomPotionManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.customPotionKey = new NamespacedKey(plugin, "custom_potion_id");
        loadPotions();
    }

    private void loadPotions() {
        ConfigurationSection potionSection = plugin.getConfig().getConfigurationSection("potion-system.custom-potions");
        if (potionSection == null) return;

        for (String potionId : potionSection.getKeys(false)) {
            ConfigurationSection potionConfig = potionSection.getConfigurationSection(potionId);
            if (potionConfig == null) continue;

            CustomPotion potion = new CustomPotion(
                    potionId,
                    potionConfig.getString("name"),
                    potionConfig.getString("description"),
                    ResourceType.valueOf(potionConfig.getString("resource-type")),
                    potionConfig.getDouble("amount"),
                    potionConfig.getLong("cooldown"),
                    parseColor(potionConfig.getString("color")),
                    potionConfig.getString("particle"),
                    potionConfig.getString("sound"),
                    potionConfig.getInt("model-data", 0)
            );
            customPotions.put(potionId, potion);
        }
    }

    public ItemStack createCustomPotion(String potionId) {
        CustomPotion potion = customPotions.get(potionId);
        if (potion == null) return null;

        // Create potion item
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta == null) return null;

        // Set custom name and lore
        meta.setDisplayName("§r" + potion.getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7" + potion.getDescription());
        lore.add("§7Restores " + potion.getAmount() + " " + potion.getResourceType().name());
        lore.add("§7Cooldown: " + potion.getCooldown() + " seconds");
        meta.setLore(lore);

        // Set custom color
        meta.setColor(potion.getColor());

        // IMPORTANT: Set custom model data - this allows resource pack models to work
        meta.setCustomModelData(potion.getModelData());

        // Store potion ID in persistent data
        meta.getPersistentDataContainer().set(customPotionKey, PersistentDataType.STRING, potionId);

        item.setItemMeta(meta);
        return item;
    }

    public int getPotionModelData(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        String potionId = getPotionId(item);
        if (potionId == null) return 0;

        CustomPotion potion = customPotions.get(potionId);
        return potion != null ? potion.getModelData() : 0;
    }

    // Add method to verify custom potion validity
    public boolean isValidCustomPotion(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return getPotionId(item) != null;
    }



    public boolean usePotion(Player player, ItemStack potion) {
        String potionId = getPotionId(potion);
        if (potionId == null) return false;

        CustomPotion customPotion = customPotions.get(potionId);
        if (customPotion == null) return false;

        // Check cooldown
        if (isOnCooldown(player, potionId)) {
            long remainingSeconds = getRemainingCooldown(player, potionId) / 1000;
            player.sendMessage("§c✦ This potion is on cooldown for " + remainingSeconds + " seconds!");
            return false;
        }

        // Apply potion effect
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return false;

        switch (customPotion.getResourceType()) {
            case MANA:
                playerData.regenMana(customPotion.getAmount());
                break;
            case STAMINA:
                playerData.regenStamina(customPotion.getAmount());
                break;
            case RAGE:
                playerData.addRage(customPotion.getAmount());
                break;
        }

        // Apply cooldown
        setCooldown(player, potionId, customPotion.getCooldown() * 1000);

        // Play effects
        player.getWorld().spawnParticle(
                org.bukkit.Particle.valueOf(customPotion.getParticleEffect()),
                player.getLocation().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1
        );
        player.playSound(
                player.getLocation(),
                org.bukkit.Sound.valueOf(customPotion.getSound()),
                1.0f, 1.0f
        );

        return true;
    }

    public String getPotionId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(customPotionKey, PersistentDataType.STRING);
    }

    public boolean isOnCooldown(Player player, String potionId) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return false;

        Long cooldownEnd = playerCooldowns.get(potionId);
        return cooldownEnd != null && cooldownEnd > System.currentTimeMillis();
    }

    public long getRemainingCooldown(Player player, String potionId) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;

        Long cooldownEnd = playerCooldowns.get(potionId);
        if (cooldownEnd == null) return 0;

        return Math.max(0, cooldownEnd - System.currentTimeMillis());
    }

    private void setCooldown(Player player, String potionId, long duration) {
        cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(potionId, System.currentTimeMillis() + duration);
    }

    public List<String> getPotionIds() {
        return new ArrayList<>(customPotions.keySet());
    }

    private Color parseColor(String colorName) {
        switch (colorName.toUpperCase()) {
            case "BLUE": return Color.BLUE;
            case "GREEN": return Color.GREEN;
            case "RED": return Color.RED;
            case "PURPLE": return Color.PURPLE;
            default: return Color.WHITE;
        }
    }

    public enum ResourceType {
        MANA,
        STAMINA,
        RAGE,
        TOXIN
    }

    private static class CustomPotion {
        private final String id;
        private final String name;
        private final String description;
        private final ResourceType resourceType;
        private final double amount;
        private final long cooldown;
        private final Color color;
        private final String particleEffect;
        private final String sound;
        private final int modelData;

        public CustomPotion(String id, String name, String description,
                            ResourceType resourceType, double amount, long cooldown,
                            Color color, String particleEffect, String sound,
                            int modelData) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.resourceType = resourceType;
            this.amount = amount;
            this.cooldown = cooldown;
            this.color = color;
            this.particleEffect = particleEffect;
            this.sound = sound;
            this.modelData = modelData;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public ResourceType getResourceType() { return resourceType; }
        public double getAmount() { return amount; }
        public long getCooldown() { return cooldown; }
        public Color getColor() { return color; }
        public String getParticleEffect() { return particleEffect; }
        public String getSound() { return sound; }
        public int getModelData() { return modelData; }
    }
}
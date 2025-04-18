package com.michael.mmorpg.skills.frostmage;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.michael.mmorpg.skills.Skill;
import java.util.ArrayList;
import java.util.List;

public class SummonIceBlockSkill extends Skill {
    // Configuration values for the summoned item
    private final String itemName;
    private final List<String> itemLore;
    private final int itemAmount;

    public SummonIceBlockSkill(ConfigurationSection config) {
        super(config);
        // Load configuration values with defaults
        this.itemName = config.getString("itemname", "§bMagical Ice Block");
        this.itemAmount = config.getInt("amount", 1);

        // Create default lore if none provided
        List<String> defaultLore = new ArrayList<>();
        defaultLore.add("§7A block of magical ice");
        defaultLore.add("§7Conjured by frost magic");
        this.itemLore = config.getStringList("lore");
        if (this.itemLore.isEmpty()) {
            this.itemLore.addAll(defaultLore);
        }
    }

    @Override
    protected void performSkill(Player player) {
        // Check if player has inventory space
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c✦ Your inventory is full!");
            setSkillSuccess(false);
            return;
        }

        // Create the ice block item
        ItemStack iceBlock = createIceBlock();

        // Give the item to the player
        player.getInventory().addItem(iceBlock);

        // Play summoning effects
        createSummonEffects(player);

        // Set success and send message
        setSkillSuccess(true);
        player.sendMessage("§b✦ You conjure a magical ice block!");
    }

    private ItemStack createIceBlock() {
        // Create the base item
        ItemStack iceBlock = new ItemStack(Material.ICE, itemAmount);
        ItemMeta meta = iceBlock.getItemMeta();

        if (meta != null) {
            // Set custom name
            meta.setDisplayName(itemName);

            // Set lore
            meta.setLore(itemLore);

            // Make it glow for extra effect
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);

            // Apply the metadata
            iceBlock.setItemMeta(meta);
        }

        return iceBlock;
    }

    private void createSummonEffects(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Create a swirl of ice particles
        for (double i = 0; i < Math.PI * 2; i += Math.PI / 8) {
            double x = Math.cos(i) * 0.8;
            double z = Math.sin(i) * 0.8;
            Location particleLoc = loc.clone().add(x, 1, z);

            world.spawnParticle(Particle.SNOWFLAKE, particleLoc, 3, 0.1, 0.1, 0.1, 0);
            world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0.02);
        }

        // Play magical ice forming sounds
        world.playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.5f, 1.5f);
        world.playSound(loc, Sound.ENTITY_PLAYER_HURT_FREEZE, 0.5f, 1.2f);
    }
}
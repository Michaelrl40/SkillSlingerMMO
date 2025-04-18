package com.michael.mmorpg.skills.zcommon;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ForageSkill extends Skill {
    private final Random random = new Random();
    private final List<Material> possibleFoods = new ArrayList<>();

    public ForageSkill(ConfigurationSection config) {
        super(config);

        // Initialize possible food items
        possibleFoods.add(Material.APPLE);
        possibleFoods.add(Material.BREAD);
        possibleFoods.add(Material.COOKED_BEEF);
        possibleFoods.add(Material.COOKED_CHICKEN);
        possibleFoods.add(Material.COOKED_MUTTON);
        possibleFoods.add(Material.COOKED_RABBIT);
        possibleFoods.add(Material.COOKED_PORKCHOP);
        possibleFoods.add(Material.COOKED_COD);
        possibleFoods.add(Material.COOKED_SALMON);
        possibleFoods.add(Material.SWEET_BERRIES);
        possibleFoods.add(Material.BEETROOT);
        possibleFoods.add(Material.CARROT);
        possibleFoods.add(Material.POTATO);
        possibleFoods.add(Material.BAKED_POTATO);
        possibleFoods.add(Material.PUMPKIN_PIE);
    }

    @Override
    protected void performSkill(Player player) {
        // Check player's surroundings - only succeed in natural environments
        Material blockBelow = player.getLocation().subtract(0, 1, 0).getBlock().getType();

        // List of natural blocks where foraging makes sense
        boolean isNaturalGround = blockBelow == Material.GRASS_BLOCK ||
                blockBelow == Material.DIRT ||
                blockBelow == Material.PODZOL ||
                blockBelow == Material.SAND ||
                blockBelow == Material.GRAVEL;

        if (!isNaturalGround) {
            player.sendMessage("§c✦ Cannot forage here. Try on natural ground like grass or dirt.");
            setSkillSuccess(false);
            return;
        }

        // Visual effects - searching for food
        player.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                player.getLocation().add(0, 1, 0),
                15, 0.5, 0.5, 0.5, 0.1
        );
        player.getWorld().playSound(
                player.getLocation(),
                Sound.BLOCK_GRASS_BREAK,
                0.8f,
                1.0f
        );

        // Determine success (80% chance)
        if (random.nextDouble() <= 0.8) {
            // Success - give random food
            Material foodType = possibleFoods.get(random.nextInt(possibleFoods.size()));
            ItemStack foodItem = new ItemStack(foodType, 1);

            // Add to inventory or drop if inventory is full
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(foodItem);
                player.sendMessage("§a✦ You foraged a " + formatItemName(foodType.toString()) + "!");
            } else {
                player.getWorld().dropItem(player.getLocation(), foodItem);
                player.sendMessage("§a✦ You foraged a " + formatItemName(foodType.toString()) + " but your inventory is full!");
            }

            // Play success sound
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
        } else {
            // Failure
            player.sendMessage("§e✦ You searched but couldn't find any food here. Try a different location.");
            player.playSound(player.getLocation(), Sound.BLOCK_GRASS_BREAK, 0.7f, 0.8f);
        }

        setSkillSuccess(true);
    }

    /**
     * Formats a material enum name into a readable string
     */
    private String formatItemName(String materialName) {
        String name = materialName.toLowerCase().replace('_', ' ');
        StringBuilder formattedName = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (capitalizeNext && Character.isLetter(c)) {
                formattedName.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                formattedName.append(c);
                if (c == ' ') {
                    capitalizeNext = true;
                }
            }
        }

        return formattedName.toString();
    }
}
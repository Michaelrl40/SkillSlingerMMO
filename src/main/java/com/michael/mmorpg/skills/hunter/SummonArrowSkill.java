package com.michael.mmorpg.skills.hunter;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class SummonArrowSkill extends Skill {
    private final int arrowAmount;

    public SummonArrowSkill(ConfigurationSection config) {
        super(config);
        this.arrowAmount = 16; // Fixed stack size of 16 arrows
    }

    @Override
    protected void performSkill(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack arrows = new ItemStack(Material.ARROW, arrowAmount);

        // Check if inventory has space
        if (hasInventorySpace(inventory, arrows)) {
            // Add arrows to inventory
            inventory.addItem(arrows);

            // Play success effects
            playSuccessEffects(player);

            // Broadcast skill usage
            broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                    player.getName() + " summons a quiver of arrows!");

            setSkillSuccess(true);
        } else {
            // Inventory full message
            player.sendMessage("§c✦ Not enough inventory space for arrows!");
            setSkillSuccess(false);
        }
    }

    private boolean hasInventorySpace(PlayerInventory inventory, ItemStack arrows) {
        // Check for empty slots
        if (inventory.firstEmpty() != -1) {
            return true;
        }

        // Check for existing arrow stacks that can fit more
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == Material.ARROW) {
                int space = item.getMaxStackSize() - item.getAmount();
                if (space >= arrowAmount) {
                    return true;
                }
            }
        }
        return false;
    }

    private void playSuccessEffects(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation().add(0, 1, 0);

        // Particle effects around the player
        world.spawnParticle(
                Particle.END_ROD,
                loc,
                20, 0.5, 0.5, 0.5, 0.05
        );

        // Additional particles for the summoning effect
        for (int i = 0; i < 2; i++) {
            world.spawnParticle(
                    Particle.CRIT,
                    loc.clone().add(
                            Math.random() * 0.5 - 0.25,
                            Math.random() * 0.5,
                            Math.random() * 0.5 - 0.25
                    ),
                    5, 0.1, 0.1, 0.1, 0.05
            );
        }

        // Play mystical summoning sounds
        world.playSound(loc, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
    }
}
package com.michael.mmorpg.skills.buccaneer;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;

public class CitrusForgeSkill extends Skill {
    private final String defaultFruitType;

    public CitrusForgeSkill(ConfigurationSection config) {
        super(config);
        this.defaultFruitType = config.getString("defaultFruitType", "healing_orange");
    }

    @Override
    protected void performSkill(Player player) {
        // Create the crafting effect
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Start the crafting animation
        new BukkitRunnable() {
            private int step = 0;
            private final Location centerLoc = loc.clone().add(0, 1, 0);

            @Override
            public void run() {
                if (step >= 20) { // 1 second animation
                    // Create the fruit
                    ItemStack fruit = plugin.getCitrusForgeManager().createMagicFruit(defaultFruitType);
                    if (fruit != null) {
                        // Give the fruit to the player
                        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(fruit);
                        if (!leftover.isEmpty()) {
                            player.sendMessage("§c✦ Your inventory is full!");
                            setSkillSuccess(false);
                        } else {
                            // Success effects
                            world.playSound(centerLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 2.0f);
                            world.spawnParticle(
                                    Particle.INSTANT_EFFECT,
                                    centerLoc,
                                    20, 0.5, 0.5, 0.5, 0.1
                            );
                            player.sendMessage("§6✦ You forge a magical citrus fruit!");
                        }
                    }
                    cancel();
                    return;
                }

                // Crafting animation
                double angle = step * Math.PI / 10;
                double x = Math.cos(angle) * 0.5;
                double z = Math.sin(angle) * 0.5;

                world.spawnParticle(
                        Particle.WITCH,
                        centerLoc.clone().add(x, 0, z),
                        3, 0.1, 0.1, 0.1, 0
                );

                if (step % 4 == 0) {
                    world.playSound(centerLoc, Sound.BLOCK_SWEET_BERRY_BUSH_PLACE, 0.5f, 1.0f + (step / 20.0f));
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }
}
package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class HungerListener implements Listener {
    private final MinecraftMMORPG plugin;
    private static final double HUNGER_REDUCTION_RATE = 0.33;

    public HungerListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        startHealingTask();
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();
        int currentFood = player.getFoodLevel();
        int newFood = event.getFoodLevel();

        // If it's hunger loss (not eating food)
        if (newFood < currentFood) {
            // Calculate reduced hunger loss
            int foodLoss = currentFood - newFood;
            int reducedLoss = (int) Math.max(1, foodLoss * HUNGER_REDUCTION_RATE);
            event.setFoodLevel(currentFood - reducedLoss);
        }
    }

    @EventHandler
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        // Cancel vanilla saturation healing
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Prevent vanilla sprint reduction on low food
        if (player.getFoodLevel() <= 6) {
            player.setFoodLevel(7); // Keep it just above sprint threshold
        }
    }

    private void startHealingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    // Only heal if player has food and is not in combat
                    if (player.getFoodLevel() >= 16 && // High enough food level
                            !plugin.getCombatManager().isInCombat(player) && // Not in combat
                            player.getHealth() < player.getMaxHealth() && // Not full health
                            !player.isDead()) { // Is alive

                        // Heal only 1 health (half heart) every 2 seconds
                        double newHealth = Math.min(player.getHealth() + 5, player.getMaxHealth());
                        player.setHealth(newHealth);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // Run every 2 seconds (40 ticks)
    }
}
package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class BandolierArrowListener implements Listener {
    private final MinecraftMMORPG plugin;
    private static final double MAX_RANGE = 15.0;
    private static final double VELOCITY_MULTIPLIER = 0.6;
    private static final int MAX_TICKS = 20;
    private static final int QUICK_CHARGE_LEVEL = 3;

    public BandolierArrowListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        // Start repeating task to check crossbows
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    checkAndEnchantCrossbow(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Check every second
    }

    private boolean isBandolier(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        return playerData != null &&
                playerData.hasClass() &&
                playerData.getGameClass().getName().equalsIgnoreCase("Bandolier");
    }

    private void checkAndEnchantCrossbow(Player player) {
        if (!isBandolier(player)) {
            return;
        }

        // Check main hand
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType() == Material.CROSSBOW) {
            mainHand.addUnsafeEnchantment(Enchantment.QUICK_CHARGE, QUICK_CHARGE_LEVEL);
        }

        // Check offhand
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() == Material.CROSSBOW) {
            offHand.addUnsafeEnchantment(Enchantment.QUICK_CHARGE, QUICK_CHARGE_LEVEL);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // Run on next tick to ensure inventory is updated
        new BukkitRunnable() {
            @Override
            public void run() {
                checkAndEnchantCrossbow(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (item.getType() == Material.CROSSBOW) {
            item.removeEnchantment(Enchantment.QUICK_CHARGE);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (event.getItem().getItemStack().getType() == Material.CROSSBOW) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    checkAndEnchantCrossbow(player);
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onProjectileShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (!isBandolier(player)) {
            return;
        }

        // Handle bow shots
        if (event.getBow().getType() == Material.BOW && event.getProjectile() instanceof Arrow) {
            handleBowShot(event, (Arrow) event.getProjectile());
        }
    }

    private void handleBowShot(EntityShootBowEvent event, Arrow arrow) {
        Vector velocity = arrow.getVelocity().multiply(VELOCITY_MULTIPLIER);
        arrow.setVelocity(velocity);
        arrow.setMetadata("bandolier_arrow", new FixedMetadataValue(plugin, true));

        new BukkitRunnable() {
            private int ticks = 0;
            private final double initialSpeed = velocity.length();

            @Override
            public void run() {
                ticks++;
                if (!arrow.isValid() || arrow.isOnGround() || ticks >= MAX_TICKS) {
                    arrow.remove();
                    this.cancel();
                    return;
                }

                double distanceTraveled = initialSpeed * ticks / 20.0;
                if (distanceTraveled >= MAX_RANGE) {
                    arrow.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
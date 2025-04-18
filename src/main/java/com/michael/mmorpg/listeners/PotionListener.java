package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.managers.CustomPotionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.event.block.Action;

public class PotionListener implements Listener {
    private final MinecraftMMORPG plugin;
    private final CustomPotionManager customPotionManager;

    public PotionListener(MinecraftMMORPG plugin, CustomPotionManager customPotionManager) {
        this.plugin = plugin;
        this.customPotionManager = customPotionManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !(item.getItemMeta() instanceof PotionMeta)) {
            return;
        }

        // Check if this is one of our custom potions
        String potionId = customPotionManager.getPotionId(item);
        if (potionId == null) {
            return; // Not our custom potion
        }

        // Check cooldown before allowing the drink action
        if (customPotionManager.isOnCooldown(event.getPlayer(), potionId)) {
            event.setCancelled(true);
            long remainingSeconds = customPotionManager.getRemainingCooldown(event.getPlayer(), potionId) / 1000;
            event.getPlayer().sendMessage("§c✦ This potion is on cooldown for " + remainingSeconds + " seconds!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPotionConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!(item.getItemMeta() instanceof PotionMeta)) {
            return;
        }

        Player player = event.getPlayer();

        // Try to use the potion as a custom potion
        boolean wasCustomPotion = customPotionManager.usePotion(player, item);

        // If it was a custom potion, cancel the vanilla event
        if (wasCustomPotion) {
            event.setCancelled(true);
            // Remove one potion from the player's inventory
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand.isSimilar(item)) {
                inHand.setAmount(inHand.getAmount() - 1);
                player.getInventory().setItemInMainHand(inHand);
            } else {
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (offHand.isSimilar(item)) {
                    offHand.setAmount(offHand.getAmount() - 1);
                    player.getInventory().setItemInOffHand(offHand);
                }
            }
        }
    }
}
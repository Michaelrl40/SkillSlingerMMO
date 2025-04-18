package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ItemBindListener implements Listener {
    private final MinecraftMMORPG plugin;

    public ItemBindListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Check if it's a right-click action
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if the player has an item
        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Check if the item has a bound skill
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "bound_skill");

        if (!container.has(key, PersistentDataType.STRING)) {
            return;
        }

        // Get the bound skill name
        String skillName = container.get(key, PersistentDataType.STRING);

        // Only execute if player is not sneaking (to allow normal block interactions while sneaking)
        if (!player.isSneaking()) {
            // Cancel the interaction to prevent normal item use
            event.setCancelled(true);

            // Execute the skill
            plugin.getSkillManager().executeSkill(player, skillName, new String[0]);
        }
    }
}
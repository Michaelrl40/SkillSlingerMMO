package com.michael.mmorpg.dungeon;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.party.Party;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class DungeonKeyListener implements Listener {
    private final MinecraftMMORPG plugin;
    private final DungeonKey dungeonKey;

    public DungeonKeyListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.dungeonKey = new DungeonKey(plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Ignore off-hand interactions
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;

        // Only process right clicks on blocks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Check if the item is a dungeon key
        if (item == null) return;

        String dungeonName = dungeonKey.getDungeonNameFromKey(item);
        if (dungeonName == null) return;

        // Check if the clicked block is near a dungeon entrance
        if (event.getClickedBlock() == null) return;

        // Get the dungeon by name
        com.michael.mmorpg.dungeon.Dungeon dungeon = plugin.getDungeonManager().getDungeonByName(dungeonName);
        if (dungeon == null) {
            player.sendMessage("§c✦ The dungeon this key is for doesn't exist anymore!");
            return;
        }

        // Check if the dungeon has an entrance set
        if (dungeon.getEntranceLocation() == null) {
            player.sendMessage("§c✦ This dungeon doesn't have an entrance set!");
            return;
        }

        // Check if clicked block is near the entrance
        if (!isNearEntrance(event.getClickedBlock().getLocation(), dungeon.getEntranceLocation(), 2)) {
            return; // Not near entrance, let the interaction pass through
        }

        // Cancel the event to prevent normal item use
        event.setCancelled(true);

        // Check if player is in a party
        Party party = plugin.getPartyManager().getParty(player);
        if (party == null) {
            player.sendMessage("§c✦ You need to be in a party to enter a dungeon!");
            return;
        }

        // Check if player is the party leader
        if (!party.getLeader().equals(player)) {
            player.sendMessage("§c✦ Only the party leader can use a dungeon key!");
            return;
        }

        // Check if dungeon is available
        if (dungeon.isOccupied()) {
            player.sendMessage("§c✦ This dungeon is currently occupied by another party!");
            return;
        }

        // Consume the key
        dungeonKey.consumeKey(player, dungeonName);

        // Begin teleport countdown
        plugin.getDungeonManager().teleportPartyToDungeon(player, dungeonName);
    }

    private boolean isNearEntrance(org.bukkit.Location location, org.bukkit.Location entrance, double maxDistance) {
        return location.getWorld().equals(entrance.getWorld()) &&
                location.distanceSquared(entrance) <= maxDistance * maxDistance;
    }
}
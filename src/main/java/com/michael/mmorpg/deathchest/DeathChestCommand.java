package com.michael.mmorpg.deathchest;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.deathchest.DeathChest;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DeathChestCommand implements CommandExecutor {
    private final MinecraftMMORPG plugin;

    public DeathChestCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        DeathChest chest = plugin.getDeathChestManager().getPlayerDeathChest(player.getUniqueId());

        if (chest == null) {
            player.sendMessage("§cYou don't have a death chest.");
            return true;
        }

        Location loc = chest.getLocation();

        player.sendMessage("§6Your death chest is located at §f" +
                String.format("x:%d, y:%d, z:%d in %s",
                        loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                        loc.getWorld().getName()));

        if (chest.isLocked()) {
            player.sendMessage("§6It's locked for §f" + chest.getFormattedRemainingTime() +
                    " §6and only you can access it during this time.");
        } else {
            player.sendMessage("§6It's unlocked and anyone can access it now.");
        }

        // Set a compass target to the chest
        player.setCompassTarget(loc);
        player.sendMessage("§6Your compass now points to your death chest.");

        return true;
    }
}
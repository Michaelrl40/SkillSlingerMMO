package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.managers.CustomPotionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GivePotionCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;
    private final CustomPotionManager potionManager;

    public GivePotionCommand(MinecraftMMORPG plugin, CustomPotionManager potionManager) {
        this.plugin = plugin;
        this.potionManager = potionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("mmorpg.givepotion")) {
            player.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage("§cUsage: /givepotion <potion_id> [amount]");
            return true;
        }

        String potionId = args[0].toLowerCase();
        int amount = 1;

        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
                if (amount < 1) amount = 1;
                if (amount > 64) amount = 64;
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid amount specified!");
                return true;
            }
        }

        ItemStack potion = potionManager.createCustomPotion(potionId);
        if (potion == null) {
            player.sendMessage("§cInvalid potion ID!");
            return true;
        }

        potion.setAmount(amount);
        player.getInventory().addItem(potion);
        player.sendMessage("§aGave " + amount + "x " + potionId + " potion!");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return potionManager.getPotionIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
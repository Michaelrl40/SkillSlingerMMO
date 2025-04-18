package com.michael.mmorpg.commands;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BindCommand implements CommandExecutor, TabCompleter {
    private final MinecraftMMORPG plugin;

    public BindCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

        if (playerData == null || !playerData.hasClass()) {
            player.sendMessage("§cYou need to select a class before binding skills.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("clear")) {
            return handleClearBind(player);
        } else if (subCommand.equals("info")) {
            return handleBindInfo(player);
        } else {
            return handleBindSkill(player, args);
        }
    }

    private boolean handleClearBind(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (heldItem.getType() == Material.AIR) {
            player.sendMessage("§cYou must hold an item to clear bindings.");
            return true;
        }

        ItemMeta meta = heldItem.getItemMeta();
        if (meta == null) {
            player.sendMessage("§cThis item cannot be bound to skills.");
            return true;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "bound_skill");

        if (!container.has(key, PersistentDataType.STRING)) {
            player.sendMessage("§cThis item doesn't have any bound skill.");
            return true;
        }

        container.remove(key);
        heldItem.setItemMeta(meta);

        // Update lore to remove binding information
        updateItemLore(heldItem, null);

        player.sendMessage("§aSkill binding cleared from your item.");
        return true;
    }

    private boolean handleBindInfo(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (heldItem.getType() == Material.AIR) {
            player.sendMessage("§cYou must hold an item to check bindings.");
            return true;
        }

        ItemMeta meta = heldItem.getItemMeta();
        if (meta == null) {
            player.sendMessage("§cThis item cannot be bound to skills.");
            return true;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "bound_skill");

        if (!container.has(key, PersistentDataType.STRING)) {
            player.sendMessage("§cThis item doesn't have any bound skill.");
            return true;
        }

        String boundSkill = container.get(key, PersistentDataType.STRING);
        player.sendMessage("§6This item is bound to the skill: §e" + boundSkill);
        return true;
    }

    private boolean handleBindSkill(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage("§cUsage: /bind <skillname> or /bind clear");
            return true;
        }

        String skillName = args[0];

        // Check if the skill exists and the player has access to it
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (!plugin.getSkillManager().skillExists(skillName)) {
            player.sendMessage("§cSkill not found: " + skillName);
            return true;
        }

        // Check if player has the skill unlocked
        if (playerData.getGameClass().getSkillConfig(skillName) == null) {
            player.sendMessage("§cYour class doesn't have access to this skill.");
            return true;
        }

        // Get the item in hand
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType() == Material.AIR) {
            player.sendMessage("§cYou must hold an item to bind a skill to it.");
            return true;
        }


        // Bind the skill to the item
        ItemMeta meta = heldItem.getItemMeta();
        if (meta == null) {
            player.sendMessage("§cThis item cannot be bound to skills.");
            return true;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey key = new NamespacedKey(plugin, "bound_skill");

        container.set(key, PersistentDataType.STRING, skillName);
        heldItem.setItemMeta(meta);

        // Update the item's lore
        updateItemLore(heldItem, skillName);

        player.sendMessage("§aSuccessfully bound §6" + skillName + "§a to your " +
                heldItem.getType().toString().toLowerCase().replace('_', ' '));
        player.sendMessage("§aRight-click to use the skill.");

        return true;
    }

    private void updateItemLore(ItemStack item, String skillName) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        if (lore == null) lore = new ArrayList<>();

        // Remove any existing binding lore
        lore.removeIf(line -> line.contains("Bound Skill:"));

        // Add new binding lore if a skill is specified
        if (skillName != null && !skillName.isEmpty()) {
            lore.add(ChatColor.GRAY + "Bound Skill: " + ChatColor.GOLD + skillName);
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void showHelp(Player player) {
        player.sendMessage("§6==== Skill Binding Help ====");
        player.sendMessage("§e/bind <skillname> §7- Bind a skill to the item in your main hand");
        player.sendMessage("§e/bind clear §7- Remove skill binding from the item");
        player.sendMessage("§e/bind info §7- Show what skill is bound to the item");
        player.sendMessage("§7Right-click with a bound item to use the skill");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        Player player = (Player) sender;
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);

        if (playerData == null || !playerData.hasClass()) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            // Add subcommands
            completions.add("clear");
            completions.add("info");

            // Add available skills
            completions.addAll(playerData.getGameClass().getAllSkillConfigs().keySet());

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
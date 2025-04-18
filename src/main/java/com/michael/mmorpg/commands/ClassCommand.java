package com.michael.mmorpg.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.GameClass;
import com.michael.mmorpg.models.PlayerData;

import java.util.*;

public class ClassCommand implements CommandExecutor {
    private final MinecraftMMORPG plugin;

    public ClassCommand(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c✦ This command can only be used by players!");
            return true;
        }

        Player player = (Player) sender;

        // If no arguments provided, show help
        if (args.length == 0) {
            sendHelp(player);
            return true;
        }


        switch (args[0].toLowerCase()) {
            case "select":
                if (args.length < 2) {
                    player.sendMessage("§c✦ Usage: /class select <className>");
                    return true;
                }
                handleClassSelection(player, args[1]);
                break;

            case "confirm":
                plugin.getClassManager().confirmClassChange(player);
                break;

            case "cancel":
                plugin.getClassManager().cancelClassChange(player);
                break;

            case "info":
                if (args.length < 2) {
                    handleClassInfo(player, new String[]{"info"});
                } else {
                    handleClassInfo(player, args);
                }
                break;

            case "list":
                if (args.length < 1) {
                    handleClassList(player, new String[]{"list", "1"});
                } else {
                    handleClassList(player, args);
                }
                break;

            case "skills":
                if (args.length < 2) {
                    showCurrentClassSkills(player);
                } else {
                    showClassSkills(player, args[1]);
                }
                break;

            case "mastery":
                handleMasteryInfo(player);
                break;

            case "view":
                if (args.length < 2) {
                    player.sendMessage("§c✦ Usage: /class view <playerName>");
                    return true;
                }
                handleViewPlayerClass(player, args[1]);
                break;

            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void handleClassSelection(Player player, String className) {
        if (plugin.getClassManager().switchClass(player, className)) {
            // Success message handled by ClassManager
            return;
        }
    }

    private void handleClassInfo(Player player, String[] args) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        FileConfiguration config = plugin.getConfig();

        if (playerData == null) {
            player.sendMessage("§c✦ Error loading player data!");
            return;
        }

        GameClass gameClass;
        boolean isCurrentClass = false;

        // If a class name is provided, look up that class
        if (args.length > 1) {
            gameClass = plugin.getConfigManager().getClass(args[1].toLowerCase());
            if (gameClass == null) {
                player.sendMessage("§c✦ Class not found: " + args[1]);
                return;
            }
        } else {
            // Show current class info
            if (!playerData.hasClass()) {
                player.sendMessage("§6=== Class Information ===");
                player.sendMessage("§7Current Class: §fNone");
                player.sendMessage("§7You need to select a class using §f/class select <className>");
                return;
            }
            gameClass = playerData.getGameClass();
            isCurrentClass = true;
        }

        String classPath = "classes." + gameClass.getName().toLowerCase() + ".";

        // Display class information
        player.sendMessage("§6=== Class Information ===");
        player.sendMessage("§7Class: §a" + gameClass.getName() +
                (isCurrentClass ? " §e(Current Class)" : ""));

        // Only show level and experience for current class
        if (isCurrentClass) {
            int level = playerData.getLevel();
            player.sendMessage("§7Level: §e" + level);

            // Round experience values
            double currentExp = Math.round(playerData.getExperience() * 10.0) / 10.0;
            double requiredExp = Math.round(playerData.getRequiredExperience() * 10.0) / 10.0;
            double totalExp = Math.round(playerData.getTotalExperience() * 10.0) / 10.0;

            player.sendMessage("§7Experience: §e" + currentExp + "§7/§e" + requiredExp);
            player.sendMessage("§7Total Experience: §e" + totalExp);
        }

        // Base Stats
        player.sendMessage("§e=== Base Stats ===");
        player.sendMessage("§7Base Health: §c" + config.getDouble(classPath + "baseHealth", 100.0));
        player.sendMessage("§7Base Mana: §b" + config.getDouble(classPath + "baseMana", 50.0));
        player.sendMessage("§7Base Stamina: §a" + config.getDouble(classPath + "baseStamina", 100.0));
        // Per Level Stats
        player.sendMessage("§e=== Per Level Stats ===");
        player.sendMessage("§7Health per Level: §c+" + config.getDouble(classPath + "healthPerLevel", 3.0));
        player.sendMessage("§7Mana per Level: §b+" + config.getDouble(classPath + "manaPerLevel", 2.0));
        player.sendMessage("§7Stamina per Level: §a+" + config.getDouble(classPath + "staminaPerLevel", 5.0));


        // Allowed Armor
        List<String> allowedArmor = gameClass.getAllowedArmor();
        if (!allowedArmor.isEmpty()) {
            player.sendMessage("§e=== Allowed Armor ===");
            StringBuilder armorStr = new StringBuilder();
            for (String armor : allowedArmor) {
                // Convert IRON_HELMET to "Iron Helmet"
                String formattedArmor = formatEquipmentName(armor);
                armorStr.append("§a").append(formattedArmor).append("§7, ");
            }
            // Remove last comma and space
            if (armorStr.length() > 2) {
                armorStr.setLength(armorStr.length() - 2);
            }
            player.sendMessage("§7" + armorStr.toString());
        }

        // Allowed Weapons
        List<String> allowedWeapons = gameClass.getAllowedWeapons();
        if (!allowedWeapons.isEmpty()) {
            player.sendMessage("§e=== Allowed Weapons ===");
            StringBuilder weaponStr = new StringBuilder();
            for (String weapon : allowedWeapons) {
                // Convert DIAMOND_SWORD to "Diamond Sword"
                String formattedWeapon = formatEquipmentName(weapon);
                weaponStr.append("§a").append(formattedWeapon).append("§7, ");
            }
            // Remove last comma and space
            if (weaponStr.length() > 2) {
                weaponStr.setLength(weaponStr.length() - 2);
            }
            player.sendMessage("§7" + weaponStr.toString());
        }
    }

    private void handleViewPlayerClass(Player viewer, String targetPlayerName) {
        // Find target player
        Player targetPlayer = Bukkit.getPlayer(targetPlayerName);

        if (targetPlayer == null) {
            viewer.sendMessage("§c✦ Player not found: " + targetPlayerName);
            return;
        }

        // Get target player's data
        PlayerData targetData = plugin.getPlayerManager().getPlayerData(targetPlayer);

        if (targetData == null) {
            viewer.sendMessage("§c✦ Error loading player data for " + targetPlayerName);
            return;
        }

        if (!targetData.hasClass()) {
            viewer.sendMessage("§6✦ " + targetPlayerName + " hasn't selected a class yet.");
            return;
        }

        // Display target player's class information
        GameClass targetClass = targetData.getGameClass();
        int targetLevel = targetData.getLevel();

        viewer.sendMessage("§6=== " + targetPlayerName + "'s Class Information ===");
        viewer.sendMessage("§7Class: §a" + targetClass.getName());
        viewer.sendMessage("§7Level: §e" + targetLevel);

        // Calculate some stat information based on level
        double maxHealth = targetClass.getMaxHealth(targetLevel);
        double maxMana = targetClass.getMaxMana(targetLevel);
        double maxStamina = targetClass.getMaxStamina(targetLevel);

        viewer.sendMessage("§7Max Health: §c" + String.format("%.1f", maxHealth));
        if (targetClass.usesMana()) {
            viewer.sendMessage("§7Max Mana: §b" + String.format("%.1f", maxMana));
        }
        if (targetClass.usesStamina()) {
            viewer.sendMessage("§7Max Stamina: §a" + String.format("%.1f", maxStamina));
        }
        if (targetClass.usesRage()) {
            viewer.sendMessage("§7Uses Rage: §cYes");
        }
        if (targetClass.usesToxin()) {
            viewer.sendMessage("§7Uses Toxin: §2Yes");
        }

        // Show mastery progress
        if (targetClass.getTier() == GameClass.ClassTier.MASTER) {
            double progress = (targetLevel * 100.0) / targetClass.getMasteryLevel();
            viewer.sendMessage("§7Mastery Progress: §e" + String.format("%.1f", progress) + "%");
        }
    }

    private String formatEquipmentName(String equipment) {
        String[] parts = equipment.toLowerCase().split("_");
        StringBuilder formatted = new StringBuilder();
        for (String part : parts) {
            if (formatted.length() > 0) {
                formatted.append(" ");
            }
            formatted.append(part.substring(0, 1).toUpperCase())
                    .append(part.substring(1));
        }
        return formatted.toString();
    }


    private void handleClassList(Player player, String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§c✦ Invalid page number!");
                return;
            }
        }

        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        List<String> availableClasses = plugin.getClassManager().getAvailableClasses(player);

        // Create organized class map
        Map<String, List<GameClass>> classesByBase = new LinkedHashMap<>(); // Keep insertion order

        // Define base classes in order
        List<String> baseClasses = Arrays.asList(
                "Healer",
                "Mage",
                "Ranger",
                "Rogue",
                "Warrior"
        );

        // Initialize lists for each base class
        for (String baseClass : baseClasses) {
            classesByBase.put(baseClass, new ArrayList<>());
        }

        // Sort classes into their base classes
        for (String className : availableClasses) {
            GameClass gameClass = plugin.getConfigManager().getClass(className.toLowerCase());
            if (gameClass != null) {
                String baseClassName = gameClass.getParentClass();
                if (baseClassName == null) {
                    // This is a base class, skip it for now
                    continue;
                }
                classesByBase.computeIfAbsent(baseClassName, k -> new ArrayList<>()).add(gameClass);
            }
        }

        // Sort classes within each base class alphabetically
        for (List<GameClass> classes : classesByBase.values()) {
            classes.sort(Comparator.comparing(GameClass::getName));
        }

        // Calculate pagination
        int itemsPerPage = 12;
        int totalClasses = availableClasses.size();
        int totalPages = (int) Math.ceil((double) totalClasses / itemsPerPage);

        if (page < 1 || page > totalPages) {
            player.sendMessage("§c✦ Invalid page number! Pages: 1-" + totalPages);
            return;
        }

        // Display header
        player.sendMessage("§6=== Available Classes (Page " + page + "/" + totalPages + ") ===");

        int displayedItems = 0;
        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = startIndex + itemsPerPage;


        for (String baseClass : baseClasses) {
            if (displayedItems >= startIndex && displayedItems < endIndex) {
                String prefix = playerData.getGameClass() != null &&
                        playerData.getGameClass().getName().equals(baseClass)
                        ? "§a* " : "§7- ";

                player.sendMessage(prefix + "§a" + baseClass);
            }
            displayedItems++;

            // Display master classes under each base class
            List<GameClass> masterClasses = classesByBase.get(baseClass);
            if (masterClasses != null && !masterClasses.isEmpty()) {
                if (displayedItems >= startIndex && displayedItems < endIndex) {
                    player.sendMessage("  §d§lMaster Classes:");
                }
                displayedItems++;

                for (GameClass masterClass : masterClasses) {
                    if (displayedItems >= startIndex && displayedItems < endIndex) {
                        String prefix = playerData.getGameClass() != null &&
                                playerData.getGameClass().getName().equals(masterClass.getName())
                                ? "§a* " : "§7  - ";

                        player.sendMessage(prefix + "§f" + masterClass.getName());
                    }
                    displayedItems++;
                }
            }
        }
        // Display footer with navigation help
        player.sendMessage("§7Use §f/class list <page> §7to view other pages");
    }

    private void showCurrentClassSkills(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null || !playerData.hasClass()) {
            player.sendMessage("§c✦ You haven't selected a class yet!");
            return;
        }

        GameClass gameClass = playerData.getGameClass();
        showSkillsForClass(player, gameClass, playerData.getLevel());
    }

    private void showClassSkills(Player player, String className) {
        GameClass gameClass = plugin.getConfigManager().getClass(className.toLowerCase());
        if (gameClass == null) {
            player.sendMessage("§c✦ Invalid class name!");
            return;
        }

        showSkillsForClass(player, gameClass, 1);
    }

    private void showSkillsForClass(Player player, GameClass gameClass, int playerLevel) {
        player.sendMessage("§6=== Skills for " + gameClass.getName() + " ===");
        Map<String, ConfigurationSection> skills = gameClass.getAllSkillConfigs();

        for (Map.Entry<String, ConfigurationSection> entry : skills.entrySet()) {
            String skillName = entry.getKey();
            ConfigurationSection skillConfig = entry.getValue();

            int unlockLevel = skillConfig.getInt("unlockLevel", 1);
            String description = skillConfig.getString("description", "No description available");
            double manaCost = skillConfig.getDouble("manaCost", 0);
            double staminaCost = skillConfig.getDouble("staminaCost", 0);

            boolean unlocked = playerLevel >= unlockLevel;
            String status = unlocked ? "§a[Unlocked]" : "§c[Locked]";

            player.sendMessage(status + " §f" + skillName + " §7(Level " + unlockLevel + ")");
            player.sendMessage("  §7" + description);

            if (manaCost > 0) {
                player.sendMessage("  §7Mana Cost: " + manaCost);
            }
            if (staminaCost > 0) {
                player.sendMessage("  §7Stamina Cost: " + staminaCost);
            }
        }
    }

    private void handleMasteryInfo(Player player) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null || !playerData.hasClass()) {
            player.sendMessage("§c✦ You haven't selected a class yet!");
            return;
        }

        GameClass gameClass = playerData.getGameClass();
        int level = playerData.getLevel();
        double totalExp = playerData.getTotalExperience();

        player.sendMessage("§6=== Mastery Progress ===");
        player.sendMessage("§7Current Class: §f" + gameClass.getName() + " §7(" + gameClass.getTier() + ")");
        player.sendMessage("§7Mastery Level Required: §f" + gameClass.getMasteryLevel());
        player.sendMessage("§7Current Level: §f" + level);
        player.sendMessage("§7Total Experience Required: §f" + gameClass.getMasteryRequiredExp());
        player.sendMessage("§7Current Total Experience: §f" + totalExp);
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6=== Class Commands ===");
        player.sendMessage("§7/class select <className> §f- Select a class");
        player.sendMessage("§7/class info §f- View your class information");
        player.sendMessage("§7/class skills [className] §f- View skills for your class or specified class");
        player.sendMessage("§7/class list §f- List available classes");
        player.sendMessage("§7/class mastery §f- View your class mastery progress");
    }
}
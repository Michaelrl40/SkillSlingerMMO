package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.GameClass;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SkillListManager {
    private final MinecraftMMORPG plugin;
    private final int SKILLS_PER_PAGE = 5;

    public SkillListManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    /**
     * Shows a paginated list of skills to the player
     * @param player The player to show skills to
     * @param pageNumber The page number (1-indexed)
     */
    public void showSkillList(Player player, int pageNumber) {
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null || !playerData.hasClass()) return;

        GameClass gameClass = playerData.getGameClass();
        int playerLevel = playerData.getLevel();

        // Get all skills and sort them
        List<SkillInfo> skills = getAllSkills(gameClass, playerLevel);

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) skills.size() / SKILLS_PER_PAGE);

        // Validate page number
        if (pageNumber < 1) {
            pageNumber = 1;
        } else if (pageNumber > totalPages) {
            pageNumber = totalPages;
        }

        // Calculate start and end indices
        int startIndex = (pageNumber - 1) * SKILLS_PER_PAGE;
        int endIndex = Math.min(startIndex + SKILLS_PER_PAGE, skills.size());

        // Display header
        player.sendMessage(ChatColor.GOLD + "======= " + gameClass.getName() + " Skills (Page " + pageNumber + "/" + totalPages + ") =======");

        // Display skills for this page
        for (int i = startIndex; i < endIndex; i++) {
            SkillInfo skill = skills.get(i);
            displaySkillInfo(player, skill);
        }

        // Display footer with navigation
        player.sendMessage(ChatColor.GOLD + "=====================================");

        StringBuilder navigationMsg = new StringBuilder(ChatColor.YELLOW.toString());
        if (pageNumber > 1) {
            navigationMsg.append("/skill list ").append(pageNumber - 1).append(" ◀ ");
        }

        navigationMsg.append("Page ").append(pageNumber).append("/").append(totalPages);

        if (pageNumber < totalPages) {
            navigationMsg.append(" ▶ /skill list ").append(pageNumber + 1);
        }

        player.sendMessage(navigationMsg.toString());
    }

    /**
     * Gets all skills for a given class, sorted by level requirement
     */
    private List<SkillInfo> getAllSkills(GameClass gameClass, int playerLevel) {
        List<SkillInfo> skills = new ArrayList<>();

        for (Map.Entry<String, ConfigurationSection> entry : gameClass.getAllSkillConfigs().entrySet()) {
            String skillName = entry.getKey();
            ConfigurationSection skillConfig = entry.getValue();

            int requiredLevel = skillConfig.getInt("unlockLevel", 1);
            String description = skillConfig.getString("description", "No description");
            double manaCost = skillConfig.getDouble("manacost", 0);
            double staminaCost = skillConfig.getDouble("staminacost", 0);
            double healthCost = skillConfig.getDouble("healthcost", 0);
            double rageCost = skillConfig.getDouble("ragecost", 0);
            double toxinCost = skillConfig.getDouble("toxincost", 0);
            long cooldown = skillConfig.getLong("cooldown", 0);
            boolean hasCastTime = skillConfig.getBoolean("hascasttime", false);
            double castTime = skillConfig.getDouble("casttime", 0);
            boolean isToggleable = skillConfig.getBoolean("istoggleableskill", false);
            boolean isMeleeSkill = skillConfig.getBoolean("ismeleeskill", false);
            double range = skillConfig.getDouble("range", skillConfig.getDouble("meleerange", 0));

            boolean unlocked = playerLevel >= requiredLevel;

            SkillInfo skillInfo = new SkillInfo(
                    skillName,
                    description,
                    requiredLevel,
                    manaCost,
                    staminaCost,
                    healthCost,
                    rageCost,
                    toxinCost,
                    cooldown,
                    hasCastTime,
                    castTime,
                    isToggleable,
                    isMeleeSkill,
                    range,
                    unlocked
            );

            skills.add(skillInfo);
        }

        // Sort by: 1. Unlocked status (unlocked first), 2. Level requirement
        skills.sort(Comparator.comparing(SkillInfo::isUnlocked).reversed()
                .thenComparing(SkillInfo::getRequiredLevel));

        return skills;
    }

    /**
     * Displays detailed information about a skill to the player
     */
    private void displaySkillInfo(Player player, SkillInfo skill) {
        // Status indicator and name
        String status = skill.isUnlocked()
                ? ChatColor.GREEN + "[Unlocked] "
                : ChatColor.RED + "[Req. Level " + skill.getRequiredLevel() + "] ";

        player.sendMessage(status + ChatColor.WHITE + skill.getName());

        // Description with indentation
        player.sendMessage(ChatColor.GRAY + "  " + skill.getDescription());

        // Costs and properties
        StringBuilder details = new StringBuilder(ChatColor.GRAY + "  ");

        // Add costs
        if (skill.getManaCost() > 0) details.append("Mana: ").append(skill.getManaCost()).append("  ");
        if (skill.getStaminaCost() > 0) details.append("Stamina: ").append(skill.getStaminaCost()).append("  ");
        if (skill.getHealthCost() > 0) details.append("Health: ").append(skill.getHealthCost()).append("  ");
        if (skill.getRageCost() > 0) details.append("Rage: ").append(skill.getRageCost()).append("  ");
        if (skill.getToxinCost() > 0) details.append("Toxin: ").append(skill.getToxinCost()).append("  ");

        // Only show details if there are any costs
        if (details.length() > ChatColor.GRAY.toString().length() + 2) {
            player.sendMessage(details.toString());
        }

        // Properties with indentation
        StringBuilder properties = new StringBuilder(ChatColor.GRAY + "  ");

        // Add properties
        properties.append("CD: ").append(formatCooldown(skill.getCooldown())).append("s  ");

        if (skill.isHasCastTime()) {
            properties.append("Cast: ").append(skill.getCastTime()).append("s  ");
        }

        if (skill.isToggleable()) {
            properties.append("Toggle  ");
        }

        if (skill.isMeleeSkill()) {
            properties.append("Melee  ");
        }

        if (skill.getRange() > 0) {
            properties.append("Range: ").append(skill.getRange()).append("  ");
        }

        player.sendMessage(properties.toString());

        // Add separator between skills
        player.sendMessage("");
    }

    /**
     * Formats cooldown in seconds
     */
    private double formatCooldown(long cooldownSeconds) {
        // Simply return the seconds value directly, no conversion needed
        return cooldownSeconds;
    }

    /**
     * Inner class to hold skill information
     */
    private static class SkillInfo {
        private final String name;
        private final String description;
        private final int requiredLevel;
        private final double manaCost;
        private final double staminaCost;
        private final double healthCost;
        private final double rageCost;
        private final double toxinCost;
        private final long cooldown;
        private final boolean hasCastTime;
        private final double castTime;
        private final boolean isToggleable;
        private final boolean isMeleeSkill;
        private final double range;
        private final boolean unlocked;

        public SkillInfo(String name, String description, int requiredLevel,
                         double manaCost, double staminaCost, double healthCost,
                         double rageCost, double toxinCost, long cooldown,
                         boolean hasCastTime, double castTime, boolean isToggleable,
                         boolean isMeleeSkill, double range, boolean unlocked) {
            this.name = name;
            this.description = description;
            this.requiredLevel = requiredLevel;
            this.manaCost = manaCost;
            this.staminaCost = staminaCost;
            this.healthCost = healthCost;
            this.rageCost = rageCost;
            this.toxinCost = toxinCost;
            this.cooldown = cooldown;
            this.hasCastTime = hasCastTime;
            this.castTime = castTime;
            this.isToggleable = isToggleable;
            this.isMeleeSkill = isMeleeSkill;
            this.range = range;
            this.unlocked = unlocked;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getRequiredLevel() { return requiredLevel; }
        public double getManaCost() { return manaCost; }
        public double getStaminaCost() { return staminaCost; }
        public double getHealthCost() { return healthCost; }
        public double getRageCost() { return rageCost; }
        public double getToxinCost() { return toxinCost; }
        public long getCooldown() { return cooldown; }
        public boolean isHasCastTime() { return hasCastTime; }
        public double getCastTime() { return castTime; }
        public boolean isToggleable() { return isToggleable; }
        public boolean isMeleeSkill() { return isMeleeSkill; }
        public double getRange() { return range; }
        public boolean isUnlocked() { return unlocked; }
    }
}
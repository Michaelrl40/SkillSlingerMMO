package com.michael.mmorpg.models;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.managers.DamageDisplayManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {
    private final UUID playerId;
    private GameClass gameClass;
    private Map<String, ClassProgress> classProgress;  // Stores progress for all classes
    private Map<String, Boolean> masteredClasses;     // Tracks mastered classes

    // Resource properties
    private double currentHealth;
    private double currentMana;
    private double currentStamina;
    private double currentRage;
    private double currentToxin;
    private int coins;

    // Constants for resources
    private static final double MAX_RAGE = 100.0;
    private static final double MAX_TOXIN = 100.0;
    private static final double DEFAULT_HEALTH = 20.0;
    private static final double DEFAULT_MANA = 50.0;
    private static final double DEFAULT_STAMINA = 50.0;

    // Active skill tracking
    private Map<String, Double> activeSkillDrains;

    public PlayerData(UUID playerId) {
        this.playerId = playerId;
        this.classProgress = new HashMap<>();
        this.masteredClasses = new HashMap<>();
        this.currentHealth = DEFAULT_HEALTH;
        this.currentMana = DEFAULT_MANA;
        this.currentStamina = DEFAULT_STAMINA;
        this.currentRage = 0;
        this.currentToxin = 0;
        this.coins = 0;
        this.activeSkillDrains = new HashMap<>();
    }

    // Class Progress Management
    public static class ClassProgress {
        private int level;
        private double experience;
        private double totalExperience;

        public ClassProgress() {
            this.level = 1;
            this.experience = 0;
            this.totalExperience = 0;
        }

        // Getters and setters
        public int getLevel() { return level; }
        public void setLevel(int level) { this.level = level; }
        public double getExperience() { return experience; }
        public void setExperience(double experience) { this.experience = experience; }
        public double getTotalExperience() { return totalExperience; }
        public void setTotalExperience(double totalExperience) { this.totalExperience = totalExperience; }
    }

    public ClassProgress getProgressForClass(String className) {
        return classProgress.computeIfAbsent(className.toLowerCase(), k -> new ClassProgress());
    }

    public void setProgressForClass(String className, int level, double experience, double totalExperience) {
        ClassProgress progress = classProgress.computeIfAbsent(className.toLowerCase(), k -> new ClassProgress());
        progress.setLevel(level);
        progress.setExperience(experience);
        progress.setTotalExperience(totalExperience);

        // Check if class is mastered
        GameClass gameClass = MinecraftMMORPG.getInstance().getConfigManager().getClass(className.toLowerCase());
        if (gameClass != null && gameClass.isMasteryComplete(level, totalExperience)) {
            masteredClasses.put(className.toLowerCase(), true);
        }
    }

    public boolean hasMasteredClass(String className) {
        return masteredClasses.getOrDefault(className.toLowerCase(), false);
    }

    // Experience System
    public void addExperience(double exp) {
        if (!hasClass()) return;

        ClassProgress progress = getProgressForClass(gameClass.getName());
        if (isAtMaxLevel() && progress.getExperience() >= getRequiredExperience()) {
            return;
        }
        progress.setExperience(progress.getExperience() + exp);
        progress.setTotalExperience(progress.getTotalExperience() + exp);

        while (checkLevelUp()) {
            // Level up handled in checkLevelUp()
        }

        updateExpBar();
        checkMastery();
    }

    private boolean checkLevelUp() {
        if (!hasClass() || isAtMaxLevel()) return false;

        ClassProgress progress = getProgressForClass(gameClass.getName());
        if (progress.getExperience() >= getRequiredExperience()) {
            progress.setLevel(progress.getLevel() + 1);
            progress.setExperience(progress.getExperience() - getRequiredExperience());
            handleLevelUp();
            return true;
        }
        return false;
    }

    private void handleLevelUp() {
        resetResources();
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            MinecraftMMORPG.getInstance().getLevelCelebrationManager()
                    .celebrateLevelUp(player, getLevel(), gameClass.getName());
        }
    }

    private void checkMastery() {
        if (!hasClass()) return;

        ClassProgress progress = getProgressForClass(gameClass.getName());
        if (gameClass.isMasteryComplete(progress.getLevel(), progress.getTotalExperience())) {
            masteredClasses.put(gameClass.getName().toLowerCase(), true);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                MinecraftMMORPG.getInstance().getLevelCelebrationManager()
                        .celebrateMastery(player, gameClass.getName());

                // Add title award code here
                String titleId = gameClass.getName().toLowerCase() + "_master";
                if (MinecraftMMORPG.getInstance().getTitleManager().getTitle(titleId) != null) {
                    boolean awarded = MinecraftMMORPG.getInstance().getTitleManager().awardTitle(playerId, titleId);
                    if (awarded) {
                        player.sendMessage("§6You've been awarded the title: " +
                                MinecraftMMORPG.getInstance().getTitleManager().getTitle(titleId).getFormattedTitle());
                        player.sendMessage("§7Use /title set " + titleId + " to display it.");
                    }
                }
            }
        }
    }

    // Resource Management
    public void resetResources() {
        if (gameClass != null) {
            this.currentHealth = gameClass.getMaxHealth(getLevel());
            this.currentMana = gameClass.getMaxMana(getLevel());
            this.currentStamina = gameClass.getMaxStamina(getLevel());
        }
        updatePlayerHealth();
    }

    private void updatePlayerHealth() {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.setHealthScale(20.0);
            player.setHealthScaled(true);
            player.setMaxHealth(getMaxHealth());
            player.setHealth(currentHealth);
        }
    }

    // Resource Regeneration Methods
    public void regenHealth(double amount) {
        double oldHealth = currentHealth;
        setCurrentHealth(Math.min(currentHealth + amount, getMaxHealth()));
        double actualHealing = currentHealth - oldHealth;

        if (actualHealing > 0) {
            showHealingEffect(actualHealing);
        }
    }

    public void regenMana(double amount) {
        setCurrentMana(Math.min(currentMana + amount, getMaxMana()));
    }

    public void regenStamina(double amount) {
        setCurrentStamina(Math.min(currentStamina + amount, getMaxStamina()));
    }

    // Combat and Damage
    public void takeDamage(double amount) {
        setCurrentHealth(Math.max(0, currentHealth - amount));
    }

    public void heal(double amount) {
        double oldHealth = currentHealth;
        setCurrentHealth(Math.min(currentHealth + amount, getMaxHealth()));
        showHealingEffect(currentHealth - oldHealth);
    }

    private void showHealingEffect(double amount) {
        if (amount <= 0) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            MinecraftMMORPG.getInstance().getDamageDisplayManager().spawnDamageDisplay(
                    player.getLocation(),
                    amount,
                    DamageDisplayManager.DamageType.HEALING
            );

            Location healLoc = player.getLocation().add(0, 1, 0);
            player.getWorld().spawnParticle(
                    Particle.HEART,
                    healLoc,
                    1, 0.2, 0.2, 0.2, 0
            );
        }
    }

    // Resource Usage Methods
    public boolean useMana(double amount) {
        if (currentMana >= amount) {
            currentMana -= amount;
            return true;
        }
        return false;
    }

    public boolean useStamina(double amount) {
        if (currentStamina >= amount) {
            currentStamina -= amount;
            return true;
        }
        return false;
    }

    public boolean useRage(double amount) {
        if (currentRage >= amount) {
            currentRage = Math.max(0, currentRage - amount);
            return true;
        }
        return false;
    }

    public void addRage(double damageDealt) {
        this.currentRage = Math.min(currentRage + damageDealt, MAX_RAGE);
    }

    public void addMana(double amount) {
        setCurrentMana(Math.min(currentMana + amount, getMaxMana()));

    }

    public boolean useToxin(double amount) {
        if (currentToxin >= amount) {
            currentToxin -= amount;
            return true;
        }
        return false;
    }



    // Class Progress Map Access
    public Map<String, ClassProgress> getClassProgress() {
        return new HashMap<>(classProgress); // Return a copy to prevent direct modification
    }

    public Map<String, Boolean> getMasteredClasses() {
        return new HashMap<>(masteredClasses); // Return a copy to prevent direct modification
    }

    // UI Updates
    private void updateExpBar() {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && hasClass()) {
            ClassProgress progress = getProgressForClass(gameClass.getName());
            player.setLevel(progress.getLevel());
            double progressRatio = progress.getExperience() / getRequiredExperience();
            player.setExp((float) Math.min(1.0, Math.max(0.0, progressRatio)));
        }
    }

    // Getters and Setters
    public UUID getPlayerId() { return playerId; }
    public GameClass getGameClass() { return gameClass; }
    public int getLevel() {
        if (!hasClass()) return 1;
        return getProgressForClass(gameClass.getName()).getLevel();
    }

    public double getExperience() {
        if (!hasClass()) return 0;
        return getProgressForClass(gameClass.getName()).getExperience();
    }

    public double getTotalExperience() {
        if (!hasClass()) return 0;
        return getProgressForClass(gameClass.getName()).getTotalExperience();
    }
    public double getCurrentHealth() { return currentHealth; }
    public double getCurrentMana() { return currentMana; }
    public double getCurrentStamina() { return currentStamina; }
    public double getCurrentRage() { return currentRage; }
    public double getCurrentToxin() { return currentToxin; }


    public double getMaxHealth() { return gameClass != null ? gameClass.getMaxHealth(getLevel()) : DEFAULT_HEALTH; }
    public double getMaxMana() { return gameClass != null ? gameClass.getMaxMana(getLevel()) : DEFAULT_MANA; }
    public double getMaxStamina() { return gameClass != null ? gameClass.getMaxStamina(getLevel()) : DEFAULT_STAMINA; }
    public double getMaxRage() { return MAX_RAGE; }
    public double getMaxToxin() { return MAX_TOXIN; }

    public void setGameClass(GameClass newClass) {
        if (newClass == null) return;
        this.gameClass = newClass;
        resetResources();
        updateExpBar();
    }

    public void setCurrentHealth(double health) {
        this.currentHealth = Math.min(Math.max(0, health), getMaxHealth());
        updatePlayerHealth();
    }
    public void setCurrentMana(double mana) { this.currentMana = Math.min(mana, getMaxMana()); }
    public void setCurrentStamina(double stamina) { this.currentStamina = Math.min(stamina, getMaxStamina()); }
    public void setCurrentRage(double rage) { this.currentRage = Math.min(Math.max(0, rage), MAX_RAGE); }
    public void setCurrentToxin(double toxin) { this.currentToxin = Math.min(Math.max(0, toxin), MAX_TOXIN); }




    // State Checks
    public boolean hasClass() { return gameClass != null; }
    public boolean isCurrentClass(String className) {
        return gameClass != null && gameClass.getName().equalsIgnoreCase(className);
    }
    public boolean isAtMaxLevel() {
        return hasClass() && getLevel() >= gameClass.getMaxLevelForTier();
    }

    // Resource Drain Processing
    public void processDrains() {
        for (Map.Entry<String, Double> drain : activeSkillDrains.entrySet()) {
            double amount = drain.getValue();
            if (currentToxin >= amount) {
                currentToxin -= amount;
            } else {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage("§c✦ Not enough toxin to maintain " + drain.getKey());
                }
                removeToxinDrain(drain.getKey());
            }
        }
    }

    //Econ Getters
    public int getCoins() {
        return coins;
    }

    public void setCoins(int coins) {
        this.coins = coins;
    }

    public void addCoins(int amount) {
        this.coins += amount;
    }

    public boolean removeCoins(int amount) {
        if (coins >= amount) {
            coins -= amount;
            return true;
        }
        return false;
    }

    public boolean hasEnoughCoins(int amount) {
        return coins >= amount;
    }

    // Active Skills Management
    public void addToxinDrain(String skillName, double drainPerTick) {
        activeSkillDrains.put(skillName.toLowerCase(), drainPerTick);
    }

    public void removeToxinDrain(String skillName) {
        activeSkillDrains.remove(skillName.toLowerCase());
    }

    public void clearActiveSkills() {
        activeSkillDrains.clear();
    }

    public Map<String, Double> getActiveSkills() {
        return new HashMap<>(activeSkillDrains);
    }

    public double getRequiredExperience() {
        int level = getLevel();
        if (level <= 20) {
            return 50 * Math.pow(1.2, level - 1);
        } else if (level <= 50) {
            return 75 * Math.pow(1.15, level - 1);
        } else {
            return 100 * Math.pow(1.1, level - 1);
        }
    }

    // Resource Regeneration
    public void processRegeneration() {
        if (gameClass != null) {
            regenMana(getMaxMana() * gameClass.getManaRegenPercent());
            regenStamina(getMaxStamina() * gameClass.getStaminaRegenPercent());
        }
    }
}
package com.michael.mmorpg.models;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static com.michael.mmorpg.skills.Skill.plugin;

public class GameClass {
    // Basic class attributes
    protected final String name;
    protected final ConfigurationSection config;
    protected final double baseHealth;
    protected final double baseStamina;
    protected final double baseMana;
    protected final double healthPerLevel;
    protected final double staminaPerLevel;
    protected final double manaPerLevel;
    protected final List<String> allowedArmor;
    protected final List<String> allowedWeapons;
    protected final Map<String, ConfigurationSection> skills;
    protected final Map<Integer, String> unlockedSkillsByLevel;
    protected final double healthRegenPercent;
    protected final double manaRegenPercent;
    protected final double staminaRegenPercent;
    protected final List<String> validSwitchClasses;

    //ACTIONBAR CONFIG
    protected final boolean usesMana;
    protected final boolean usesStamina;
    protected final boolean usesToxin;
    protected final boolean usesRage;

    // Progression system fields
    protected final ClassTier tier;
    protected final String parentClass;
    protected final int requiredLevel;
    protected final int masteryLevel;
    protected final double masteryRequiredExp;

    // Configurable level ranges with default values
    private static int BASE_MIN_LEVEL = 1;
    private static int BASE_MAX_LEVEL = 1;
    private static int SECONDARY_MIN_LEVEL = 1;
    private static int SECONDARY_MAX_LEVEL = 10;
    private static int MASTER_MIN_LEVEL = 1;
    private static int MASTER_MAX_LEVEL = 50;

    public enum ClassTier {
        BASE,
        SECONDARY,
        MASTER
    }

    /**
     * Initializes level ranges from config. Call this when plugin starts.
     */
    public static void initializeLevelRanges(ConfigurationSection config) {
        ConfigurationSection tiers = config.getConfigurationSection("class-tiers");
        if (tiers != null) {
            ConfigurationSection baseConfig = tiers.getConfigurationSection("base");
            if (baseConfig != null) {
                BASE_MIN_LEVEL = baseConfig.getInt("min-level", 1);
                BASE_MAX_LEVEL = baseConfig.getInt("max-level", 20);
            }

            ConfigurationSection secondaryConfig = tiers.getConfigurationSection("secondary");
            if (secondaryConfig != null) {
                SECONDARY_MIN_LEVEL = secondaryConfig.getInt("min-level", 20);
                SECONDARY_MAX_LEVEL = secondaryConfig.getInt("max-level", 50);
            }

            ConfigurationSection masterConfig = tiers.getConfigurationSection("master");
            if (masterConfig != null) {
                MASTER_MIN_LEVEL = masterConfig.getInt("min-level", 50);
                MASTER_MAX_LEVEL = masterConfig.getInt("max-level", 70);
            }
        }
    }

    /**
     * Creates a new GameClass with the specified configuration.
     */
    public GameClass(ConfigurationSection config) {
        this.config = config;
        this.name = config.getName();

        // Initialize base stats
        this.baseHealth = config.getDouble("baseHealth");
        this.baseStamina = config.getDouble("baseStamina");
        this.baseMana = config.getDouble("baseMana");
        this.healthPerLevel = config.getDouble("healthPerLevel");
        this.staminaPerLevel = config.getDouble("staminaPerLevel");
        this.manaPerLevel = config.getDouble("manaPerLevel");

        // For class action bars
        this.usesMana = config.getBoolean("usesMana", false);
        this.usesStamina = config.getBoolean("usesStamina", false);
        this.usesToxin = config.getBoolean("usesToxin", false);
        this.usesRage = config.getBoolean("usesRage", false);

        // Initialize equipment lists
        this.allowedArmor = config.getStringList("allowedArmor");
        this.allowedWeapons = config.getStringList("allowedWeapons");

        // Initialize skills
        this.skills = loadSkills(config.getConfigurationSection("skills"));
        this.unlockedSkillsByLevel = new HashMap<>();
        mapSkillsToLevels();

        // Initialize regeneration rates
        this.healthRegenPercent = config.getDouble("healthRegenPercent", 0.0);
        this.manaRegenPercent = config.getDouble("manaRegenPercent", 0.0);
        this.staminaRegenPercent = config.getDouble("staminaRegenPercent", 0.0);


        // Initialize progression attributes
        String tierStr = config.getString("tier", "BASE");
        this.tier = ClassTier.valueOf(tierStr.toUpperCase());
        this.parentClass = config.getString("parentClass", null);
        this.requiredLevel = config.getInt("requiredLevel", getMinLevelForTier());

        // Set mastery level based on tier
        this.masteryLevel = switch (tier) {
            case BASE -> config.getInt("masteryLevel", BASE_MAX_LEVEL);
            case SECONDARY -> config.getInt("masteryLevel", SECONDARY_MAX_LEVEL);
            case MASTER -> config.getInt("masteryLevel", MASTER_MAX_LEVEL);
        };

        this.validSwitchClasses = config.getStringList("validSwitchClasses") != null ?
                config.getStringList("validSwitchClasses") : new ArrayList<>();

        this.masteryRequiredExp = config.getDouble("masteryRequiredExp", 100000);
    }

    public boolean canSwitchTo(String className) {
        return validSwitchClasses.contains(className);
    }


    // Skill management methods
    private Map<String, ConfigurationSection> loadSkills(ConfigurationSection skillsConfig) {
        Map<String, ConfigurationSection> loadedSkills = new HashMap<>();
        if (skillsConfig != null) {
            for (String skillName : skillsConfig.getKeys(false)) {
                ConfigurationSection skillSection = skillsConfig.getConfigurationSection(skillName);
                if (skillSection != null) {
                    loadedSkills.put(skillName.toLowerCase(), skillSection);
                }
            }
        }
        return loadedSkills;
    }

    private void mapSkillsToLevels() {
        skills.forEach((name, skillConfig) -> {
            int unlockLevel = skillConfig.getInt("unlockLevel", 1);
            unlockedSkillsByLevel.put(unlockLevel, name);
        });
    }

    public int getMinLevelForTier() {
        return switch (tier) {
            case BASE -> BASE_MIN_LEVEL;
            case SECONDARY -> SECONDARY_MIN_LEVEL;
            case MASTER -> MASTER_MIN_LEVEL;
        };
    }

    public int getMaxLevelForTier() {
        return switch (tier) {
            case BASE -> BASE_MAX_LEVEL;
            case SECONDARY -> SECONDARY_MAX_LEVEL;
            case MASTER -> MASTER_MAX_LEVEL;
        };
    }


    // Class progression methods
    public boolean isMasteryComplete(int level, double totalExp) {
        return level >= masteryLevel && totalExp >= masteryRequiredExp;
    }

    // Getters for base attributes
    public String getName() {
        return name;
    }

    public List<String> getAllowedArmor() {
        return allowedArmor;
    }

    public List<String> getAllowedWeapons() {
        return plugin.getConfig().getStringList("classes." + name + ".allowedWeapons");
    }

    // Skill-related getters
    public ConfigurationSection getSkillConfig(String skillName) {
        return skills.get(skillName.toLowerCase());
    }

    public Map<String, ConfigurationSection> getAllSkillConfigs() {
        return skills;
    }

    public Map<Integer, String> getUnlockedSkillsByLevel() {
        return unlockedSkillsByLevel;
    }

    // Resource calculation getters
    public double getMaxHealth(int level) {
        return baseHealth + (healthPerLevel * (level - 1));
    }

    public double getMaxStamina(int level) {
        return baseStamina + (staminaPerLevel * (level - 1));
    }

    public double getMaxMana(int level) {
        return baseMana + (manaPerLevel * (level - 1));
    }

    public double getManaRegenPercent() {
        return manaRegenPercent;
    }

    public double getStaminaRegenPercent() {
        return staminaRegenPercent;
    }

    public List<String> getValidSwitchClasses() {
        return validSwitchClasses;
    }


    // Progression system getters
    public ClassTier getTier() {
        return tier;
    }

    public String getParentClass() {
        return parentClass;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public int getMasteryLevel() {
        return masteryLevel;
    }

    public double getMasteryRequiredExp() {
        return masteryRequiredExp;
    }



    // Static level range getters
    public static int getBaseMinLevel() { return BASE_MIN_LEVEL; }
    public static int getBaseMaxLevel() { return BASE_MAX_LEVEL; }
    public static int getSecondaryMinLevel() { return SECONDARY_MIN_LEVEL; }
    public static int getSecondaryMaxLevel() { return SECONDARY_MAX_LEVEL; }
    public static int getMasterMinLevel() { return MASTER_MIN_LEVEL; }
    public static int getMasterMaxLevel() { return MASTER_MAX_LEVEL; }
    public boolean usesMana() { return usesMana; }
    public boolean usesStamina() { return usesStamina; }
    public boolean usesToxin() { return usesToxin; }
    public boolean usesRage() { return usesRage; }
}
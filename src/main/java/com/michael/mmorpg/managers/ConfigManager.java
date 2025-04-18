package com.michael.mmorpg.managers;

import com.michael.mmorpg.models.classes.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.GameClass;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final MinecraftMMORPG plugin;
    private FileConfiguration config;
    private final Map<String, GameClass> classConfigurations;

    public ConfigManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.classConfigurations = new HashMap<>();
        loadConfig();
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        loadClasses();
    }

    private void loadClasses() {
        ConfigurationSection classesSection = config.getConfigurationSection("classes");
        if (classesSection == null) return;

        for (String className : classesSection.getKeys(false)) {
            ConfigurationSection classConfig = classesSection.getConfigurationSection(className);
            if (classConfig != null) {
                GameClass gameClass;

                // Create specific class instances based on class name
                switch (className.toLowerCase()) {
                    case "Warrior":
                        gameClass = new Warrior(classConfig);
                        break;
                    case "Mage":
                        gameClass = new Mage(classConfig);
                        break;
                    case "Thief":
                        gameClass = new Thief(classConfig);
                        break;
                    case "frostmage":  // Added proper case handling
                        gameClass = new FrostMage(classConfig);
                        break;
                    case "druid":      // Added Druid case
                        gameClass = new Druid(classConfig);
                        break;
                    case "Charmer":
                        gameClass = new Charmer(classConfig);
                        break;
                    case "WindWaker":
                        gameClass = new WindWaker(classConfig);
                        break;
                    case "Toxicologist":
                        gameClass = new Toxicologist(classConfig);
                        break;
                    case "Chainwarden":
                        gameClass = new Chainwarden(classConfig);
                        break;
                    case "hunter":
                        gameClass = new Hunter(classConfig);
                        break;
                    case "Priest":
                        gameClass = new Priest(classConfig);
                    case "Engineer":
                        gameClass = new Engineer(classConfig);
                        break;
                    case "Renegade":
                        gameClass = new Renegade(classConfig);
                        break;
                    case "Skyknight":
                        gameClass = new Skyknight(classConfig);
                        break;
                    case "Bard":
                        gameClass = new Bard(classConfig);
                        break;
                    case "Buccaneer":
                        gameClass = new Buccaneer(classConfig);
                        break;
                    case "Bandolier":
                        gameClass = new Bandolier(classConfig);
                        break;
                    case "Berserker":
                        gameClass = new Berserker(classConfig);
                        break;
                    case "Ninja":
                        gameClass = new Ninja(classConfig);
                        break;
                    case "ElementalRanger":
                        gameClass = new ElementalRanger(classConfig);
                        break;
                    case "Chronomancer":
                        gameClass = new Chronomancer(classConfig);
                        break;
                    case "DarkBlade":
                        gameClass = new DarkBlade(classConfig);
                        break;
                    case "Guardian":
                        gameClass = new Guardian(classConfig);
                        break;
                    case "Vagrant":
                        gameClass = new Vagrant(classConfig);
                        break;
                    default:
                        gameClass = new GameClass(classConfig);
                        break;
                }

                classConfigurations.put(className.toLowerCase(), gameClass);
                plugin.getLogger().info("Loaded class: " + className);
            }
        }
    }

    public GameClass getClass(String className) {
        GameClass result = classConfigurations.get(className.toLowerCase());
        if (result == null) {
            plugin.getLogger().warning("Failed to find class: " + className);
        }
        return result;
    }

    public Map<String, GameClass> getClasses() {
        return classConfigurations;
    }

    public int getMobExp(String mobType) {
        return config.getInt("experience.mobExp." + mobType, 0);
    }

    // Add method to reload configuration
    public void reloadConfig() {
        loadConfig();
    }

    // Add method to save configuration
    public void saveConfig() {
        plugin.saveConfig();
    }


}
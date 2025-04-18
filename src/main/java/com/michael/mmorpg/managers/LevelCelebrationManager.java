package com.michael.mmorpg.managers;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import com.michael.mmorpg.MinecraftMMORPG;

public class LevelCelebrationManager {
    private final MinecraftMMORPG plugin;

    public LevelCelebrationManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    public void celebrateLevelUp(Player player, int newLevel, String className) {
        // Create a simple firework effect for regular level-ups
        Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
        FireworkMeta fwm = fw.getFireworkMeta();

        // Regular level-up firework - single burst with class-colored effect
        FireworkEffect effect = FireworkEffect.builder()
                .withColor(getClassColor(className))
                .with(FireworkEffect.Type.BURST)
                .trail(true)
                .build();

        fwm.addEffect(effect);
        fwm.setPower(1);
        fw.setFireworkMeta(fwm);

        // Send level-up message
        player.sendMessage("§6§l=============================");
        player.sendMessage("§a§lLEVEL UP! §e" + (newLevel - 1) + " → " + newLevel);
        player.sendMessage("§7Class: §f" + className);
        player.sendMessage("§6§l=============================");

        // Play celebratory sound
        player.playSound(player.getLocation(), "entity.player.levelup", 1.0f, 1.0f);
    }

    public void celebrateMastery(Player player, String className) {
        // Create multiple impressive fireworks for mastery
        Location loc = player.getLocation();

        // Launch several fireworks in sequence
        for (int i = 0; i < 3; i++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                Firework fw = player.getWorld().spawn(loc, Firework.class);
                FireworkMeta fwm = fw.getFireworkMeta();

                // Create an impressive combination effect
                FireworkEffect effect = FireworkEffect.builder()
                        .withColor(getClassColor(className))
                        .withFade(Color.WHITE)
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .trail(true)
                        .withFlicker()
                        .build();

                fwm.addEffect(effect);
                fwm.setPower(2);
                fw.setFireworkMeta(fwm);
            }, i * 5L); // Stagger the fireworks
        }

        // Send mastery achievement message
        player.sendMessage("§6§l================================");
        player.sendMessage("§b§lCLASS MASTERY ACHIEVED!");
        player.sendMessage("§e§lCongratulations!");
        player.sendMessage("§fYou have mastered the " + className + " class!");
        player.sendMessage("§6§l================================");

        // Play epic sound combination
        player.playSound(player.getLocation(), "ui.toast.challenge_complete", 1.0f, 1.0f);
        player.playSound(player.getLocation(), "entity.ender_dragon.death", 0.5f, 1.0f);
    }

    private Color getClassColor(String className) {
        // You can customize colors for different classes
        return switch (className.toLowerCase()) {
            case "warrior" -> Color.RED;
            case "mage" -> Color.BLUE;
            case "rogue" -> Color.GREEN;
            case "druid" -> Color.fromRGB(139, 69, 19); // Brown
            default -> Color.WHITE;
        };
    }
}
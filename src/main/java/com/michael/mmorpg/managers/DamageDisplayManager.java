package com.michael.mmorpg.managers;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.Random;

public class DamageDisplayManager {
    private final MinecraftMMORPG plugin;
    private final Random random = new Random();

    public DamageDisplayManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    public void spawnDamageDisplay(Location location, double damage, DamageType type) {
        // Add slight random offset to base location to prevent perfect stacking
        double randomOffset = (random.nextDouble() - 0.5) * 0.3;
        Location spawnLoc = location.clone().add(getTypeOffset(type)).add(randomOffset, 1, randomOffset);

        // Create text display entity
        TextDisplay display = location.getWorld().spawn(spawnLoc, TextDisplay.class);

        // Set the text with color based on damage type
        String text = formatDamageText(damage, type);
        display.setText(text);

        // Configure display settings
        display.setBillboard(Display.Billboard.CENTER);
        display.setVisibleByDefault(true);

        // Add motion based on damage type
        animateDisplay(display, type);
    }


    private Vector getTypeOffset(DamageType type) {
        return switch (type) {
            case NORMAL -> new Vector(0.7, 0, 0);     // Physical damage appears to the right
            case MAGIC -> new Vector(-0.7, 0, 0);     // Magic damage appears to the left
            case CRITICAL -> new Vector(0, 0, 0.7);   // Critical hits appear forward
            case HEALING -> new Vector(0, 0, -0.7);   // Healing appears behind
            case TRUE -> new Vector(0, 0.7, 0);       // True damage appears above
        };
    }

    private void animateDisplay(TextDisplay display, DamageType type) {
        // Create unique motion pattern for each damage type
        Vector baseVelocity = getTypeVelocity(type);

        new BukkitRunnable() {
            private int ticks = 0;
            private final Vector velocity = baseVelocity.clone();

            @Override
            public void run() {
                if (ticks >= 40 || display.isDead()) {
                    display.remove();
                    this.cancel();
                    return;
                }

                // Update position with custom motion
                display.teleport(display.getLocation().add(velocity));

                // Adjust velocity based on damage type
                switch (type) {
                    case NORMAL:
                        // Arcing motion for physical damage
                        velocity.setY(velocity.getY() - 0.003);
                        break;
                    case MAGIC:
                        // Floating motion for magic damage
                        velocity.setY(velocity.getY() * 0.95);
                        break;
                    case CRITICAL:
                        // Quick upward burst for crits
                        velocity.setY(velocity.getY() * 0.9);
                        break;
                    case HEALING:
                        // Gentle rising motion for healing
                        velocity.setY(velocity.getY() * 0.98);
                        break;
                }

                // Fade out gradually
                display.setViewRange((1 - (ticks / 40.0f)) * 32);

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Vector getTypeVelocity(DamageType type) {
        return switch (type) {
            case NORMAL -> new Vector(0.02, 0.15, 0);     // Arc right and up
            case MAGIC -> new Vector(-0.02, 0.08, 0);     // Float left and up slowly
            case CRITICAL -> new Vector(0, 0.2, 0.02);    // Burst straight up and slightly forward
            case HEALING -> new Vector(0, 0.05, -0.02);   // Gentle rise and drift backward
            case TRUE -> new Vector(0, 0.12, 0);          // Strong upward motion for true damage
        };
    }

    private String formatDamageText(double damage, DamageType type) {
        String formattedDamage = String.format("%.1f", damage);
        return switch (type) {
            case CRITICAL -> "§6✦ " + formattedDamage;      // Gold with star for crits
            case MAGIC -> "§d❋ " + formattedDamage;         // Light purple with magic symbol
            case HEALING -> "§a❤ +" + formattedDamage;      // Green with heart for healing
            case TRUE -> "§f⚡ " + formattedDamage;          // White with lightning bolt for true damage
            default -> "§c" + formattedDamage;              // Red for normal damage
        };
    }

    public enum DamageType {
        NORMAL,
        CRITICAL,
        MAGIC,
        HEALING,
        TRUE,
    }
}
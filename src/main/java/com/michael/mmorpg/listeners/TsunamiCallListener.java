package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.elementalranger.TsunamiCallSkill;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class TsunamiCallListener implements Listener {
    private final MinecraftMMORPG plugin;

    public TsunamiCallListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onArrowLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getEntity();
        if (!(arrow.getShooter() instanceof Player)) return;

        Player shooter = (Player) arrow.getShooter();
        if (shooter.hasMetadata("tsunami_ready")) {
            // Mark the arrow for tsunami creation
            arrow.setMetadata("tsunami_arrow", new FixedMetadataValue(plugin, true));
            // Remove the ready state from player
            shooter.removeMetadata("tsunami_ready", plugin);

            // Add particle trail to arrow
            arrow.setGlowing(true);
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getEntity();

        if (arrow.hasMetadata("tsunami_arrow")) {
            if (!(arrow.getShooter() instanceof Player)) return;
            Player shooter = (Player) arrow.getShooter();

            // Get arrow's direction for the wave
            TsunamiCallSkill skill = (TsunamiCallSkill) plugin.getSkillManager()
                    .getSkillInstance("tsunamicall");

            if (skill != null) {
                // Create wave in arrow's direction
                skill.createWave(
                        arrow.getLocation(),
                        arrow.getVelocity().normalize(),
                        shooter
                );

                // Remove the arrow
                arrow.remove();
            }
        }
    }
}
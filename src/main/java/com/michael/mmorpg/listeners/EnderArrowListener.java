package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.elementalranger.EnderArrowSkill;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class EnderArrowListener implements Listener {
    private final MinecraftMMORPG plugin;

    public EnderArrowListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onArrowLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getEntity();
        if (!(arrow.getShooter() instanceof Player)) return;

        Player shooter = (Player) arrow.getShooter();
        if (shooter.hasMetadata("ender_arrow_ready")) {
            // Mark the arrow
            arrow.setMetadata("ender_arrow", new FixedMetadataValue(plugin, true));
            shooter.removeMetadata("ender_arrow_ready", plugin);

            // Make arrow glow purple
            arrow.setGlowing(true);

            // Add particle trail to arrow
            new org.bukkit.scheduler.BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (!arrow.isValid() || arrow.isDead() || ticks++ > 100) {
                        this.cancel();
                        return;
                    }

                    arrow.getWorld().spawnParticle(
                            Particle.PORTAL,
                            arrow.getLocation(),
                            1, 0, 0, 0, 0
                    );
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getEntity();

        if (arrow.hasMetadata("ender_arrow")) {
            if (!(arrow.getShooter() instanceof Player)) return;
            Player shooter = (Player) arrow.getShooter();

            EnderArrowSkill skill = (EnderArrowSkill) plugin.getSkillManager()
                    .getSkillInstance("enderarrow");

            if (skill != null) {
                skill.teleportPlayer(shooter, arrow.getLocation());
            }

            // Remove the arrow
            arrow.remove();
        }
    }
}
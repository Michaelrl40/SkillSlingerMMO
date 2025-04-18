package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.elementalranger.NetherShotSkill;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class NetherShotListener implements Listener {
    private final MinecraftMMORPG plugin;

    public NetherShotListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onArrowLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getEntity();
        if (!(arrow.getShooter() instanceof Player)) return;

        Player shooter = (Player) arrow.getShooter();
        if (shooter.hasMetadata("nether_shot_ready")) {
            // Mark the arrow
            arrow.setMetadata("nether_shot", new FixedMetadataValue(plugin, true));
            shooter.removeMetadata("nether_shot_ready", plugin);

            // Make arrow glow red
            arrow.setGlowing(true);

            // Add fire trail to arrow
            arrow.setFireTicks(200);
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getEntity();

        if (arrow.hasMetadata("nether_shot")) {
            if (!(arrow.getShooter() instanceof Player)) return;
            Player shooter = (Player) arrow.getShooter();

            NetherShotSkill skill = (NetherShotSkill) plugin.getSkillManager()
                    .getSkillInstance("nethershot");

            if (skill != null) {
                skill.createExplosion(arrow.getLocation(), shooter);
            }

            // Remove the arrow
            arrow.remove();
        }
    }
}
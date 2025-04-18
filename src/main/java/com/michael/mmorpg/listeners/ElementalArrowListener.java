package com.michael.mmorpg.listeners;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.skills.elementalranger.EnderArrowSkill;
import com.michael.mmorpg.skills.elementalranger.NetherShotSkill;
import com.michael.mmorpg.skills.elementalranger.TsunamiCallSkill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class ElementalArrowListener implements Listener {
    private final MinecraftMMORPG plugin;

    public ElementalArrowListener(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArrowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Arrow)) return;

        Player player = (Player) event.getEntity();
        Arrow arrow = (Arrow) event.getProjectile();

        // Check for enhanced arrow
        if (player.hasMetadata("enhanced_arrow")) {
            String arrowType = player.getMetadata("enhanced_arrow").get(0).asString();

            // Set arrow metadata
            arrow.setMetadata("enhanced_arrow", new FixedMetadataValue(plugin, arrowType));

            // Add visual effects to arrow
            switch (arrowType) {
                case "Ender Arrow":
                    arrow.setGlowing(true);
                    // Add ender particles to arrow path
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (arrow.isDead() || arrow.isOnGround()) {
                                this.cancel();
                                return;
                            }
                            arrow.getWorld().spawnParticle(Particle.PORTAL, arrow.getLocation(), 1, 0, 0, 0, 0);
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                    break;

                case "Nether Shot":
                    arrow.setFireTicks(Integer.MAX_VALUE);
                    // Add fire particles to arrow path
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (arrow.isDead() || arrow.isOnGround()) {
                                this.cancel();
                                return;
                            }
                            arrow.getWorld().spawnParticle(Particle.FLAME, arrow.getLocation(), 1, 0, 0, 0, 0);
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                    break;

                case "Tsunami Call":
                    // Add water particles to arrow path
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (arrow.isDead() || arrow.isOnGround()) {
                                this.cancel();
                                return;
                            }
                            arrow.getWorld().spawnParticle(Particle.SPLASH, arrow.getLocation(), 2, 0, 0, 0, 0);
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                    break;
            }

            // Remove the enhanced arrow status from player
            player.removeMetadata("enhanced_arrow", plugin);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow)) return;
        Arrow arrow = (Arrow) event.getEntity();

        if (!arrow.hasMetadata("enhanced_arrow")) return;

        String arrowType = arrow.getMetadata("enhanced_arrow").get(0).asString();
        Player shooter = (arrow.getShooter() instanceof Player) ? (Player) arrow.getShooter() : null;

        if (shooter == null) return;

        switch (arrowType) {
            case "Ender Arrow":
                if (plugin.getSkillManager().getSkillInstance("EnderArrow") instanceof EnderArrowSkill enderArrow) {
                    Location targetLoc = arrow.getLocation();
                    // Ensure the location is safe
                    targetLoc.setY(targetLoc.getBlockY() + 1);
                    enderArrow.teleportPlayer(shooter, targetLoc);
                }
                break;

            case "Nether Shot":
                if (plugin.getSkillManager().getSkillInstance("NetherShot") instanceof NetherShotSkill netherShot) {
                    netherShot.createExplosion(arrow.getLocation(), shooter);
                }
                break;

            case "Tsunami Call":
                if (plugin.getSkillManager().getSkillInstance("TsunamiCall") instanceof TsunamiCallSkill tsunamiCall) {
                    Vector direction = arrow.getVelocity().normalize();
                    tsunamiCall.createWave(arrow.getLocation(), direction, shooter);
                }
                break;
        }

        // Remove the arrow
        arrow.remove();
    }
}
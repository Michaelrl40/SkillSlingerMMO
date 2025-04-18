package com.michael.mmorpg.skills.elementalranger;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class EnderArrowSkill extends Skill {
    private final double maxDistance;

    public EnderArrowSkill(ConfigurationSection config) {
        super(config);
        this.maxDistance = config.getDouble("maxdistance", 50.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Cancel if player doesn't have a bow
        if (!(player.getInventory().getItemInMainHand().getType() == Material.BOW ||
                player.getInventory().getItemInMainHand().getType() == Material.CROSSBOW)) {
            player.sendMessage("§c✦ You must be holding a bow or crossbow!");
            setSkillSuccess(false);
            return;
        }

        if (player.hasMetadata("enhanced_arrow")) {
            String activeSkill = player.getMetadata("enhanced_arrow").get(0).value().toString();
            player.sendMessage("§c✦ You already have " + activeSkill + " active!");
            setSkillSuccess(false);
            return;
        }

        // Mark the player as having ender arrow ready
        player.setMetadata("enhanced_arrow", new FixedMetadataValue(plugin, "Ender Arrow"));
        player.setMetadata("ender_arrow_ready", new FixedMetadataValue(plugin, true));

        // Visual effect to show skill is ready
        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (!player.hasMetadata("ender_arrow_ready") || ticks++ > 100) { // 5 second timeout
                    this.cancel();
                    if (player.hasMetadata("enhanced_arrow")) {
                        player.removeMetadata("enhanced_arrow", plugin);
                    }
                    return;
                }

                // Ender particle spiral effect around player
                Location loc = player.getLocation();
                angle += Math.PI / 8;
                double x = Math.cos(angle) * 0.5;
                double z = Math.sin(angle) * 0.5;
                loc.add(x, 1, z);

                player.getWorld().spawnParticle(
                        Particle.PORTAL,
                        loc,
                        1, 0, 0, 0, 0
                );

                loc.subtract(x, 1, z);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.sendMessage("§5✦ Ender Arrow ready! Shoot to teleport!");
        setSkillSuccess(true);
    }

    public void teleportPlayer(Player player, Location target) {

        // Check distance
        if (player.getLocation().distance(target) > maxDistance) {
            player.sendMessage("§c✦ Target location too far!");
            return;
        }

        // Departure effects
        Location departLoc = player.getLocation();
        departLoc.getWorld().spawnParticle(
                Particle.PORTAL,
                departLoc.add(0, 1, 0),
                50, 0.5, 1, 0.5, 0.1
        );
        departLoc.getWorld().playSound(
                departLoc,
                Sound.ENTITY_ENDERMAN_TELEPORT,
                1.0f,
                1.0f
        );

        // Teleport player
        player.teleport(target.add(0, 0.2, 0)); // Add slight offset to prevent stuck in ground

        // Arrival effects
        Location arriveLoc = player.getLocation();
        arriveLoc.getWorld().spawnParticle(
                Particle.PORTAL,
                arriveLoc.add(0, 1, 0),
                50, 0.5, 1, 0.5, 0.1
        );
        arriveLoc.getWorld().playSound(
                arriveLoc,
                Sound.ENTITY_ENDERMAN_TELEPORT,
                1.0f,
                1.2f
        );

        // Visual effect ring at arrival
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 16) {
            double x = Math.cos(angle) * 1;
            double z = Math.sin(angle) * 1;
            Location particleLoc = arriveLoc.clone().add(x, 0, z);

            arriveLoc.getWorld().spawnParticle(
                    Particle.REVERSE_PORTAL,
                    particleLoc,
                    1, 0, 0, 0, 0
            );
        }
    }

}
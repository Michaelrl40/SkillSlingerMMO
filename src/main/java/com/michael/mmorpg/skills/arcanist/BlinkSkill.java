package com.michael.mmorpg.skills.arcanist;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class BlinkSkill extends Skill {
    private final double distance;

    public BlinkSkill(ConfigurationSection config) {
        super(config);
        this.distance = config.getDouble("distance", 15.0);
    }

    @Override
    protected void performSkill(Player player) {
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection();
        Location target = null;

        // Ray trace to find first solid block
        for (double d = 0; d <= distance; d += 0.5) {
            Location checkLoc = start.clone().add(direction.clone().multiply(d));
            Block block = checkLoc.getBlock();

            if (!block.isPassable()) {
                // Found a solid block, set target to position just before it
                target = start.clone().add(direction.clone().multiply(Math.max(0, d - 0.5)));
                break;
            }
        }

        // If no block found, use maximum distance
        if (target == null) {
            target = start.clone().add(direction.multiply(distance));
        }

        // Maintain player's pitch and yaw
        target.setPitch(player.getLocation().getPitch());
        target.setYaw(player.getLocation().getYaw());

        // Teleport and effects
        player.teleport(target);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.DRAGON_BREATH,
                player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05);

        setSkillSuccess(true);
    }
}
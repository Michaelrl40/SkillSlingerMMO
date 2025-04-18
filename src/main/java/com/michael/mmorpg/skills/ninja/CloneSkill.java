package com.michael.mmorpg.skills.ninja;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.skills.ninja.ShadowClonesSkill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.joml.Random;

import java.util.List;

// Clone skill for teleporting
public class CloneSkill extends Skill {

    public CloneSkill(ConfigurationSection config) {
        super(config);
    }

    @Override
    protected void performSkill(Player player) {
        if (!player.hasMetadata("has_shadow_clones")) {
            player.sendMessage("§c✦ You need to create shadow clones first!");
            setSkillSuccess(false);
            return;
        }

        // Get all possible locations (clones + original position)
        List<Location> locations = ShadowClonesSkill.getCloneLocations(player);
        if (locations.isEmpty()) {
            setSkillSuccess(false);
            return;
        }

        // Pick random location
        Location targetLoc = locations.get(new Random().nextInt(locations.size()));

        // Teleport effects at start location
        player.getWorld().spawnParticle(
                Particle.SMOKE,
                player.getLocation().add(0, 1, 0),
                30, 0.2, 0.5, 0.2, 0.05
        );
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // Teleport player
        player.teleport(targetLoc);

        // Teleport effects at end location
        player.getWorld().spawnParticle(
                Particle.SMOKE,
                targetLoc.add(0, 1, 0),
                30, 0.2, 0.5, 0.2, 0.05
        );
        player.getWorld().playSound(targetLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

        setSkillSuccess(true);
    }
}
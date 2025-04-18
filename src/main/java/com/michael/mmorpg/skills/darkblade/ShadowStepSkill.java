package com.michael.mmorpg.skills.darkblade;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ShadowStepSkill extends Skill {
    private final double backstabDistance;

    public ShadowStepSkill(ConfigurationSection config) {
        super(config);
        this.backstabDistance = config.getDouble("backstabdistance", 1.0);
    }

    @Override
    public void execute(Player caster) {
        // Get target using built-in targeting
        currentTarget = getTargetEntity(caster, targetRange);

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No valid target in range!");
            return;
        }

        // Start casting if has cast time
        if (hasCastTime) {
            caster.sendMessage("§5✦ Stepping through shadows to " + currentTarget.getName() + "...");
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        LivingEntity target = currentTarget;

        // Play effect at start location
        playTeleportEffect(caster.getLocation());

        // Calculate teleport location behind target
        Location targetLoc = target.getLocation();
        Vector direction = targetLoc.getDirection().normalize();
        Location teleportLoc = targetLoc.clone().subtract(direction.multiply(backstabDistance));

        // Ensure safe teleport
        teleportLoc.setY(targetLoc.getY());
        teleportLoc.setPitch(targetLoc.getPitch());
        teleportLoc.setYaw(targetLoc.getYaw());

        // Teleport the player
        caster.teleport(teleportLoc);

        // Play effect at end location
        playTeleportEffect(teleportLoc);

        // Send messages
        caster.sendMessage("§5✦ You step through shadows behind " + target.getName() + "!");
        if (target instanceof Player) {
            ((Player)target).sendMessage("§5✦ " + caster.getName() + " appears behind you from the shadows!");
        }

        // Set cooldown - Added this line
        plugin.getSkillManager().setCooldown(caster, getName(), getCooldown());

        setSkillSuccess(true);
    }

    private void playTeleportEffect(Location location) {
        // Shadow particle effect
        location.getWorld().spawnParticle(
                Particle.DUST,
                location.clone().add(0, 1, 0),
                20, 0.3, 0.5, 0.3, 0,
                new Particle.DustOptions(Color.fromRGB(75, 0, 130), 2)
        );

        // Additional shadows effect
        location.getWorld().spawnParticle(
                Particle.WITCH,
                location.clone().add(0, 1, 0),
                15, 0.3, 0.5, 0.3, 0.1
        );

        // Teleport sound
        location.getWorld().playSound(
                location,
                Sound.ENTITY_ENDERMAN_TELEPORT,
                1.0f,
                0.5f
        );
    }
}
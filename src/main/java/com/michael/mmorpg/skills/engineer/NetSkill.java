package com.michael.mmorpg.skills.engineer;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class NetSkill extends Skill {
    private final double range;
    private final long stunDuration;
    private LivingEntity target;

    public NetSkill(ConfigurationSection config) {
        super(config);
        this.range = config.getDouble("range", 15.0);
        this.stunDuration = (long)(config.getDouble("stunDuration", 1.5) * 1000);
        this.isHarmfulSkill = true;
    }

    @Override
    public void execute(Player player) {
        // First get the player data
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null) return;

        // Check resources BEFORE doing anything else
        if (!plugin.getSkillManager().checkResources(player, playerData, this)) {
            return;
        }

        // Use base class targeting that includes party checks
        currentTarget = getTargetEntity(player, range);
        if (currentTarget == null) {
            player.sendMessage("§c✦ No target in range!");
            return;
        }
        target = currentTarget;

        // If we have cast time, start casting
        if (hasCastTime) {
            startCasting(player);
        } else {
            performSkill(player);
        }
    }

    @Override
    protected void performSkill(Player player) {
        // Validate target is still valid
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Apply net effect immediately
        if (target instanceof Player) {
            Player playerTarget = (Player) target;
            StatusEffect stunEffect = new StatusEffect(CCType.STUN, stunDuration, playerTarget, 1);
            plugin.getStatusEffectManager().applyEffect(playerTarget, stunEffect);
        }

        // Visual effects for net launcher
        Location playerLoc = player.getLocation().add(0, 1.5, 0);
        Location targetLoc = target.getLocation().add(0, target.getHeight() / 2, 0);

        // Launch effect at player
        player.getWorld().spawnParticle(Particle.CLOUD, playerLoc, 15, 0.2, 0.2, 0.2, 0.05);
        player.getWorld().spawnParticle(Particle.SMOKE, playerLoc, 10, 0.1, 0.1, 0.1, 0.05);
        player.getWorld().playSound(playerLoc, Sound.BLOCK_PISTON_EXTEND, 1.0f, 1.2f);

        // Net effect at target
        target.getWorld().spawnParticle(Particle.WAX_OFF, targetLoc, 50, 0.8, 0.8, 0.8, 0.1);
        target.getWorld().spawnParticle(Particle.CLOUD, targetLoc, 20, 0.3, 0.3, 0.3, 0.05);
        target.getWorld().playSound(targetLoc, Sound.BLOCK_CHAIN_BREAK, 1.0f, 0.5f);
        target.getWorld().playSound(targetLoc, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.2f);

        // Quick line effect between player and target
        drawNetLine(playerLoc, targetLoc);

        setSkillSuccess(true);

        // Feedback messages
        if (target instanceof Player) {
            ((Player) target).sendMessage("§c✦ You've been caught in a mechanical net!");
        }
    }

    private void drawNetLine(Location start, Location end) {
        org.bukkit.util.Vector direction = end.clone().subtract(start).toVector();
        double distance = direction.length();
        direction.normalize().multiply(0.5);

        // Draw a quick line of particles
        for (double d = 0; d < distance; d += 0.5) {
            Location particleLoc = start.clone().add(direction.multiply(0.5));
            start.getWorld().spawnParticle(Particle.WAX_OFF, particleLoc, 1, 0, 0, 0, 0);
        }
    }

    // Override showTargetingBeam to use mechanical particles
    @Override
    protected void showTargetingBeam(Player player, LivingEntity target) {
        Location start = player.getEyeLocation();
        Location end = target.getEyeLocation();
        org.bukkit.util.Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize().multiply(0.5);

        for (double d = 0; d < distance; d += 0.5) {
            Location particleLoc = start.clone().add(direction.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(150, 150, 150), 1));
        }
    }
}
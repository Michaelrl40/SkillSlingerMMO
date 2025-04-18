package com.michael.mmorpg.skills.windwaker;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.metadata.FixedMetadataValue;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.models.PlayerData;

public class BoltSkill extends Skill {
    private final double damage;
    private final double range;
    private LivingEntity target;

    public BoltSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 15.0);
        this.range = config.getDouble("range", 20.0);
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

        Location strikeLocation = target.getLocation();
        World world = strikeLocation.getWorld();
        if (world != null) {
            // First set the damage metadata
            target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
            target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));

            // Apply damage
            target.damage(0.1, player);

            // Visual and sound effects
            world.strikeLightningEffect(strikeLocation);
            world.playSound(strikeLocation, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
            world.spawnParticle(Particle.ELECTRIC_SPARK, strikeLocation.add(0, 1, 0), 50, 0.5, 1, 0.5, 0.1);

            // Clean up metadata
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (target.isValid()) {
                    target.removeMetadata("skill_damage", plugin);
                    target.removeMetadata("skill_damage_amount", plugin);
                    target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));
                }
            }, 1L);

            // Broadcast the strike
            broadcastLocalSkillMessage(player, "§e⚡ " + player.getName() + " strikes " +
                    target.getName() + " with lightning!");

            setSkillSuccess(true);
        }
    }

    // Override showTargetingBeam to use electric particles
    @Override
    protected void showTargetingBeam(Player player, LivingEntity target) {
        Location start = player.getEyeLocation();
        Location end = target.getEyeLocation();
        org.bukkit.util.Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize().multiply(0.5);

        for (double d = 0; d < distance; d += 0.5) {
            Location particleLoc = start.clone().add(direction.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, particleLoc, 1, 0, 0, 0, 0);
        }
    }
}
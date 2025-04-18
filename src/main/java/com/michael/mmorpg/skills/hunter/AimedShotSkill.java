package com.michael.mmorpg.skills.hunter;

import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.Sound;
import org.bukkit.Particle;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AimedShotSkill extends Skill implements Listener {
    private final Map<UUID, LivingEntity> aimedShotTargets = new HashMap<>();
    private final double damage;
    private final double range;
    private LivingEntity target;

    public AimedShotSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 20.0);
        this.range = config.getDouble("range", 15.0);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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

        // Check if player already has an aimed shot
        if (aimedShotTargets.containsKey(player.getUniqueId())) {
            player.sendMessage("§c✦ You are already aiming at a target!");
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

        // Store the target for this player
        aimedShotTargets.put(player.getUniqueId(), target);

        // Visual and sound effects for target lock
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.CRIT,
                target.getLocation().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1);

        // Tell player they're ready to shoot
        player.sendMessage("§6✦ Your aim is locked on - fire when ready!");

        // Start buff expiry timer
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (aimedShotTargets.containsKey(player.getUniqueId())) {
                aimedShotTargets.remove(player.getUniqueId());
                player.sendMessage("§c✦ You lost focus on your target!");
            }
        }, 1200L); // 60 seconds

        setSkillSuccess(true);
    }

    @EventHandler
    public void onBowShot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player shooter = (Player) event.getEntity();

        // Check if player has an aimed shot ready
        LivingEntity target = aimedShotTargets.get(shooter.getUniqueId());
        if (target == null) return;

        // Cancel the actual arrow
        event.setCancelled(true);

        // Remove the buff
        aimedShotTargets.remove(shooter.getUniqueId());

        // Apply the damage with metadata
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, shooter));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));

        // Apply a tiny amount of damage to trigger the damage event
        target.damage(0.1, shooter);

        // Visual and sound effects
        shooter.getWorld().playSound(shooter.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.5f);
        target.getWorld().spawnParticle(Particle.CRIT,
                target.getLocation().add(0, 1, 0),
                30, 0.3, 0.3, 0.3, 0.2);

        // Set cooldown
        plugin.getSkillManager().setCooldown(shooter, getName(), getCooldown());

        // Broadcast the hit
        broadcastLocalSkillMessage(shooter, "§6[" + getPlayerClass(shooter) + "] " +
                shooter.getName() + " lands a perfect shot on " + target.getName() + "!");

        // Clean up metadata
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (target.isValid()) {
                target.removeMetadata("skill_damage", plugin);
                target.removeMetadata("skill_damage_amount", plugin);
                target.removeMetadata("magic_damage", plugin);
            }
        }, 1L);
    }
}
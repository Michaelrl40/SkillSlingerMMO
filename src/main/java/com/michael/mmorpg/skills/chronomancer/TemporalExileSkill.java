package com.michael.mmorpg.skills.chronomancer;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TemporalExileSkill extends Skill {
    private final double exileDuration;
    private final double voidY = -75; // Deep in the void
    private static final Map<UUID, Location> exiledPlayers = new HashMap<>();

    public TemporalExileSkill(ConfigurationSection config) {
        super(config);
        this.exileDuration = config.getDouble("exileduration", 5.0);
    }

    @Override
    public void execute(Player caster) {
        // Get target using built-in targeting
        currentTarget = getTargetEntity(caster, config.getDouble("range", 15.0));

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No valid target in range!");
            return;
        }

        // Check if target is a player
        if (!(currentTarget instanceof Player)) {
            caster.sendMessage("§c✦ You can only exile players!");
            return;
        }

        // Skip if targeting self
        if (currentTarget.equals(caster)) {
            caster.sendMessage("§c✦ You cannot exile yourself!");
            return;
        }

        // Start casting if has cast time
        if (hasCastTime) {
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        Player target = (Player) currentTarget;

        // Store original location
        Location originalLoc = target.getLocation().clone();
        exiledPlayers.put(target.getUniqueId(), originalLoc);

        // Create void location
        Location voidLoc = originalLoc.clone();
        voidLoc.setY(voidY);

        // Prevent all damage and movement
        target.setMetadata("temporal_exile", new FixedMetadataValue(plugin, true));
        target.setAllowFlight(true);
        target.setFlying(true);
        target.setInvulnerable(true);

        // Apply effects to prevent interaction/movement
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(exileDuration * 20), 100, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, (int)(exileDuration * 20), 128, false, false));

        // Exile effects at original location
        playExileEffect(originalLoc);

        // Teleport to void
        target.teleport(voidLoc);
        target.sendMessage("§5✦ You have been exiled from time!");
        caster.sendMessage("§5✦ You exiled " + target.getName() + " from time!");

        // Schedule return
        new BukkitRunnable() {
            @Override
            public void run() {
                returnFromExile(target);
            }
        }.runTaskLater(plugin, (long)(exileDuration * 20));

        setSkillSuccess(true);
    }

    private void playExileEffect(Location location) {
        World world = location.getWorld();

        // Portal-like effect
        for (double y = 0; y < 2; y += 0.2) {
            double radius = 1.0 - (y / 2.0);
            for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                Location particleLoc = location.clone().add(x, y, z);

                world.spawnParticle(
                        Particle.PORTAL,
                        particleLoc,
                        1, 0, 0, 0, 0
                );
            }
        }

        // Sound effects
        world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        world.playSound(location, Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 2.0f);

        // Flash effect
        world.spawnParticle(
                Particle.FLASH,
                location.clone().add(0, 1, 0),
                1, 0, 0, 0, 0
        );
    }

    private void returnFromExile(Player target) {
        if (!target.isOnline()) return;

        Location returnLoc = exiledPlayers.remove(target.getUniqueId());
        if (returnLoc != null) {
            // Return effects at void location
            playExileEffect(target.getLocation());

            // Teleport back
            target.teleport(returnLoc);

            // Return effects at original location
            playExileEffect(returnLoc);

            // Remove effects
            target.setInvulnerable(false);
            target.setFlying(false);
            target.setAllowFlight(false);
            target.removeMetadata("temporal_exile", plugin);
            target.removePotionEffect(PotionEffectType.BLINDNESS);
            target.removePotionEffect(PotionEffectType.SLOWNESS);
            target.removePotionEffect(PotionEffectType.JUMP_BOOST);

            target.sendMessage("§5✦ You have returned to your timeline!");
        }
    }

    // Clean up method for plugin disable
    public static void returnAllExiled() {
        for (Map.Entry<UUID, Location> entry : exiledPlayers.entrySet()) {
            Player target = Bukkit.getPlayer(entry.getKey());
            if (target != null && target.isOnline()) {
                target.teleport(entry.getValue());
                target.setInvulnerable(false);
                target.setFlying(false);
                target.setAllowFlight(false);
                target.removeMetadata("temporal_exile", plugin);
            }
        }
        exiledPlayers.clear();
    }
}
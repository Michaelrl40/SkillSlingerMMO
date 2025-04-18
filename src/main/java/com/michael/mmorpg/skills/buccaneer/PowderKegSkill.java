package com.michael.mmorpg.skills.buccaneer;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

public class PowderKegSkill extends Skill implements Listener {
    private final double damage;
    private final double radius;
    private final long fuseTime;

    public PowderKegSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 25.0);
        this.radius = config.getDouble("radius", 4.0);
        this.fuseTime = config.getLong("fuseTime", 60); // 3 seconds
    }

    @Override
    protected void performSkill(Player player) {
        // Get spawn location in front of player
        Location targetLoc = player.getLocation().add(player.getLocation().getDirection().multiply(2));
        Location loc = targetLoc.clone();

        // Find the highest block at this location
        int highestY = loc.getWorld().getHighestBlockYAt(loc);
        loc.setY(highestY + 1); // Set Y to one block above the highest block


        // Center the barrel on the block
        loc.setX(loc.getBlockX() + 0.5);
        loc.setZ(loc.getBlockZ() + 0.5);

        // Create barrel using armor stand
        ArmorStand barrel = player.getWorld().spawn(loc, ArmorStand.class);
        barrel.setSmall(false);
        barrel.setVisible(false);
        barrel.setGravity(false);
        barrel.setInvulnerable(false);
        barrel.setCustomName("§c❤ Powder Keg ❤");
        barrel.setCustomNameVisible(true);
        barrel.setMarker(false);

        // Create the disguise
        me.libraryaddict.disguise.disguisetypes.MiscDisguise disguise = new me.libraryaddict.disguise.disguisetypes.MiscDisguise(
                me.libraryaddict.disguise.disguisetypes.DisguiseType.FALLING_BLOCK,
                Material.BARREL
        );
        disguise.setDynamicName(true);
        disguise.getWatcher().setNoGravity(true);
        disguise.getWatcher().setCustomName(barrel.getCustomName());
        disguise.getWatcher().setCustomNameVisible(true);
        // Set the disguise to be a full block
        disguise.getWatcher().setYModifier(0);

        // Apply the disguise
        me.libraryaddict.disguise.DisguiseAPI.disguiseEntity(barrel, disguise);

        // Add all necessary metadata
        barrel.setMetadata("powder_keg", new FixedMetadataValue(plugin, true));
        barrel.setMetadata("keg_owner", new FixedMetadataValue(plugin, player));
        barrel.setMetadata("keg_damage", new FixedMetadataValue(plugin, damage));
        barrel.setMetadata("keg_radius", new FixedMetadataValue(plugin, radius));

        // Store party information if applicable
        if (plugin.getPartyManager().getParty(player) != null) {
            barrel.setMetadata("owner_party", new FixedMetadataValue(plugin, plugin.getPartyManager().getParty(player)));
        }

        // Visual and sound effects for placement
        barrel.getWorld().playSound(barrel.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 0.8f);
        spawnFuseParticles(barrel);

        // Start fuse timer with visual countdown
        int particleTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (barrel.isValid() && !barrel.isDead()) {
                spawnFuseParticles(barrel);
            }
        }, 0L, 5L).getTaskId();

        // Schedule explosion
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.getScheduler().cancelTask(particleTaskId);
            if (barrel.isValid() && !barrel.isDead()) {
                plugin.getPowderKegListener().explodeBarrel(barrel, damage, radius);
            }
        }, fuseTime);

        setSkillSuccess(true);
    }

    private void spawnFuseParticles(ArmorStand barrel) {
        Location particleLoc = barrel.getLocation().add(0, 1, 0);

        // Smoke particles rising from the top
        barrel.getWorld().spawnParticle(
                Particle.SMOKE,
                particleLoc,
                3, 0.2, 0.1, 0.2, 0.02
        );

        // Occasional spark particles
        if (Math.random() < 0.3) {
            barrel.getWorld().spawnParticle(
                    Particle.FLAME,
                    particleLoc,
                    1, 0.1, 0.1, 0.1, 0.01
            );
        }
    }
}
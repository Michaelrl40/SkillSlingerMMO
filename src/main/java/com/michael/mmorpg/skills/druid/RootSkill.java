package com.michael.mmorpg.skills.druid;

import me.libraryaddict.disguise.disguisetypes.Disguise;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;

public class RootSkill extends DruidShapeshiftSkill {
    private final double range;
    private final long rootDuration;
    private LivingEntity target;

    public RootSkill(ConfigurationSection config) {
        super(config);
        this.range = config.getDouble("range", 8.0);
        this.rootDuration = config.getLong("rootDuration", 3000);
    }

    @Override
    public void execute(Player player) {
        // Check if we're transformed
        if (player.hasMetadata("druid_form")) {
            player.sendMessage("§c✦ Root can only be used in base form!");
            return;
        }

        // Use base targeting with our range
        target = getTargetEntity(player, range);
        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            return;
        }

        // Store target for messages and validation
        currentTarget = target;

        // If we have cast time configured, start casting
        if (hasCastTime) {
            startCasting(player);
        } else {
            performSkill(player);
        }
    }

    @Override
    protected void performSkill(Player player) {
        // Verify target is still valid
        if (target == null || !target.isValid() || target.isDead()) {
            setSkillSuccess(false);
            return;
        }

        // Apply root effect based on target type
        if (target instanceof Player) {
            StatusEffect root = new StatusEffect(CCType.ROOT, rootDuration, player, 1);
            plugin.getStatusEffectManager().applyEffect((Player)target, root);
        } else {
            Location rootLocation = target.getLocation();
            target.setAI(false);

            // Keep entity in place
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!target.isDead()) {
                        target.teleport(rootLocation);
                    }
                }
            }.runTaskTimer(plugin, 0L, 1L);

            // Re-enable AI after duration
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!target.isDead()) {
                        target.setAI(true);
                    }
                }
            }.runTaskLater(plugin, rootDuration/50);
        }

        // Play effects
        playRootEffect(target.getLocation());

        // Broadcast success message
        broadcastLocalSkillMessage(player, "§2[Druid] " + player.getName() +
                " binds " + target.getName() + " with roots!");

        setSkillSuccess(true);
    }

    private void playRootEffect(Location location) {
        // Create rising roots effect
        for (double y = 0; y < 2; y += 0.2) {
            Location particleLoc = location.clone().add(0, y, 0);
            location.getWorld().spawnParticle(Particle.COMPOSTER, particleLoc, 5, 0.3, 0, 0.3, 0);
            location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 2, 0.3, 0, 0.3, 0);
        }

        // Play nature-themed sounds
        location.getWorld().playSound(location, Sound.BLOCK_GRASS_BREAK, 1.0f, 0.8f);
        location.getWorld().playSound(location, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1.0f, 0.6f);
    }

    // Required abstract method implementations - not used for this skill
    @Override protected Disguise createDisguise() { return null; }
    @Override protected void setupDisguise(Disguise disguise) {}
    @Override protected void applyFormEffects(Player player) {}
    @Override protected void removeFormEffects(Player player) {}
}
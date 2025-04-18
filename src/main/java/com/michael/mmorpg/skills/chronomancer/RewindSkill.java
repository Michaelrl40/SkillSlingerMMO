package com.michael.mmorpg.skills.chronomancer;

import com.michael.mmorpg.managers.DamageDisplayManager;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class RewindSkill extends Skill {
    private final Map<UUID, LinkedList<Location>> positionHistory = new HashMap<>();
    private final double healAmount;
    private final double range;

    public RewindSkill(ConfigurationSection config) {
        super(config);
        this.healAmount = config.getDouble("healamount", 50.0);
        this.range = config.getDouble("range", 15.0);
        this.isHarmfulSkill = false;

        // Start position tracking for all online players
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getServer().getOnlinePlayers().forEach(player -> {
                    UUID playerId = player.getUniqueId();
                    LinkedList<Location> positions = positionHistory.computeIfAbsent(
                            playerId, k -> new LinkedList<>()
                    );

                    positions.addFirst(player.getLocation().clone());

                    // Keep only last 5 seconds (100 ticks) of positions
                    while (positions.size() > 100) {
                        positions.removeLast();
                    }
                });
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public void execute(Player caster) {
        // Get target and let validateTarget handle the party/self targeting rules
        currentTarget = getTargetEntity(caster, range);

        // If no target found, default to self (which is always valid)
        if (currentTarget == null) {
            currentTarget = caster;
        }

        // validateTarget will handle all the party/player validation rules
        if (validateTarget(caster, currentTarget)) {
            return;  // Validation failed, appropriate message was sent
        }

        if (hasCastTime) {
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @Override
    protected void performSkill(Player caster) {
        if (!(currentTarget instanceof Player)) {
            caster.sendMessage("§c✦ You can only rewind players!");
            setSkillSuccess(false);
            return;
        }

        Player target = (Player) currentTarget;
        UUID targetId = target.getUniqueId();

        // Get position history
        LinkedList<Location> positions = positionHistory.get(targetId);
        if (positions == null || positions.isEmpty()) {
            caster.sendMessage("§c✦ No position history available for target!");
            setSkillSuccess(false);
            return;
        }

        // Get position from 5 seconds ago (100 ticks)
        Location oldPosition = positions.size() >= 100 ? positions.getLast() : positions.get(positions.size() - 1);

        // Store original location for effects
        Location originalLocation = target.getLocation().clone();

        // Teleport player back
        target.teleport(oldPosition);

        // Apply healing through PlayerData system
        PlayerData targetData = plugin.getPlayerManager().getPlayerData(target);
        if (targetData != null) {
            targetData.regenHealth(healAmount);

            // Display healing amount
            plugin.getDamageDisplayManager().spawnDamageDisplay(
                    target.getLocation(),
                    healAmount,
                    DamageDisplayManager.DamageType.HEALING
            );
        }

        // Visual effects at both locations
        playRewindEffects(originalLocation);
        playRewindEffects(oldPosition);

        // Sound effects
        target.playSound(target.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 2.0f);
        caster.playSound(caster.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 2.0f);

        // Success messages
        if (caster.equals(target)) {
            caster.sendMessage("§b✦ You rewound yourself through time and healed for " + healAmount + " health!");
        } else {
            target.sendMessage("§b✦ You were rewound through time and healed for " + healAmount + " health!");
            caster.sendMessage("§b✦ Rewound " + target.getName() + " through time and healed them!");
        }

        setSkillSuccess(true);
    }

    private void playRewindEffects(Location location) {
        // Clock-like particle effect
        double radius = 1.0;
        int particles = 32;

        for (int i = 0; i < particles; i++) {
            double angle = 2 * Math.PI * i / particles;
            double x = radius * Math.cos(angle);
            double z = radius * Math.sin(angle);

            location.getWorld().spawnParticle(
                    Particle.WITCH,
                    location.clone().add(x, 1, z),
                    1, 0, 0, 0, 0
            );
        }

        // Central spiral effect
        for (double y = 0; y < 2; y += 0.1) {
            double angle = y * 20;
            double x = 0.3 * Math.cos(angle);
            double z = 0.3 * Math.sin(angle);

            location.getWorld().spawnParticle(
                    Particle.END_ROD,
                    location.clone().add(x, y, z),
                    1, 0, 0, 0, 0
            );
        }

        // Additional particle effects
        location.getWorld().spawnParticle(
                Particle.PORTAL,
                location.clone().add(0, 1, 0),
                50, 0.5, 0.5, 0.5, 0.1
        );
    }
}
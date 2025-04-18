package com.michael.mmorpg.status;

import com.michael.mmorpg.MinecraftMMORPG;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class StatusEffectManager {
    private final MinecraftMMORPG plugin;
    private final Map<UUID, Map<CCType, StatusEffect>> activeEffects;
    private final Map<UUID, Map<CCType, Long>> immunities;

    public StatusEffectManager(MinecraftMMORPG plugin) {
        this.plugin = plugin;
        this.activeEffects = new HashMap<>();
        this.immunities = new HashMap<>();
        startEffectCleanupTask();
    }

    public void applyEffect(Player target, StatusEffect effect) {
        UUID targetId = target.getUniqueId();

        // Check immunity
        if (isImmune(target, effect.getType())) {
            target.sendMessage("§7" + effect.getType().getSymbol() + " You are immune to " + effect.getType().getDisplayName() + "!");
            return;
        }

        // Get current effects
        Map<CCType, StatusEffect> playerEffects = activeEffects.computeIfAbsent(targetId, k -> new HashMap<>());

        // Check if can stack or higher priority
        StatusEffect existingEffect = playerEffects.get(effect.getType());
        if (existingEffect != null) {
            if (!effect.getType().canStack() && existingEffect.getIntensity() >= effect.getIntensity()) {
                return;
            }
        }



        // Check for Future Sight protection FIRST
        if (target.hasMetadata("future_sight")) {
            target.removeMetadata("future_sight", plugin);
            target.sendMessage("§b✦ Your Future Sight protected you from " + effect.getType().getDisplayName() + "!");

            // Visual and sound effects for blocked CC
            target.getWorld().spawnParticle(
                    Particle.WITCH,
                    target.getLocation().add(0, 1, 0),
                    20, 0.5, 0.5, 0.5, 0.1
            );
            target.getWorld().spawnParticle(
                    Particle.END_ROD,
                    target.getLocation().add(0, 1, 0),
                    15, 0.3, 0.3, 0.3, 0.05
            );
            target.getWorld().playSound(
                    target.getLocation(),
                    Sound.BLOCK_GLASS_BREAK,
                    1.0f,
                    2.0f
            );
            target.getWorld().playSound(
                    target.getLocation(),
                    Sound.ENTITY_ENDER_EYE_DEATH,
                    0.5f,
                    1.5f
            );
            return;
        }

        // Apply effect
        playerEffects.put(effect.getType(), effect);
        effect.apply(target);

        // Schedule immunity
        scheduleImmunity(target, effect);
    }

    public void removeEffect(Player target, CCType type) {
        UUID targetId = target.getUniqueId();
        Map<CCType, StatusEffect> playerEffects = activeEffects.get(targetId);
        if (playerEffects != null) {
            StatusEffect removed = playerEffects.remove(type);
            if (removed != null) {
                removed.setActive(false);
                target.sendMessage("§a" + type.getSymbol() + " You are no longer " + type.getDisplayName() + "!");
            }
        }
    }

    public boolean hasEffect(Player target, CCType type) {
        UUID targetId = target.getUniqueId();
        Map<CCType, StatusEffect> playerEffects = activeEffects.get(targetId);
        return playerEffects != null && playerEffects.containsKey(type);
    }

    private boolean isImmune(Player target, CCType type) {
        UUID targetId = target.getUniqueId();
        Map<CCType, Long> playerImmunities = immunities.get(targetId);
        if (playerImmunities == null) return false;

        Long immunityEnd = playerImmunities.get(type);
        return immunityEnd != null && System.currentTimeMillis() < immunityEnd;
    }

    private void scheduleImmunity(Player target, StatusEffect effect) {
        UUID targetId = target.getUniqueId();
        Map<CCType, Long> playerImmunities = immunities.computeIfAbsent(targetId, k -> new HashMap<>());
        long immunityEnd = System.currentTimeMillis() + effect.getImmunityDuration();
        playerImmunities.put(effect.getType(), immunityEnd);
    }

    private void startEffectCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Set<Map.Entry<UUID, Map<CCType, StatusEffect>>> entries = new HashSet<>(activeEffects.entrySet());

                for (Map.Entry<UUID, Map<CCType, StatusEffect>> entry : entries) {
                    Player player = plugin.getServer().getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) continue;

                    Map<CCType, StatusEffect> effects = new HashMap<>(entry.getValue());
                    for (StatusEffect effect : effects.values()) {
                        if (effect.isExpired()) {
                            removeEffect(player, effect.getType());
                        }
                    }
                }

                // Clean up expired immunities
                long currentTime = System.currentTimeMillis();
                new HashMap<>(immunities).forEach((uuid, typeMap) -> {
                    Map<CCType, Long> newTypeMap = new HashMap<>(typeMap);
                    newTypeMap.entrySet().removeIf(immunityEntry ->
                            currentTime >= immunityEntry.getValue());
                    immunities.put(uuid, newTypeMap);
                });
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // Special effect break checks
    public void onDamageBreakEffects(Player target) {
        if (hasEffect(target, CCType.SLEEP)) {
            removeEffect(target, CCType.SLEEP);
        }
        if (hasEffect(target, CCType.ROOT)) {
            removeEffect(target, CCType.ROOT);
        }
    }
    //this clears ccs
    public void clearAllEffects(Player player) {
        UUID playerId = player.getUniqueId();

        // Clear active effects
        activeEffects.remove(playerId);

        // Clear immunities
        immunities.remove(playerId);
    }

    // For removing just active CC effects while preserving immunities
    public void removeAllActiveEffects(Player player) {
        UUID playerId = player.getUniqueId();
        activeEffects.remove(playerId);
    }

    // For adding immunities to all CC types at once
    public void addFullCCImmunity(Player player, long duration) {
        UUID playerId = player.getUniqueId();
        Map<CCType, Long> playerImmunities = immunities.computeIfAbsent(playerId, k -> new HashMap<>());
        long immunityEnd = System.currentTimeMillis() + duration;

        // Add immunity to all CC types
        for (CCType ccType : CCType.values()) {
            playerImmunities.put(ccType, immunityEnd);
        }
    }

    public void handleMovementEffects(Player player) {
        if (hasEffect(player, CCType.FEAR)) {
            // Move away from fear source
            StatusEffect fear = activeEffects.get(player.getUniqueId()).get(CCType.FEAR);
            Player source = fear.getSource();
            if (source != null && source.isOnline()) {
                org.bukkit.util.@NotNull Vector direction = player.getLocation().toVector()
                        .subtract(source.getLocation().toVector())
                        .normalize()
                        .multiply(0.5);
                player.setVelocity(direction);
            }
        } else if (hasEffect(player, CCType.CHARM)) {
            // Move toward charm source
            StatusEffect charm = activeEffects.get(player.getUniqueId()).get(CCType.CHARM);
            Player source = charm.getSource();
            if (source != null && source.isOnline()) {
                @NotNull Vector direction = source.getLocation().toVector()
                        .subtract(player.getLocation().toVector())
                        .normalize()
                        .multiply(0.3);
                player.setVelocity(direction);
            }
        }
    }
}
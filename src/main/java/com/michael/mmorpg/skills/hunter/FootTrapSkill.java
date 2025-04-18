package com.michael.mmorpg.skills.hunter;

import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Wolf;
import com.michael.mmorpg.party.Party;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FootTrapSkill extends Skill {
    private final int rootDuration;
    private final double trapRadius;
    private final int trapDuration;
    private static final Set<Location> activeTrapLocations = new HashSet<>();

    // Store trap owner information to check for pet owner relationship
    private static final Set<TrapInfo> trapOwners = new HashSet<>();

    public FootTrapSkill(ConfigurationSection config) {
        super(config);
        this.rootDuration = config.getInt("rootduration", 3) * 1000; // Convert to milliseconds
        this.trapRadius = config.getDouble("trapradius", 2.0);
        this.trapDuration = config.getInt("trapduration", 30) * 20; // Convert to ticks
    }

    @Override
    protected void performSkill(Player player) {
        Location trapLocation = player.getLocation().clone();
        Party casterParty = plugin.getPartyManager().getParty(player);

        // Add trap to active traps
        activeTrapLocations.add(trapLocation);

        // Store trap owner information
        trapOwners.add(new TrapInfo(trapLocation, player.getUniqueId()));

        // Show trap to caster and party members
        showTrapParticles(trapLocation, player, casterParty);

        // Start trap monitoring
        new BukkitRunnable() {
            int ticksLived = 0;

            @Override
            public void run() {
                if (ticksLived >= trapDuration || !activeTrapLocations.contains(trapLocation)) {
                    activeTrapLocations.remove(trapLocation);
                    trapOwners.removeIf(info -> info.location.equals(trapLocation));

                    // Broadcast trap expiration to local area
                    broadcastTrapExpiration(player, trapLocation);

                    this.cancel();
                    return;
                }

                // Show particles only to caster and party
                showTrapParticles(trapLocation, player, casterParty);

                // Check for entities in trap radius
                for (Entity entity : trapLocation.getWorld().getNearbyEntities(trapLocation, trapRadius, trapRadius, trapRadius)) {
                    if (!(entity instanceof LivingEntity) || entity == player) continue;

                    // Skip party members
                    if (entity instanceof Player && casterParty != null && casterParty.isMember((Player) entity)) {
                        continue;
                    }

                    // Skip hunter's pet - multiple ways to check
                    if (isHunterPet(entity, player)) {
                        continue;
                    }

                    // When trap is triggered, broadcast the event
                    if (entity instanceof Player) {
                        // Broadcast trap trigger to local area
                        broadcastTrapTrigger(player, (Player) entity, trapLocation);

                        // Apply root effect to players using status system
                        StatusEffect rootEffect = new StatusEffect(CCType.ROOT, rootDuration, player, 1);
                        plugin.getStatusEffectManager().applyEffect((Player) entity, rootEffect);
                    } else {
                        // For non-player entities, still broadcast but with different message
                        broadcastTrapTriggerMob(player, entity, trapLocation);

                        // Apply vanilla root effect to non-players
                        ((LivingEntity) entity).addPotionEffect(
                                new PotionEffect(PotionEffectType.SLOWNESS, rootDuration / 50, 6, false, true)
                        );
                    }

                    // Visual and sound effects for trap trigger
                    playTrapTriggerEffects(trapLocation);

                    // Remove trap after triggering
                    activeTrapLocations.remove(trapLocation);
                    trapOwners.removeIf(info -> info.location.equals(trapLocation));
                    this.cancel();
                    return;
                }

                ticksLived++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        setSkillSuccess(true);
    }

    /**
     * Checks if an entity is a Hunter's pet in multiple ways
     */
    private boolean isHunterPet(Entity entity, Player trapOwner) {
        // First check: Use the static method from SummonPetSkill if available
        if (SummonPetSkill.isPlayerPet(entity, trapOwner)) {
            return true;
        }

        // Second check: Check if it's a Wolf with hunter_pet metadata
        if (entity instanceof Wolf && entity.hasMetadata("hunter_pet")) {
            try {
                String petOwnerId = entity.getMetadata("hunter_pet").get(0).asString();
                if (trapOwner.getUniqueId().toString().equals(petOwnerId)) {
                    return true;
                }
            } catch (Exception e) {
                // Fallback if metadata access fails
            }
        }

        // Third check: Look for ignore_hunter_traps metadata
        if (entity.hasMetadata("ignore_hunter_traps")) {
            try {
                String ignoreOwnerId = entity.getMetadata("ignore_hunter_traps").get(0).asString();
                if (trapOwner.getUniqueId().toString().equals(ignoreOwnerId)) {
                    return true;
                }
            } catch (Exception e) {
                // Fallback if metadata access fails
            }
        }

        // Fourth check: Check active pets map directly
        if (entity instanceof Wolf) {
            Wolf pet = SummonPetSkill.ACTIVE_PETS.get(trapOwner.getUniqueId());
            if (pet != null && pet.getUniqueId().equals(entity.getUniqueId())) {
                return true;
            }
        }

        return false;
    }

    // New method for broadcasting trap triggers against players
    private void broadcastTrapTrigger(Player trapper, Player victim, Location location) {
        // Get all players within 30 blocks of the trap
        location.getWorld().getNearbyEntities(location, 30, 30, 30).forEach(entity -> {
            if (entity instanceof Player) {
                Player nearby = (Player) entity;
                // Format message differently based on if it's the trapper, victim, or observer
                if (nearby.equals(trapper)) {
                    nearby.sendMessage("§6✦ Your trap was triggered by " + victim.getName() + "!");
                } else if (nearby.equals(victim)) {
                    nearby.sendMessage("§c✦ You triggered " + trapper.getName() + "'s trap!");
                } else {
                    nearby.sendMessage("§e✦ " + victim.getName() + " triggered " + trapper.getName() + "'s trap!");
                }
            }
        });
    }

    // New method for broadcasting trap triggers against mobs
    private void broadcastTrapTriggerMob(Player trapper, Entity victim, Location location) {
        String mobName = formatMobName(victim.getType().toString());

        location.getWorld().getNearbyEntities(location, 30, 30, 30).forEach(entity -> {
            if (entity instanceof Player) {
                Player nearby = (Player) entity;
                if (nearby.equals(trapper)) {
                    nearby.sendMessage("§6✦ Your trap was triggered by a " + mobName + "!");
                } else {
                    nearby.sendMessage("§e✦ A " + mobName + " triggered " + trapper.getName() + "'s trap!");
                }
            }
        });
    }

    // New method for broadcasting trap expiration
    private void broadcastTrapExpiration(Player trapper, Location location) {
        location.getWorld().getNearbyEntities(location, 30, 30, 30).forEach(entity -> {
            if (entity instanceof Player) {
                Player nearby = (Player) entity;
                if (nearby.equals(trapper)) {
                    nearby.sendMessage("§7✦ Your trap has expired!");
                } else {
                    // Only inform party members about trap expiration
                    Party trapperParty = plugin.getPartyManager().getParty(trapper);
                    if (trapperParty != null && trapperParty.isMember(nearby)) {
                        nearby.sendMessage("§7✦ " + trapper.getName() + "'s trap has expired!");
                    }
                }
            }
        });
    }

    // Helper method to format mob names nicely
    private String formatMobName(String mobType) {
        String[] words = mobType.toLowerCase().split("_");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (formatted.length() > 0) formatted.append(" ");
            formatted.append(word.substring(0, 1).toUpperCase())
                    .append(word.substring(1));
        }
        return formatted.toString();
    }


    private void showTrapParticles(Location location, Player caster, Party party) {
        // Create particle packet for trap visualization
        World world = location.getWorld();
        Location particleLoc = location.clone().add(0, 0.1, 0);

        // Show particles only to caster and party members
        Set<Player> viewers = new HashSet<>();
        viewers.add(caster);
        if (party != null) {
            viewers.addAll(party.getMembers());
        }

        for (Player viewer : viewers) {
            // Create a circular pattern of particles
            for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
                double x = trapRadius * Math.cos(angle);
                double z = trapRadius * Math.sin(angle);
                Location borderLoc = particleLoc.clone().add(x, 0, z);

                viewer.spawnParticle(
                        Particle.DUST,
                        borderLoc,
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(139, 69, 19), 1)
                );
            }
        }
    }

    private void playTrapTriggerEffects(Location location) {
        World world = location.getWorld();

        // Visible to everyone since trap is triggered
        world.spawnParticle(
                Particle.EXPLOSION,
                location.add(0, 0.5, 0),
                20, 0.5, 0.5, 0.5, 0.1
        );

        world.playSound(
                location,
                Sound.BLOCK_WOODEN_PRESSURE_PLATE_CLICK_ON,
                1.0f,
                0.8f
        );

        world.playSound(
                location,
                Sound.ENTITY_IRON_GOLEM_HURT,
                1.0f,
                1.2f
        );
    }

    // Simple class to store trap location and owner
    private static class TrapInfo {
        private final Location location;
        private final UUID ownerId;

        public TrapInfo(Location location, UUID ownerId) {
            this.location = location;
            this.ownerId = ownerId;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TrapInfo)) return false;
            TrapInfo other = (TrapInfo) obj;
            return location.equals(other.location) && ownerId.equals(other.ownerId);
        }

        @Override
        public int hashCode() {
            return location.hashCode() * 31 + ownerId.hashCode();
        }
    }
}
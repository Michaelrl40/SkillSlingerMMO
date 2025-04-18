package com.michael.mmorpg.skills.arcanist;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import com.michael.mmorpg.party.Party;

public class GroupTeleportSkill extends Skill {
    private final double warmupTime;

    public GroupTeleportSkill(ConfigurationSection config) {
        super(config);
        this.warmupTime = config.getDouble("warmuptime", 3.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Check if player is in combat
        if (plugin.getCombatManager().isInCombat(player)) {
            player.sendMessage("§c✦ Cannot use Group Teleport while in combat!");
            setSkillSuccess(false);
            return;
        }

        // Check if player is in a party
        Party party = plugin.getPartyManager().getParty(player);
        if (party == null) {
            player.sendMessage("§c✦ You must be in a party to use Group Teleport!");
            setSkillSuccess(false);
            return;
        }

        // Apply slowness to caster
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(warmupTime * 20), 4));
        startCasting(player, party);
        setSkillSuccess(true);
    }

    private void startCasting(Player player, Party party) {
        party.broadcast("§6✦ " + player.getName() + " is casting Group Teleport!");

        new BukkitRunnable() {
            private int ticks = 0;
            private final int totalTicks = (int)(warmupTime * 20);

            @Override
            public void run() {
                // Display particles
                player.getWorld().spawnParticle(
                        Particle.PORTAL,
                        player.getLocation().add(0, 1, 0),
                        10, 0.5, 0.5, 0.5, 0.1
                );

                if (++ticks >= totalTicks) {
                    executeGroupTeleport(player, party);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void executeGroupTeleport(Player player, Party party) {
        Location destination = player.getLocation();

        for (Player member : party.getMembers()) {
            if (member.equals(player)) continue;

            // Skip members in combat
            if (plugin.getCombatManager().isInCombat(member)) {
                member.sendMessage("§c✦ You are in combat and cannot be teleported!");
                continue;
            }

            // Teleport and play effects
            member.teleport(destination);
            member.getWorld().spawnParticle(
                    Particle.PORTAL,
                    member.getLocation().add(0, 1, 0),
                    20, 0.5, 1.0, 0.5, 0.1
            );
            member.playSound(member.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        }

        party.broadcast("§a✦ Group Teleport successful!");
    }
}
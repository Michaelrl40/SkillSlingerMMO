package com.michael.mmorpg.skills.priest;

import com.michael.mmorpg.party.Party;
import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.managers.DamageDisplayManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class RadiantBurstSkill extends Skill {
    private final double healAmount;
    private final double radius;

    public RadiantBurstSkill(ConfigurationSection config) {
        super(config);
        this.healAmount = config.getDouble("healamount", 12.0);
        this.radius = config.getDouble("radius", 8.0);
        this.isHarmfulSkill = false;  // Essential for party healing
    }

    @Override
    protected void performSkill(Player healer) {
        Party party = plugin.getPartyManager().getParty(healer);
        if (party == null) {
            // If not in party, only heal self
            healTarget(healer, healer);
            setSkillSuccess(true);
            return;
        }

        // Heal all nearby party members
        boolean anyoneHealed = false;
        Location healerLoc = healer.getLocation();

        for (Player member : party.getMembers()) {
            if (member.getWorld() != healer.getWorld()) continue;
            if (member.getLocation().distance(healerLoc) > radius) continue;

            healTarget(healer, member);
            anyoneHealed = true;
        }

        if (anyoneHealed) {
            playHealEffects(healerLoc);
            broadcastLocalSkillMessage(healer, "§a✦ " + healer.getName() + "'s radiant burst mends their allies!");
            setSkillSuccess(true);
        } else {
            healer.sendMessage("§c✦ No valid targets in range!");
            setSkillSuccess(false);
        }
    }

    private void healTarget(Player healer, Player target) {
        double maxHealth = target.getMaxHealth();
        double currentHealth = target.getHealth();
        double newHealth = Math.min(maxHealth, currentHealth + healAmount);

        // Calculate actual healing done
        double actualHealing = newHealth - currentHealth;

        // Apply the healing
        target.setHealth(newHealth);

        // Show healing numbers
        if (actualHealing > 0) {
            plugin.getDamageDisplayManager().spawnDamageDisplay(
                    target.getLocation(),
                    actualHealing,
                    DamageDisplayManager.DamageType.HEALING
            );
        }
    }

    private void playHealEffects(Location center) {
        // Create an expanding ring of particles
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particleLoc = center.clone().add(x, 0.1, z);

            center.getWorld().spawnParticle(
                    Particle.END_ROD,
                    particleLoc,
                    3, 0.1, 0.1, 0.1, 0.01
            );
        }

        // Play healing sound
        center.getWorld().playSound(
                center,
                Sound.BLOCK_BEACON_POWER_SELECT,
                0.5f,
                1.2f
        );
    }
}
package com.michael.mmorpg.skills.bandolier;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class BladebreakerSkill extends Skill {
    private final double damage;
    private final double meleeRange;
    private final long disarmDuration;

    public BladebreakerSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 8.0);
        this.meleeRange = config.getDouble("meleerange", 3.0);
        this.disarmDuration = config.getLong("disarmDuration", 2000);
    }

    @Override
    protected void performSkill(Player player) {
        // Get target using melee targeting system
        LivingEntity target = getMeleeTarget(player, meleeRange);

        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Validate target (checks for party members, etc)
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        if (!(target instanceof Player)) {
            player.sendMessage("§c✦ You can only disarm players!");
            setSkillSuccess(false);
            return;
        }

        Player targetPlayer = (Player) target;

        // Check if target is holding an item
        if (targetPlayer.getInventory().getItemInMainHand().getType() == Material.AIR) {
            player.sendMessage("§c✦ Target is not holding anything!");
            setSkillSuccess(false);
            return;
        }

        // Store the target for messages
        currentTarget = target;

        // Deal damage
        target.setMetadata("skill_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new org.bukkit.metadata.FixedMetadataValue(plugin, damage));
        target.damage(0.1, player);

        // Apply disarm effect using the CC system
        StatusEffect disarm = new StatusEffect(CCType.DISARM, disarmDuration, player, 1);
        plugin.getStatusEffectManager().applyEffect(targetPlayer, disarm);

        // Visual effects
        Location targetLoc = target.getLocation().add(0, 1, 0);

        player.getWorld().spawnParticle(
                Particle.SWEEP_ATTACK,
                targetLoc,
                1, 0.0, 0.0, 0.0, 0
        );

        // Swirling particle effect
        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 10) {
                    this.cancel();
                    return;
                }

                for (int i = 0; i < 2; i++) {
                    angle += Math.PI / 8;
                    double x = Math.cos(angle) * 0.8;
                    double z = Math.sin(angle) * 0.8;
                    targetLoc.add(x, 0, z);
                    player.getWorld().spawnParticle(
                            Particle.CRIT,
                            targetLoc,
                            1, 0.0, 0.0, 0.0, 0
                    );
                    targetLoc.subtract(x, 0, z);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Play additional skill sounds
        player.getWorld().playSound(targetLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);

        setSkillSuccess(true);
    }
}
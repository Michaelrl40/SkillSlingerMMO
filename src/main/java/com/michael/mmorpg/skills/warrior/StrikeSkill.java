package com.michael.mmorpg.skills.warrior;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.managers.DamageDisplayManager;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

public class StrikeSkill extends Skill {

    private final double damage;

    public StrikeSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 10.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Get target within melee range
        LivingEntity target = isMeleeSkill ? getMeleeTarget(player, targetRange) : getTargetEntity(player, targetRange);

        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Store target for reference (used in broadcastLocalSkillMessage)
        currentTarget = target;

        // Validate target for party/self restrictions
        if (validateTarget(player, target)) {
            setSkillSuccess(false);
            return;
        }

        // Get weapon damage from player's held item
        ItemStack weapon = player.getInventory().getItemInMainHand();
        double weaponDamage = calculateWeaponDamage(player, weapon);

        // Apply damage
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, weaponDamage));

        // Deal damage
        target.damage(0.1, player); // Small amount to trigger damage event

        // Visual and sound effects
        Location targetLoc = target.getLocation().add(0, 1, 0);
        target.getWorld().spawnParticle(Particle.CRIT, targetLoc, 10, 0.5, 0.5, 0.5, 0.1);
        target.getWorld().playSound(targetLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);

        // Spawn damage display (handled by CombatListener)
        plugin.getDamageDisplayManager().spawnDamageDisplay(
                targetLoc,
                weaponDamage,
                DamageDisplayManager.DamageType.NORMAL
        );

        setSkillSuccess(true);
    }

    /**
     * Calculate the weapon damage based on the player's currently equipped weapon
     */
    private double calculateWeaponDamage(Player player, ItemStack weapon) {
        // Check if weapon is empty/null
        if (weapon == null || weapon.getType() == Material.AIR) {
            // Fist damage
            return 1.0;
        }

        // Try to get weapon damage from the WeaponManager
        try {
            return plugin.getWeaponManager().getWeaponDamage(weapon);
        } catch (Exception e) {
            // If there's an error, return base damage defined in the skill config
            return damage;
        }
    }
}
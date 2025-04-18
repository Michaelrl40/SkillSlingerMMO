package com.michael.mmorpg.skills.guardian;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ShieldBashSkill extends Skill {
    private final long stunDuration;

    public ShieldBashSkill(ConfigurationSection config) {
        super(config);
        this.stunDuration = config.getLong("stunduration", 2000);
    }

    @Override
    public void execute(Player caster) {
        // Check if player has a shield equipped
        if (!hasShieldEquipped(caster)) {
            caster.sendMessage("§c✦ You need a shield equipped to use Shield Bash!");
            return;
        }

        // Get target using melee targeting
        currentTarget = getMeleeTarget(caster, targetRange);

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No valid target in melee range!");
            return;
        }

        // Start casting if has cast time
        if (hasCastTime) {
            caster.sendMessage("§6✦ Preparing to bash " + currentTarget.getName() + " with your shield...");
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    private boolean hasShieldEquipped(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return mainHand.getType() == Material.SHIELD || offHand.getType() == Material.SHIELD;
    }

    @Override
    protected void performSkill(Player caster) {
        if (currentTarget == null || !(currentTarget instanceof Player)) return;

        Player target = (Player) currentTarget;

        // Apply stun effect
        StatusEffect stunEffect = new StatusEffect(CCType.STUN, stunDuration, caster, 1);
        plugin.getStatusEffectManager().applyEffect(target, stunEffect);

        // Play shield bash effects
        playBashEffect(caster, target);

        // Set cooldown
        plugin.getSkillManager().setCooldown(caster, getName(), getCooldown());

        // Send messages
        caster.sendMessage("§6✦ You bash " + target.getName() + " with your shield!");
        target.sendMessage("§6✦ " + caster.getName() + " bashes you with their shield!");

        setSkillSuccess(true);
    }

    private void playBashEffect(Player caster, Player target) {
        Location targetLoc = target.getLocation();

        // Shield impact particles
        target.getWorld().spawnParticle(
                Particle.CRIT,
                targetLoc.clone().add(0, 1, 0),
                15, 0.3, 0.3, 0.3, 0.5
        );

        // White shockwave to represent the stun
        target.getWorld().spawnParticle(
                Particle.DUST,
                targetLoc.clone().add(0, 1, 0),
                20, 0.3, 0.3, 0.3, 0,
                new Particle.DustOptions(Color.WHITE, 1.5f)
        );

        // Shield bash sound effects
        target.getWorld().playSound(
                targetLoc,
                Sound.ITEM_SHIELD_BLOCK,
                1.5f,
                0.8f
        );
        target.getWorld().playSound(
                targetLoc,
                Sound.ENTITY_IRON_GOLEM_HURT,
                0.8f,
                1.2f
        );
    }
}
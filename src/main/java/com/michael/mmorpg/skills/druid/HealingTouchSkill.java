package com.michael.mmorpg.skills.druid;

import com.michael.mmorpg.managers.DamageDisplayManager;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class HealingTouchSkill extends DruidShapeshiftSkill {
    // Core healing properties
    private final double healAmount;
    private final double range;

    public HealingTouchSkill(ConfigurationSection config) {
        super(config);
        // Initialize healing-specific properties
        this.healAmount = config.getDouble("healamount", 10.0);
        this.range = config.getDouble("range", 10.0);

        // Override toggleable to false since this is a direct healing spell
        this.isToggleable = false;
        // Force this to be a non-harmful skill for proper party targeting
        this.isHarmfulSkill = false;
    }

    @Override
    public void execute(Player healer) {
        if (healer.hasMetadata("druid_form")) {
            healer.sendMessage("§c✦ Healing Touch can only be used in your natural form!");
            return;
        }

        currentTarget = getTargetEntity(healer, range);

        if (currentTarget == null) {
            currentTarget = healer;
        }

        if (validateTarget(healer, currentTarget)) {
            return;
        }

        if (hasCastTime) {
            startCasting(healer);
        } else {
            performSkill(healer);
        }
    }

    @Override
    protected void performSkill(Player healer) {
        if (currentTarget == null || !currentTarget.isValid() || currentTarget.isDead()) {
            setSkillSuccess(false);
            healer.sendMessage("§c✦ Target is no longer valid!");
            return;
        }

        double maxHealth = currentTarget.getMaxHealth();
        double currentHealth = currentTarget.getHealth();
        double newHealth = Math.min(maxHealth, currentHealth + healAmount);

        // Calculate actual healing done
        double actualHealing = newHealth - currentHealth;

        // Apply the healing
        currentTarget.setHealth(newHealth);

        // Show healing numbers if actual healing occurred
        if (actualHealing > 0) {
            plugin.getDamageDisplayManager().spawnDamageDisplay(
                    currentTarget.getLocation(),
                    actualHealing,
                    DamageDisplayManager.DamageType.HEALING
            );
        }

        // Visual and sound effects
        playHealEffects(currentTarget.getLocation());

        // Set success and broadcast appropriate message
        setSkillSuccess(true);
        if (currentTarget.equals(healer)) {
            broadcastLocalSkillMessage(healer, "§2[Druid] " + healer.getName() +
                    " channels nature's energy to heal themselves!");
        } else {
            broadcastLocalSkillMessage(healer, "§2[Druid] " + healer.getName() +
                    " channels nature's energy to heal " + currentTarget.getName() + "!");
        }
    }

    private void playHealEffects(Location location) {
        location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                location.add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0);

        location.getWorld().spawnParticle(Particle.COMPOSTER,
                location, 8, 0.3, 0.3, 0.3, 0);

        location.getWorld().playSound(location, Sound.BLOCK_GRASS_BREAK, 1.0f, 1.2f);
        location.getWorld().playSound(location, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1.0f, 1.0f);
    }

    @Override protected Disguise createDisguise() { return null; }
    @Override protected void setupDisguise(Disguise disguise) {}
    @Override protected void applyFormEffects(Player player) {}
    @Override protected void removeFormEffects(Player player) {}
}
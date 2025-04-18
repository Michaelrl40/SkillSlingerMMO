package com.michael.mmorpg.skills.druid;

import com.michael.mmorpg.managers.DamageDisplayManager;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.LivingEntity;

public class RegrowthSkill extends DruidShapeshiftSkill {
    // Core healing properties
    private final double initialHeal;
    private final double overTimeHeal;
    private final double range;
    private final int tickDuration;

    public RegrowthSkill(ConfigurationSection config) {
        super(config);
        // Initialize healing-specific properties
        this.initialHeal = config.getDouble("initialheal", 20.0);
        this.overTimeHeal = config.getDouble("overtimeheal", 20.0);
        this.range = config.getDouble("range", 10.0);
        this.tickDuration = config.getInt("duration", 10);

        // Override toggleable to false since this is a direct healing spell
        this.isToggleable = false;
        // Force this to be a non-harmful skill for proper party targeting
        this.isHarmfulSkill = false;
    }

    @Override
    public void execute(Player healer) {
        // First check if we're in the correct form
        if (healer.hasMetadata("druid_form")) {
            healer.sendMessage("§c✦ Regrowth can only be used in your natural form!");
            return;
        }

        // Try to find a valid target (party member) in front of the player
        currentTarget = getTargetEntity(healer, range);

        // Default to self-healing if no valid target found
        if (currentTarget == null) {
            currentTarget = healer;
        }

        // Validate our target (this includes party membership checks)
        if (validateTarget(healer, currentTarget)) {
            return; // Target validation failed
        }

        // Handle cast time if configured
        if (hasCastTime) {
            startCasting(healer);
        } else {
            performSkill(healer);
        }
    }

    @Override
    protected void performSkill(Player healer) {
        // Final validation check
        if (currentTarget == null || !currentTarget.isValid() || currentTarget.isDead()) {
            setSkillSuccess(false);
            healer.sendMessage("§c✦ Target is no longer valid!");
            return;
        }

        // Calculate and apply initial heal
        double maxHealth = currentTarget.getMaxHealth();
        double currentHealth = currentTarget.getHealth();
        double newHealth = Math.min(maxHealth, currentHealth + initialHeal);
        double actualInitialHeal = newHealth - currentHealth;

        currentTarget.setHealth(newHealth);

        // Show initial heal numbers if healing occurred
        if (actualInitialHeal > 0) {
            plugin.getDamageDisplayManager().spawnDamageDisplay(
                    currentTarget.getLocation(),
                    actualInitialHeal,
                    DamageDisplayManager.DamageType.HEALING
            );
        }

        // Visual and sound effects for initial heal
        playHealEffects(currentTarget.getLocation());

        // Broadcast initial heal message
        if (currentTarget.equals(healer)) {
            broadcastLocalSkillMessage(healer, "§2[Druid] " + healer.getName() +
                    " begins channeling nature's regrowth on themselves!");
        } else {
            broadcastLocalSkillMessage(healer, "§2[Druid] " + healer.getName() +
                    " begins channeling nature's regrowth on " + currentTarget.getName() + "!");
        }

        // Start healing over time effect
        final LivingEntity healTarget = currentTarget; // Store for lambda
        new BukkitRunnable() {
            int ticks = 0;
            final double healPerTick = overTimeHeal / tickDuration;

            @Override
            public void run() {
                // Stop if target is invalid or maximum duration reached
                if (ticks >= tickDuration || !healTarget.isValid() || healTarget.isDead()) {
                    cancel();
                    return;
                }

                // Calculate and apply heal tick
                double currentHealth = healTarget.getHealth();
                double maxHealth = healTarget.getMaxHealth();
                double newHealth = Math.min(maxHealth, currentHealth + healPerTick);
                double actualTickHeal = newHealth - currentHealth;

                healTarget.setHealth(newHealth);

                // Show HoT tick numbers if healing occurred
                if (actualTickHeal > 0) {
                    plugin.getDamageDisplayManager().spawnDamageDisplay(
                            healTarget.getLocation(),
                            actualTickHeal,
                            DamageDisplayManager.DamageType.HEALING
                    );
                }

                // Show healing effects each tick
                playTickEffects(healTarget.getLocation());
                ticks++;
            }
        }.runTaskTimer(getPlugin(), 20L, 20L);

        setSkillSuccess(true);
    }

    private void playHealEffects(Location location) {
        // Nature-themed healing particles rising up
        location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                location.add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0);

        // Leaf and growth particles for druidic theme
        location.getWorld().spawnParticle(Particle.COMPOSTER,
                location, 8, 0.3, 0.3, 0.3, 0);

        // Nature-themed sounds
        location.getWorld().playSound(location, Sound.BLOCK_GRASS_BREAK, 1.0f, 1.2f);
        location.getWorld().playSound(location, Sound.BLOCK_AZALEA_LEAVES_BREAK, 1.0f, 1.0f);
    }

    private void playTickEffects(Location location) {
        // Smaller particle effect for HoT ticks
        location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                location.add(0, 1, 0), 5, 0.3, 0.3, 0.3, 0);
        location.getWorld().spawnParticle(Particle.COMPOSTER,
                location, 3, 0.2, 0.2, 0.2, 0);
    }

    // Required method implementations from DruidShapeshiftSkill
    @Override protected Disguise createDisguise() { return null; }
    @Override protected void setupDisguise(Disguise disguise) {}
    @Override protected void applyFormEffects(Player player) {}
    @Override protected void removeFormEffects(Player player) {}
}
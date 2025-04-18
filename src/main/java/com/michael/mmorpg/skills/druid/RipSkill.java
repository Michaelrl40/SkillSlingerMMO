package com.michael.mmorpg.skills.druid;

import me.libraryaddict.disguise.disguisetypes.Disguise;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.metadata.FixedMetadataValue;

public class RipSkill extends DruidShapeshiftSkill {
    private final double damagePerTick;
    private final int tickCount;
    private final int tickInterval;

    public RipSkill(ConfigurationSection config) {
        super(config);
        this.damagePerTick = config.getDouble("damageper_tick", 2.0);
        this.tickCount = config.getInt("tick_count", 5);
        this.tickInterval = config.getInt("tick_interval", 20);

        // This skill is not itself a shapeshift skill
        this.isToggleable = false;
    }

    @Override
    protected void performSkill(Player player) {
        // Form requirements are already checked by DruidShapeshiftSkill's execute method

        // Use base melee targeting system
        LivingEntity target = getMeleeTarget(player, targetRange);
        if (target == null) {
            player.sendMessage("§c✦ No target in range!");
            setSkillSuccess(false);
            return;
        }

        // Store current target for messages
        currentTarget = target;

        // Start the bleeding effect
        applyRipEffect(player, target);

        // Play initial attack effects
        playRipEffects(target.getLocation());

        // Set skill as successful for cooldown
        setSkillSuccess(true);

        // Set cooldown
        plugin.getSkillManager().setCooldown(player, getName(), getCooldown());
    }

    private void applyRipEffect(Player attacker, LivingEntity target) {
        // Mark target as bleeding
        target.setMetadata("bleeding", new FixedMetadataValue(plugin, true));

        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                // Stop if maximum ticks reached or target invalid
                if (ticks >= tickCount || !target.isValid() || target.isDead()) {
                    if (target.isValid()) {
                        target.removeMetadata("bleeding", plugin);
                    }
                    this.cancel();
                    return;
                }

                // Apply damage for this tick if target is still valid
                if (target.isValid() && !target.isDead()) {
                    // Set metadata for damage handling
                    target.setMetadata("skill_damage", new FixedMetadataValue(plugin, attacker));
                    target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damagePerTick));

                    // Apply the damage
                    target.damage(0.1, attacker);

                    // Clean up damage metadata
                    target.removeMetadata("skill_damage", plugin);
                    target.removeMetadata("skill_damage_amount", plugin);

                    // Visual effects for this tick
                    playBleedEffects(target.getLocation());
                }

                ticks++;
            }
        }.runTaskTimer(plugin, tickInterval, tickInterval);

        // Send skill use message after initial hit
        broadcastLocalSkillMessage(attacker, "§c[Wolf] " + attacker.getName() +
                " rips into " + target.getName() + " causing them to bleed!");
    }

    private void playRipEffects(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Wolf attack particles
        world.spawnParticle(
                Particle.DUST,
                location.add(0, 1, 0),
                20, 0.4, 0.6, 0.4, 0.1,
                new Particle.DustOptions(Color.RED, 1.5f)
        );

        // Claw slash effect
        for (double i = 0; i < Math.PI; i += Math.PI / 8) {
            Location particleLoc = location.clone().add(
                    Math.cos(i) * 0.8,
                    Math.sin(i) * 0.8,
                    Math.sin(i) * 0.8
            );
            world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
        }

        // Wolf attack sounds
        world.playSound(location, Sound.ENTITY_WOLF_GROWL, 1.0f, 1.2f);
        world.playSound(location, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1.0f);
    }

    private void playBleedEffects(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Bleeding particles
        world.spawnParticle(
                Particle.DUST,
                location.add(0, 1, 0),
                5, 0.2, 0.3, 0.2, 0.05,
                new Particle.DustOptions(Color.RED, 1)
        );

        // Subtle dripping sound
        world.playSound(location, Sound.BLOCK_STONE_BREAK, 0.4f, 2.0f);
    }

    // Required abstract method implementations - not used for this skill
    @Override protected Disguise createDisguise() { return null; }
    @Override protected void setupDisguise(Disguise disguise) {}
    @Override protected void applyFormEffects(Player player) {}
    @Override protected void removeFormEffects(Player player) {}
}
package com.michael.mmorpg.skills.toxicologist;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.metadata.FixedMetadataValue;

public class PoisonShivSkill extends Skill {
    private final double damage;
    private final double velocity;
    private final int poisonDuration;
    private final double hitboxSize = 0.5;

    public PoisonShivSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("arrowDamage", 4.0);
        this.velocity = config.getDouble("arrowVelocity", 4.0);
        this.poisonDuration = config.getInt("poisonDuration", 60);
    }

    @Override
    protected void performSkill(Player player) {
        PlayerData data = getPlugin().getPlayerManager().getPlayerData(player);
        if (data == null) return;

        if (data.getCurrentToxin() < toxinCost) {
            player.sendMessage("§c☠ Not enough toxin for poison shiv!");
            return;
        }

        data.setCurrentToxin(data.getCurrentToxin() - toxinCost);

        Location startLoc = player.getLocation().add(0, 1, 0);
        ArmorStand shiv = player.getWorld().spawn(startLoc, ArmorStand.class, armorStand -> {
            armorStand.setVisible(false);
            armorStand.setSmall(true);
            armorStand.setMarker(true);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);

            ItemStack sword = new ItemStack(Material.IRON_SWORD);
            armorStand.setRightArmPose(new EulerAngle(Math.toRadians(350), 0, 0));
            armorStand.getEquipment().setItemInMainHand(sword);
        });

        shiv.setMetadata("poison_shiv", new FixedMetadataValue(plugin, true));
        shiv.setMetadata("owner", new FixedMetadataValue(plugin, player.getUniqueId()));

        Vector direction = player.getLocation().getDirection();
        new BukkitRunnable() {
            int ticks = 0;
            final Location startingLoc = startLoc.clone();
            final Vector velocity = direction.multiply(PoisonShivSkill.this.velocity);

            @Override
            public void run() {
                ticks++;

                if (ticks >= 12 || !shiv.isValid()) {
                    shiv.remove();
                    this.cancel();
                    return;
                }

                Location newLoc = shiv.getLocation().add(velocity.clone().multiply(0.05));
                shiv.teleport(newLoc);

                EulerAngle currentAngle = shiv.getRightArmPose();
                shiv.setRightArmPose(currentAngle.add(0.5, 0.5, 0));

                for (Entity entity : shiv.getNearbyEntities(hitboxSize, hitboxSize, hitboxSize)) {
                    if (entity instanceof LivingEntity && entity != player) {
                        // Skip party members
                        if (entity instanceof Player &&
                                plugin.getPartyManager().areInSameParty(player, (Player)entity)) {
                            continue;
                        }
                        handleHit((LivingEntity) entity, player);
                        shiv.remove();
                        this.cancel();
                        return;
                    }
                }

            }
        }.runTaskTimer(plugin, 0L, 1L);

        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                1.0F,
                1.5F
        );

        player.getWorld().spawnParticle(
                Particle.SWEEP_ATTACK,
                player.getLocation().add(player.getLocation().getDirection()),
                1, 0, 0, 0, 0
        );

        setSkillSuccess(true);
    }

    private void handleHit(LivingEntity target, Player attacker) {
        target.damage(damage, attacker);
        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));

        target.addPotionEffect(new PotionEffect(
                PotionEffectType.POISON,
                poisonDuration,
                0,
                false,
                true
        ));


        target.getWorld().playSound(
                target.getLocation(),
                Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH,
                1.0F,
                1.2F
        );
    }
}
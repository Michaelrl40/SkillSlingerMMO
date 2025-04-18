package com.michael.mmorpg.status;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import static com.michael.mmorpg.skills.Skill.plugin;

public class StatusEffect {
    private final CCType type;
    private final long duration;
    private final long startTime;
    private final Player source;
    private final int intensity;
    private boolean active;
    private long immunityDuration;

    public StatusEffect(CCType type, long duration, Player source, int intensity) {
        this.type = type;
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
        this.source = source;
        this.intensity = intensity;
        this.active = true;
        this.immunityDuration = duration * 2; // Default immunity is 2x the effect duration
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - startTime >= duration;
    }

    public void apply(Player target) {
        switch (type) {
            case STUN:
                applyStun(target);
                break;
            case SILENCE:
                applySilence(target);
                break;
            case ROOT:
                applyRoot(target);
                break;
            case BLIND:
                applyBlind(target);
                break;
            case KNOCKUP:
                applyKnockup(target);
                break;
            case SLOW:
                applySlow(target);
                break;
            case DISARM:
                applyDisarm(target);
                break;
            case FEAR:
                applyFear(target);
                break;
            case CHARM:
                applyCharm(target);
                break;
            case SLEEP:
                applySleep(target);
                break;
        }
        sendStatusMessage(target);
    }

    private void applyStun(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(duration/50), 4, false, false));
        // Add stun particles and sound
    }

    private void applySilence(Player target) {
        // Visual effect only - actual silence handling is in SkillManager
    }

    private void applyRoot(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(duration/50), 6, false, false));
        // Add root particles
    }

    private void applyBlind(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (int)(duration/50), 0, false, false));
    }

    private void applyKnockup(Player target) {
        target.setVelocity(target.getVelocity().setY(1.0));
    }

    private void applySlow(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(duration/50), intensity-1, false, false));
    }

    private void applyDisarm(Player target) {
        if (target.getInventory().getItemInMainHand().getType() != Material.AIR) {
            // Store their current item
            ItemStack storedItem = target.getInventory().getItemInMainHand().clone();
            target.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            target.updateInventory();

            // Store the item data for return
            target.setMetadata("disarmed_item", new FixedMetadataValue(plugin, storedItem));

            // Schedule the item return
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (target.isOnline() && target.hasMetadata("disarmed_item")) {
                        ItemStack returnItem = (ItemStack) target.getMetadata("disarmed_item").get(0).value();
                        target.getInventory().setItemInMainHand(returnItem);
                        target.updateInventory();
                        target.removeMetadata("disarmed_item", plugin);

                        // Return effect
                        target.getWorld().playSound(target.getLocation(),
                                Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 1.2f);
                        target.getWorld().spawnParticle(
                                Particle.HAPPY_VILLAGER,
                                target.getLocation().add(0, 1, 0),
                                10, 0.3, 0.3, 0.3, 0.1
                        );

                        target.sendMessage("§a✦ You regain control of your weapon!");
                    }
                }
            }.runTaskLater(plugin, this.duration / 50); // Convert milliseconds to ticks

            // Initial disarm effects
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 1.2f);
            target.getWorld().spawnParticle(
                    Particle.SWEEP_ATTACK,
                    target.getLocation().add(0, 1, 0),
                    1, 0.0, 0.0, 0.0, 0
            );
        }
    }

    public void removeDisarm(Player target) {
        target.removeMetadata("disarmed", plugin);
        target.removePotionEffect(PotionEffectType.GLOWING);
    }

    private void applyFear(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, (int)(duration/50), 1, false, false));
        // Add fear movement handling
    }

    private void applyCharm(Player target) {
        // Add charm movement handling
    }

    private void applySleep(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int)(duration/50), 6, false, false));
        // Add sleep particles
    }

    private void sendStatusMessage(Player target) {
        target.sendMessage("§c" + type.getSymbol() + " You are " + type.getDisplayName() + "!");
    }

    // Getters
    public CCType getType() { return type; }
    public long getDuration() { return duration; }
    public Player getSource() { return source; }
    public int getIntensity() { return intensity; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getImmunityDuration() { return immunityDuration; }
}
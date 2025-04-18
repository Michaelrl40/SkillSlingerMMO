package com.michael.mmorpg.skills.arcanist;

import com.michael.mmorpg.skills.Skill;
import com.michael.mmorpg.status.CCType;
import com.michael.mmorpg.status.StatusEffect;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;

public class PolymorphSkill extends Skill implements org.bukkit.event.Listener {
    private final double range;
    private final long duration;
    private final Random random = new Random();
    private BukkitRunnable movementTask;

    public PolymorphSkill(ConfigurationSection config) {
        super(config);
        this.range = config.getDouble("range", 15.0);
        this.duration = config.getLong("duration", 10000); // 10 seconds in milliseconds
        // Register this class's event listeners
        getPlugin().getServer().getPluginManager().registerEvents(this, getPlugin());
    }

    @Override
    public void execute(Player caster) {
        // Get target using built-in targeting
        currentTarget = getTargetEntity(caster, range);

        if (currentTarget == null) {
            caster.sendMessage("§c✦ No valid target in range!");
            setSkillSuccess(false);
            return;
        }

        if (!(currentTarget instanceof Player)) {
            caster.sendMessage("§c✦ Can only polymorph players!");
            setSkillSuccess(false);
            return;
        }

        // Start casting if has cast time
        if (hasCastTime) {
            startCasting(caster);
            return;
        }

        performSkill(caster);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Check if the damaged entity is a polymorphed player
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        if (victim.hasMetadata("polymorphed")) {
            // Get original caster for the message
            Player caster = null;
            if (victim.hasMetadata("polymorph_caster")) {
                try {
                    caster = getPlugin().getServer().getPlayer(
                            (java.util.UUID) victim.getMetadata("polymorph_caster").get(0).value()
                    );
                } catch (Exception ignored) {}
            }

            // Remove polymorph effect
            removePolymorph(victim);

            // Send break message
            victim.sendMessage("§c✦ Polymorph breaks as you take damage!");
            if (caster != null && caster.isOnline()) {
                caster.sendMessage("§c✦ Your polymorph on " + victim.getName() + " breaks from damage!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Check if the attacker is a polymorphed player
        Player attacker = null;

        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        // If attacker is polymorphed, cancel the attack
        if (attacker != null && attacker.hasMetadata("prevent_attacks")) {
            event.setCancelled(true);
            attacker.sendMessage("§c✦ You can't attack while transformed into a chicken!");

            // Play chicken sound as feedback
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1.0f, 1.2f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Check if the player is polymorphed and trying to use an item
        if (player.hasMetadata("prevent_attacks")) {
            // Cancel any right or left click actions with items
            if (event.getAction() == Action.RIGHT_CLICK_AIR ||
                    event.getAction() == Action.RIGHT_CLICK_BLOCK ||
                    event.getAction() == Action.LEFT_CLICK_AIR ||
                    event.getAction() == Action.LEFT_CLICK_BLOCK) {

                // Check if they're trying to use a weapon or bow
                ItemStack item = event.getItem();
                if (item != null) {
                    String itemType = item.getType().name().toUpperCase();
                    if (itemType.contains("SWORD") || itemType.contains("AXE") ||
                            itemType.contains("BOW") || itemType.contains("TRIDENT") ||
                            itemType.contains("CROSSBOW")) {

                        event.setCancelled(true);
                        player.sendMessage("§c✦ Chickens can't use weapons!");
                        player.playSound(player.getLocation(), Sound.ENTITY_CHICKEN_AMBIENT, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        // Prevent polymorphed players from dropping items
        if (event.getPlayer().hasMetadata("prevent_attacks")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✦ Chickens can't drop items!");
        }
    }

    @Override
    protected void performSkill(Player caster) {
        Player victim = (Player) currentTarget;

        // Cancel if already polymorphed
        if (victim.hasMetadata("polymorphed")) {
            caster.sendMessage("§c✦ Target is already polymorphed!");
            setSkillSuccess(false);
            return;
        }

        // Apply polymorph effects
        applyPolymorph(caster, victim);
        setSkillSuccess(true);
    }

    private void applyPolymorph(Player caster, Player victim) {
        // Create basic chicken disguise
        MobDisguise disguise = new MobDisguise(DisguiseType.CHICKEN);
        disguise.setModifyBoundingBox(false);

        // Set the custom name
        victim.setCustomName(victim.getName() + " 🐔");
        victim.setCustomNameVisible(true);

        // Apply the disguise
        DisguiseAPI.disguiseToAll(victim, disguise);

        // Apply silence effect using existing CC system
        StatusEffect silence = new StatusEffect(CCType.SILENCE, duration, caster, 1);
        getPlugin().getStatusEffectManager().applyEffect(victim, silence);

        // Mark as polymorphed
        victim.setMetadata("polymorphed", new FixedMetadataValue(getPlugin(), true));
        victim.setMetadata("polymorph_caster", new FixedMetadataValue(getPlugin(), caster.getUniqueId()));
        // Add specific metadata to prevent combat actions
        victim.setMetadata("prevent_attacks", new FixedMetadataValue(getPlugin(), true));

        // Remove from combat
        getPlugin().getCombatManager().exitCombat(victim);

        // Start random movement
        startRandomMovement(victim);

        // Play transformation effects
        playPolymorphEffects(victim.getLocation());

        // Schedule removal
        getPlugin().getServer().getScheduler().runTaskLater(getPlugin(), () -> {
            if (victim.hasMetadata("polymorphed")) {
                removePolymorph(victim);
            }
        }, duration / 50); // Convert milliseconds to ticks

        // Broadcast effect
        broadcastLocalSkillMessage(caster, "§6[" + getPlayerClass(caster) + "] " +
                caster.getName() + " transformed " + victim.getName() + " into a chicken!");

        // Send message to victim
        victim.sendMessage("§e✦ You've been transformed into a chicken! You've lost control of your movement!");
    }

    private void startRandomMovement(Player victim) {
        // Cancel any existing movement task
        if (movementTask != null) {
            movementTask.cancel();
        }

        // Create new movement task
        movementTask = new BukkitRunnable() {
            private int ticksRunning = 0;
            private Vector currentDirection = null;
            private static final int DIRECTION_CHANGE_INTERVAL = 20; // Change direction every second

            @Override
            public void run() {
                if (!victim.hasMetadata("polymorphed") || !victim.isOnline()) {
                    this.cancel();
                    return;
                }

                // Update direction periodically or initially
                if (currentDirection == null || ticksRunning % DIRECTION_CHANGE_INTERVAL == 0) {
                    // Random direction in XZ plane
                    double angle = random.nextDouble() * Math.PI * 2;
                    currentDirection = new Vector(
                            Math.cos(angle) * 0.2, // X component
                            0,                     // Y component (no vertical movement)
                            Math.sin(angle) * 0.2  // Z component
                    );

                    // Small random vertical movement occasionally
                    if (random.nextFloat() < 0.2 && victim.isOnGround()) {
                        currentDirection.setY(0.2); // Small jump
                    }
                }

                // Apply movement
                victim.setVelocity(currentDirection);

                // Spawn chicken particles occasionally
                if (random.nextFloat() < 0.1) {
                    victim.getWorld().spawnParticle(
                            Particle.END_ROD,
                            victim.getLocation().add(0, 1, 0),
                            3, 0.2, 0.2, 0.2, 0.02
                    );
                }

                ticksRunning++;
            }
        };

        // Start the movement task
        movementTask.runTaskTimer(getPlugin(), 0L, 1L);
    }

    private void removePolymorph(Player victim) {
        if (!victim.hasMetadata("polymorphed")) return;

        // Stop movement control
        if (movementTask != null) {
            movementTask.cancel();
            movementTask = null;
        }

        // Remove disguise and clean up display name
        DisguiseAPI.undisguiseToAll(victim);
        victim.setCustomName(null);
        victim.setCustomNameVisible(false);

        // Remove metadata
        victim.removeMetadata("polymorphed", getPlugin());
        victim.removeMetadata("polymorph_caster", getPlugin());
        // Remove the prevent_attacks metadata
        victim.removeMetadata("prevent_attacks", getPlugin());

        // Remove silence effect (CC system will handle immunity)
        getPlugin().getStatusEffectManager().removeEffect(victim, CCType.SILENCE);

        // Play removal effects
        playPolymorphEndEffects(victim.getLocation());
        victim.sendMessage("§6✦ You return to your normal form!");
    }

    private void playPolymorphEffects(Location location) {
        location.getWorld().spawnParticle(Particle.WITCH, location.add(0, 1, 0),
                30, 0.5, 1.0, 0.5, 0.1);
        location.getWorld().spawnParticle(Particle.CLOUD, location,
                20, 0.3, 0.5, 0.3, 0.05);
        location.getWorld().playSound(location, Sound.ENTITY_CHICKEN_AMBIENT, 1.0f, 0.5f);
        location.getWorld().playSound(location, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.2f);
    }

    private void playPolymorphEndEffects(Location location) {
        location.getWorld().spawnParticle(Particle.INSTANT_EFFECT, location.add(0, 1, 0),
                30, 0.5, 1.0, 0.5, 0.1);
        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
    }
}
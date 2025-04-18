package com.michael.mmorpg.skills.hunter;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SummonPetSkill extends Skill implements Listener {
    public static final Map<UUID, Wolf> ACTIVE_PETS = new HashMap<>();
    private final Map<UUID, BukkitRunnable> combatCheckers = new HashMap<>();
    private final Map<UUID, Long> lastPathfindingUpdate = new HashMap<>();

    private final double petHealth;
    private final double petDamage;
    private final double petMovementSpeed;
    private final double petRange;
    private final double followDistance;
    private final long pathfindingUpdateDelay;

    public SummonPetSkill(ConfigurationSection config) {
        super(config);
        this.petHealth = config.getDouble("petHealth", 40.0);
        this.petDamage = config.getDouble("petDamage", 5.0);
        this.petMovementSpeed = config.getDouble("petMovementSpeed", 0.3);
        this.petRange = config.getDouble("petRange", 25.0);
        this.followDistance = config.getDouble("followDistance", 3.0);
        this.pathfindingUpdateDelay = config.getLong("pathfindingUpdateDelay", 10L); // Update path every 10 ticks
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void performSkill(Player player) {
        // Check for existing pet
        if (ACTIVE_PETS.containsKey(player.getUniqueId())) {
            player.sendMessage("§c✦ You already have an active pet! Use /skill dismisspet to dismiss it.");
            return;
        }

        // Summon the wolf
        Wolf pet = (Wolf) player.getWorld().spawnEntity(player.getLocation(), EntityType.WOLF);

        // Configure wolf
        pet.setTamed(true);
        pet.setOwner(player);
        pet.setAdult();
        pet.setMetadata("hunter_pet", new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        pet.setMetadata("party_member", new FixedMetadataValue(plugin, player.getUniqueId().toString())); // Mark as in player's party
        pet.setCustomName("§6" + player.getName() + "'s Wolf");
        pet.setCustomNameVisible(true);
        pet.setCollarColor(org.bukkit.DyeColor.BLUE); // Distinctive color

        // Set attributes
        pet.getAttribute(Attribute.MAX_HEALTH).setBaseValue(petHealth);
        pet.setHealth(petHealth);
        pet.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(petDamage);
        pet.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(petMovementSpeed);
        pet.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(petRange);

        // Initial follow state
        pet.setSitting(false);

        // Store pet reference
        ACTIVE_PETS.put(player.getUniqueId(), pet);

        // Start pet controller for AI improvement and combat checking
        startPetController(player, pet);

        // Play wolf spawn sound
        pet.getWorld().playSound(pet.getLocation(), Sound.ENTITY_WOLF_HOWL, 1.0f, 1.0f);

        // Success message
        broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                player.getName() + "'s wolf companion has arrived!");

        setSkillSuccess(true);
    }

    private void startPetController(Player owner, Wolf pet) {
        // Cancel any existing checker
        BukkitRunnable existingTask = combatCheckers.remove(owner.getUniqueId());
        if (existingTask != null) {
            existingTask.cancel();
        }

        // Create new controller with improved AI
        BukkitRunnable controller = new BukkitRunnable() {
            @Override
            public void run() {
                // Exit conditions
                if (!pet.isValid() || !owner.isOnline() || pet.isDead()) {
                    cancel();
                    combatCheckers.remove(owner.getUniqueId());
                    return;
                }

                // Combat state check
                boolean ownerInCombat = plugin.getCombatManager().isInCombat(owner);
                LivingEntity petTarget = pet.getTarget();

                // If owner dies, make pet defensive
                if (owner.isDead()) {
                    // Don't clear target if already fighting
                    if (petTarget == null) {
                        // Look for nearby enemies that might have killed the owner
                        for (Entity entity : pet.getNearbyEntities(10, 10, 10)) {
                            if (entity instanceof LivingEntity && !(entity instanceof Player) &&
                                    !entity.equals(owner) && !isAlly(owner, entity)) {
                                pet.setTarget((LivingEntity) entity);
                                break;
                            }
                        }
                    }
                    return;
                }

                // If owner is not in combat, clear pet's target and manage following behavior
                if (!ownerInCombat) {
                    if (petTarget != null) {
                        pet.setTarget(null);
                    }

                    // Smart following - only update path periodically to avoid jerky movement
                    long currentTime = System.currentTimeMillis();
                    long lastUpdate = lastPathfindingUpdate.getOrDefault(owner.getUniqueId(), 0L);

                    if (currentTime - lastUpdate > pathfindingUpdateDelay * 50) { // Convert ticks to ms
                        double distanceToOwner = pet.getLocation().distance(owner.getLocation());

                        // If too far from owner, teleport to avoid getting stuck
                        if (distanceToOwner > 30) {
                            pet.teleport(owner.getLocation());
                        }
                        // If moderately far, run to catch up
                        else if (distanceToOwner > followDistance * 3) {
                            // Use direct movement rather than pathfinder
                            Vector direction = owner.getLocation().toVector().subtract(pet.getLocation().toVector()).normalize();
                            pet.setVelocity(direction.multiply(0.5));
                        }
                        // If somewhat far but not too far, walk normally
                        else if (distanceToOwner > followDistance) {
                            // Make wolf walk toward owner
                            pet.setTarget(null);
                            pet.setSitting(false);
                            // Simple movement toward owner
                            pet.getLocation().setDirection(owner.getLocation().subtract(pet.getLocation()).toVector());
                        }
                        // If close enough, stop moving
                        else {
                            pet.setVelocity(new Vector(0, 0, 0));
                        }

                        lastPathfindingUpdate.put(owner.getUniqueId(), currentTime);
                    }
                } else {
                    // Owner is in combat - help with combat
                    if (petTarget == null || !petTarget.isValid() || petTarget.isDead()) {
                        // Look for owner's target by checking nearby entities
                        // since we can't directly get the last target

                        // First check entities the owner is looking at
                        double closestDistance = Double.MAX_VALUE;
                        LivingEntity bestTarget = null;

                        for (Entity entity : owner.getNearbyEntities(10, 5, 10)) {
                            if (!(entity instanceof LivingEntity) || entity instanceof Player ||
                                    entity.equals(owner) || entity.equals(pet) || isAlly(owner, entity)) {
                                continue;
                            }

                            LivingEntity livingEntity = (LivingEntity) entity;

                            // Skip dead entities
                            if (livingEntity.isDead() || !livingEntity.isValid()) {
                                continue;
                            }

                            // Check if owner is looking at this entity
                            Vector ownerDirection = owner.getLocation().getDirection();
                            Vector toEntityVector = livingEntity.getLocation().subtract(owner.getLocation()).toVector().normalize();

                            double dot = ownerDirection.dot(toEntityVector);
                            // If owner is facing this entity (within about 45 degrees)
                            if (dot > 0.7) {
                                double distance = owner.getLocation().distance(livingEntity.getLocation());
                                if (distance < closestDistance) {
                                    closestDistance = distance;
                                    bestTarget = livingEntity;
                                }
                            }
                        }

                        if (bestTarget != null) {
                            // Target what owner is looking at
                            pet.setTarget(bestTarget);
                        } else {
                            // Look for any nearby enemies
                            for (Entity entity : owner.getNearbyEntities(10, 5, 10)) {
                                if (entity instanceof LivingEntity && !(entity instanceof Player) &&
                                        !entity.equals(owner) && !entity.equals(pet) && !isAlly(owner, entity)) {
                                    LivingEntity livingEntity = (LivingEntity) entity;
                                    if (!livingEntity.isDead() && livingEntity.isValid()) {
                                        pet.setTarget(livingEntity);
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    // If we're far from our target, update movement less frequently
                    if (petTarget != null && petTarget.isValid() && !petTarget.isDead()) {
                        long currentTime = System.currentTimeMillis();
                        long lastUpdate = lastPathfindingUpdate.getOrDefault(owner.getUniqueId(), 0L);

                        if (currentTime - lastUpdate > pathfindingUpdateDelay * 50) {
                            // Manual movement toward target
                            Vector direction = petTarget.getLocation().toVector().subtract(pet.getLocation().toVector()).normalize();
                            pet.setVelocity(direction.multiply(0.6)); // Faster in combat
                            lastPathfindingUpdate.put(owner.getUniqueId(), currentTime);
                        }
                    }
                }

                // Look for any nearby traps that might be owned by the hunter
                // and prevent pet from triggering them by setting metadata
                if (pet.getLocation().distance(owner.getLocation()) < 15) {
                    pet.setMetadata("ignore_hunter_traps", new FixedMetadataValue(plugin, owner.getUniqueId().toString()));
                } else {
                    if (pet.hasMetadata("ignore_hunter_traps")) {
                        pet.removeMetadata("ignore_hunter_traps", plugin);
                    }
                }
            }
        };

        // Run the controller every tick (20 times per second)
        controller.runTaskTimer(plugin, 0L, 1L);
        combatCheckers.put(owner.getUniqueId(), controller);
    }

    /**
     * Checks if an entity is an ally of the player (pet or party member)
     */
    private boolean isAlly(Player player, Entity entity) {
        // Check if it's the player's pet
        if (entity instanceof Wolf && entity.hasMetadata("hunter_pet")) {
            String petOwnerId = entity.getMetadata("hunter_pet").get(0).asString();
            if (player.getUniqueId().toString().equals(petOwnerId)) {
                return true;
            }
        }

        // Check if it's a party member
        if (entity instanceof Player) {
            Player otherPlayer = (Player) entity;
            if (plugin.getPartyManager().getParty(player) != null &&
                    plugin.getPartyManager().getParty(player).isMember(otherPlayer)) {
                return true;
            }
        }

        return false;
    }

    @EventHandler
    public void onWolfTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Wolf) ||
                !event.getEntity().hasMetadata("hunter_pet")) return;

        Wolf pet = (Wolf) event.getEntity();
        LivingEntity target = event.getTarget();

        // Get the pet's owner
        String petOwnerId = pet.getMetadata("hunter_pet").get(0).asString();
        Player owner = plugin.getServer().getPlayer(UUID.fromString(petOwnerId));

        if (owner != null) {
            // If owner is not in combat, prevent targeting
            if (!plugin.getCombatManager().isInCombat(owner)) {
                event.setCancelled(true);
                return;
            }

            // Additional target validity checks
            if (target instanceof Player) {
                Player targetPlayer = (Player) target;
                // Don't target owner
                if (targetPlayer.equals(owner)) {
                    event.setCancelled(true);
                    return;
                }

                // Don't target party members
                if (plugin.getPartyManager().getParty(owner) != null &&
                        plugin.getPartyManager().getParty(owner).isMember(targetPlayer)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPetDamage(EntityDamageByEntityEvent event) {
        // Check if the damaged entity is a pet
        if (!(event.getEntity() instanceof Wolf) ||
                !event.getEntity().hasMetadata("hunter_pet")) return;

        Wolf pet = (Wolf) event.getEntity();
        String petOwnerId = pet.getMetadata("hunter_pet").get(0).asString();
        Player owner = plugin.getServer().getPlayer(UUID.fromString(petOwnerId));

        // Determine the actual damage source (handle projectiles and AOE)
        Entity damager = event.getDamager();
        Player attacker = null;

        // Handle direct player attacks
        if (damager instanceof Player) {
            attacker = (Player) damager;
        }
        // Handle projectiles shot by players
        else if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Player) {
            attacker = (Player) ((Projectile) damager).getShooter();
        }
        // Handle skill damage from metadata
        else if (damager.hasMetadata("skill_damage") && damager.getMetadata("skill_damage").get(0).value() instanceof Player) {
            attacker = (Player) damager.getMetadata("skill_damage").get(0).value();
        }

        // If we found a player attacker, check friendly fire
        if (attacker != null) {
            // Prevent owner damage
            if (attacker.getUniqueId().toString().equals(petOwnerId)) {
                event.setCancelled(true);
                return;
            }

            // Prevent party damage
            if (owner != null) {
                if (plugin.getPartyManager().getParty(owner) != null &&
                        plugin.getPartyManager().getParty(owner).isMember(attacker)) {
                    event.setCancelled(true);
                }
            }
        }

        // Process skill damage that isn't from a specific player by checking metadata
        if (damager.hasMetadata("skill_damage")) {
            Object damageSource = damager.getMetadata("skill_damage").get(0).value();
            if (damageSource != null) {
                String sourceString = damageSource.toString();
                // If the damage comes from the owner's UUID, cancel it
                if (sourceString.contains(petOwnerId)) {
                    event.setCancelled(true);
                    return;
                }

                // If owner is in a party, check if damage comes from a party member
                if (owner != null && plugin.getPartyManager().getParty(owner) != null) {
                    for (Player partyMember : plugin.getPartyManager().getParty(owner).getMembers()) {
                        if (sourceString.contains(partyMember.getUniqueId().toString())) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPetDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wolf) ||
                !event.getEntity().hasMetadata("hunter_pet")) return;

        Wolf pet = (Wolf) event.getEntity();
        String petOwnerId = pet.getMetadata("hunter_pet").get(0).asString();
        Player owner = plugin.getServer().getPlayer(UUID.fromString(petOwnerId));

        if (owner != null) {
            // Clear pet reference
            ACTIVE_PETS.remove(owner.getUniqueId());

            // Cancel combat checker
            BukkitRunnable checker = combatCheckers.remove(owner.getUniqueId());
            if (checker != null) {
                checker.cancel();
            }

            // Clear pathfinding timestamps
            lastPathfindingUpdate.remove(owner.getUniqueId());

            // Set cooldown
            plugin.getSkillManager().setCooldown(owner, getName(), getCooldown());

            // Death message
            broadcastLocalSkillMessage(owner, "§c[" + getPlayerClass(owner) + "] " +
                    owner.getName() + "'s wolf companion has fallen!");
        }
    }

    /**
     * Use this method in other hunter skills to check if an entity is the player's pet
     */
    public static boolean isPlayerPet(Entity entity, Player player) {
        if (!(entity instanceof Wolf) || !entity.hasMetadata("hunter_pet")) {
            return false;
        }

        String petOwnerId = entity.getMetadata("hunter_pet").get(0).asString();
        return player.getUniqueId().toString().equals(petOwnerId);
    }
}
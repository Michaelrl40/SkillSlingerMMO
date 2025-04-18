package com.michael.mmorpg.skills.engineer;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.party.Party;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.*;

public class AutoTurretSkill extends Skill {
    private final double damage;
    private final double range;
    private final double attackSpeed;
    private final int duration;
    private final int maxTurrets;
    private final double turretHealth;

    private static final Map<UUID, Set<ArmorStand>> playerTurrets = new HashMap<>();

    public AutoTurretSkill(ConfigurationSection config) {
        super(config);
        this.damage = config.getDouble("damage", 5.0);
        this.range = config.getDouble("range", 10.0);
        this.attackSpeed = config.getDouble("attackSpeed", 1.0);
        this.duration = config.getInt("duration", 30);
        this.maxTurrets = config.getInt("maxTurrets", 2);
        this.turretHealth = config.getDouble("turretHealth", 50.0);
    }

    @Override
    protected void performSkill(Player player) {
        // Check max turrets
        Set<ArmorStand> turrets = playerTurrets.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (turrets.size() >= maxTurrets) {
            player.sendMessage("§c✦ You have too many active turrets! (Maximum: " + maxTurrets + ")");
            setSkillSuccess(false);
            return;
        }

        // Get placement location (block player is looking at)
        Location target = player.getTargetBlock(null, 5).getLocation();

        // Find the ground below the target if it's in the air
        while (target.clone().add(0, -1, 0).getBlock().isPassable() && target.getY() > 0) {
            target.subtract(0, 1, 0);
        }

        // Center the turret on the block and place it on top
        target.add(0.5, 1, 0.5);

        // Create turret
        createTurret(player, target);
        setSkillSuccess(true);
    }

    private void createTurret(Player owner, Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Create armor stand
        ArmorStand turret = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);

        // Basic properties
        turret.setSmall(false);
        turret.setMaxHealth(turretHealth);
        turret.setHealth(turretHealth);
        turret.setGravity(false);
        turret.setVisible(true);
        turret.setCustomName("§6⚙ Engineer's Turret");
        turret.setCustomNameVisible(true);

        // Make armor stand completely locked
        turret.setBasePlate(true);
        turret.setArms(true);
        turret.setCanPickupItems(false);
        turret.setInvulnerable(true); // Make it invulnerable to prevent equipment breaking

        // Lock ALL equipment slots
        for (org.bukkit.inventory.EquipmentSlot slot : org.bukkit.inventory.EquipmentSlot.values()) {
            turret.addEquipmentLock(slot, org.bukkit.entity.ArmorStand.LockType.ADDING_OR_CHANGING);
            turret.addEquipmentLock(slot, org.bukkit.entity.ArmorStand.LockType.REMOVING_OR_CHANGING);
        }

        // Set engineer-themed equipment that can't be removed
        turret.getEquipment().setHelmet(new ItemStack(Material.DISPENSER));
        turret.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        turret.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        turret.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));

        // Add engineer-themed items in hands
        ItemStack rightHand = new ItemStack(Material.CROSSBOW); // or any other fitting item
        ItemStack leftHand = new ItemStack(Material.REDSTONE_TORCH);
        turret.getEquipment().setItemInMainHand(rightHand);
        turret.getEquipment().setItemInOffHand(leftHand);

        // Set arm poses to hold items properly
        turret.setRightArmPose(new EulerAngle(Math.toRadians(90), Math.toRadians(0), Math.toRadians(0)));
        turret.setLeftArmPose(new EulerAngle(Math.toRadians(90), Math.toRadians(0), Math.toRadians(0)));

        // Set metadata
        turret.setMetadata("turret_owner", new FixedMetadataValue(plugin, owner.getUniqueId()));
        turret.setMetadata("turret_health", new FixedMetadataValue(plugin, turretHealth));


        // Add to tracking
        playerTurrets.computeIfAbsent(owner.getUniqueId(), k -> new HashSet<>()).add(turret);

        // Deployment effects
        deploymentEffects(location);

        // Start turret AI
        startTurretBehavior(owner, turret);

        // Set duration
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (turret.isValid()) {
                removeTurret(turret, owner);
            }
        }, duration * 20L);
    }

    private void deploymentEffects(Location location) {
        World world = location.getWorld();

        // Steam effect
        world.spawnParticle(Particle.CLOUD, location, 30, 0.5, 0.5, 0.5, 0.05);

        // Mechanical assembly effect
        for (int i = 0; i < 360; i += 20) {
            double rad = Math.toRadians(i);
            Location particleLoc = location.clone().add(
                    Math.cos(rad) * 0.5,
                    0,
                    Math.sin(rad) * 0.5
            );
            world.spawnParticle(Particle.CRIT, particleLoc, 1, 0, 0, 0, 0);
        }

        // Sound effects
        world.playSound(location, Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
        world.playSound(location, Sound.BLOCK_PISTON_EXTEND, 1.0f, 1.0f);
        world.playSound(location, Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.2f);
    }

    private void startTurretBehavior(Player owner, ArmorStand turret) {
        new BukkitRunnable() {
            private long lastShot = 0;

            @Override
            public void run() {
                // Check if turret is no longer valid or owner is offline
                if (!turret.isValid() || !owner.isOnline()) {
                    // Clean up the turret tracking when it's no longer valid
                    Set<ArmorStand> turrets = playerTurrets.get(owner.getUniqueId());
                    if (turrets != null) {
                        turrets.remove(turret);
                        if (turrets.isEmpty()) {
                            playerTurrets.remove(owner.getUniqueId());
                        }
                    }
                    this.cancel();
                    return;
                }

                // Check if it's time for next shot
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastShot < (1000 / attackSpeed)) {
                    return;
                }

                // Find nearest valid target
                LivingEntity target = findNearestTarget(owner, turret);
                if (target != null) {
                    // Rotate turret head to face target
                    Location turretLoc = turret.getLocation();
                    Vector direction = target.getLocation().subtract(turretLoc).toVector();
                    turretLoc.setDirection(direction);

                    // Update turret head rotation
                    double angle = Math.atan2(-direction.getX(), direction.getZ());
                    turret.setHeadPose(new EulerAngle(0, angle, 0));

                    // Fire projectile
                    fireAtTarget(turret, target, owner);
                    lastShot = currentTime;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private LivingEntity findNearestTarget(Player owner, ArmorStand turret) {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Entity entity : turret.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity) || entity == owner || entity == turret) {
                continue;
            }

            LivingEntity livingEntity = (LivingEntity) entity;

            // Skip if entity is not a valid target
            if (!isValidTarget(owner, livingEntity)) {
                continue;
            }

            double distance = entity.getLocation().distance(turret.getLocation());
            if (distance < nearestDistance) {
                nearest = livingEntity;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    private boolean isValidTarget(Player owner, LivingEntity target) {
        // Skip other turrets
        if (target instanceof ArmorStand) {
            return false;
        }

        // Skip friendly NPCs (pets, allays, etc.)
        if (target.hasMetadata("owner")) {
            return false;
        }

        // Skip if target is in owner's party
        if (target instanceof Player) {
            Player targetPlayer = (Player) target;
            Party ownerParty = plugin.getPartyManager().getParty(owner);
            if (ownerParty != null && ownerParty.isMember(targetPlayer)) {
                return false;
            }
        }

        // Only target hostile mobs and enemy players
        return (target instanceof Monster) ||
                (target instanceof Player && target != owner) ||
                (target instanceof Wither) ||
                (target instanceof EnderDragon);
    }

    private void fireAtTarget(ArmorStand turret, LivingEntity target, Player owner) {
        Location turretEye = turret.getLocation().add(0, 1.6, 0);
        Location targetLocation = target.getLocation().add(0, 1, 0); // Aim at center mass
        Vector direction = targetLocation.subtract(turretEye).toVector().normalize();

        // Visual and sound effects for firing
        turret.getWorld().playSound(turretEye, Sound.BLOCK_DISPENSER_LAUNCH, 0.5f, 2.0f);
        turret.getWorld().playSound(turretEye, Sound.BLOCK_CHAIN_BREAK, 0.3f, 2.0f);

        // Instant hit visual effect
        new BukkitRunnable() {
            private final Location particleLoc = turretEye.clone();
            private double progress = 0;
            private final double maxProgress = 1.0;
            private final double stepSize = 0.2; // Controls the density of particles

            @Override
            public void run() {
                if (progress >= maxProgress) {
                    this.cancel();
                    return;
                }

                // Calculate current position
                Vector currentDirection = direction.clone().multiply(progress);
                particleLoc.setX(turretEye.getX() + currentDirection.getX());
                particleLoc.setY(turretEye.getY() + currentDirection.getY());
                particleLoc.setZ(turretEye.getZ() + currentDirection.getZ());

                // Projectile trail effect
                turret.getWorld().spawnParticle(
                        Particle.DUST,
                        particleLoc,
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1)
                );

                progress += stepSize;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Direct hit implementation
        if (target.isValid() && !target.isDead()) {
            // Apply damage
            target.setMetadata("skill_damage", new FixedMetadataValue(plugin, owner));
            target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, damage));
            target.damage(0.1, owner); // Trigger damage event

            // Hit effect at target location
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0),
                    15, 0.2, 0.2, 0.2, 0.2);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 1.2f);
        }

        // Muzzle flash effect
        turret.getWorld().spawnParticle(
                Particle.FLASH,
                turretEye,
                1, 0, 0, 0, 0
        );
        turret.getWorld().spawnParticle(
                Particle.DUST,
                turretEye,
                10, 0.1, 0.1, 0.1, 0.1,
                new Particle.DustOptions(Color.fromRGB(255, 50, 50), 1)
        );
    }

    private void removeTurret(ArmorStand turret, Player owner) {
        // Visual and sound effects
        Location loc = turret.getLocation();
        turret.getWorld().spawnParticle(Particle.CLOUD, loc, 20, 0.5, 0.5, 0.5, 0.05);
        turret.getWorld().playSound(loc, Sound.BLOCK_PISTON_CONTRACT, 1.0f, 1.0f);

        // Remove from tracking
        Set<ArmorStand> turrets = playerTurrets.get(owner.getUniqueId());
        if (turrets != null) {
            turrets.remove(turret);
            // If no more turrets, remove the player's entry entirely
            if (turrets.isEmpty()) {
                playerTurrets.remove(owner.getUniqueId());
            }
        }

        // Remove entity
        turret.remove();
    }

    public static void cleanup() {
        for (Set<ArmorStand> turrets : playerTurrets.values()) {
            for (ArmorStand turret : turrets) {
                if (turret.isValid()) {
                    turret.remove();
                }
            }
        }
        playerTurrets.clear();
    }
}
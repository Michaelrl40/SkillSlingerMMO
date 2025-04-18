package com.michael.mmorpg.skills.engineer;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class MegaCannonSkill extends Skill {
    private final double projectileDamage;
    private final double explosionRadius;
    private final double projectileSpeed;
    private final int maxAmmo;

    private static final Map<UUID, Integer> ammoCount = new HashMap<>();
    private static final String CANNON_ID = "MEGA_CANNON";

    private final double shotCooldown;  // Cooldown between shots in seconds
    private final int cannonDuration;  // How long the cannon lasts in seconds
    private static final Map<UUID, Long> lastShotTime = new HashMap<>();

    public MegaCannonSkill(ConfigurationSection config) {
        super(config);
        this.projectileDamage = config.getDouble("projectileDamage", 20.0);
        this.explosionRadius = config.getDouble("explosionRadius", 4.0);
        this.projectileSpeed = config.getDouble("projectileSpeed", 1.5);
        this.maxAmmo = config.getInt("maxAmmo", 3);
        this.shotCooldown = config.getDouble("shotCooldown", 1.0);
        this.cannonDuration = config.getInt("cannonDuration", 20);
    }

    @Override
    protected void performSkill(Player player) {
        // Create and give cannon item
        giveCannon(player);
        setSkillSuccess(true);
    }

    private void giveCannon(Player player) {
        ItemStack cannon = new ItemStack(Material.DISPENSER);
        ItemMeta meta = cannon.getItemMeta();
        if (meta == null) return;

        // Set custom name and lore
        meta.setDisplayName("§6§lMega Cannon");
        List<String> lore = new ArrayList<>();
        lore.add("§7A powerful engineering marvel!");
        lore.add("§eAmmo: " + maxAmmo);
        lore.add("§cRight-click to fire!");
        meta.setLore(lore);

        // Add custom tag
        meta.setCustomModelData(12345); // Unique identifier
        cannon.setItemMeta(meta);

        // Give item to player
        PlayerInventory inv = player.getInventory();
        inv.addItem(cannon);

        // Initialize ammo count
        ammoCount.put(player.getUniqueId(), maxAmmo);

        // Play equip effects
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.SMOKE,
                player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);

        player.sendMessage("§6✦ You've equipped the Mega Cannon! (" + maxAmmo + " shots remaining)");

        // Start expiration timer
        new BukkitRunnable() {
            private int timeLeft = cannonDuration;

            @Override
            public void run() {
                if (!player.isOnline() || !hasCannon(player)) {
                    this.cancel();
                    return;
                }

                if (timeLeft <= 5 && timeLeft > 0) {
                    player.sendMessage("§c✦ Your Mega Cannon will expire in " + timeLeft + " seconds!");
                }

                if (timeLeft <= 0) {
                    removeCannon(player);
                    this.cancel();
                    return;
                }

                timeLeft--;
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second
    }

    private boolean isMegaCannon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == 12345;
    }

    private boolean hasCannon(Player player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item != null && isMegaCannon(item)) {
                return true;
            }
        }
        return false;
    }

    public void fireProjectile(Player player) {
        // Check cooldown
        long currentTime = System.currentTimeMillis();
        long lastShot = lastShotTime.getOrDefault(player.getUniqueId(), 0L);
        long timeLeft = (long)((lastShot + (shotCooldown * 1000) - currentTime) / 1000);

        if (currentTime - lastShot < shotCooldown * 1000) {
            player.sendMessage("§c✦ Cannon is reloading! (" + timeLeft + " seconds)");
            return;
        }

        // Check ammo
        int ammo = ammoCount.getOrDefault(player.getUniqueId(), 0);
        if (ammo <= 0) {
            player.sendMessage("§c✦ Out of ammo!");
            removeCannon(player);
            return;
        }

        // Update ammo count
        ammoCount.put(player.getUniqueId(), ammo - 1);
        lastShotTime.put(player.getUniqueId(), currentTime);
        updateAmmoDisplay(player);

        // Get projectile spawn location (slightly in front and above player)
        Location spawnLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(1));
        Vector direction = player.getLocation().getDirection();

        // Create snowball as projectile base
        Snowball projectile = player.getWorld().spawn(spawnLoc, Snowball.class);
        projectile.setShooter(player);
        projectile.setMetadata("mega_cannon_projectile", new FixedMetadataValue(plugin, true));
        projectile.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        projectile.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, projectileDamage));
        projectile.setVelocity(direction.multiply(projectileSpeed));

        // Projectile effects
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!projectile.isValid() || projectile.isDead()) {
                    this.cancel();
                    return;
                }

                // Trailing particles
                projectile.getWorld().spawnParticle(Particle.SMOKE,
                        projectile.getLocation(), 5, 0.1, 0.1, 0.1, 0.05);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Play fire effects
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2.0f);
        player.getWorld().spawnParticle(Particle.FLAME, spawnLoc, 10, 0.1, 0.1, 0.1, 0.1);

        // Check if last shot
        if (ammo - 1 <= 0) {
            removeCannon(player);
        }
    }

    private void updateAmmoDisplay(Player player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item != null && isMegaCannon(item)) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.getLore();
                    if (lore != null && !lore.isEmpty()) {
                        lore.set(1, "§eAmmo: " + ammoCount.get(player.getUniqueId()));
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                    }
                }
                break;
            }
        }
    }

    private void removeCannon(Player player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item != null && isMegaCannon(item)) {
                inv.remove(item);
                break;
            }
        }
        ammoCount.remove(player.getUniqueId());

        // Play remove effects
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        player.sendMessage("§c✦ Your Mega Cannon has disappeared!");
    }

    public void handleProjectileHit(Entity projectile, Entity hitEntity) {
        Location loc = projectile.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // Create explosion effect
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        // Area damage
        for (Entity entity : world.getNearbyEntities(loc, explosionRadius, explosionRadius, explosionRadius)) {
            if (!(entity instanceof LivingEntity)) continue;

            // Get shooter from projectile
            Entity shooter = null;
            if (projectile instanceof Projectile) {
                shooter = (Entity) ((Projectile) projectile).getShooter();
                if (entity == shooter) continue; // Skip damaging the shooter
            }

            LivingEntity target = (LivingEntity) entity;
            if (shooter instanceof Player) {
                Player playerShooter = (Player) shooter;

                // Skip party members
                if (target instanceof Player && plugin.getPartyManager().getParty(playerShooter) != null &&
                        plugin.getPartyManager().getParty(playerShooter).isMember((Player)target)) {
                    continue;
                }

                // Calculate damage based on distance
                double distance = target.getLocation().distance(loc);
                double scaledDamage = projectileDamage * (1 - (distance / explosionRadius));

                // Apply damage
                target.setMetadata("skill_damage", new FixedMetadataValue(plugin, playerShooter));
                target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, scaledDamage));
                target.damage(0.1, playerShooter);
            }
        }
    }

    public static void cleanup() {
        // Remove all cannons from online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();
            for (ItemStack item : inv.getContents()) {
                if (item != null && item.hasItemMeta() &&
                        item.getItemMeta().hasCustomModelData() &&
                        item.getItemMeta().getCustomModelData() == 12345) {
                    inv.remove(item);
                }
            }
        }
        ammoCount.clear();
    }
}
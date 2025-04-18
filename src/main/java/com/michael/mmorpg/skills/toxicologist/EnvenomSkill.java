package com.michael.mmorpg.skills.toxicologist;

import com.michael.mmorpg.managers.DamageDisplayManager;
import com.michael.mmorpg.skills.Skill;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EnvenomSkill extends Skill implements Listener {
    private final double poisonDamage;
    private final Map<UUID, BukkitRunnable> drainTasks = new HashMap<>();

    public EnvenomSkill(ConfigurationSection config) {
        super(config);
        this.poisonDamage = config.getDouble("poisonDamage", 2.0);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void performSkill(Player player) {
        // This will be called for both activation and deactivation
        if (!isToggleActive(player)) {
            activateEnvenom(player);
            setSkillSuccess(true);
        }
    }

    private void activateEnvenom(Player player) {
        // Apply effects
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1.0F, 1.2F);
        player.getWorld().spawnParticle(Particle.FALLING_DRIPSTONE_WATER,
                player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0);
        player.setMetadata("envenom_active", new FixedMetadataValue(plugin, true));

        // Add toxin drain to player
        PlayerData data = getPlugin().getPlayerManager().getPlayerData(player);
        if (data != null) {
            data.addToxinDrain(name, toxinDrainPerTick);
        }

        startDrainTask(player);
        player.sendMessage("§2☠ Your weapon is now coated with deadly poison! Use again to deactivate.");
    }

    private void startDrainTask(Player player) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isToggleActive(player)) {
                    cancel();
                    return;
                }

                PlayerData data = getPlugin().getPlayerManager().getPlayerData(player);
                if (data == null || data.getCurrentToxin() < toxinDrainPerTick) {
                    player.sendMessage("§c☠ Envenom ended - Not enough toxin!");
                    deactivateToggle(player);
                    return;
                }

                // Use toxin like stamina is used in BattleStance
                if (!data.useToxin(toxinDrainPerTick)) {
                    player.sendMessage("§c☠ Envenom ended - Not enough toxin!");
                    deactivateToggle(player);
                    return;
                }
            }
        };

        task.runTaskTimer(getPlugin(), 20L, 20L);
        drainTasks.put(player.getUniqueId(), task);
    }

    @Override
    protected void onToggleDeactivate(Player player) {
        // Clean up metadata
        player.removeMetadata("envenom_active", plugin);

        // Remove toxin drain from player
        PlayerData data = getPlugin().getPlayerManager().getPlayerData(player);
        if (data != null) {
            data.removeToxinDrain(name);
        }

        // Cancel drain task
        BukkitRunnable task = drainTasks.remove(player.getUniqueId());
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }

        player.sendMessage("§c☠ Envenom deactivated!");
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        Player player = (Player) event.getDamager();
        if (!player.hasMetadata("envenom_active")) return;

        // Simple recursion check
        if (event.getEntity().hasMetadata("envenom_pending")) return;

        LivingEntity target = (LivingEntity) event.getEntity();

        // Let the normal weapon hit process as usual (will be reduced by armor)
        // We don't modify event.setDamage() here anymore

        // Mark that we're processing envenom
        target.setMetadata("envenom_pending", new FixedMetadataValue(plugin, true));

        // Apply the poison damage separately as magic damage (ignoring armor)
        target.setMetadata("skill_damage", new FixedMetadataValue(plugin, player));
        target.setMetadata("skill_damage_amount", new FixedMetadataValue(plugin, poisonDamage));
        target.setMetadata("magic_damage", new FixedMetadataValue(plugin, true));

        // Apply a tiny bit of damage to trigger our damage handler
        target.damage(0.1, player);

        // Clear the pending flag
        target.removeMetadata("envenom_pending", plugin);

        // Visual and sound effects
        event.getEntity().getWorld().spawnParticle(Particle.FALLING_DRIPSTONE_WATER,
                event.getEntity().getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0);
        event.getEntity().getWorld().playSound(event.getEntity().getLocation(),
                Sound.ENTITY_SPIDER_AMBIENT, 0.5F, 1.2F);

        // Display just the poison portion
        plugin.getDamageDisplayManager().spawnDamageDisplay(
                event.getEntity().getLocation(),
                poisonDamage,
                DamageDisplayManager.DamageType.MAGIC
        );
    }
}
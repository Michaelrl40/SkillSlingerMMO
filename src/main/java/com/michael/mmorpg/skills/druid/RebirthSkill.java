package com.michael.mmorpg.skills.druid;

import me.libraryaddict.disguise.disguisetypes.Disguise;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import com.michael.mmorpg.models.PlayerData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RebirthSkill extends DruidShapeshiftSkill {
    private final double range;
    private UUID targetUUID;  // Store UUID instead of Player reference
    private static final Map<UUID, Long> recentDeaths = new HashMap<>();
    private static final long DEATH_TIMEOUT = 60000; // 1 minute in milliseconds

    public RebirthSkill(ConfigurationSection config) {
        super(config);
        this.range = config.getDouble("range", 30.0);
        this.requiredForm = "none";
        this.isToggleable = false;
        this.isHarmfulSkill = false;
    }

    public static void trackDeath(Player player) {
        System.out.println("Tracking death for: " + player.getName());  // Debug
        recentDeaths.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private static void cleanupDeaths() {
        long currentTime = System.currentTimeMillis();
        recentDeaths.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > DEATH_TIMEOUT);
    }

    @Override
    public void execute(Player player, String[] args) {
        cleanupDeaths();

        // Don't set skill success to false here to avoid early returns

        // Check form
        if (player.hasMetadata("druid_form")) {
            player.sendMessage("§c✦ Rebirth can only be used in your natural form!");
            setSkillSuccess(false);
            return;
        }

        // Check arguments
        if (args == null || args.length < 1) {
            player.sendMessage("§c✦ Usage: /skill rebirth <player name>");
            setSkillSuccess(false);
            return;
        }

        // Debug info
        System.out.println("Rebirth args: " + String.join(", ", args));
        System.out.println("Current deaths tracked: " + recentDeaths.keySet());

        // Get target player
        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("§c✦ Cannot find player: " + args[0]);
            setSkillSuccess(false);
            return;
        }

        // Store target UUID
        this.targetUUID = target.getUniqueId();

        // Debug
        System.out.println("Rebirth target found: " + target.getName() + " UUID: " + targetUUID);

        // Check if player died recently
        if (!recentDeaths.containsKey(targetUUID)) {
            player.sendMessage("§c✦ That player hasn't died recently!");
            setSkillSuccess(false);
            return;
        }

        // Check time since death
        long deathTime = recentDeaths.get(targetUUID);
        long timeElapsed = System.currentTimeMillis() - deathTime;
        if (timeElapsed > DEATH_TIMEOUT) {
            recentDeaths.remove(targetUUID);
            player.sendMessage("§c✦ Too much time has passed since their death!");
            setSkillSuccess(false);
            return;
        }

        // Check if in same party
        if (!plugin.getPartyManager().areInSameParty(player, target)) {
            player.sendMessage("§c✦ You can only rebirth players in your party!");
            setSkillSuccess(false);
            return;
        }

        // Check resources
        PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
        if (playerData == null || !plugin.getSkillManager().checkResources(player, playerData, this)) {
            setSkillSuccess(false);
            return;
        }

        // If we reach this point, skill initialization is successful
        setSkillSuccess(true);

        // Handle casting or immediate execution
        if (hasCastTime) {
            showReviveEffects(player, target);
            startCasting(player);
        } else {
            performSkill(player);
        }
    }

    @Override
    protected void performSkill(Player caster) {
        Player target = plugin.getServer().getPlayer(targetUUID);
        if (target == null || !target.isOnline()) {
            caster.sendMessage("§c✦ Rebirth target is no longer available!");
            setSkillSuccess(false);
            return;
        }

        // Remove from death tracking
        recentDeaths.remove(targetUUID);

        // Teleport player
        target.teleport(caster.getLocation());

        // Set their health to half
        target.setHealth(target.getMaxHealth() / 2);

        // Set mana to half if they have mana
        PlayerData targetData = plugin.getPlayerManager().getPlayerData(target);
        if (targetData != null) {
            targetData.setCurrentMana(targetData.getMaxMana() / 2);
        }

        playReviveEffects(caster.getLocation());

        broadcastLocalSkillMessage(caster, "§2[Druid] " + caster.getName() + " rebirths " + target.getName() + "!");
        target.sendMessage("§a✦ You have been reborn by " + caster.getName() + "!");

        setSkillSuccess(true);
    }

private void showReviveEffects(Player caster, Player target) {
        Location start = caster.getLocation().add(0, 1, 0);
        Location end = target.getLocation().add(0, 0.5, 0);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks++ >= 20 || !caster.isValid()) {
                    cancel();
                    return;
                }

                // Draw beam between players
                org.bukkit.util.Vector direction = end.clone().subtract(start).toVector().normalize().multiply(0.5);
                double distance = start.distance(end);

                for (double d = 0; d < distance; d += 0.5) {
                    Location particleLoc = start.clone().add(direction.clone().multiply(d));
                    caster.getWorld().spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playReviveEffects(Location location) {
        World world = location.getWorld();
        for (double y = 0; y < 2; y += 0.2) {
            Location particleLoc = location.clone().add(0, y, 0);
            world.spawnParticle(Particle.END_ROD, particleLoc, 5, 0.3, 0, 0.3, 0.05);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, particleLoc, 3, 0.3, 0, 0.3, 0.05);
        }
        world.playSound(location, Sound.ITEM_TOTEM_USE, 1.0f, 1.2f);
        world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);
    }

    // Required abstract method implementations
    @Override protected Disguise createDisguise() { return null; }
    @Override protected void setupDisguise(Disguise disguise) {}
    @Override protected void applyFormEffects(Player player) {}
    @Override protected void removeFormEffects(Player player) {}
}
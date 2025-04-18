package com.michael.mmorpg.skills.hunter;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class TrackSkill extends Skill {
    public TrackSkill(ConfigurationSection config) {
        super(config);
    }

    @Override
    protected void performSkillWithTarget(Player player, String[] args) {
        String targetName = args[0];
        Player target = plugin.getServer().getPlayer(targetName);

        if (target == null || !target.isOnline()) {
            player.sendMessage("§c✦ Player '" + targetName + "' not found or is offline!");
            setSkillSuccess(false);
            return;
        }

        if (target.equals(player)) {
            player.sendMessage("§c✦ You cannot track yourself!");
            setSkillSuccess(false);
            return;
        }

        // Get target's location
        Location targetLoc = target.getLocation();
        double distance = player.getLocation().distance(targetLoc);

        String locationInfo = String.format(
                "§6✦ Tracking %s §e(%.0f blocks away):\n" +
                        "§7    x: §f%d\n" +
                        "§7    y: §f%d\n" +
                        "§7    z: §f%d",
                target.getName(),
                distance,
                targetLoc.getBlockX(),
                targetLoc.getBlockY(),
                targetLoc.getBlockZ()
        );

        player.sendMessage(locationInfo);
        player.playSound(
                player.getLocation(),
                Sound.BLOCK_NOTE_BLOCK_PLING,
                0.5f,
                1.2f
        );

        // Store the target for messaging purposes
        currentTarget = target;

        // The skill succeeded
        setSkillSuccess(true);
    }

    @Override
    protected void performSkill(Player player) {
        player.sendMessage("§c✦ Usage: /skill track <playername>");
        setSkillSuccess(false);
    }
}
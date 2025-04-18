package com.michael.mmorpg.skills.hunter;

import com.michael.mmorpg.skills.Skill;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class DismissPetSkill extends Skill implements Listener {

    public DismissPetSkill(ConfigurationSection config) {
        super(config);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    protected void performSkill(Player player) {
        Wolf pet = SummonPetSkill.ACTIVE_PETS.get(player.getUniqueId());

        if (pet == null || !pet.isValid()) {
            player.sendMessage("§c✦ You don't have an active pet to dismiss!");
            return;
        }

        // Remove pet reference
        SummonPetSkill.ACTIVE_PETS.remove(player.getUniqueId());

        // Dismiss effects
        pet.getWorld().spawnParticle(Particle.CLOUD,
                pet.getLocation().add(0, 1, 0),
                30, 0.5, 0.5, 0.5, 0.1);
        pet.getWorld().playSound(pet.getLocation(),
                Sound.ENTITY_WOLF_WHINE, 1.0f, 1.0f);

        // Remove the pet
        pet.remove();

        // Set cooldown
        plugin.getSkillManager().setCooldown(player, getName(), getCooldown());

        // Notify
        broadcastLocalSkillMessage(player, "§6[" + getPlayerClass(player) + "] " +
                player.getName() + " has dismissed their wolf companion!");
    }

    @EventHandler
    public void onPetDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Wolf) ||
                !event.getEntity().hasMetadata("hunter_pet")) return;

        Wolf pet = (Wolf) event.getEntity();
        String petOwnerId = pet.getMetadata("hunter_pet").get(0).asString();
        Player owner = plugin.getServer().getPlayer(java.util.UUID.fromString(petOwnerId));

        if (owner != null) {
            // Clear pet reference
            SummonPetSkill.ACTIVE_PETS.remove(owner.getUniqueId());

            // Set cooldown on both skills
            plugin.getSkillManager().setCooldown(owner, getName(), getCooldown());
            plugin.getSkillManager().setCooldown(owner, "summonpet", getCooldown());

            // Death message
            broadcastLocalSkillMessage(owner, "§c[" + getPlayerClass(owner) + "] " +
                    owner.getName() + "'s wolf companion has fallen!");
        }
    }
}
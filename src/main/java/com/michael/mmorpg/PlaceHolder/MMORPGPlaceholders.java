package com.michael.mmorpg.PlaceHolder;

import com.michael.mmorpg.MinecraftMMORPG;
import com.michael.mmorpg.models.PlayerData;
import com.michael.mmorpg.party.Party;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MMORPGPlaceholders extends PlaceholderExpansion {

    private final MinecraftMMORPG plugin;

    public MMORPGPlaceholders(MinecraftMMORPG plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mmorpg";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {
        if (player == null || !player.isOnline()) {
            return "";
        }

        return onPlaceholderRequest(player.getPlayer(), identifier);
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }

        try {
            // Handle individual player placeholders first - these work outside of parties
            PlayerData playerData = plugin.getPlayerManager().getPlayerData(player);
            if (playerData != null) {
                // Basic player info
                if (identifier.equals("class")) {
                    return playerData.hasClass() ? playerData.getGameClass().getName() : "None";
                }
                if (identifier.equals("level")) {
                    return String.valueOf(playerData.getLevel());
                }

                // Mana placeholders
                if (identifier.equals("mana")) {
                    return String.format("%.0f", playerData.getCurrentMana());
                }
                if (identifier.equals("max_mana")) {
                    return String.format("%.0f", playerData.getMaxMana());
                }
                if (identifier.equals("show_mana")) {
                    return playerData.hasClass() && playerData.getGameClass().usesMana() ? "true" : "false";
                }

                // Stamina placeholders
                if (identifier.equals("stamina")) {
                    return String.format("%.0f", playerData.getCurrentStamina());
                }
                if (identifier.equals("max_stamina")) {
                    return String.format("%.0f", playerData.getMaxStamina());
                }
                if (identifier.equals("show_stamina")) {
                    return playerData.hasClass() && playerData.getGameClass().usesStamina() ? "true" : "false";
                }

                // Rage placeholders
                if (identifier.equals("rage")) {
                    return String.format("%.0f", playerData.getCurrentRage());
                }
                if (identifier.equals("max_rage")) {
                    return String.format("%.0f", playerData.getMaxRage());
                }
                if (identifier.equals("show_rage")) {
                    return playerData.hasClass() && playerData.getGameClass().usesRage() ? "true" : "false";
                }

                // Toxin placeholders
                if (identifier.equals("toxin")) {
                    return String.format("%.0f", playerData.getCurrentToxin());
                }
                if (identifier.equals("max_toxin")) {
                    return String.format("%.0f", playerData.getMaxToxin());
                }
                if (identifier.equals("show_Toxin")) {
                    return playerData.hasClass() && playerData.getGameClass().usesToxin() ? "true" : "false";
                }

                // Experience placeholders
                if (identifier.equals("experience")) {
                    return String.format("%.0f", playerData.getExperience());
                }
                if (identifier.equals("experience_required")) {
                    return String.format("%.0f", playerData.getRequiredExperience());
                }
                if (identifier.equals("experience_percent")) {
                    double percent = (playerData.getExperience() / playerData.getRequiredExperience()) * 100;
                    return String.format("%.1f", percent);
                }

                // Health bar specific placeholders for MythicHUD
                if (identifier.equals("health-bar-value")) {
                    return String.format("%.0f", playerData.getCurrentHealth());
                }
                if (identifier.equals("health-bar-max")) {
                    return String.format("%.0f", playerData.getMaxHealth());
                }

                // Health placeholders
                if (identifier.equals("health")) {
                    return String.format("%.0f", playerData.getCurrentHealth());
                }
                if (identifier.equals("max_health")) {
                    return String.format("%.0f", playerData.getMaxHealth());
                }
            }

            // Party count placeholder - safe to call even if not in party
            if (identifier.equals("party_count")) {
                Party party = plugin.getPartyManager().getParty(player);
                return party == null ? "0" : String.valueOf(party.getMembers().size());
            }

            // Handle party-related placeholders
            Party party = plugin.getPartyManager().getParty(player);

            // If not in a party, return empty for all party member placeholders
            if (party == null) {
                if (identifier.startsWith("party_member_") ||
                        identifier.startsWith("party_member") ||
                        identifier.equals("party_size")) {
                    return "";
                }
            } else {
                // Party size
                if (identifier.equals("party_size")) {
                    return String.valueOf(party.getMembers().size());
                }

                // Get ordered party members list with leader first
                List<Player> partyMembers = getOrderedPartyMembers(party);
                int partySize = partyMembers.size();

                // Handle party_member_X placeholders (for parseother)
                if (identifier.startsWith("party_member_")) {
                    try {
                        int memberIndex = Integer.parseInt(identifier.substring(identifier.lastIndexOf("_") + 1));

                        // Only return a name if this member index actually exists
                        if (memberIndex > 0 && memberIndex <= partySize) {
                            return partyMembers.get(memberIndex - 1).getName();
                        }
                        return ""; // Return empty for non-existent members
                    } catch (NumberFormatException e) {
                        return "";
                    }
                }

                // Legacy format party member placeholders
                // Only process these if we have enough members for the requested index

                // Member 1 placeholders
                if (partySize >= 1) {
                    Player member = partyMembers.get(0);

                    if (identifier.equals("party_member1_name")) {
                        return member.getName();
                    }

                    if (identifier.equals("party_member1_health")) {
                        return String.format("%.0f", member.getHealth());
                    }

                    if (identifier.equals("party_member1_max_health")) {
                        return String.format("%.0f", member.getMaxHealth());
                    }

                    if (identifier.equals("party_member1_level")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.valueOf(memberData.getLevel());
                        }
                        return "0";
                    }

                    if (identifier.equals("party_member1_mana")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.format("%.0f", memberData.getCurrentMana());
                        }
                        return "0";
                    }

                    if (identifier.equals("party_member1_max_mana")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.format("%.0f", memberData.getMaxMana());
                        }
                        return "0";
                    }
                }

                // Member 2 placeholders - only process if party has at least 2 members
                if (partySize >= 2) {
                    Player member = partyMembers.get(1);

                    if (identifier.equals("party_member2_name")) {
                        return member.getName();
                    }

                    if (identifier.equals("party_member2_health")) {
                        return String.format("%.0f", member.getHealth());
                    }

                    if (identifier.equals("party_member2_max_health")) {
                        return String.format("%.0f", member.getMaxHealth());
                    }

                    if (identifier.equals("party_member2_level")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.valueOf(memberData.getLevel());
                        }
                        return "0";
                    }

                    if (identifier.equals("party_member2_mana")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.format("%.0f", memberData.getCurrentMana());
                        }
                        return "0";
                    }

                    if (identifier.equals("party_member2_max_mana")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.format("%.0f", memberData.getMaxMana());
                        }
                        return "0";
                    }
                }

                // Member 3 placeholders - only process if party has at least 3 members
                if (partySize >= 3) {
                    Player member = partyMembers.get(2);

                    if (identifier.equals("party_member3_name")) {
                        return member.getName();
                    }

                    if (identifier.equals("party_member3_health")) {
                        return String.format("%.0f", member.getHealth());
                    }

                    if (identifier.equals("party_member3_max_health")) {
                        return String.format("%.0f", member.getMaxHealth());
                    }

                    if (identifier.equals("party_member3_level")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.valueOf(memberData.getLevel());
                        }
                        return "0";
                    }

                    if (identifier.equals("party_member3_mana")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.format("%.0f", memberData.getCurrentMana());
                        }
                        return "0";
                    }

                    if (identifier.equals("party_member3_max_mana")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.format("%.0f", memberData.getMaxMana());
                        }
                        return "0";
                    }
                }

                // Member 4 placeholders - only process if party has at least 4 members
                if (partySize >= 4) {
                    Player member = partyMembers.get(3);

                    if (identifier.equals("party_member4_name")) {
                        return member.getName();
                    }

                    if (identifier.equals("party_member4_health")) {
                        return String.format("%.0f", member.getHealth());
                    }

                    if (identifier.equals("party_member4_max_health")) {
                        return String.format("%.0f", member.getMaxHealth());
                    }

                    if (identifier.equals("party_member4_level")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.valueOf(memberData.getLevel());
                        }
                        return "0";
                    }

                    if (identifier.equals("party_member4_mana")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.format("%.0f", memberData.getCurrentMana());
                        }
                        return "0";
                    }

                    if (identifier.equals("party_member4_max_mana")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.format("%.0f", memberData.getMaxMana());
                        }
                        return "0";
                    }
                }

                // Member 5 placeholders - only process if party has at least 5 members
                if (partySize >= 5) {
                    Player member = partyMembers.get(4);

                    if (identifier.equals("party_member5_name")) {
                        return member.getName();
                    }

                    if (identifier.equals("party_member5_health")) {
                        return String.format("%.0f", member.getHealth());
                    }

                    if (identifier.equals("party_member5_max_health")) {
                        return String.format("%.0f", member.getMaxHealth());
                    }

                    if (identifier.equals("party_member5_level")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.valueOf(memberData.getLevel());
                        }
                        return "0";
                    }

                    if (identifier.equals("party_member5_mana")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.format("%.0f", memberData.getCurrentMana());
                        }
                        return "0";
                    }

                    if (identifier.equals("party_member5_max_mana")) {
                        PlayerData memberData = plugin.getPlayerManager().getPlayerData(member);
                        if (memberData != null) {
                            return String.format("%.0f", memberData.getMaxMana());
                        }
                        return "0";
                    }
                }
            }

            // Return null if no placeholder matched
            return null;
        } catch (Exception e) {
            plugin.getLogger().warning("Error in placeholder: " + e.getMessage());
            e.printStackTrace();
            return "Error";
        }
    }

    /**
     * Returns an ordered list of party members with leader first
     */
    private List<Player> getOrderedPartyMembers(Party party) {
        List<Player> orderedMembers = new ArrayList<>();

        // Add leader first
        Player leader = party.getLeader();
        if (leader != null) {
            orderedMembers.add(leader);
        }

        // Add other members
        for (Player member : party.getMembers()) {
            if (member != leader) {
                orderedMembers.add(member);
            }
        }

        return orderedMembers;
    }
}
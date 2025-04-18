package com.michael.mmorpg.chatclasses;

import org.bukkit.ChatColor;

public enum ChatChannel {
    LOCAL("Local", ChatColor.WHITE, 50),
    GLOBAL("Global", ChatColor.GRAY, -1),
    HELP("Help", ChatColor.AQUA, -1),
    TRADE("Trade", ChatColor.GOLD, -1),
    LFG("LFG", ChatColor.LIGHT_PURPLE, -1),
    RECRUITMENT("Recruitment", ChatColor.BLUE, -1),
    SHOUT("Shout", ChatColor.RED, 150);

    private final String displayName;
    private final ChatColor color;
    private final int range; // -1 for server-wide

    ChatChannel(String displayName, ChatColor color, int range) {
        this.displayName = displayName;
        this.color = color;
        this.range = range;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getColor() {
        return color;
    }

    public int getRange() {
        return range;
    }

    public boolean isProximityBased() {
        return range > 0;
    }

    public String format(String playerName, String message, String titlePrefix) {
        if (titlePrefix != null && !titlePrefix.isEmpty()) {
            return String.format("%s[%s]%s%s: %s",
                    color, displayName, titlePrefix, playerName, message);
        } else {
            return String.format("%s[%s] %s: %s",
                    color, displayName, playerName, message);
        }
    }
}
package com.michael.mmorpg.title;

public class Title {
    private final String id;
    private final String displayName;
    private final String description;
    private final TitleCategory category;
    private final TitleRarity rarity;

    public Title(String id, String displayName, String description, TitleCategory category, TitleRarity rarity) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.rarity = rarity;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public TitleCategory getCategory() {
        return category;
    }

    public TitleRarity getRarity() {
        return rarity;
    }

    public String getFormattedTitle() {
        return rarity.getColor() + "【" + displayName + "】" + "§r";
    }

    // Enums for organization
    public enum TitleCategory {
        CLASS_MASTERY,
        ACHIEVEMENT,
        EVENT,
        SPECIAL
    }

    public enum TitleRarity {
        COMMON("§7"),     // Gray
        UNCOMMON("§a"),   // Green
        RARE("§9"),       // Blue
        EPIC("§5"),       // Purple
        LEGENDARY("§6"),  // Gold
        MYTHIC("§d");     // Light Purple

        private final String color;

        TitleRarity(String color) {
            this.color = color;
        }

        public String getColor() {
            return color;
        }
    }
}
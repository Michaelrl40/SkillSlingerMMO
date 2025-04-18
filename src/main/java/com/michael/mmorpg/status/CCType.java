package com.michael.mmorpg.status;

public enum CCType {
    STUN(1, false, "Stunned", "⚡"),
    SILENCE(2, false, "Silenced", "🤐"),
    ROOT(3, false, "Rooted", "⛓"),
    BLIND(4, true, "Blinded", "👁"),
    KNOCKUP(5, false, "Knocked Up", "⬆"),
    SLOW(6, true, "Slowed", "🐌"),
    DISARM(7, false, "Disarmed", "🗡"),
    FEAR(8, false, "Feared", "👻"),
    CHARM(9, false, "Charmed", "💕"),
    SLEEP(10, false, "Sleeping", "💤");

    private final int priority;
    private final boolean canStack;
    private final String displayName;
    private final String symbol;

    CCType(int priority, boolean canStack, String displayName, String symbol) {
        this.priority = priority;
        this.canStack = canStack;
        this.displayName = displayName;
        this.symbol = symbol;
    }

    public int getPriority() { return priority; }
    public boolean canStack() { return canStack; }
    public String getDisplayName() { return displayName; }
    public String getSymbol() { return symbol; }
}
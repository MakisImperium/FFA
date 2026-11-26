package net.zernyth.ffa.config;

/** Combat tagging and the kill-farming guard (config.json, section "fairness"). */
public final class FairnessConfiguration {
    /** How long a hit keeps both players in combat. 0 switches combat tagging off. */
    private int combatTagSeconds = 10;
    /** Kills of the same player that still count inside the window. 0 switches the guard off. */
    private int farmMaxKills = 3;
    private int farmWindowMinutes = 10;

    public int getCombatTagSeconds() {
        return Math.max(0, combatTagSeconds);
    }

    public int getFarmMaxKills() {
        return Math.max(0, farmMaxKills);
    }

    public int getFarmWindowMinutes() {
        return Math.max(1, farmWindowMinutes);
    }
}

package net.zernyth.ffa.economy;

import java.util.List;

/** FFA coins and the cosmetics they buy (config.json, section "economy"). */
public final class EconomyConfiguration {
    private int coinsPerKill = 5;
    /** Extra coins per kill for each streak step, up to {@link #coinStreakCap} steps. */
    private int coinsPerStreakStep = 1;
    private int coinStreakCap = 10;
    private int coinsPerShutdown = 10;
    private List<Cosmetic> cosmetics = Cosmetic.defaults();

    public int getCoinsPerKill() {
        return Math.max(0, coinsPerKill);
    }

    public int getCoinsPerStreakStep() {
        return Math.max(0, coinsPerStreakStep);
    }

    public int getCoinStreakCap() {
        return Math.max(0, coinStreakCap);
    }

    public int getCoinsPerShutdown() {
        return Math.max(0, coinsPerShutdown);
    }

    /**
     * Coins for one counted kill: the base, a bonus per streak step (capped), a shutdown bonus when the
     * victim's streak was at least {@code shutdownFrom} (0 = never), all times the event multiplier.
     */
    public long coinsFor(int killerStreak, int victimStreakBefore, int shutdownFrom, double multiplier) {
        boolean shutdown = shutdownFrom > 0 && victimStreakBefore >= shutdownFrom;
        long coins = getCoinsPerKill()
                + (long) Math.min(Math.max(0, killerStreak), getCoinStreakCap()) * getCoinsPerStreakStep()
                + (shutdown ? getCoinsPerShutdown() : 0);
        return Math.round(coins * Math.max(0, multiplier));
    }

    /** The valid cosmetics; a second entry with the same id is ignored. */
    public List<Cosmetic> getCosmetics() {
        if (cosmetics == null) {
            cosmetics = Cosmetic.defaults();
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        return cosmetics.stream().filter(Cosmetic::valid).filter(c -> seen.add(c.getId())).toList();
    }

    public java.util.Optional<Cosmetic> find(String id) {
        return getCosmetics().stream().filter(c -> c.getId().equals(id)).findFirst();
    }
}

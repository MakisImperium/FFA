package net.zernyth.ffa.player.model;

import java.util.Comparator;
import java.util.function.ToDoubleFunction;

/** What a leaderboard ranks by. Ties go to the alphabetically first name, so the order is stable. */
public enum LeaderboardCategory {
    KILLS(profile -> profile.getStatistics().getKills()),
    BEST_STREAK(profile -> profile.getStatistics().getBestKillstreak()),
    KD(profile -> kd(profile.getStatistics()));

    private final ToDoubleFunction<PlayerProfile> value;

    LeaderboardCategory(ToDoubleFunction<PlayerProfile> value) {
        this.value = value;
    }

    public double valueOf(PlayerProfile profile) {
        return value.applyAsDouble(profile);
    }

    /** Best first. */
    public Comparator<PlayerProfile> comparator() {
        Comparator<PlayerProfile> byValue = Comparator.comparingDouble(this::valueOf);
        Comparator<PlayerProfile> byName = Comparator.comparing(
                (PlayerProfile profile) -> profile.getLastKnownName() == null ? "" : profile.getLastKnownName(),
                String.CASE_INSENSITIVE_ORDER);
        return byValue.reversed().thenComparing(byName);
    }

    /** Kills per death; with no deaths, the kills themselves. */
    public static double kd(PlayerStatistics statistics) {
        return statistics.getDeaths() == 0 ? statistics.getKills() : (double) statistics.getKills() / statistics.getDeaths();
    }
}

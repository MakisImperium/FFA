package net.zernyth.ffa.streak;

import java.util.List;
import java.util.Map;

/** What a streak earns and who hears about it. Plain rules, tested without a server. */
public final class StreakRules {
    public enum Audience { NONE, ARENA, SERVER }

    private StreakRules() {
    }

    /**
     * The rewards for exactly this streak: the arena's own table when it has one, otherwise the
     * server's. An arena table without an entry for this streak means no reward - an arena that
     * defines rewards defines all of them.
     */
    public static List<StreakReward> rewardsFor(int streak, Map<String, List<StreakReward>> arenaRewards,
                                                Map<String, List<StreakReward>> serverRewards) {
        Map<String, List<StreakReward>> table = arenaRewards != null && !arenaRewards.isEmpty() ? arenaRewards : serverRewards;
        if (table == null) {
            return List.of();
        }
        List<StreakReward> rewards = table.get(Integer.toString(streak));
        return rewards == null ? List.of() : rewards.stream().filter(r -> r.getType() != null).toList();
    }

    /** Whether reaching {@code streak} is announced, and to whom. */
    public static Audience streakAudience(int streak, StreakConfiguration config) {
        int every = config.getAnnounceEvery();
        if (every <= 0 || streak < every || streak % every != 0) {
            return Audience.NONE;
        }
        int server = config.getServerWideFrom();
        return server > 0 && streak >= server ? Audience.SERVER : Audience.ARENA;
    }

    /** Whether ending a streak of {@code endedStreak} is a shutdown worth announcing, and to whom. */
    public static Audience shutdownAudience(int endedStreak, StreakConfiguration config) {
        int from = config.getShutdownFrom();
        if (from <= 0 || endedStreak < from) {
            return Audience.NONE;
        }
        int server = config.getServerWideFrom();
        return server > 0 && endedStreak >= server ? Audience.SERVER : Audience.ARENA;
    }
}

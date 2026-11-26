package net.zernyth.ffa.streak;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Streak rewards and announcements (config.json, section "streaks"). An arena can replace the
 * rewards with its own ("streakRewards" in its arena file); the announcements are server-wide.
 */
public final class StreakConfiguration {
    /** Streak -> rewards, e.g. "3": [speed for 10 s]. Keys are strings in JSON. */
    private Map<String, List<StreakReward>> rewards = defaults();
    /** Tell the arena at every multiple of this streak (5, 10, 15, ...). 0 = never. */
    private int announceEvery = 5;
    /** Announce to the whole server from this streak on. 0 = arena only. */
    private int serverWideFrom = 15;
    /** "Shutdown!" when someone ends a streak of at least this. 0 = never. */
    private int shutdownFrom = 5;

    private static Map<String, List<StreakReward>> defaults() {
        Map<String, List<StreakReward>> defaults = new LinkedHashMap<>();
        defaults.put("3", List.of(StreakReward.effect("speed", 0, 10)));
        defaults.put("5", List.of(StreakReward.item("minecraft:golden_apple", 2)));
        defaults.put("10", List.of(StreakReward.of(StreakReward.Type.HEAL), StreakReward.of(StreakReward.Type.REPAIR),
                StreakReward.effect("regeneration", 1, 8)));
        return defaults;
    }

    public Map<String, List<StreakReward>> getRewards() {
        if (rewards == null) {
            rewards = defaults();
        }
        return rewards;
    }

    public int getAnnounceEvery() {
        return Math.max(0, announceEvery);
    }

    public int getServerWideFrom() {
        return Math.max(0, serverWideFrom);
    }

    public int getShutdownFrom() {
        return Math.max(0, shutdownFrom);
    }
}

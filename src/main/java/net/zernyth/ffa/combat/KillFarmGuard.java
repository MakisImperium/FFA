package net.zernyth.ffa.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Stops boosting: killing the same player again and again in a short time - a friend standing
 * still, or a second account - stops counting after {@code maxKills} kills in {@code windowMillis}.
 * The kill still happens in the game; it just earns no stats, XP, coins or task progress.
 */
public final class KillFarmGuard {
    private record Pair(UUID killer, UUID victim) {}

    private final Map<Pair, Deque<Long>> recent = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile int maxKills;
    private volatile long windowMillis;

    public KillFarmGuard(int maxKills, long windowMillis, LongSupplier clock) {
        this.maxKills = maxKills;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    public void configure(int maxKills, long windowMillis) {
        this.maxKills = maxKills;
        this.windowMillis = windowMillis;
    }

    /** Records a kill; true when it counts, false when it is farming. Disabled with maxKills <= 0. */
    public boolean counts(UUID killer, UUID victim) {
        if (maxKills <= 0) {
            return true;
        }
        long now = clock.getAsLong();
        Deque<Long> times = recent.computeIfAbsent(new Pair(killer, victim), key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() <= now - windowMillis) {
                times.pollFirst();
            }
            if (times.size() >= maxKills) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    /**
     * Drops pairs with no kill inside the window. Deliberately not done when a player leaves: logging
     * out and back in must not reset the count.
     */
    public void prune() {
        long cutoff = clock.getAsLong() - windowMillis;
        recent.values().removeIf(times -> {
            synchronized (times) {
                return times.isEmpty() || times.peekLast() <= cutoff;
            }
        });
    }
}

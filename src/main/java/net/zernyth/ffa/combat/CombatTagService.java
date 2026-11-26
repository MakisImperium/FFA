package net.zernyth.ffa.combat;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Who is in a fight, and with whom.
 *
 * <p>A hit between two arena players tags both for {@code tagMillis}. While tagged, logging out or
 * leaving the arena counts as a death, and the last player who hit you gets the kill - running
 * away from a lost fight is not a way to keep your streak. The last attacker also gets the kill when
 * you die to the void or a fall after their hit.
 */
public final class CombatTagService {
    private record Tag(UUID lastAttacker, long expiresAt) {}

    private final Map<UUID, Tag> tags = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile long tagMillis;

    public CombatTagService(long tagMillis, LongSupplier clock) {
        this.tagMillis = tagMillis;
        this.clock = clock;
    }

    public void setTagMillis(long tagMillis) {
        this.tagMillis = tagMillis;
    }

    /** Records a hit: both are in combat; the victim remembers who hit them last. */
    public void tag(UUID attacker, UUID victim) {
        if (tagMillis <= 0 || attacker.equals(victim)) {
            return;
        }
        long until = clock.getAsLong() + tagMillis;
        tags.put(victim, new Tag(attacker, until));
        Tag own = tags.get(attacker);
        tags.put(attacker, new Tag(own != null ? own.lastAttacker() : null, until));
    }

    public boolean isTagged(UUID player) {
        Tag tag = tags.get(player);
        return tag != null && tag.expiresAt() > clock.getAsLong();
    }

    /** Whoever hit this player last, while the tag lasts. */
    public Optional<UUID> lastAttacker(UUID player) {
        Tag tag = tags.get(player);
        if (tag == null || tag.expiresAt() <= clock.getAsLong()) {
            return Optional.empty();
        }
        return Optional.ofNullable(tag.lastAttacker());
    }

    /** Seconds left, rounded up; 0 when not in combat. */
    public int remainingSeconds(UUID player) {
        Tag tag = tags.get(player);
        if (tag == null) {
            return 0;
        }
        long left = tag.expiresAt() - clock.getAsLong();
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }

    /** Ends a player's combat, e.g. after they died or left. */
    public void clear(UUID player) {
        tags.remove(player);
    }
}

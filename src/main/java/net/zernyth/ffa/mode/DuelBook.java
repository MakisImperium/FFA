package net.zernyth.ffa.mode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Open duel requests and running duels. Plain state with an injected clock, tested without a
 * server; the duel service adds the players, forms and messages.
 */
public final class DuelBook {
    public record Request(UUID challenger, UUID target, String arenaId, long expiresAt) {}

    public enum RequestResult { SENT, SELF, CHALLENGER_BUSY, TARGET_BUSY }

    private final long validMillis;
    private final LongSupplier clock;
    /** Open requests by target; a newer request to the same target replaces the old one. */
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<UUID, UUID> partners = new HashMap<>();

    public DuelBook(long validMillis, LongSupplier clock) {
        this.validMillis = validMillis;
        this.clock = clock;
    }

    public synchronized RequestResult request(UUID challenger, UUID target, String arenaId) {
        if (challenger.equals(target)) {
            return RequestResult.SELF;
        }
        if (partners.containsKey(challenger)) {
            return RequestResult.CHALLENGER_BUSY;
        }
        if (partners.containsKey(target)) {
            return RequestResult.TARGET_BUSY;
        }
        requests.put(target, new Request(challenger, target, arenaId, clock.getAsLong() + validMillis));
        return RequestResult.SENT;
    }

    /** The open, unexpired request to {@code target}, if any. */
    public synchronized Optional<Request> pending(UUID target) {
        Request request = requests.get(target);
        if (request == null) {
            return Optional.empty();
        }
        if (request.expiresAt() < clock.getAsLong()) {
            requests.remove(target);
            return Optional.empty();
        }
        return Optional.of(request);
    }

    /** Accepts the open request: the duel starts when both are still free. */
    public synchronized Optional<Request> accept(UUID target) {
        Optional<Request> request = pending(target);
        requests.remove(target);
        if (request.isEmpty() || partners.containsKey(target) || partners.containsKey(request.get().challenger())) {
            return Optional.empty();
        }
        partners.put(target, request.get().challenger());
        partners.put(request.get().challenger(), target);
        // Other requests to or from the two are void now.
        requests.values().removeIf(r -> r.challenger().equals(target) || r.challenger().equals(request.get().challenger()));
        requests.remove(request.get().challenger());
        return request;
    }

    public synchronized Optional<Request> decline(UUID target) {
        return Optional.ofNullable(requests.remove(target));
    }

    public synchronized Optional<UUID> partner(UUID player) {
        return Optional.ofNullable(partners.get(player));
    }

    /** Ends the duel {@code loser} is in; returns the winner. */
    public synchronized Optional<UUID> end(UUID loser) {
        UUID winner = partners.remove(loser);
        if (winner != null) {
            partners.remove(winner);
        }
        return Optional.ofNullable(winner);
    }

    /** Duel pairs only hit each other, and nobody else hits them. */
    public synchronized boolean hitAllowed(UUID attacker, UUID victim) {
        UUID attackerPartner = partners.get(attacker);
        UUID victimPartner = partners.get(victim);
        if (attackerPartner == null && victimPartner == null) {
            return true;
        }
        return victim.equals(attackerPartner);
    }

    /** Drops every request to or from a player (logout). */
    public synchronized void forgetRequests(UUID player) {
        requests.remove(player);
        requests.values().removeIf(r -> r.challenger().equals(player));
    }
}

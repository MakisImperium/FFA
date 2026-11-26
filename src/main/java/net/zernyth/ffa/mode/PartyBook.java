package net.zernyth.ffa.mode;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Groups for team arenas: invites with an expiry, and who is grouped with whom. */
public final class PartyBook {
    public enum InviteResult { SENT, SELF, FULL, ALREADY_TOGETHER, IN_OTHER_GROUP }

    private record Invite(UUID inviter, long expiresAt) {}

    private final int maxSize;
    private final long validMillis;
    private final LongSupplier clock;
    private final Map<UUID, Set<UUID>> groupOf = new HashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    public PartyBook(int maxSize, long validMillis, LongSupplier clock) {
        this.maxSize = Math.max(2, maxSize);
        this.validMillis = validMillis;
        this.clock = clock;
    }

    public synchronized InviteResult invite(UUID inviter, UUID invitee) {
        if (inviter.equals(invitee)) {
            return InviteResult.SELF;
        }
        Set<UUID> group = groupOf.get(inviter);
        if (group != null && group.contains(invitee)) {
            return InviteResult.ALREADY_TOGETHER;
        }
        if (groupOf.containsKey(invitee)) {
            return InviteResult.IN_OTHER_GROUP;
        }
        if (group != null && group.size() >= maxSize) {
            return InviteResult.FULL;
        }
        invites.put(invitee, new Invite(inviter, clock.getAsLong() + validMillis));
        return InviteResult.SENT;
    }

    /** Joins the inviter's group; returns the group's members, or empty when nothing valid is open. */
    public synchronized Optional<Set<UUID>> accept(UUID invitee) {
        Invite invite = invites.remove(invitee);
        if (invite == null || invite.expiresAt() < clock.getAsLong() || groupOf.containsKey(invitee)) {
            return Optional.empty();
        }
        Set<UUID> group = groupOf.computeIfAbsent(invite.inviter(), id -> {
            Set<UUID> created = new LinkedHashSet<>();
            created.add(id);
            return created;
        });
        if (group.size() >= maxSize) {
            return Optional.empty();
        }
        group.add(invitee);
        groupOf.put(invitee, group);
        return Optional.of(Set.copyOf(group));
    }

    /** Leaves the group; returns who is left in it. A group of one dissolves. */
    public synchronized Set<UUID> leave(UUID player) {
        invites.remove(player);
        Set<UUID> group = groupOf.remove(player);
        if (group == null) {
            return Set.of();
        }
        group.remove(player);
        Set<UUID> rest = Set.copyOf(group);
        if (group.size() < 2) {
            group.forEach(groupOf::remove);
            group.clear();
        }
        return rest;
    }

    /** The other members of a player's group. */
    public synchronized Set<UUID> mates(UUID player) {
        Set<UUID> group = groupOf.get(player);
        if (group == null) {
            return Set.of();
        }
        Set<UUID> mates = new LinkedHashSet<>(group);
        mates.remove(player);
        return mates;
    }

    public synchronized Optional<UUID> pendingInviter(UUID invitee) {
        Invite invite = invites.get(invitee);
        return invite == null || invite.expiresAt() < clock.getAsLong() ? Optional.empty() : Optional.of(invite.inviter());
    }
}

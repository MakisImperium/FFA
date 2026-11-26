package net.zernyth.ffa.event;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Which XP and coin boost runs now: one started by an admin wins over the schedule. Plain logic with
 * an injected clock; the bootstrap announces starts and ends and feeds the multipliers to the
 * progression and the economy.
 */
public final class EventService {
    /** A running boost. {@code endsAt} in epoch millis. */
    public record Active(double xp, double coins, long endsAt, String nameEn, String nameDe, boolean manual) {
        public String name(boolean german) {
            return german && nameDe != null ? nameDe : nameEn;
        }
    }

    private final Supplier<EventConfiguration> config;
    private final Supplier<ZoneId> zone;
    private final LongSupplier clock;
    private volatile Active manual;
    private Active lastSeen;

    public enum Change { STARTED, ENDED }

    /** A boost began or ended since the last {@link #poll()}. */
    public record Transition(Change change, Active active) {}

    public EventService(Supplier<EventConfiguration> config, Supplier<ZoneId> zone, LongSupplier clock) {
        this.config = config;
        this.zone = zone;
        this.clock = clock;
    }

    public Active start(double xp, double coins, int minutes) {
        manual = new Active(Math.max(0, xp), Math.max(0, coins), clock.getAsLong() + minutes * 60_000L,
                "Boost event", "Boost-Event", true);
        return manual;
    }

    /** Ends an admin-started boost; returns whether one was running. */
    public boolean stop() {
        boolean wasRunning = manualActive().isPresent();
        manual = null;
        return wasRunning;
    }

    public Optional<Active> active() {
        Optional<Active> running = manualActive();
        return running.isPresent() ? running : scheduled();
    }

    /** Compares with the last call: what started or ended in between (for announcements). */
    public synchronized Optional<Transition> poll() {
        Active now = active().orElse(null);
        Active before = lastSeen;
        lastSeen = now;
        if (java.util.Objects.equals(now, before)) {
            return Optional.empty();
        }
        return now != null ? Optional.of(new Transition(Change.STARTED, now)) : Optional.of(new Transition(Change.ENDED, before));
    }

    public double xpMultiplier() {
        return active().map(Active::xp).orElse(1.0);
    }

    public double coinMultiplier() {
        return active().map(Active::coins).orElse(1.0);
    }

    private Optional<Active> manualActive() {
        Active current = manual;
        if (current != null && current.endsAt() <= clock.getAsLong()) {
            manual = null;
            return Optional.empty();
        }
        return Optional.ofNullable(current);
    }

    /** A scheduled event running now; one that started yesterday evening may still run after midnight. */
    private Optional<Active> scheduled() {
        long now = clock.getAsLong();
        ZoneId zoneId = zone.get();
        LocalDate today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate();
        for (EventConfiguration.ScheduledEvent event : config.get().getSchedule()) {
            for (LocalDate day : new LocalDate[]{today, today.minusDays(1)}) {
                if (!event.days().contains(day.getDayOfWeek())) {
                    continue;
                }
                ZonedDateTime start = day.atTime(event.startTime()).atZone(zoneId);
                long startMillis = start.toInstant().toEpochMilli();
                long endMillis = startMillis + event.getMinutes() * 60_000L;
                if (now >= startMillis && now < endMillis) {
                    return Optional.of(new Active(event.getXp(), event.getCoins(), endMillis, event.name(false), event.name(true), false));
                }
            }
        }
        return Optional.empty();
    }
}

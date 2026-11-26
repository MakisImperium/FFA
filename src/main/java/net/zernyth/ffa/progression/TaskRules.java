package net.zernyth.ffa.progression;

import net.zernyth.ffa.player.model.PlayerProgress;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Which tasks a player has today and this week, and how a kill moves them on. Each player gets
 * their own random pick per period, stable for that period: the same tasks after every relog.
 */
public final class TaskRules {
    /** What happened, as far as tasks care. */
    public record Event(boolean kill, int streak, boolean shutdown, String kitId, String arenaId) {}

    /** A task that was just finished. */
    public record Completion(TaskDefinition task) {}

    private TaskRules() {
    }

    public static String dailyKey(Instant now, ZoneId zone) {
        return LocalDate.ofInstant(now, zone).toString();
    }

    /** ISO week, e.g. "2026-W48": weeks start on Monday. */
    public static String weeklyKey(Instant now, ZoneId zone) {
        LocalDate date = LocalDate.ofInstant(now, zone).with(DayOfWeek.MONDAY);
        return date.get(IsoFields.WEEK_BASED_YEAR) + "-W" + String.format("%02d", date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** This player's tasks for one period: {@code count} picked from the valid pool entries of that period. */
    public static List<TaskDefinition> assigned(UUID player, String periodKey, TaskDefinition.Period period,
                                                List<TaskDefinition> pool, int count) {
        List<TaskDefinition> candidates = new ArrayList<>(pool.stream()
                .filter(TaskDefinition::valid)
                .filter(task -> task.getPeriod() == period)
                .toList());
        java.util.Collections.shuffle(candidates, new Random((player.toString() + "|" + periodKey).hashCode()));
        return candidates.subList(0, Math.min(count, candidates.size()));
    }

    /** Starts a new period when the stored one is over; returns true if it did. */
    public static boolean roll(PlayerProgress.TaskState state, String periodKey) {
        if (periodKey.equals(state.getPeriodKey())) {
            return false;
        }
        state.reset(periodKey);
        return true;
    }

    /** Applies an event to a period's tasks and returns the ones it just completed. */
    public static List<Completion> apply(PlayerProgress.TaskState state, List<TaskDefinition> tasks, Event event) {
        List<Completion> done = new ArrayList<>();
        for (TaskDefinition task : tasks) {
            if (state.getCompleted().contains(task.getId())) {
                continue;
            }
            int before = state.getProgress().getOrDefault(task.getId(), 0);
            int after = switch (task.getType()) {
                case KILLS -> event.kill() ? before + 1 : before;
                case STREAK -> event.kill() ? Math.max(before, event.streak()) : before;
                case KILLS_WITH_KIT -> event.kill() && task.getKit().equalsIgnoreCase(String.valueOf(event.kitId())) ? before + 1 : before;
                case KILLS_IN_ARENA -> event.kill() && task.getArena().equalsIgnoreCase(String.valueOf(event.arenaId())) ? before + 1 : before;
                case SHUTDOWNS -> event.shutdown() ? before + 1 : before;
            };
            after = Math.min(after, task.getTarget());
            if (after != before) {
                state.getProgress().put(task.getId(), after);
            }
            if (after >= task.getTarget()) {
                state.getCompleted().add(task.getId());
                done.add(new Completion(task));
            }
        }
        return done;
    }
}

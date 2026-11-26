package net.zernyth.ffa.event;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Timed boosts (config.json, section "events"):
 * <pre>"events": {"schedule": [
 *   {"days": ["FRIDAY","SATURDAY"], "start": "20:00", "minutes": 120, "xp": 2.0, "coins": 2.0,
 *    "nameEn": "Weekend rush", "nameDe": "Wochenend-Rausch"}]}</pre>
 * Times are in the time zone of the progression settings.
 */
public final class EventConfiguration {
    private List<ScheduledEvent> schedule = new ArrayList<>();

    public List<ScheduledEvent> getSchedule() {
        if (schedule == null) {
            schedule = new ArrayList<>();
        }
        return schedule.stream().filter(ScheduledEvent::valid).toList();
    }

    public static final class ScheduledEvent {
        private List<String> days;
        private String start;
        private int minutes;
        private double xp = 1.0;
        private double coins = 1.0;
        private String nameEn;
        private String nameDe;

        public ScheduledEvent() {
        }

        public ScheduledEvent(List<String> days, String start, int minutes, double xp, double coins, String nameEn) {
            this.days = days;
            this.start = start;
            this.minutes = minutes;
            this.xp = xp;
            this.coins = coins;
            this.nameEn = nameEn;
        }

        /** Empty or missing days means every day. Unknown day names make the entry invalid. */
        public List<DayOfWeek> days() {
            if (days == null || days.isEmpty()) {
                return List.of(DayOfWeek.values());
            }
            return days.stream().map(d -> DayOfWeek.valueOf(d.trim().toUpperCase(Locale.ROOT))).toList();
        }

        public LocalTime startTime() {
            return LocalTime.parse(start.trim());
        }

        public int getMinutes() {
            return minutes;
        }

        public double getXp() {
            return Math.max(0, xp);
        }

        public double getCoins() {
            return Math.max(0, coins);
        }

        public String name(boolean german) {
            String chosen = german && nameDe != null && !nameDe.isBlank() ? nameDe : nameEn;
            return chosen == null || chosen.isBlank() ? (german ? "Event" : "Event") : chosen;
        }

        boolean valid() {
            try {
                days();
                startTime();
                return minutes > 0 && minutes <= 24 * 60 && (xp != 1.0 || coins != 1.0);
            } catch (RuntimeException invalid) {
                return false;
            }
        }
    }
}

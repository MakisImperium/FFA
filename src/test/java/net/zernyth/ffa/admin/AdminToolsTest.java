package net.zernyth.ffa.admin;

import com.google.gson.Gson;
import net.zernyth.ffa.arena.model.CuboidZone;
import net.zernyth.ffa.event.EventConfiguration;
import net.zernyth.ffa.event.EventService;
import net.zernyth.ffa.player.model.PlayerProfile;
import net.zernyth.ffa.player.storage.PlayerProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminToolsTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private final AtomicLong now = new AtomicLong();

    private void at(int year, int month, int day, int hour, int minute) {
        now.set(ZonedDateTime.of(year, month, day, hour, minute, 0, 0, BERLIN).toInstant().toEpochMilli());
    }

    private EventService events(String json) {
        EventConfiguration config = new Gson().fromJson(json, EventConfiguration.class);
        return new EventService(() -> config, () -> BERLIN, now::get);
    }

    @Test
    void scheduledEventsRunInTheirWindowAndPastMidnight() {
        // 2026-11-27 is a Friday.
        EventService events = events("""
                {"schedule":[{"days":["FRIDAY"],"start":"23:00","minutes":120,"xp":2,"coins":3,"nameEn":"Night"}]}""");
        at(2026, 11, 27, 22, 59);
        assertEquals(1.0, events.xpMultiplier());
        at(2026, 11, 27, 23, 0);
        assertEquals(2.0, events.xpMultiplier());
        assertEquals(3.0, events.coinMultiplier());
        at(2026, 11, 28, 0, 30);
        assertEquals(2.0, events.xpMultiplier(), "started Friday, still runs on Saturday night");
        at(2026, 11, 28, 1, 0);
        assertEquals(1.0, events.xpMultiplier(), "ended after 120 minutes");
        at(2026, 11, 28, 23, 30);
        assertEquals(1.0, events.xpMultiplier(), "not on Saturdays");
    }

    @Test
    void brokenScheduleEntriesAreIgnored() {
        EventService events = events("""
                {"schedule":[{"days":["FUNDAY"],"start":"20:00","minutes":60,"xp":2},
                             {"start":"25:00","minutes":60,"xp":2},
                             {"start":"20:00","minutes":0,"xp":2},
                             {"start":"20:00","minutes":60}]}""");
        at(2026, 11, 27, 20, 30);
        assertTrue(events.active().isEmpty());
    }

    @Test
    void adminEventsWinAndExpire() {
        EventService events = events("""
                {"schedule":[{"start":"20:00","minutes":600,"xp":1.5,"nameEn":"Daily"}]}""");
        at(2026, 11, 27, 21, 0);
        assertEquals(1.5, events.xpMultiplier());
        events.start(3, 1, 30);
        assertEquals(3.0, events.xpMultiplier(), "the admin's event comes first");
        now.addAndGet(31 * 60_000L);
        assertEquals(1.5, events.xpMultiplier(), "back to the schedule after it ran out");
        assertFalse(events.stop(), "nothing started by an admin is left");
    }

    @Test
    void pollReportsEachChangeOnce() {
        EventService events = events("{}");
        at(2026, 11, 27, 12, 0);
        assertTrue(events.poll().isEmpty());
        events.start(2, 2, 10);
        assertEquals(EventService.Change.STARTED, events.poll().orElseThrow().change());
        assertTrue(events.poll().isEmpty(), "reported once");
        events.stop();
        EventService.Transition ended = events.poll().orElseThrow();
        assertEquals(EventService.Change.ENDED, ended.change());
        assertEquals(2.0, ended.active().xp());
        assertTrue(events.poll().isEmpty());
    }

    /** A repository in memory. */
    private static final class MemoryRepository implements PlayerProfileRepository {
        final Map<UUID, PlayerProfile> rows = new HashMap<>();
        final Set<UUID> failing = new java.util.HashSet<>();

        @Override
        public Optional<PlayerProfile> findByUniqueId(UUID uniqueId) {
            return Optional.ofNullable(rows.get(uniqueId));
        }

        @Override
        public List<PlayerProfile> loadAllProfiles() {
            return new ArrayList<>(rows.values());
        }

        @Override
        public void save(PlayerProfile profile) throws Exception {
            if (failing.contains(profile.getUniqueId())) {
                throw new Exception("disk full");
            }
            rows.put(profile.getUniqueId(), profile);
        }
    }

    @Test
    void migrationCopiesSkipsAndReports() throws Exception {
        MemoryRepository json = new MemoryRepository();
        MemoryRepository mysql = new MemoryRepository();
        UUID fresh = UUID.randomUUID();
        UUID existing = UUID.randomUUID();
        UUID online = UUID.randomUUID();
        UUID broken = UUID.randomUUID();
        for (UUID id : List.of(fresh, existing, online, broken)) {
            PlayerProfile profile = PlayerProfile.createDefault(id, "p", true);
            profile.getStatistics().setKills(50);
            json.rows.put(id, profile);
        }
        mysql.rows.put(existing, PlayerProfile.createDefault(existing, "p", true));
        mysql.failing.add(broken);

        ProfileMigration.Report dry = ProfileMigration.run(json, mysql, Set.of(online), true, false, done -> { });
        assertEquals(new ProfileMigration.Report(4, 2, 1, 1, 0, true), dry, "a dry run counts what it would copy");
        assertFalse(mysql.rows.containsKey(fresh), "and writes nothing");

        ProfileMigration.Report real = ProfileMigration.run(json, mysql, Set.of(online), false, false, done -> { });
        assertEquals(new ProfileMigration.Report(4, 1, 1, 1, 1, false), real);
        assertEquals(50, mysql.rows.get(fresh).getStatistics().getKills());
        assertEquals(0, mysql.rows.get(existing).getStatistics().getKills(), "existing rows stay");

        ProfileMigration.run(json, mysql, Set.of(online), false, true, done -> { });
        assertEquals(50, mysql.rows.get(existing).getStatistics().getKills(), "overwrite replaces them");
        assertFalse(mysql.rows.containsKey(online), "online players are never copied");
    }

    @Test
    void zoneOutlineCoversTheEdgesWithinTheLimit() {
        CuboidZone zone = new CuboidZone("w", 0, 60, 0, 9, 64, 9);
        List<org.powernukkitx.math.Vector3> points = ZoneOutline.points(zone, 600);
        assertTrue(points.size() <= 624, "about the limit: " + points.size());
        assertTrue(points.stream().anyMatch(p -> p.x == 0 && p.y == 60 && p.z == 0));
        assertTrue(points.stream().anyMatch(p -> p.x == 10 && p.y == 65 && p.z == 10), "the far corner of the last block");
        CuboidZone huge = new CuboidZone("w", 0, 0, 0, 999, 255, 999);
        assertTrue(ZoneOutline.points(huge, 600).size() <= 624, "12 edges: one end point and one rounded-up segment each");
    }
}

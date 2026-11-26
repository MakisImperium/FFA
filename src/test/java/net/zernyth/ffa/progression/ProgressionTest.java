package net.zernyth.ffa.progression;

import com.google.gson.Gson;
import net.zernyth.ffa.player.model.PlayerProfile;
import net.zernyth.ffa.player.model.PlayerProgress;
import net.zernyth.ffa.player.storage.JsonPlayerProfileRepository;
import net.zernyth.ffa.util.JsonFileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressionTest {
    @TempDir
    Path dir;

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private final UUID alex = UUID.fromString("00000000-0000-0000-0000-00000000a1e5");

    @Test
    void levelsGrowAndCap() {
        LevelCurve curve = new LevelCurve(100, 1.2, 5);
        assertEquals(1, curve.levelFor(0));
        assertEquals(1, curve.levelFor(99));
        assertEquals(2, curve.levelFor(100));
        assertEquals(120, curve.xpForNext(2));
        assertEquals(3, curve.levelFor(220));
        assertEquals(220, curve.xpAtLevel(3));
        assertEquals(5, curve.levelFor(1_000_000), "capped at the max level");
        long[] within = curve.progressInLevel(150);
        assertEquals(50, within[0]);
        assertEquals(120, within[1]);
        assertEquals(0, curve.progressInLevel(1_000_000)[1], "no next level at the cap");
    }

    @Test
    void ranksFollowTheLevel() {
        List<Rank> ranks = Rank.defaults();
        assertEquals("Rookie", Rank.forLevel(1, ranks).getName());
        assertEquals("Fighter", Rank.forLevel(9, ranks).getName());
        assertEquals("Veteran", Rank.forLevel(10, ranks).getName());
        assertEquals("Legend", Rank.forLevel(99, ranks).getName());
        assertEquals("§7", new Gson().fromJson("{\"fromLevel\":1,\"name\":\"X\",\"color\":\"red\"}", Rank.class).getColor(), "bad colours fall back");
    }

    @Test
    void taskPeriodsFollowTheConfiguredZone() {
        Instant lateSundayUtc = Instant.parse("2026-11-29T23:30:00Z"); // Monday 00:30 in Berlin
        assertEquals("2026-11-30", TaskRules.dailyKey(lateSundayUtc, BERLIN));
        assertEquals("2026-11-29", TaskRules.dailyKey(lateSundayUtc, ZoneId.of("UTC")));
        assertEquals("2026-W49", TaskRules.weeklyKey(lateSundayUtc, BERLIN));
        assertEquals("2026-W48", TaskRules.weeklyKey(lateSundayUtc, ZoneId.of("UTC")));
    }

    @Test
    void everyPlayerGetsTheirOwnStablePick() {
        List<TaskDefinition> pool = TaskDefinition.defaults();
        List<TaskDefinition> first = TaskRules.assigned(alex, "2026-11-26", TaskDefinition.Period.DAILY, pool, 3);
        assertEquals(3, first.size());
        assertEquals(ids(first), ids(TaskRules.assigned(alex, "2026-11-26", TaskDefinition.Period.DAILY, pool, 3)), "same tasks after a relog");
        assertTrue(first.stream().allMatch(task -> task.getPeriod() == TaskDefinition.Period.DAILY));
        boolean anyDifferent = false;
        for (int i = 0; i < 10 && !anyDifferent; i++) {
            anyDifferent = !ids(first).equals(ids(TaskRules.assigned(UUID.randomUUID(), "2026-11-26", TaskDefinition.Period.DAILY, pool, 3)));
        }
        assertTrue(anyDifferent, "others get other picks");
        assertEquals(4, TaskRules.assigned(alex, "2026-W48", TaskDefinition.Period.WEEKLY, pool, 9).size(), "never more than the pool has");
    }

    @Test
    void killsMoveTasksOnAndCompleteThemOnce() {
        TaskDefinition kills = new Gson().fromJson("{\"id\":\"k\",\"type\":\"kills\",\"target\":2,\"xp\":5}", TaskDefinition.class);
        TaskDefinition streak = new Gson().fromJson("{\"id\":\"s\",\"type\":\"streak\",\"target\":3}", TaskDefinition.class);
        TaskDefinition kit = new Gson().fromJson("{\"id\":\"b\",\"type\":\"kills_with_kit\",\"kit\":\"archer\",\"target\":1}", TaskDefinition.class);
        TaskDefinition broken = new Gson().fromJson("{\"id\":\"x\",\"type\":\"kills_with_kit\",\"target\":1}", TaskDefinition.class);
        assertFalse(broken.valid(), "a kit task without a kit is never offered");

        PlayerProgress.TaskState state = new PlayerProgress.TaskState();
        assertTrue(TaskRules.roll(state, "2026-11-26"));
        List<TaskDefinition> tasks = List.of(kills, streak, kit);
        assertEquals(List.of(), TaskRules.apply(state, tasks, new TaskRules.Event(true, 1, false, "soldier", "pit")));
        var done = TaskRules.apply(state, tasks, new TaskRules.Event(true, 3, false, "archer", "pit"));
        assertEquals(List.of("k", "s", "b"), done.stream().map(c -> c.task().getId()).toList());
        assertEquals(List.of(), TaskRules.apply(state, tasks, new TaskRules.Event(true, 4, false, "archer", "pit")), "completed only once");
        assertEquals(2, state.getProgress().get("k"), "progress stops at the target");

        assertFalse(TaskRules.roll(state, "2026-11-26"));
        assertTrue(TaskRules.roll(state, "2026-11-27"));
        assertTrue(state.getCompleted().isEmpty(), "a new day starts fresh");
    }

    @Test
    void achievementsUnlockByValueAndCustomOnesReplaceBuiltIns() {
        PlayerProfile profile = PlayerProfile.createDefault(alex, "Alex", true);
        profile.getStatistics().setKills(100);
        var all = AchievementDefinition.all(List.of(
                new Gson().fromJson("{\"id\":\"kills_100\",\"type\":\"kills\",\"target\":150}", AchievementDefinition.class),
                new Gson().fromJson("{\"id\":\"rich\",\"type\":\"nonsense\"}", AchievementDefinition.class)));
        var kills100 = all.stream().filter(a -> a.getId().equals("kills_100")).findFirst().orElseThrow();
        assertEquals(150, kills100.getTarget(), "the custom definition wins");
        assertFalse(kills100.reached(profile, 1));
        assertTrue(all.stream().noneMatch(a -> a.getId().equals("rich")), "unknown types are dropped");
        var level10 = all.stream().filter(a -> a.getId().equals("level_10")).findFirst().orElseThrow();
        assertTrue(level10.reached(profile, 12));
    }

    @Test
    void seasonStatsResetWhenAProfileEntersANewSeason() {
        PlayerProgress progress = new PlayerProgress();
        progress.enterSeason("S1");
        progress.registerSeasonKill(4);
        progress.registerSeasonKill(5);
        progress.registerSeasonDeath();
        progress.enterSeason("S1");
        assertEquals(2, progress.getSeasonKills(), "same season: kept");
        progress.enterSeason("S2");
        assertEquals(0, progress.getSeasonKills());
        assertEquals(0, progress.getSeasonDeaths());
        assertEquals(0, progress.getSeasonBestStreak());
    }

    @Test
    void progressSurvivesJsonStorage() throws Exception {
        JsonPlayerProfileRepository repository = new JsonPlayerProfileRepository(new JsonFileService(), dir.resolve("players"));
        PlayerProfile profile = PlayerProfile.createDefault(alex, "Alex", true);
        profile.getProgress().addXp(1234);
        profile.getProgress().addCoins(55);
        profile.getProgress().getAchievements().put("first_blood", 1L);
        profile.getProgress().getDailyTasks().reset("2026-11-26");
        profile.getProgress().getDailyTasks().getProgress().put("daily_kills_10", 4);
        repository.save(profile);
        var loaded = repository.findByUniqueId(alex).orElseThrow().getProgress();
        assertEquals(1234, loaded.getXp());
        assertEquals(55, loaded.getCoins());
        assertTrue(loaded.getAchievements().containsKey("first_blood"));
        assertEquals(4, loaded.getDailyTasks().getProgress().get("daily_kills_10"));
    }

    @Test
    void aSeasonChangeArchivesStoredAndOnlinePlayersAndStartsTheNext() throws Exception {
        AtomicLong now = new AtomicLong(Instant.parse("2026-11-15T12:00:00Z").toEpochMilli());
        ProgressionConfiguration config = new Gson().fromJson("{\"timeZone\":\"UTC\",\"seasonArchiveSize\":2}", ProgressionConfiguration.class);
        List<String> started = new ArrayList<>();
        PlayerProfile stored = seasonProfile("Robin", "S1", 9);
        PlayerProfile online = seasonProfile("Alex", "S1", 12);
        PlayerProfile third = seasonProfile("Kai", "S1", 3);
        SeasonService.Ranking ranking = new SeasonService.Ranking() {
            @Override
            public void storedTop(String seasonId, int limit, Consumer<List<PlayerProfile>> result) {
                assertEquals("S1", seasonId);
                result.accept(List.of(stored, third));
            }

            @Override
            public List<PlayerProfile> online(String seasonId) {
                return List.of(online);
            }
        };
        SeasonService seasons = new SeasonService(new JsonFileService(), dir.resolve("seasons"), () -> config, now::get, Runnable::run,
                ranking, started::add);
        seasons.load();
        assertEquals("S1", seasons.currentId());
        assertFalse(seasons.checkRollover(), "same month");

        now.set(Instant.parse("2026-12-01T00:01:00Z").toEpochMilli());
        assertTrue(seasons.checkRollover());
        assertEquals("S2", seasons.currentId());
        assertEquals(List.of("S2"), started);
        var finished = seasons.archive().get(0);
        assertEquals(List.of("Alex", "Robin"), finished.top().stream().map(SeasonService.Entry::name).toList(), "online and stored, best two");

        SeasonService reloaded = new SeasonService(new JsonFileService(), dir.resolve("seasons"), () -> config, now::get, Runnable::run,
                ranking, started::add);
        reloaded.load();
        assertEquals("S2", reloaded.currentId(), "the season survives a restart");
        assertEquals(1, reloaded.archive().size());
        assertNotEquals(0, reloaded.startedAt());
    }

    private static PlayerProfile seasonProfile(String name, String season, int kills) {
        PlayerProfile profile = PlayerProfile.createDefault(UUID.randomUUID(), name, true);
        profile.getProgress().setSeason(season, kills, 1, 2);
        return profile;
    }

    private static List<String> ids(List<TaskDefinition> tasks) {
        return tasks.stream().map(TaskDefinition::getId).toList();
    }
}

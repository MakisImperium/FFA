package net.zernyth.ffa.progression;

import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.model.PlayerProfile;
import net.zernyth.ffa.util.JsonFileService;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Seasons: numbered periods (a month or a week) with their own kill counts.
 *
 * <p>When a period ends - checked every few minutes, or ended by an admin - the best players of the
 * season go into {@code seasons/archive.json} and a new season starts. Profiles are not rewritten
 * in bulk: each one drops its old season numbers the next time it is used ({@code enterSeason}), so
 * a season change costs nothing on a big database.
 */
public final class SeasonService {
    /** The running season, stored in {@code seasons/state.json}. */
    public static final class State {
        int number;
        long startedAt;
        String periodKey;
    }

    public record Entry(String name, String uuid, int kills, int deaths, int bestStreak) {}

    /** A finished season. */
    public static final class Archived {
        int number;
        String id;
        long startedAt;
        long endedAt;
        List<Entry> top = new ArrayList<>();

        public int number() { return number; }
        public String id() { return id; }
        public long startedAt() { return startedAt; }
        public long endedAt() { return endedAt; }
        public List<Entry> top() { return top == null ? List.of() : top; }
    }

    private static final class ArchiveFile {
        List<Archived> seasons = new ArrayList<>();
    }

    /** Where the best of a season come from: storage (in the background) and the players online now. */
    public interface Ranking {
        /** The stored best of a season; {@code result} may run on any thread. */
        void storedTop(String seasonId, int limit, Consumer<List<PlayerProfile>> result);

        /** Copies of the online profiles that are still in this season. */
        List<PlayerProfile> online(String seasonId);
    }

    private final JsonFileService json;
    private final Path statePath;
    private final Path archivePath;
    private final Supplier<ProgressionConfiguration> config;
    private final LongSupplier clock;
    private final Executor io;
    private final Ranking ranking;
    private final Consumer<String> onNewSeason;

    private volatile State state;
    private volatile List<Archived> archive = List.of();

    public SeasonService(JsonFileService json, Path directory, Supplier<ProgressionConfiguration> config, LongSupplier clock,
                         Executor io, Ranking ranking, Consumer<String> onNewSeason) {
        this.json = json;
        this.statePath = directory.resolve("state.json");
        this.archivePath = directory.resolve("archive.json");
        this.config = config;
        this.clock = clock;
        this.io = io;
        this.ranking = ranking;
        this.onNewSeason = onNewSeason;
    }

    /** Reads the stored season and archive; at plugin start, before players join. */
    public void load() throws IOException {
        this.state = json.read(statePath, State.class).orElse(null);
        this.archive = json.read(archivePath, ArchiveFile.class).map(file -> List.copyOf(file.seasons)).orElse(List.of());
        if (state == null) {
            State first = new State();
            first.number = 1;
            first.startedAt = clock.getAsLong();
            first.periodKey = periodKey(Instant.ofEpochMilli(first.startedAt));
            this.state = first;
            json.write(statePath, first);
        }
    }

    /** The current season's id, e.g. "S3"; null when seasons are switched off. */
    public String currentId() {
        State current = state;
        return config.get().isSeasonsEnabled() && current != null ? "S" + current.number : null;
    }

    public int currentNumber() {
        return state == null ? 0 : state.number;
    }

    public long startedAt() {
        return state == null ? 0 : state.startedAt;
    }

    public List<Archived> archive() {
        return archive;
    }

    /** Starts a new season when the calendar period has moved on. Call regularly. */
    public boolean checkRollover() {
        if (!config.get().isSeasonsEnabled() || state == null) {
            return false;
        }
        String now = periodKey(Instant.ofEpochMilli(clock.getAsLong()));
        if (now.equals(state.periodKey)) {
            return false;
        }
        rollover(now);
        return true;
    }

    /** Ends the season now, e.g. by an admin; the next one runs until the end of the current period. */
    public void endSeasonNow() {
        rollover(periodKey(Instant.ofEpochMilli(clock.getAsLong())));
    }

    /**
     * Ends the running season. The online players' numbers are copied first - starting the new season
     * resets them - and merged with the stored best of the old season, read in the background.
     */
    private synchronized void rollover(String newPeriodKey) {
        State old = state;
        String oldId = "S" + old.number;
        int size = config.get().getSeasonArchiveSize();
        List<PlayerProfile> onlineBefore = ranking.online(oldId);

        Archived finished = new Archived();
        finished.number = old.number;
        finished.id = oldId;
        finished.startedAt = old.startedAt;
        finished.endedAt = clock.getAsLong();

        State next = new State();
        next.number = old.number + 1;
        next.startedAt = clock.getAsLong();
        next.periodKey = newPeriodKey;
        this.state = next;
        io.execute(() -> write(statePath, next));
        onNewSeason.accept("S" + next.number);

        ranking.storedTop(oldId, size, stored -> {
            java.util.Map<String, PlayerProfile> merged = new java.util.LinkedHashMap<>();
            stored.forEach(profile -> merged.put(profile.getUniqueIdRaw(), profile));
            onlineBefore.forEach(profile -> merged.put(profile.getUniqueIdRaw(), profile));
            merged.values().stream()
                    .sorted(LeaderboardCategory.SEASON_KILLS.comparator())
                    .limit(size)
                    .forEach(profile -> {
                        var progress = profile.getProgress();
                        finished.top.add(new Entry(profile.getLastKnownName(), profile.getUniqueIdRaw(),
                                progress.getSeasonKills(), progress.getSeasonDeaths(), progress.getSeasonBestStreak()));
                    });
            ArchiveFile file = new ArchiveFile();
            synchronized (this) {
                List<Archived> nextArchive = new ArrayList<>(archive);
                nextArchive.add(finished);
                this.archive = List.copyOf(nextArchive);
                file.seasons = nextArchive;
            }
            io.execute(() -> write(archivePath, file));
        });
    }

    private void write(Path path, Object value) {
        try {
            json.write(path, value);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not store the season change in " + path, exception);
        }
    }

    private String periodKey(Instant instant) {
        ZoneId zone = config.get().zone();
        return config.get().isWeeklySeasons() ? TaskRules.weeklyKey(instant, zone) : YearMonth.from(instant.atZone(zone)).toString();
    }
}

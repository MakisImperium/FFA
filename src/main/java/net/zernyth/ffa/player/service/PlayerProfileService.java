package net.zernyth.ffa.player.service;

import com.google.gson.Gson;
import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.model.PlayerProfile;
import net.zernyth.ffa.player.model.PlayerStatistics;
import net.zernyth.ffa.player.storage.PlayerProfileRepository;
import org.powernukkitx.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Player profiles in memory, with every file and database access off the main thread.
 *
 * <p>All repository work runs on one background thread, in the order it was asked for. That keeps
 * the order per player for free: a save queued when a player leaves runs before the load queued
 * when they come straight back, so they never get yesterday's numbers. Results that touch the game
 * come back through {@code mainThread}.
 *
 * <p>While a profile loads the player holds a placeholder. It is never saved - saving it would
 * overwrite the real profile with zeros - and anything that changes stats waits until the real one
 * is there ({@link #isLoaded}).
 */
public final class PlayerProfileService {

    /** Where failures are reported; the plugin logger in production. */
    public interface ErrorReporter {
        void error(String message, Throwable cause);
    }

    private static final Gson COPIER = new Gson();

    private final PlayerProfileRepository repository;
    private final ErrorReporter errors;
    private final Executor mainThread;
    private final boolean scoreboardEnabledByDefault;
    private final boolean strictDataDependency;
    private final Runnable repositoryUnavailableCallback;

    private final Map<UUID, PlayerProfile> profilesById = new ConcurrentHashMap<>();
    /** Players whose profile is still on its way; the value identifies the load, so a stale one is ignored. */
    private final Map<UUID, Long> loadingGeneration = new ConcurrentHashMap<>();
    private final AtomicLong generations = new AtomicLong();

    private final Map<UUID, PlayerProfile> pendingPersistence = new ConcurrentHashMap<>();
    private final Set<UUID> queuedPersistenceIds = ConcurrentHashMap.newKeySet();
    private final ExecutorService io;
    private final AtomicBoolean outageSignalSent = new AtomicBoolean(false);
    private final AtomicBoolean pinging = new AtomicBoolean(false);
    /** Told on the main thread whenever a profile has arrived (season and task periods hook in here). */
    private final List<Consumer<UUID>> loadListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile boolean dataAvailable = true;

    public PlayerProfileService(
            PlayerProfileRepository repository,
            ErrorReporter errors,
            Executor mainThread,
            boolean scoreboardEnabledByDefault,
            boolean strictDataDependency,
            Runnable repositoryUnavailableCallback
    ) {
        this.repository = repository;
        this.errors = errors;
        this.mainThread = mainThread;
        this.scoreboardEnabledByDefault = scoreboardEnabledByDefault;
        this.strictDataDependency = strictDataDependency;
        this.repositoryUnavailableCallback = repositoryUnavailableCallback;
        this.io = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ffa-profile-io");
            thread.setDaemon(true);
            return thread;
        });
    }

    // --- loading --------------------------------------------------------------------------------

    /** Starts loading a joining player's profile; returns at once. */
    public void loadProfile(Player player) {
        loadProfile(player.getUniqueId(), player.getName(), profile -> { });
    }

    /** As above, then runs {@code onLoaded} on the main thread once the real profile is in place. */
    public void loadProfile(Player player, Consumer<PlayerProfile> onLoaded) {
        loadProfile(player.getUniqueId(), player.getName(), onLoaded);
    }

    public void loadProfile(UUID uniqueId, String playerName, Consumer<PlayerProfile> onLoaded) {
        PlayerProfile placeholder = PlayerProfile.createDefault(uniqueId, playerName, scoreboardEnabledByDefault);
        if (strictDataDependency && !dataAvailable) {
            profilesById.put(uniqueId, placeholder);
            loadingGeneration.remove(uniqueId);
            onLoaded.accept(placeholder);
            return;
        }
        long generation = generations.incrementAndGet();
        loadingGeneration.put(uniqueId, generation);
        profilesById.put(uniqueId, placeholder);
        io.execute(() -> {
            PlayerProfile loaded;
            try {
                loaded = repository.findByUniqueId(uniqueId)
                        .orElseGet(() -> PlayerProfile.createDefault(uniqueId, playerName, scoreboardEnabledByDefault));
            } catch (Exception exception) {
                handleRepositoryFailure("load profile for " + playerName, exception);
                loaded = PlayerProfile.createDefault(uniqueId, playerName, scoreboardEnabledByDefault);
            }
            PlayerProfile result = loaded;
            result.setLastKnownName(playerName);
            mainThread.execute(() -> {
                // Left again, or joined again with a newer load: this result is not wanted any more.
                if (!Long.valueOf(generation).equals(loadingGeneration.get(uniqueId))) {
                    return;
                }
                loadingGeneration.remove(uniqueId);
                profilesById.put(uniqueId, result);
                loadListeners.forEach(listener -> listener.accept(uniqueId));
                onLoaded.accept(result);
            });
        });
    }

    public void addLoadListener(Consumer<UUID> listener) {
        loadListeners.add(listener);
    }

    /** True once the real profile is there; false while it loads or when the player is not online. */
    public boolean isLoaded(UUID uniqueId) {
        return profilesById.containsKey(uniqueId) && !loadingGeneration.containsKey(uniqueId);
    }

    public void unloadProfile(Player player, boolean persist) {
        unloadProfile(player.getUniqueId(), persist);
    }

    public void unloadProfile(UUID uniqueId, boolean persist) {
        boolean wasLoading = loadingGeneration.remove(uniqueId) != null;
        PlayerProfile profile = profilesById.remove(uniqueId);
        // A placeholder is never saved: it would overwrite the stored profile with zeros.
        if (persist && profile != null && !wasLoading) {
            persistProfile(profile);
        }
    }

    public Optional<PlayerProfile> getProfile(UUID uniqueId) {
        return Optional.ofNullable(profilesById.get(uniqueId));
    }

    /** The profiles of everyone online with a loaded profile; copies, safe to sort and keep. */
    public List<PlayerProfile> onlineSnapshot() {
        List<PlayerProfile> out = new ArrayList<>();
        profilesById.forEach((id, profile) -> {
            if (!loadingGeneration.containsKey(id)) {
                out.add(copy(profile));
            }
        });
        return out;
    }

    /**
     * The best {@code limit} stored profiles for a category, read in the background; {@code onResult}
     * gets them on the calling thread of the IO queue, not the main thread - the result is plain data.
     */
    public void fetchTop(LeaderboardCategory category, int limit, Consumer<List<PlayerProfile>> onResult) {
        fetchTop(category, limit, null, onResult);
    }

    /** As above; a seasonal category only ranks profiles of {@code seasonId}. */
    public void fetchTop(LeaderboardCategory category, int limit, String seasonId, Consumer<List<PlayerProfile>> onResult) {
        if (strictDataDependency && !dataAvailable) {
            return;
        }
        io.execute(() -> {
            try {
                onResult.accept(repository.findTop(category, limit, seasonId));
            } catch (Exception exception) {
                handleRepositoryFailure("load the " + category + " leaderboard", exception);
            }
        });
    }

    // --- preferences and statistics -------------------------------------------------------------

    public boolean isScoreboardEnabled(UUID uniqueId) {
        if (strictDataDependency && !dataAvailable) {
            return false;
        }
        return getProfile(uniqueId)
                .map(profile -> profile.getPreferences().isScoreboardEnabled())
                .orElse(scoreboardEnabledByDefault);
    }

    public boolean setScoreboardEnabled(UUID uniqueId, boolean enabled) {
        PlayerProfile profile = editable(uniqueId);
        if (profile == null) {
            return false;
        }
        profile.getPreferences().setScoreboardEnabled(enabled);
        persistProfile(profile);
        return true;
    }

    public Optional<Boolean> toggleScoreboard(UUID uniqueId) {
        PlayerProfile profile = editable(uniqueId);
        if (profile == null) {
            return Optional.empty();
        }
        boolean nextState = !profile.getPreferences().isScoreboardEnabled();
        profile.getPreferences().setScoreboardEnabled(nextState);
        persistProfile(profile);
        return Optional.of(nextState);
    }

    public PlayerLanguage getLanguage(UUID uniqueId) {
        return getProfile(uniqueId)
                .map(profile -> profile.getPreferences().getLanguage())
                .orElse(PlayerLanguage.ENGLISH);
    }

    public boolean setLanguage(UUID uniqueId, PlayerLanguage language) {
        PlayerProfile profile = editable(uniqueId);
        if (profile == null) {
            return false;
        }
        profile.getPreferences().setLanguage(language);
        persistProfile(profile);
        return true;
    }

    public void setSelectedKit(UUID uniqueId, String kitId) {
        PlayerProfile profile = editable(uniqueId);
        if (profile == null) {
            return;
        }
        profile.setSelectedKitId(kitId);
        persistProfile(profile);
    }

    public Optional<String> getSelectedKit(UUID uniqueId) {
        return getProfile(uniqueId).map(PlayerProfile::getSelectedKitId);
    }

    public void registerKill(UUID uniqueId) {
        PlayerProfile profile = editable(uniqueId);
        if (profile == null) {
            return;
        }
        profile.getStatistics().registerKill();
        persistProfile(profile);
    }

    public void registerDeath(UUID uniqueId) {
        PlayerProfile profile = editable(uniqueId);
        if (profile == null) {
            return;
        }
        profile.getStatistics().registerDeath();
        persistProfile(profile);
    }

    /** Applies a change to a loaded profile and saves it; does nothing while it loads or offline. */
    public boolean update(UUID uniqueId, Consumer<PlayerProfile> change) {
        PlayerProfile profile = editable(uniqueId);
        if (profile == null) {
            return false;
        }
        change.accept(profile);
        persistProfile(profile);
        return true;
    }

    public PlayerStatistics getStatisticsOrDefault(UUID uniqueId) {
        return profilesById.getOrDefault(
                uniqueId,
                PlayerProfile.createDefault(uniqueId, "Unknown", scoreboardEnabledByDefault)
        ).getStatistics();
    }

    /** A loaded profile that may be changed now, or null. */
    private PlayerProfile editable(UUID uniqueId) {
        if (strictDataDependency && !dataAvailable) {
            return null;
        }
        if (loadingGeneration.containsKey(uniqueId)) {
            return null;
        }
        return profilesById.get(uniqueId);
    }

    // --- persistence ----------------------------------------------------------------------------

    /** Saves every loaded profile and waits for it: for shutdown, when nothing may be lost. */
    public void persistAllCachedProfiles() {
        if (strictDataDependency && !dataAvailable) {
            return;
        }
        profilesById.forEach((id, profile) -> {
            if (!loadingGeneration.containsKey(id)) {
                persistProfile(profile);
            }
        });
        flushPendingSynchronously();
    }

    public boolean pingRepository() {
        try {
            boolean reachable = repository.ping();
            if (reachable) {
                setDataAvailable(true);
            }
            return reachable;
        } catch (Exception exception) {
            handleRepositoryFailure("ping player profile repository", exception);
            return false;
        }
    }

    /** Pings storage in the background; {@code onResult} runs on the main thread. One ping at a time. */
    public void pingRepositoryAsync(Consumer<Boolean> onResult) {
        if (!pinging.compareAndSet(false, true)) {
            return;
        }
        io.execute(() -> {
            boolean reachable;
            try {
                reachable = repository.ping();
            } catch (Exception exception) {
                errors.error("Failed to ping player profile repository", exception);
                reachable = false;
            }
            boolean result = reachable;
            pinging.set(false);
            mainThread.execute(() -> onResult.accept(result));
        });
    }

    public boolean isDataAvailable() {
        return dataAvailable;
    }

    public void setDataAvailable(boolean available) {
        this.dataAvailable = available;
        if (available) {
            outageSignalSent.set(false);
        }
    }

    public void closeRepository() {
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                io.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
        flushPendingSynchronously();
        try {
            repository.close();
        } catch (Exception exception) {
            errors.error("Failed to close player repository", exception);
        }
    }

    private void persistProfile(PlayerProfile profile) {
        if (strictDataDependency && !dataAvailable) {
            return;
        }
        UUID uniqueId = profile.getUniqueId();
        // A copy: the background thread must not read a profile the game is changing at that moment.
        pendingPersistence.put(uniqueId, copy(profile));
        if (queuedPersistenceIds.add(uniqueId)) {
            try {
                io.execute(() -> flushOne(uniqueId));
            } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
                queuedPersistenceIds.remove(uniqueId);
                // Picked up by flushPendingSynchronously during shutdown.
            }
        }
    }

    private void flushPendingSynchronously() {
        for (UUID pendingId : new ArrayList<>(pendingPersistence.keySet())) {
            flushOne(pendingId);
        }
    }

    private synchronized void flushOne(UUID uniqueId) {
        queuedPersistenceIds.remove(uniqueId);
        PlayerProfile profile = pendingPersistence.remove(uniqueId);
        if (profile == null) {
            return;
        }
        try {
            repository.save(profile);
        } catch (Exception exception) {
            handleRepositoryFailure("save profile for " + profile.getLastKnownName(), exception);
        }
    }

    private static PlayerProfile copy(PlayerProfile profile) {
        return COPIER.fromJson(COPIER.toJsonTree(profile), PlayerProfile.class);
    }

    private void handleRepositoryFailure(String action, Exception exception) {
        errors.error("Failed to " + action, exception);
        if (!strictDataDependency) {
            return;
        }

        dataAvailable = false;
        if (!outageSignalSent.compareAndSet(false, true)) {
            return;
        }

        if (repositoryUnavailableCallback != null) {
            mainThread.execute(() -> {
                try {
                    repositoryUnavailableCallback.run();
                } catch (Exception callbackException) {
                    errors.error("Failed to trigger repository outage callback", callbackException);
                }
            });
        }
    }
}

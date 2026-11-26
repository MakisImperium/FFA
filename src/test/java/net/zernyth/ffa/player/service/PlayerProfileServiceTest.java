package net.zernyth.ffa.player.service;

import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.model.PlayerProfile;
import net.zernyth.ffa.player.storage.PlayerProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Profiles load and save off the main thread, in order, and a half-loaded profile is never stored. */
class PlayerProfileServiceTest {
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000a1e5");

    /** Storage that can be held back, to see what the plugin does while it waits. */
    static final class SlowRepository implements PlayerProfileRepository {
        final Map<UUID, PlayerProfile> stored = new ConcurrentHashMap<>();
        final List<PlayerProfile> saves = new ArrayList<>();
        volatile CountDownLatch gate = new CountDownLatch(0);

        @Override
        public Optional<PlayerProfile> findByUniqueId(UUID uniqueId) throws Exception {
            gate.await(5, TimeUnit.SECONDS);
            return Optional.ofNullable(stored.get(uniqueId));
        }

        @Override
        public List<PlayerProfile> loadAllProfiles() {
            return new ArrayList<>(stored.values());
        }

        @Override
        public synchronized void save(PlayerProfile profile) {
            saves.add(profile);
            stored.put(profile.getUniqueId(), profile);
        }
    }

    /** The main thread, as a queue the test runs by hand. */
    final ConcurrentLinkedQueue<Runnable> main = new ConcurrentLinkedQueue<>();
    final SlowRepository repository = new SlowRepository();
    final PlayerProfileService service = new PlayerProfileService(
            repository, (message, cause) -> { }, main::add, true, false, null);

    @AfterEach
    void close() {
        repository.gate = new CountDownLatch(0);
        service.closeRepository();
    }

    private void runMainUntil(java.util.function.BooleanSupplier done) throws InterruptedException {
        long until = System.currentTimeMillis() + 5000;
        while (!done.getAsBoolean() && System.currentTimeMillis() < until) {
            Runnable task;
            while ((task = main.poll()) != null) {
                task.run();
            }
            Thread.sleep(5);
        }
        assertTrue(done.getAsBoolean(), "timed out");
    }

    private static PlayerProfile storedWithKills(int kills) {
        PlayerProfile profile = PlayerProfile.createDefault(ALEX, "Alex", true);
        profile.getStatistics().setKills(kills);
        return profile;
    }

    @Test
    void joiningDoesNotWaitForStorage() throws Exception {
        repository.stored.put(ALEX, storedWithKills(42));
        repository.gate = new CountDownLatch(1);

        long start = System.nanoTime();
        AtomicReference<PlayerProfile> loaded = new AtomicReference<>();
        service.loadProfile(ALEX, "Alex", loaded::set);
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 200, "join must not block");

        assertFalse(service.isLoaded(ALEX));
        assertFalse(service.update(ALEX, profile -> profile.getStatistics().registerKill()), "no changes while loading");

        repository.gate.countDown();
        runMainUntil(() -> loaded.get() != null);
        assertTrue(service.isLoaded(ALEX));
        assertEquals(42, service.getStatisticsOrDefault(ALEX).getKills());
    }

    @Test
    void aPlaceholderIsNeverSaved() throws Exception {
        repository.stored.put(ALEX, storedWithKills(42));
        repository.gate = new CountDownLatch(1);
        AtomicReference<PlayerProfile> loaded = new AtomicReference<>();
        service.loadProfile(ALEX, "Alex", loaded::set);

        service.unloadProfile(ALEX, true); // left before the profile arrived
        repository.gate.countDown();
        Thread.sleep(100);
        runMainUntil(main::isEmpty);

        assertEquals(List.of(), repository.saves, "the zero placeholder must not overwrite the stored profile");
        assertEquals(null, loaded.get(), "a late result for a player who left is dropped");
        assertEquals(42, repository.stored.get(ALEX).getStatistics().getKills());
    }

    @Test
    void aQuickRejoinSeesWhatWasJustSaved() throws Exception {
        AtomicReference<PlayerProfile> first = new AtomicReference<>();
        service.loadProfile(ALEX, "Alex", first::set);
        runMainUntil(() -> first.get() != null);

        service.registerKill(ALEX);
        service.registerKill(ALEX);
        service.unloadProfile(ALEX, true);

        AtomicReference<PlayerProfile> second = new AtomicReference<>();
        service.loadProfile(ALEX, "Alex", second::set);
        runMainUntil(() -> second.get() != null);
        assertEquals(2, second.get().getStatistics().getKills(), "the save ran before the new load");
    }

    @Test
    void savesAreCopiesNotTheLiveProfile() throws Exception {
        AtomicReference<PlayerProfile> loaded = new AtomicReference<>();
        service.loadProfile(ALEX, "Alex", loaded::set);
        runMainUntil(() -> loaded.get() != null);

        service.registerKill(ALEX);
        service.persistAllCachedProfiles();
        PlayerProfile saved = repository.stored.get(ALEX);
        service.registerKill(ALEX); // the game goes on
        assertEquals(1, saved.getStatistics().getKills(), "what was saved does not change afterwards");
    }

    @Test
    void leaderboardsMergeStoredAndOnlinePlayers() throws Exception {
        UUID robin = UUID.randomUUID();
        PlayerProfile stored = PlayerProfile.createDefault(robin, "Robin", true);
        stored.getStatistics().setKills(10);
        repository.stored.put(robin, stored);
        repository.stored.put(ALEX, storedWithKills(5));

        LeaderboardService boards = new LeaderboardService(service);
        boards.refresh();
        Thread.sleep(200);

        AtomicReference<PlayerProfile> loaded = new AtomicReference<>();
        service.loadProfile(ALEX, "Alex", loaded::set);
        runMainUntil(() -> loaded.get() != null);
        for (int i = 0; i < 10; i++) service.registerKill(ALEX); // online now, 15 kills, not saved yet

        List<PlayerProfile> top = boards.top(LeaderboardCategory.KILLS, 10);
        assertEquals(List.of("Alex", "Robin"), top.stream().map(PlayerProfile::getLastKnownName).toList());
        assertEquals(15, top.get(0).getStatistics().getKills());
    }
}

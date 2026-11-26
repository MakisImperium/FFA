package net.zernyth.ffa;

import com.google.gson.Gson;
import net.zernyth.ffa.arena.model.ArenaDefinition;
import net.zernyth.ffa.arena.model.ArenaStatus;
import net.zernyth.ffa.arena.model.CuboidZone;
import net.zernyth.ffa.arena.model.SpawnPoint;
import net.zernyth.ffa.arena.persistence.JsonArenaRepository;
import net.zernyth.ffa.config.PluginConfiguration;
import net.zernyth.ffa.config.StorageBackend;
import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.model.PlayerProfile;
import net.zernyth.ffa.player.model.PlayerStatistics;
import net.zernyth.ffa.player.storage.JsonPlayerProfileRepository;
import net.zernyth.ffa.util.JsonFileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What FFA 3.0 already does, pinned down before anything is moved around. */
class CoreBehaviourTest {
    @TempDir
    Path dir;

    private final JsonFileService json = new JsonFileService();

    @Test
    void killsBuildAStreakAndADeathEndsIt() {
        PlayerStatistics stats = new PlayerStatistics();
        stats.registerKill();
        stats.registerKill();
        stats.registerKill();
        assertEquals(3, stats.getCurrentKillstreak());
        assertEquals(3, stats.getBestKillstreak());
        stats.registerDeath();
        stats.registerKill();
        assertEquals(1, stats.getCurrentKillstreak());
        assertEquals(3, stats.getBestKillstreak(), "the best streak stays");
        assertEquals(4, stats.getKills());
        assertEquals(1, stats.getDeaths());
    }

    @Test
    void statisticsNeverGoNegative() {
        PlayerStatistics stats = new PlayerStatistics();
        stats.setKills(-5);
        stats.setDeaths(-1);
        stats.setCurrentKillstreak(4);
        stats.setBestKillstreak(2);
        assertEquals(0, stats.getKills());
        assertEquals(0, stats.getDeaths());
        assertEquals(4, stats.getBestKillstreak(), "best can never be below current");
    }

    @Test
    void kdIsKillsPerDeathOrTheKillsWithoutDeaths() {
        PlayerStatistics stats = new PlayerStatistics();
        stats.setKills(9);
        assertEquals(9.0, LeaderboardCategory.kd(stats));
        stats.setDeaths(3);
        assertEquals(3.0, LeaderboardCategory.kd(stats));
    }

    @Test
    void zonesContainTheirEdgesInTheirWorldOnly() {
        CuboidZone zone = new CuboidZone("arena", 10, 60, 10, 0, 70, 0); // corners in any order
        assertTrue(zone.contains("arena", 0, 60, 0));
        assertTrue(zone.contains("ARENA", 10, 70, 10), "world names ignore case");
        assertTrue(zone.contains("arena", 5, 65, 5));
        assertFalse(zone.contains("arena", 10.01, 65, 5));
        assertFalse(zone.contains("lobby", 5, 65, 5));
        assertFalse(zone.contains(null, 5, 65, 5));
    }

    @Test
    void anOldOrPartialConfigIsCompletedWithDefaults() {
        PluginConfiguration config = new Gson().fromJson("{\"storageBackend\":\"json\"}", PluginConfiguration.class);
        config.normalize();
        assertEquals(StorageBackend.JSON, config.resolveStorageBackend());
        assertNotNull(config.getDatabase());
        assertNotNull(config.getScoreboard());
        assertFalse(config.getDatabase().isConfigured(), "the shipped placeholder password does not count as configured");
    }

    @Test
    void profilesRoundTripThroughJsonFiles() throws Exception {
        JsonPlayerProfileRepository repository = new JsonPlayerProfileRepository(json, dir.resolve("players"));
        UUID id = UUID.randomUUID();
        PlayerProfile profile = PlayerProfile.createDefault(id, "Alex", false);
        profile.getStatistics().setKills(12);
        profile.getPreferences().setLanguage(PlayerLanguage.GERMAN);
        profile.setSelectedKitId("soldier");
        repository.save(profile);

        PlayerProfile loaded = repository.findByUniqueId(id).orElseThrow();
        assertEquals(12, loaded.getStatistics().getKills());
        assertEquals(PlayerLanguage.GERMAN, loaded.getPreferences().getLanguage());
        assertEquals("soldier", loaded.getSelectedKitId());
        assertFalse(loaded.getPreferences().isScoreboardEnabled());
        try (var files = Files.list(dir.resolve("players"))) {
            assertEquals(1, files.count(), "no temporary files are left behind");
        }
    }

    @Test
    void theJsonLeaderboardRanksBestFirstWithStableTies() throws Exception {
        JsonPlayerProfileRepository repository = new JsonPlayerProfileRepository(json, dir.resolve("players"));
        for (String[] row : new String[][]{{"Kai", "3"}, {"alex", "7"}, {"Bea", "7"}, {"Cem", "1"}}) {
            PlayerProfile p = PlayerProfile.createDefault(UUID.randomUUID(), row[0], true);
            p.getStatistics().setKills(Integer.parseInt(row[1]));
            repository.save(p);
        }
        List<String> top = repository.findTop(LeaderboardCategory.KILLS, 3).stream().map(PlayerProfile::getLastKnownName).toList();
        assertEquals(List.of("alex", "Bea", "Kai"), top);
    }

    @Test
    void arenasRoundTripWithSpawnsZonesAndStatus() throws Exception {
        JsonArenaRepository repository = new JsonArenaRepository(json, dir.resolve("arenas"));
        ArenaDefinition arena = new ArenaDefinition("pit", "The Pit", "world_pit");
        arena.getSpawnPoints().add(new Gson().fromJson("{\"x\":1,\"y\":64,\"z\":2,\"yaw\":90,\"pitch\":0}", SpawnPoint.class));
        arena.upsertZone("safe", new CuboidZone("world_pit", 0, 60, 0, 5, 70, 5));
        arena.setSafeZoneId("safe");
        arena.setStatus(ArenaStatus.ACTIVE);
        repository.save(arena);

        ArenaDefinition loaded = repository.load("pit").orElseThrow();
        assertEquals("The Pit", loaded.getDisplayName());
        assertEquals(ArenaStatus.ACTIVE, loaded.getStatus());
        assertEquals(1, loaded.getSpawnPoints().size());
        assertTrue(loaded.getSafeZone().orElseThrow().contains("world_pit", 3, 65, 3));
    }
}

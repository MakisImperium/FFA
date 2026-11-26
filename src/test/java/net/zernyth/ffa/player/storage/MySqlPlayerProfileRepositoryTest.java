package net.zernyth.ffa.player.storage;

import com.google.gson.Gson;
import net.zernyth.ffa.config.DatabaseConfiguration;
import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.model.PlayerProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The MySQL repository against a real MySQL; skipped when Docker is not running. */
@Testcontainers(disabledWithoutDocker = true)
class MySqlPlayerProfileRepositoryTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("ffa");

    static MySqlPlayerProfileRepository repository;

    @BeforeAll
    static void connect() throws Exception {
        DatabaseConfiguration config = new Gson().fromJson("""
                {"host":"%s","port":%d,"database":"ffa","username":"root","password":"%s",
                 "table":"ffa_player_profiles","useSsl":false,"allowPublicKeyRetrieval":true}
                """.formatted(MYSQL.getHost(), MYSQL.getMappedPort(3306), MYSQL.getPassword()), DatabaseConfiguration.class);
        repository = new MySqlPlayerProfileRepository(config);
    }

    @AfterAll
    static void close() {
        if (repository != null) repository.close();
    }

    @Test
    void savesLoadsAndRanks() throws Exception {
        UUID alex = UUID.randomUUID();
        PlayerProfile profile = PlayerProfile.createDefault(alex, "Alex", true);
        profile.getStatistics().setKills(30);
        profile.getStatistics().setDeaths(3);
        repository.save(profile);
        PlayerProfile bea = PlayerProfile.createDefault(UUID.randomUUID(), "Bea", true);
        bea.getStatistics().setKills(40);
        bea.getStatistics().setDeaths(20);
        repository.save(bea);

        profile.getStatistics().setKills(31);
        repository.save(profile); // update, not a second row
        assertEquals(31, repository.findByUniqueId(alex).orElseThrow().getStatistics().getKills());

        assertEquals(List.of("Bea", "Alex"), names(repository.findTop(LeaderboardCategory.KILLS, 5)));
        assertEquals(List.of("Alex", "Bea"), names(repository.findTop(LeaderboardCategory.KD, 5)));
        assertEquals(1, repository.findTop(LeaderboardCategory.KILLS, 1).size());
        assertTrue(repository.ping());
    }

    @Test
    void storesProgressAndRanksTheSeason() throws Exception {
        PlayerProfile kai = PlayerProfile.createDefault(UUID.randomUUID(), "Kai", true);
        kai.getProgress().addXp(5000);
        kai.getProgress().addCoins(77);
        kai.getProgress().setSeason("S7", 12, 2, 5);
        kai.getProgress().getAchievements().put("first_blood", 42L);
        kai.getProgress().getWeeklyTasks().reset("2026-W48");
        repository.save(kai);
        PlayerProfile old = PlayerProfile.createDefault(UUID.randomUUID(), "Old", true);
        old.getProgress().setSeason("S6", 99, 0, 0);
        repository.save(old);

        var loaded = repository.findByUniqueId(kai.getUniqueId()).orElseThrow().getProgress();
        assertEquals(5000, loaded.getXp());
        assertEquals(77, loaded.getCoins());
        assertEquals("S7", loaded.getSeasonId());
        assertEquals(12, loaded.getSeasonKills());
        assertEquals(42L, loaded.getAchievements().get("first_blood"));
        assertEquals("2026-W48", loaded.getWeeklyTasks().getPeriodKey());

        assertEquals(List.of("Kai"), names(repository.findTop(LeaderboardCategory.SEASON_KILLS, 10, "S7")), "other seasons do not count");
        assertEquals("Kai", repository.findTop(LeaderboardCategory.LEVEL, 1).get(0).getLastKnownName());
    }

    private static List<String> names(List<PlayerProfile> profiles) {
        return profiles.stream().map(PlayerProfile::getLastKnownName).toList();
    }
}

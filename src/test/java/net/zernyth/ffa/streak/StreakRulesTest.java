package net.zernyth.ffa.streak;

import com.google.gson.Gson;
import net.zernyth.ffa.arena.model.ArenaDefinition;
import net.zernyth.ffa.config.PluginConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static net.zernyth.ffa.streak.StreakRules.Audience.ARENA;
import static net.zernyth.ffa.streak.StreakRules.Audience.NONE;
import static net.zernyth.ffa.streak.StreakRules.Audience.SERVER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StreakRulesTest {
    private final StreakConfiguration defaults = new StreakConfiguration();

    @Test
    void theDefaultRewardsArriveAtThreeFiveAndTen() {
        assertEquals(List.of(StreakReward.Type.EFFECT), types(StreakRules.rewardsFor(3, null, defaults.getRewards())));
        assertEquals(List.of(StreakReward.Type.ITEM), types(StreakRules.rewardsFor(5, null, defaults.getRewards())));
        assertEquals(List.of(StreakReward.Type.HEAL, StreakReward.Type.REPAIR, StreakReward.Type.EFFECT),
                types(StreakRules.rewardsFor(10, null, defaults.getRewards())));
        assertEquals(List.of(), StreakRules.rewardsFor(4, null, defaults.getRewards()));
    }

    @Test
    void anArenaTableReplacesTheServerTableCompletely() {
        ArenaDefinition arena = new Gson().fromJson("""
                {"arenaId":"pit","streakRewards":{"2":[{"type":"feed"}],"3":[{"type":"teleport"}]}}
                """, ArenaDefinition.class);
        assertEquals(List.of(StreakReward.Type.FEED), types(StreakRules.rewardsFor(2, arena.getStreakRewards(), defaults.getRewards())));
        assertEquals(List.of(), StreakRules.rewardsFor(3, arena.getStreakRewards(), defaults.getRewards()), "unknown types are skipped");
        assertEquals(List.of(), StreakRules.rewardsFor(5, arena.getStreakRewards(), defaults.getRewards()), "no server fallback per streak");
        ArenaDefinition plain = new Gson().fromJson("{\"arenaId\":\"plain\",\"streakRewards\":{}}", ArenaDefinition.class);
        assertEquals(1, StreakRules.rewardsFor(5, plain.getStreakRewards(), defaults.getRewards()).size(), "an empty table uses the server's");
    }

    @Test
    void rewardValuesAreClampedToSaneRanges() {
        StreakReward reward = new Gson().fromJson("{\"type\":\"Effect\",\"id\":\"speed\",\"amplifier\":99,\"seconds\":-4,\"count\":900}", StreakReward.class);
        assertEquals(StreakReward.Type.EFFECT, reward.getType(), "type ignores case");
        assertEquals(10, reward.getAmplifier());
        assertEquals(1, reward.getSeconds());
        assertEquals(64, reward.getCount());
        assertNull(new Gson().fromJson("{}", StreakReward.class).getType());
    }

    @Test
    void streaksAreAnnouncedToTheArenaThenTheServer() {
        assertEquals(NONE, StreakRules.streakAudience(4, defaults));
        assertEquals(ARENA, StreakRules.streakAudience(5, defaults));
        assertEquals(NONE, StreakRules.streakAudience(7, defaults));
        assertEquals(ARENA, StreakRules.streakAudience(10, defaults));
        assertEquals(SERVER, StreakRules.streakAudience(15, defaults));
        assertEquals(SERVER, StreakRules.streakAudience(20, defaults));
    }

    @Test
    void shutdownsNeedAStreakWorthEnding() {
        assertEquals(NONE, StreakRules.shutdownAudience(4, defaults));
        assertEquals(ARENA, StreakRules.shutdownAudience(5, defaults));
        assertEquals(ARENA, StreakRules.shutdownAudience(9, defaults));
        assertEquals(SERVER, StreakRules.shutdownAudience(16, defaults));
    }

    @Test
    void zeroSwitchesAnnouncementsOff() {
        StreakConfiguration quiet = new Gson().fromJson("{\"announceEvery\":0,\"serverWideFrom\":0,\"shutdownFrom\":0}", StreakConfiguration.class);
        assertEquals(NONE, StreakRules.streakAudience(10, quiet));
        assertEquals(NONE, StreakRules.shutdownAudience(30, quiet));
        StreakConfiguration arenaOnly = new Gson().fromJson("{\"serverWideFrom\":0}", StreakConfiguration.class);
        assertEquals(ARENA, StreakRules.streakAudience(50, arenaOnly));
    }

    @Test
    void anOldConfigWithoutStreaksGetsTheDefaults() {
        PluginConfiguration config = new Gson().fromJson("{\"storageBackend\":\"JSON\"}", PluginConfiguration.class);
        config.normalize();
        assertEquals(Map.of("3", 1, "5", 1, "10", 3), Map.of(
                "3", config.getStreaks().getRewards().get("3").size(),
                "5", config.getStreaks().getRewards().get("5").size(),
                "10", config.getStreaks().getRewards().get("10").size()));
    }

    private static List<StreakReward.Type> types(List<StreakReward> rewards) {
        return rewards.stream().map(StreakReward::getType).toList();
    }
}

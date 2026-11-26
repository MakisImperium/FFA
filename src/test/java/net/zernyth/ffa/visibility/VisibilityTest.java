package net.zernyth.ffa.visibility;

import com.google.gson.Gson;
import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.model.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisibilityTest {
    private static PlayerProfile profile(String name, int kills, int deaths) {
        PlayerProfile profile = PlayerProfile.createDefault(UUID.randomUUID(), name, true);
        profile.getStatistics().setKills(kills);
        profile.getStatistics().setDeaths(deaths);
        return profile;
    }

    private static String plain(String text) {
        return text.replaceAll("§.", "");
    }

    @Test
    void boardListsPlacesWithValues() {
        List<PlayerProfile> top = List.of(profile("Alex", 30, 4), profile("Bea", 20, 10));
        assertEquals("#1 Alex - 30\n#2 Bea - 20", plain(BoardText.lines(LeaderboardCategory.KILLS, top, false, p -> 1)));
        assertEquals("#1 Alex - 7.50\n#2 Bea - 2.00", plain(BoardText.lines(LeaderboardCategory.KD, top, false, p -> 1)));
        assertEquals("#1 Alex - Lv 12", plain(BoardText.lines(LeaderboardCategory.LEVEL, top.subList(0, 1), false, p -> 12)));
        assertEquals("Noch keine Eintraege", plain(BoardText.lines(LeaderboardCategory.KILLS, List.of(), true, p -> 1)));
        assertTrue(plain(BoardText.title(LeaderboardCategory.SEASON_KILLS, true)).contains("Saison-Kills"));
    }

    @Test
    void hologramCategoriesFromConfig() {
        var hologram = new Gson().fromJson("{\"categories\":[\"kd\",\"nope\",\"KD\",\"LEVEL\"],\"rotateSeconds\":1,\"size\":99}",
                VisibilityConfiguration.Hologram.class);
        assertEquals(List.of(LeaderboardCategory.KD, LeaderboardCategory.LEVEL), hologram.categories());
        assertEquals(3, hologram.getRotateSeconds());
        assertEquals(15, hologram.getSize());
        var empty = new Gson().fromJson("{\"categories\":[\"nope\"]}", VisibilityConfiguration.Hologram.class);
        assertEquals(List.of(LeaderboardCategory.KILLS), empty.categories());
    }

    @Test
    void queueSendsOnePerIntervalAndDropsOverflow() {
        WebhookQueue queue = new WebhookQueue(2);
        assertTrue(queue.offer("a"));
        assertTrue(queue.offer("b"));
        assertFalse(queue.offer("c"), "full");
        assertFalse(queue.offer(" "), "nothing to say");
        assertEquals("a", queue.next(1000, 3000).orElseThrow());
        assertTrue(queue.next(2000, 3000).isEmpty(), "too soon");
        assertEquals("b", queue.next(4000, 3000).orElseThrow());
        assertTrue(queue.next(9000, 3000).isEmpty(), "empty");
    }

    @Test
    void namesCannotFormatOrPing() {
        assertEquals("\\_xX\\_Pro\\_Xx\\_", WebhookQueue.escape("_xX_Pro_Xx_"));
        assertEquals("Red", WebhookQueue.escape("§cRed"));
        assertEquals("\\*\\*bold\\*\\*", WebhookQueue.escape("**bold**"));
    }

    @Test
    void onlyDiscordWebhooksAreUsed() {
        Gson gson = new Gson();
        assertFalse(gson.fromJson("{}", VisibilityConfiguration.Discord.class).enabled(), "off by default");
        assertTrue(gson.fromJson("{\"webhookUrl\":\"https://discord.com/api/webhooks/1/abc\"}", VisibilityConfiguration.Discord.class).enabled());
        assertFalse(gson.fromJson("{\"webhookUrl\":\"http://discord.com/api/webhooks/1/abc\"}", VisibilityConfiguration.Discord.class).enabled());
        assertFalse(gson.fromJson("{\"webhookUrl\":\"https://example.com/hook\"}", VisibilityConfiguration.Discord.class).enabled());
    }

    @Test
    void longStreaksPostEveryFiveKills() {
        assertFalse(DiscordService.streakWorthPosting(14, 15));
        assertTrue(DiscordService.streakWorthPosting(15, 15));
        assertFalse(DiscordService.streakWorthPosting(16, 15));
        assertTrue(DiscordService.streakWorthPosting(20, 15));
        assertFalse(DiscordService.streakWorthPosting(30, 0), "0 turns it off");
    }
}

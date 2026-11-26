package net.zernyth.ffa.visibility;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.zernyth.ffa.combat.KillService;
import net.zernyth.ffa.player.service.PlayerProfileService;
import net.zernyth.ffa.progression.AchievementDefinition;
import net.zernyth.ffa.progression.SeasonService;
import org.powernukkitx.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Posts highlights to Discord: new best streaks, season ends with the top three, achievements.
 * Everything goes through a small queue sent from a background thread, one message per interval;
 * the game never waits for Discord.
 */
public final class DiscordService implements KillService.KillListener {
    private final Supplier<VisibilityConfiguration.Discord> config;
    private final PlayerProfileService profiles;
    private final BiConsumer<String, Throwable> log;
    private final WebhookQueue queue = new WebhookQueue(25);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ffa-discord");
        thread.setDaemon(true);
        return thread;
    });

    public DiscordService(Supplier<VisibilityConfiguration.Discord> config, PlayerProfileService profiles, BiConsumer<String, Throwable> log) {
        this.config = config;
        this.profiles = profiles;
        this.log = log;
        sender.scheduleWithFixedDelay(this::sendNext, 1, 1, TimeUnit.SECONDS);
    }

    public void post(String message) {
        if (config.get().enabled()) {
            queue.offer(message);
        }
    }

    @Override
    public void onKill(KillService.Kill kill) {
        int from = config.get().getStreakFrom();
        if (kill.killer() == null || !streakWorthPosting(kill.killerStreak(), from)) {
            return;
        }
        int best = profiles.getStatisticsOrDefault(kill.killer().getUniqueId()).getBestKillstreak();
        if (kill.killerStreak() >= best) {
            post(":fire: **" + WebhookQueue.escape(kill.killer().getName()) + "** reached a new best streak: **" + kill.killerStreak() + "**");
        }
    }

    /** At the threshold and every 5 kills beyond it, so a long streak is not a post per kill. */
    static boolean streakWorthPosting(int streak, int from) {
        return from > 0 && streak >= from && (streak - from) % 5 == 0;
    }

    public void onSeasonArchived(String seasonId, List<SeasonService.Entry> top) {
        if (!config.get().isSeasonEnd()) {
            return;
        }
        StringBuilder text = new StringBuilder(":trophy: Season **" + WebhookQueue.escape(seasonId) + "** has ended.");
        String[] medals = {":first_place:", ":second_place:", ":third_place:"};
        for (int i = 0; i < Math.min(3, top.size()); i++) {
            SeasonService.Entry entry = top.get(i);
            text.append('\n').append(medals[i]).append(' ').append(WebhookQueue.escape(entry.name()))
                    .append(" - ").append(entry.kills()).append(" kills");
        }
        if (top.isEmpty()) {
            text.append("\nNobody played this season.");
        }
        post(text.toString());
    }

    public void onAchievement(Player player, AchievementDefinition achievement) {
        if (config.get().isAchievements()) {
            post(":medal: **" + WebhookQueue.escape(player.getName()) + "** unlocked **" + WebhookQueue.escape(achievement.name(false)) + "**");
        }
    }

    private void sendNext() {
        VisibilityConfiguration.Discord settings = config.get();
        if (!settings.enabled()) {
            return;
        }
        queue.next(System.currentTimeMillis(), settings.minIntervalMillis()).ifPresent(message -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("username", settings.getUsername());
            payload.addProperty("content", message.length() > 1900 ? message.substring(0, 1900) : message);
            JsonObject mentions = new JsonObject();
            mentions.add("parse", new JsonArray()); // never ping anyone
            payload.add("allowed_mentions", mentions);
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(settings.getWebhookUrl()))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                        .build();
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() >= 400) {
                    log.accept("FFA: Discord answered " + response.statusCode() + " to a webhook post", null);
                }
            } catch (Exception failure) {
                log.accept("FFA: Discord webhook post failed: " + failure.getMessage(), null);
            }
        });
    }

    public void shutdown() {
        sender.shutdownNow();
    }
}

package net.zernyth.ffa.visibility;

import net.zernyth.ffa.player.model.LeaderboardCategory;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** The floating leaderboard and the Discord webhook (config.json, sections "hologram" and "discord"). */
public final class VisibilityConfiguration {

    /** The floating top list; its place is set in game with /ffaadmin hologram set. */
    public static final class Hologram {
        private boolean enabled = true;
        private List<String> categories = List.of("KILLS", "KD", "LEVEL", "SEASON_KILLS");
        private int rotateSeconds = 10;
        private int viewDistance = 48;
        private int size = 10;

        public boolean isEnabled() {
            return enabled;
        }

        /** Known categories in the configured order; unknown names are skipped, none at all gives KILLS. */
        public List<LeaderboardCategory> categories() {
            List<LeaderboardCategory> known = (categories == null ? List.<String>of() : categories).stream()
                    .filter(Objects::nonNull)
                    .map(name -> {
                        try {
                            return LeaderboardCategory.valueOf(name.trim().toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException unknown) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            return known.isEmpty() ? List.of(LeaderboardCategory.KILLS) : known;
        }

        public int getRotateSeconds() {
            return Math.max(3, rotateSeconds);
        }

        public int getViewDistance() {
            return Math.max(4, Math.min(128, viewDistance));
        }

        public int getSize() {
            return Math.max(3, Math.min(15, size));
        }
    }

    /** Posts to a Discord channel through a webhook; empty URL = off. */
    public static final class Discord {
        private String webhookUrl = "";
        private String username = "Zernyth FFA";
        /** Post when someone reaches a new best streak of at least this (0 = off). */
        private int streakFrom = 15;
        private boolean seasonEnd = true;
        private boolean achievements = true;
        private int minIntervalSeconds = 3;

        public String getWebhookUrl() {
            return webhookUrl == null ? "" : webhookUrl.trim();
        }

        /** Only Discord webhook addresses over https are used. */
        public boolean enabled() {
            String url = getWebhookUrl();
            return url.startsWith("https://discord.com/api/webhooks/")
                    || url.startsWith("https://discordapp.com/api/webhooks/")
                    || url.startsWith("https://canary.discord.com/api/webhooks/")
                    || url.startsWith("https://ptb.discord.com/api/webhooks/");
        }

        public String getUsername() {
            return username == null || username.isBlank() ? "Zernyth FFA" : username.trim();
        }

        public int getStreakFrom() {
            return Math.max(0, streakFrom);
        }

        public boolean isSeasonEnd() {
            return seasonEnd;
        }

        public boolean isAchievements() {
            return achievements;
        }

        public long minIntervalMillis() {
            return Math.max(1, minIntervalSeconds) * 1000L;
        }
    }
}

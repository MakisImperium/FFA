package net.zernyth.ffa.visibility;

import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.model.PlayerProfile;
import org.powernukkitx.utils.TextFormat;

import java.util.List;
import java.util.Locale;

/** The text of the floating leaderboard. Plain formatting, tested without a server. */
public final class BoardText {
    private BoardText() {
    }

    public static String title(LeaderboardCategory category, boolean german) {
        String name = switch (category) {
            case KILLS -> german ? "Top Kills" : "Top Kills";
            case BEST_STREAK -> german ? "Beste Streaks" : "Best Streaks";
            case KD -> "Top K/D";
            case LEVEL -> german ? "Top Level" : "Top Level";
            case SEASON_KILLS -> german ? "Saison-Kills" : "Season Kills";
        };
        return TextFormat.GOLD + "" + TextFormat.BOLD + "FFA " + TextFormat.RESET + TextFormat.YELLOW + name;
    }

    /**
     * One line per place: gold, silver and bronze for the first three. {@code level} turns XP into a
     * level for the LEVEL category.
     */
    public static String lines(LeaderboardCategory category, List<PlayerProfile> top, boolean german,
                               java.util.function.ToIntFunction<PlayerProfile> level) {
        if (top.isEmpty()) {
            return TextFormat.GRAY + (german ? "Noch keine Eintraege" : "No entries yet");
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            PlayerProfile profile = top.get(i);
            TextFormat place = switch (i) {
                case 0 -> TextFormat.GOLD;
                case 1 -> TextFormat.WHITE;
                case 2 -> TextFormat.RED;
                default -> TextFormat.GRAY;
            };
            if (i > 0) {
                text.append('\n');
            }
            String name = profile.getLastKnownName() == null ? "?" : profile.getLastKnownName();
            text.append(place).append('#').append(i + 1).append(' ').append(TextFormat.WHITE).append(name)
                    .append(TextFormat.DARK_GRAY).append(" - ").append(TextFormat.YELLOW).append(value(category, profile, level));
        }
        return text.toString();
    }

    static String value(LeaderboardCategory category, PlayerProfile profile, java.util.function.ToIntFunction<PlayerProfile> level) {
        return switch (category) {
            case KD -> String.format(Locale.ROOT, "%.2f", category.valueOf(profile));
            case LEVEL -> "Lv " + level.applyAsInt(profile);
            default -> String.valueOf((long) category.valueOf(profile));
        };
    }
}

package net.zernyth.ffa.progression;

import net.zernyth.ffa.player.model.PlayerProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An achievement: reached once, kept forever. Built-in ones plus any in config.json:
 * <pre>{"id":"kills_500","type":"kills","target":500,"coins":250,"nameEn":"500 kills","nameDe":"500 Kills"}</pre>
 * Types: kills, best_streak, level, shutdowns, duel_wins, tasks.
 */
public final class AchievementDefinition {
    public enum Type { KILLS, BEST_STREAK, LEVEL, SHUTDOWNS, DUEL_WINS, TASKS }

    private String id;
    private String type;
    private int target = 1;
    private int coins;
    private String nameEn;
    private String nameDe;

    public AchievementDefinition() {
    }

    private AchievementDefinition(String id, Type type, int target, int coins, String nameEn, String nameDe) {
        this.id = id;
        this.type = type.name().toLowerCase(Locale.ROOT);
        this.target = target;
        this.coins = coins;
        this.nameEn = nameEn;
        this.nameDe = nameDe;
    }

    public static List<AchievementDefinition> builtIn() {
        return List.of(
                new AchievementDefinition("first_blood", Type.KILLS, 1, 10, "First blood", "Erstes Blut"),
                new AchievementDefinition("kills_100", Type.KILLS, 100, 100, "100 kills", "100 Kills"),
                new AchievementDefinition("kills_1000", Type.KILLS, 1000, 1000, "1,000 kills", "1.000 Kills"),
                new AchievementDefinition("streak_5", Type.BEST_STREAK, 5, 25, "On fire: streak of 5", "Am Brennen: Serie von 5"),
                new AchievementDefinition("streak_15", Type.BEST_STREAK, 15, 250, "Unstoppable: streak of 15", "Unaufhaltsam: Serie von 15"),
                new AchievementDefinition("shutdown", Type.SHUTDOWNS, 1, 25, "Shutdown", "Shutdown"),
                new AchievementDefinition("level_10", Type.LEVEL, 10, 100, "Level 10", "Level 10"),
                new AchievementDefinition("level_25", Type.LEVEL, 25, 300, "Level 25", "Level 25"),
                new AchievementDefinition("duel_win", Type.DUEL_WINS, 1, 25, "Won a duel", "Duell gewonnen"),
                new AchievementDefinition("tasks_10", Type.TASKS, 10, 150, "10 tasks done", "10 Aufgaben erledigt"));
    }

    /** Built-in plus custom; a custom entry with a built-in id replaces it. */
    public static List<AchievementDefinition> all(List<AchievementDefinition> custom) {
        List<AchievementDefinition> out = new ArrayList<>(builtIn());
        for (AchievementDefinition extra : custom) {
            if (extra.getId() == null || extra.getType() == null) continue;
            out.removeIf(existing -> existing.getId().equals(extra.getId()));
            out.add(extra);
        }
        return out;
    }

    /** Whether the profile has reached it; the level is passed in because it comes from the curve. */
    public boolean reached(PlayerProfile profile, int level) {
        Type t = getType();
        if (t == null) {
            return false;
        }
        long value = switch (t) {
            case KILLS -> profile.getStatistics().getKills();
            case BEST_STREAK -> profile.getStatistics().getBestKillstreak();
            case LEVEL -> level;
            case SHUTDOWNS -> profile.getProgress().getShutdowns();
            case DUEL_WINS -> profile.getProgress().getDuelWins();
            case TASKS -> profile.getProgress().getTasksCompleted();
        };
        return value >= getTarget();
    }

    public String getId() {
        return id;
    }

    public Type getType() {
        try {
            return type == null ? null : Type.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    public int getTarget() {
        return Math.max(1, target);
    }

    public int getCoins() {
        return Math.max(0, coins);
    }

    public String name(boolean german) {
        String chosen = german ? nameDe : nameEn;
        return chosen == null || chosen.isBlank() ? (nameEn == null ? id : nameEn) : chosen;
    }
}

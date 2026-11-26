package net.zernyth.ffa.progression;

import java.util.List;
import java.util.Locale;

/**
 * A daily or weekly task from the pool in config.json:
 * <pre>{"id":"daily_kills_10","period":"daily","type":"kills","target":10,"xp":50,"coins":20,
 *  "nameEn":"Get 10 kills","nameDe":"Erziele 10 Kills"}</pre>
 * Types: kills, streak (reach a streak), kills_with_kit (needs "kit"), kills_in_arena (needs
 * "arena"), shutdowns.
 */
public final class TaskDefinition {
    public enum Period { DAILY, WEEKLY }

    public enum Type { KILLS, STREAK, KILLS_WITH_KIT, KILLS_IN_ARENA, SHUTDOWNS }

    private String id;
    private String period;
    private String type;
    private int target = 1;
    private String kit;
    private String arena;
    private int xp;
    private int coins;
    private String nameEn;
    private String nameDe;

    public TaskDefinition() {
    }

    private TaskDefinition(String id, Period period, Type type, int target, int xp, int coins, String nameEn, String nameDe) {
        this.id = id;
        this.period = period.name().toLowerCase(Locale.ROOT);
        this.type = type.name().toLowerCase(Locale.ROOT);
        this.target = target;
        this.xp = xp;
        this.coins = coins;
        this.nameEn = nameEn;
        this.nameDe = nameDe;
    }

    public static List<TaskDefinition> defaults() {
        return List.of(
                new TaskDefinition("daily_kills_10", Period.DAILY, Type.KILLS, 10, 50, 20, "Get 10 kills", "Erziele 10 Kills"),
                new TaskDefinition("daily_kills_25", Period.DAILY, Type.KILLS, 25, 120, 50, "Get 25 kills", "Erziele 25 Kills"),
                new TaskDefinition("daily_streak_5", Period.DAILY, Type.STREAK, 5, 80, 30, "Reach a streak of 5", "Erreiche eine Serie von 5"),
                new TaskDefinition("daily_shutdown", Period.DAILY, Type.SHUTDOWNS, 1, 60, 25, "End someone's streak", "Beende eine Serie"),
                new TaskDefinition("daily_streak_3", Period.DAILY, Type.STREAK, 3, 40, 15, "Reach a streak of 3", "Erreiche eine Serie von 3"),
                new TaskDefinition("weekly_kills_100", Period.WEEKLY, Type.KILLS, 100, 400, 150, "Get 100 kills", "Erziele 100 Kills"),
                new TaskDefinition("weekly_kills_250", Period.WEEKLY, Type.KILLS, 250, 900, 350, "Get 250 kills", "Erziele 250 Kills"),
                new TaskDefinition("weekly_streak_10", Period.WEEKLY, Type.STREAK, 10, 500, 200, "Reach a streak of 10", "Erreiche eine Serie von 10"),
                new TaskDefinition("weekly_shutdowns_5", Period.WEEKLY, Type.SHUTDOWNS, 5, 350, 120, "End 5 streaks", "Beende 5 Serien"));
    }

    public String getId() {
        return id;
    }

    public Period getPeriod() {
        return "weekly".equalsIgnoreCase(period) ? Period.WEEKLY : Period.DAILY;
    }

    /** The type, or null when unknown (the task is then never offered). */
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

    public String getKit() {
        return kit;
    }

    public String getArena() {
        return arena;
    }

    public int getXp() {
        return Math.max(0, xp);
    }

    public int getCoins() {
        return Math.max(0, coins);
    }

    public String name(boolean german) {
        String chosen = german ? nameDe : nameEn;
        return chosen == null || chosen.isBlank() ? (nameEn == null ? id : nameEn) : chosen;
    }

    /** Whether the entry is usable: an id, a known type and what its type needs. */
    public boolean valid() {
        Type t = getType();
        return id != null && !id.isBlank() && t != null
                && (t != Type.KILLS_WITH_KIT || (kit != null && !kit.isBlank()))
                && (t != Type.KILLS_IN_ARENA || (arena != null && !arena.isBlank()));
    }
}

package net.zernyth.ffa.progression;

import java.util.ArrayList;
import java.util.List;

/** XP, levels, ranks, tasks, achievements and seasons (config.json, section "progression"). */
public final class ProgressionConfiguration {
    private int xpPerKill = 10;
    /** Extra XP per kill for each streak step, up to {@link #streakBonusCap} steps. */
    private int xpPerStreakStep = 2;
    private int streakBonusCap = 10;
    private int xpPerShutdown = 20;

    /** XP from level 1 to 2; each further level needs {@code levelGrowth} times the previous. */
    private int firstLevelXp = 100;
    private double levelGrowth = 1.2;
    private int maxLevel = 100;

    private List<Rank> ranks = Rank.defaults();
    /** Show the rank before the name in chat. */
    private boolean chatPrefix = true;

    private int dailyTaskCount = 3;
    private int weeklyTaskCount = 3;
    /** Time zone for resetting tasks and seasons, e.g. "Europe/Berlin". Empty = the server's. */
    private String timeZone = "";
    private List<TaskDefinition> taskPool = TaskDefinition.defaults();
    /** Achievements beyond the built-in ones. */
    private List<AchievementDefinition> customAchievements = new ArrayList<>();

    private boolean seasonsEnabled = true;
    /** MONTHLY or WEEKLY. */
    private String seasonLength = "MONTHLY";
    private int seasonArchiveSize = 10;

    public int getXpPerKill() {
        return Math.max(0, xpPerKill);
    }

    public int getXpPerStreakStep() {
        return Math.max(0, xpPerStreakStep);
    }

    public int getStreakBonusCap() {
        return Math.max(0, streakBonusCap);
    }

    public int getXpPerShutdown() {
        return Math.max(0, xpPerShutdown);
    }

    public LevelCurve levelCurve() {
        return new LevelCurve(Math.max(1, firstLevelXp), Math.max(1.0, levelGrowth), Math.max(1, maxLevel));
    }

    public List<Rank> getRanks() {
        if (ranks == null || ranks.isEmpty()) {
            ranks = Rank.defaults();
        }
        return ranks;
    }

    public boolean isChatPrefix() {
        return chatPrefix;
    }

    public int getDailyTaskCount() {
        return Math.max(0, dailyTaskCount);
    }

    public int getWeeklyTaskCount() {
        return Math.max(0, weeklyTaskCount);
    }

    public java.time.ZoneId zone() {
        try {
            return timeZone == null || timeZone.isBlank() ? java.time.ZoneId.systemDefault() : java.time.ZoneId.of(timeZone);
        } catch (java.time.DateTimeException invalid) {
            return java.time.ZoneId.systemDefault();
        }
    }

    public List<TaskDefinition> getTaskPool() {
        if (taskPool == null) {
            taskPool = TaskDefinition.defaults();
        }
        return taskPool;
    }

    public List<AchievementDefinition> getCustomAchievements() {
        if (customAchievements == null) {
            customAchievements = new ArrayList<>();
        }
        return customAchievements;
    }

    public boolean isSeasonsEnabled() {
        return seasonsEnabled;
    }

    public boolean isWeeklySeasons() {
        return "WEEKLY".equalsIgnoreCase(seasonLength);
    }

    public int getSeasonArchiveSize() {
        return Math.max(1, Math.min(50, seasonArchiveSize));
    }
}

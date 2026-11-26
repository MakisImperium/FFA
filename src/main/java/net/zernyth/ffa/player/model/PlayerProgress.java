package net.zernyth.ffa.player.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Everything a player earns beyond kills and deaths: XP, coins, the current season's numbers,
 * achievements, task progress, cosmetics and duels.
 *
 * <p>In MySQL the sortable numbers have their own columns; the rest is stored as one JSON column.
 * In JSON files it is simply part of the profile.
 */
public final class PlayerProgress {
    private long xp;
    private long coins;

    private String seasonId;
    private int seasonKills;
    private int seasonDeaths;
    private int seasonBestStreak;

    /** Achievement id -> when it was unlocked (epoch millis). */
    private Map<String, Long> achievements = new HashMap<>();
    private TaskState dailyTasks = new TaskState();
    private TaskState weeklyTasks = new TaskState();

    private Set<String> ownedCosmetics = new HashSet<>();
    /** Cosmetic slot (kill_effect, death_message, join_message) -> cosmetic id. */
    private Map<String, String> equippedCosmetics = new HashMap<>();

    private int shutdowns;
    private int tasksCompleted;
    private int duelWins;
    private int duelLosses;

    /** Tasks of one period: which period, and the progress per task id. */
    public static final class TaskState {
        private String periodKey;
        private Map<String, Integer> progress = new HashMap<>();
        private Set<String> completed = new HashSet<>();

        public String getPeriodKey() {
            return periodKey;
        }

        public Map<String, Integer> getProgress() {
            if (progress == null) progress = new HashMap<>();
            return progress;
        }

        public Set<String> getCompleted() {
            if (completed == null) completed = new HashSet<>();
            return completed;
        }

        /** Starts a new period: progress and completions are cleared. */
        public void reset(String periodKey) {
            this.periodKey = periodKey;
            getProgress().clear();
            getCompleted().clear();
        }
    }

    public long getXp() {
        return xp;
    }

    public void addXp(long amount) {
        xp = Math.max(0, xp + amount);
    }

    public void setXp(long xp) {
        this.xp = Math.max(0, xp);
    }

    public long getCoins() {
        return coins;
    }

    public void addCoins(long amount) {
        coins = Math.max(0, coins + amount);
    }

    public void setCoins(long coins) {
        this.coins = Math.max(0, coins);
    }

    public String getSeasonId() {
        return seasonId;
    }

    public int getSeasonKills() {
        return seasonKills;
    }

    public int getSeasonDeaths() {
        return seasonDeaths;
    }

    public int getSeasonBestStreak() {
        return seasonBestStreak;
    }

    /** Moves this profile into a season; numbers of another season are dropped. */
    public void enterSeason(String id) {
        if (id != null && !id.equals(seasonId)) {
            seasonId = id;
            seasonKills = 0;
            seasonDeaths = 0;
            seasonBestStreak = 0;
        }
    }

    public void setSeason(String id, int kills, int deaths, int bestStreak) {
        this.seasonId = id;
        this.seasonKills = Math.max(0, kills);
        this.seasonDeaths = Math.max(0, deaths);
        this.seasonBestStreak = Math.max(0, bestStreak);
    }

    public void registerSeasonKill(int currentStreak) {
        seasonKills++;
        seasonBestStreak = Math.max(seasonBestStreak, currentStreak);
    }

    public void registerSeasonDeath() {
        seasonDeaths++;
    }

    public Map<String, Long> getAchievements() {
        if (achievements == null) achievements = new HashMap<>();
        return achievements;
    }

    public TaskState getDailyTasks() {
        if (dailyTasks == null) dailyTasks = new TaskState();
        return dailyTasks;
    }

    public TaskState getWeeklyTasks() {
        if (weeklyTasks == null) weeklyTasks = new TaskState();
        return weeklyTasks;
    }

    public Set<String> getOwnedCosmetics() {
        if (ownedCosmetics == null) ownedCosmetics = new HashSet<>();
        return ownedCosmetics;
    }

    public Map<String, String> getEquippedCosmetics() {
        if (equippedCosmetics == null) equippedCosmetics = new HashMap<>();
        return equippedCosmetics;
    }

    public int getShutdowns() {
        return shutdowns;
    }

    public void registerShutdown() {
        shutdowns++;
    }

    public int getTasksCompleted() {
        return tasksCompleted;
    }

    public void registerTaskCompleted() {
        tasksCompleted++;
    }

    public int getDuelWins() {
        return duelWins;
    }

    public int getDuelLosses() {
        return duelLosses;
    }

    public void registerDuel(boolean won) {
        if (won) duelWins++;
        else duelLosses++;
    }

    public void setDuels(int wins, int losses) {
        this.duelWins = Math.max(0, wins);
        this.duelLosses = Math.max(0, losses);
    }
}

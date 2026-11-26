package net.zernyth.ffa.config;

public final class PluginConfiguration {

    private String storageBackend = "MYSQL";
    private DatabaseConfiguration database = new DatabaseConfiguration();
    private ScoreboardConfiguration scoreboard = new ScoreboardConfiguration();
    private FairnessConfiguration fairness = new FairnessConfiguration();
    private net.zernyth.ffa.streak.StreakConfiguration streaks = new net.zernyth.ffa.streak.StreakConfiguration();
    private net.zernyth.ffa.progression.ProgressionConfiguration progression = new net.zernyth.ffa.progression.ProgressionConfiguration();

    public StorageBackend resolveStorageBackend() {
        return StorageBackend.fromText(storageBackend);
    }

    public DatabaseConfiguration getDatabase() {
        if (database == null) {
            database = new DatabaseConfiguration();
        }
        return database;
    }

    public ScoreboardConfiguration getScoreboard() {
        if (scoreboard == null) {
            scoreboard = new ScoreboardConfiguration();
        }
        return scoreboard;
    }

    public FairnessConfiguration getFairness() {
        if (fairness == null) {
            fairness = new FairnessConfiguration();
        }
        return fairness;
    }

    public net.zernyth.ffa.streak.StreakConfiguration getStreaks() {
        if (streaks == null) {
            streaks = new net.zernyth.ffa.streak.StreakConfiguration();
        }
        return streaks;
    }

    public net.zernyth.ffa.progression.ProgressionConfiguration getProgression() {
        if (progression == null) {
            progression = new net.zernyth.ffa.progression.ProgressionConfiguration();
        }
        return progression;
    }

    public void normalize() {
        getDatabase();
        getScoreboard();
        getFairness();
        getStreaks();
        getProgression();
    }
}



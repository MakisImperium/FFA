package net.zernyth.ffa.config;

public final class PluginConfiguration {

    private String storageBackend = "MYSQL";
    private DatabaseConfiguration database = new DatabaseConfiguration();
    private ScoreboardConfiguration scoreboard = new ScoreboardConfiguration();
    private FairnessConfiguration fairness = new FairnessConfiguration();

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

    public void normalize() {
        getDatabase();
        getScoreboard();
        getFairness();
    }
}



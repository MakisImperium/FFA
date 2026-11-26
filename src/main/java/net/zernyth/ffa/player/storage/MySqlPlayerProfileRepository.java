package net.zernyth.ffa.player.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.zernyth.ffa.config.DatabaseConfiguration;
import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.model.PlayerPreferences;
import net.zernyth.ffa.player.model.PlayerProfile;
import net.zernyth.ffa.player.model.PlayerStatistics;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Player profiles in MySQL.
 *
 * <p>Connections come from a small HikariCP pool rather than one new connection per query: opening a
 * MySQL connection costs a network round trip and authentication, which was most of the time of
 * every save. All calls arrive from the plugin's background thread, so the pool stays small.
 */
public final class MySqlPlayerProfileRepository implements PlayerProfileRepository {

    private static final String COLUMNS = "uuid, username, kills, deaths, current_killstreak, best_killstreak, " +
            "scoreboard_enabled, language, selected_kit, xp, coins, season_id, season_kills, season_deaths, " +
            "season_best_streak, extra";
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private final DatabaseConfiguration databaseConfiguration;
    private final String databaseName;
    private final String tableName;
    private final HikariDataSource dataSource;

    public MySqlPlayerProfileRepository(DatabaseConfiguration databaseConfiguration) throws Exception {
        this.databaseConfiguration = databaseConfiguration;
        this.databaseName = sanitizeDatabaseName(databaseConfiguration.getDatabase());
        this.tableName = sanitizeTableName(databaseConfiguration.getTable());
        Class.forName("com.mysql.cj.jdbc.Driver");
        ensureDatabaseExists();
        this.dataSource = createPool();
        try {
            initializeSchema();
        } catch (Exception exception) {
            dataSource.close();
            throw exception;
        }
    }

    private HikariDataSource createPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(databaseUrl());
        config.setUsername(databaseConfiguration.getUsername());
        config.setPassword(databaseConfiguration.getPassword());
        config.setPoolName("ffa-mysql");
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setValidationTimeout(5_000);
        // Recycle before MySQL's default wait_timeout (8 h) closes idle connections on its side.
        config.setMaxLifetime(30 * 60_000L);
        config.setKeepaliveTime(5 * 60_000L);
        config.setInitializationFailTimeout(10_000);
        return new HikariDataSource(config);
    }

    @Override
    public List<PlayerProfile> findTop(LeaderboardCategory category, int limit, String seasonId) throws Exception {
        String order = switch (category) {
            case KILLS -> "kills DESC";
            case BEST_STREAK -> "best_killstreak DESC";
            case KD -> "(CASE WHEN deaths = 0 THEN kills ELSE kills / deaths END) DESC";
            case LEVEL -> "xp DESC";
            case SEASON_KILLS -> "season_kills DESC";
        };
        String where = category.seasonal() ? " WHERE season_id = ?" : "";
        String sql = "SELECT " + COLUMNS + " FROM `" + tableName + "`" + where + " ORDER BY " + order + ", username ASC LIMIT ?";
        List<PlayerProfile> profiles = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (category.seasonal()) {
                statement.setString(index++, seasonId == null ? "" : seasonId);
            }
            statement.setInt(index, Math.max(1, limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    profiles.add(mapProfile(resultSet));
                }
            }
        }
        return profiles;
    }

    @Override
    public void close() {
        dataSource.close();
    }

    @Override
    public Optional<PlayerProfile> findByUniqueId(UUID uniqueId) throws Exception {
        String sql = "SELECT " + COLUMNS + " FROM `" + tableName + "` WHERE uuid = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uniqueId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapProfile(resultSet));
            }
        }
    }

    @Override
    public List<PlayerProfile> loadAllProfiles() throws Exception {
        String sql = "SELECT " + COLUMNS + " FROM `" + tableName + "`";
        List<PlayerProfile> profiles = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                profiles.add(mapProfile(resultSet));
            }
        }
        return profiles;
    }

    @Override
    public void save(PlayerProfile profile) throws Exception {
        String sql = "INSERT INTO `" + tableName + "` " +
                "(" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE username = VALUES(username), kills = VALUES(kills), deaths = VALUES(deaths), " +
                "current_killstreak = VALUES(current_killstreak), best_killstreak = VALUES(best_killstreak), " +
                "scoreboard_enabled = VALUES(scoreboard_enabled), language = VALUES(language), selected_kit = VALUES(selected_kit), " +
                "xp = VALUES(xp), coins = VALUES(coins), season_id = VALUES(season_id), season_kills = VALUES(season_kills), " +
                "season_deaths = VALUES(season_deaths), season_best_streak = VALUES(season_best_streak), extra = VALUES(extra)";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            PlayerStatistics statistics = profile.getStatistics();
            PlayerPreferences preferences = profile.getPreferences();

            statement.setString(1, profile.getUniqueIdRaw());
            statement.setString(2, profile.getLastKnownName());
            statement.setInt(3, statistics.getKills());
            statement.setInt(4, statistics.getDeaths());
            statement.setInt(5, statistics.getCurrentKillstreak());
            statement.setInt(6, statistics.getBestKillstreak());
            statement.setBoolean(7, preferences.isScoreboardEnabled());
            statement.setString(8, preferences.getLanguageCode());
            if (profile.getSelectedKitId() == null || profile.getSelectedKitId().isBlank()) {
                statement.setNull(9, Types.VARCHAR);
            } else {
                statement.setString(9, profile.getSelectedKitId());
            }
            var progress = profile.getProgress();
            statement.setLong(10, progress.getXp());
            statement.setLong(11, progress.getCoins());
            statement.setString(12, progress.getSeasonId());
            statement.setInt(13, progress.getSeasonKills());
            statement.setInt(14, progress.getSeasonDeaths());
            statement.setInt(15, progress.getSeasonBestStreak());
            // Achievements, tasks, cosmetics and duels: one JSON column; the sortable numbers above win on reading.
            statement.setString(16, GSON.toJson(progress));
            statement.executeUpdate();
        }
    }

    @Override
    public boolean ping() throws Exception {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet ignored = statement.executeQuery()) {
            return true;
        }
    }

    private PlayerProfile mapProfile(ResultSet resultSet) throws SQLException {
        PlayerProfile profile = PlayerProfile.createDefault(
                UUID.fromString(resultSet.getString("uuid")),
                resultSet.getString("username"),
                resultSet.getBoolean("scoreboard_enabled")
        );

        PlayerStatistics statistics = profile.getStatistics();
        statistics.setKills(resultSet.getInt("kills"));
        statistics.setDeaths(resultSet.getInt("deaths"));
        statistics.setCurrentKillstreak(resultSet.getInt("current_killstreak"));
        statistics.setBestKillstreak(resultSet.getInt("best_killstreak"));

        profile.setSelectedKitId(resultSet.getString("selected_kit"));
        profile.setLastKnownName(resultSet.getString("username"));
        profile.getPreferences().setScoreboardEnabled(resultSet.getBoolean("scoreboard_enabled"));
        profile.getPreferences().setLanguage(PlayerLanguage.fromCode(resultSet.getString("language")));

        String extra = resultSet.getString("extra");
        net.zernyth.ffa.player.model.PlayerProgress progress = extra == null || extra.isBlank()
                ? new net.zernyth.ffa.player.model.PlayerProgress()
                : GSON.fromJson(extra, net.zernyth.ffa.player.model.PlayerProgress.class);
        progress.setXp(resultSet.getLong("xp"));
        progress.setCoins(resultSet.getLong("coins"));
        progress.setSeason(resultSet.getString("season_id"), resultSet.getInt("season_kills"),
                resultSet.getInt("season_deaths"), resultSet.getInt("season_best_streak"));
        profile.setProgress(progress);
        return profile;
    }

    private void initializeSchema() throws Exception {
        String sql = "CREATE TABLE IF NOT EXISTS `" + tableName + "` (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "username VARCHAR(32) NOT NULL," +
                "kills INT NOT NULL DEFAULT 0," +
                "deaths INT NOT NULL DEFAULT 0," +
                "current_killstreak INT NOT NULL DEFAULT 0," +
                "best_killstreak INT NOT NULL DEFAULT 0," +
                "scoreboard_enabled BOOLEAN NOT NULL DEFAULT TRUE," +
                "language VARCHAR(8) NOT NULL DEFAULT 'EN'," +
                "selected_kit VARCHAR(64) NULL" +
                ")";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
        ensureLanguageColumnExists();
        migrate();
    }

    /**
     * Numbered schema changes, applied once each and remembered in {@code <table>_schema}. Every step
     * is safe to repeat, so a run that stopped half-way finishes on the next start.
     */
    private void migrate() throws SQLException {
        String versionTable = tableName + "_schema";
        try (Connection connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS `" + versionTable + "` (version INT NOT NULL)")) {
                statement.execute();
            }
            int version = 0;
            try (PreparedStatement statement = connection.prepareStatement("SELECT MAX(version) FROM `" + versionTable + "`");
                 ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    version = result.getInt(1);
                }
            }
            if (version < 1) {
                // FFA 4.0: progression, seasons and everything stored as JSON.
                ensureColumn(connection, "xp", "BIGINT NOT NULL DEFAULT 0");
                ensureColumn(connection, "coins", "BIGINT NOT NULL DEFAULT 0");
                ensureColumn(connection, "season_id", "VARCHAR(32) NULL");
                ensureColumn(connection, "season_kills", "INT NOT NULL DEFAULT 0");
                ensureColumn(connection, "season_deaths", "INT NOT NULL DEFAULT 0");
                ensureColumn(connection, "season_best_streak", "INT NOT NULL DEFAULT 0");
                ensureColumn(connection, "extra", "MEDIUMTEXT NULL");
                // Leaderboards sort by these.
                ensureIndex(connection, "idx_ffa_kills", "kills");
                ensureIndex(connection, "idx_ffa_best_streak", "best_killstreak");
                ensureIndex(connection, "idx_ffa_xp", "xp");
                ensureIndex(connection, "idx_ffa_season", "season_id, season_kills");
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO `" + versionTable + "` (version) VALUES (1)")) {
                    statement.execute();
                }
            }
        }
    }

    private void ensureColumn(Connection connection, String column, String definition) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, column)) {
            if (columns.next()) {
                return;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "ALTER TABLE `" + tableName + "` ADD COLUMN " + column + " " + definition)) {
            statement.execute();
        }
    }

    private void ensureIndex(Connection connection, String index, String columns) throws SQLException {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE INDEX " + index + " ON `" + tableName + "` (" + columns + ")")) {
            statement.execute();
        }
    }

    private void ensureLanguageColumnExists() throws SQLException {
        try (Connection connection = openConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet columns = metaData.getColumns(connection.getCatalog(), null, tableName, "language")) {
                if (columns.next()) {
                    return;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "ALTER TABLE `" + tableName + "` ADD COLUMN language VARCHAR(8) NOT NULL DEFAULT 'EN'")) {
                statement.execute();
            }
        }
    }

    private void ensureDatabaseExists() throws SQLException {
        String sql = "CREATE DATABASE IF NOT EXISTS `" + databaseName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
        try (Connection connection = openServerConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private Connection openConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private String databaseUrl() {
        return "jdbc:mysql://" + databaseConfiguration.getHost() + ":" + databaseConfiguration.getPort() + "/" +
                databaseName +
                "?createDatabaseIfNotExist=true" +
                "&useUnicode=true" +
                "&characterEncoding=utf8" +
                "&useSSL=" + databaseConfiguration.isUseSsl() +
                "&allowPublicKeyRetrieval=" + databaseConfiguration.isAllowPublicKeyRetrieval() +
                "&serverTimezone=UTC";
    }

    private Connection openServerConnection() throws SQLException {
        String url = "jdbc:mysql://" + databaseConfiguration.getHost() + ":" + databaseConfiguration.getPort() + "/" +
                "?useUnicode=true" +
                "&characterEncoding=utf8" +
                "&useSSL=" + databaseConfiguration.isUseSsl() +
                "&allowPublicKeyRetrieval=" + databaseConfiguration.isAllowPublicKeyRetrieval() +
                "&serverTimezone=UTC";
        return DriverManager.getConnection(url, databaseConfiguration.getUsername(), databaseConfiguration.getPassword());
    }

    private String sanitizeDatabaseName(String rawDatabaseName) {
        if (rawDatabaseName == null || rawDatabaseName.isBlank()) {
            return "ffa";
        }
        String sanitized = rawDatabaseName.replaceAll("[^a-zA-Z0-9_]", "");
        return sanitized.isBlank() ? "ffa" : sanitized;
    }

    private String sanitizeTableName(String rawTableName) {
        if (rawTableName == null || rawTableName.isBlank()) {
            return "ffa_player_profiles";
        }
        String sanitized = rawTableName.replaceAll("[^a-zA-Z0-9_]", "");
        return sanitized.isBlank() ? "ffa_player_profiles" : sanitized;
    }
}



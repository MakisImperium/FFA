package net.zernyth.ffa.ui.form;

import org.powernukkitx.Player;
import org.powernukkitx.form.element.ElementLabel;
import org.powernukkitx.form.element.custom.ElementDropdown;
import org.powernukkitx.form.element.custom.ElementInput;
import org.powernukkitx.form.element.custom.ElementToggle;
import org.powernukkitx.form.element.simple.ElementButton;
import org.powernukkitx.form.response.CustomResponse;
import org.powernukkitx.form.response.ElementResponse;
import org.powernukkitx.form.response.Response;
import org.powernukkitx.form.response.SimpleResponse;
import org.powernukkitx.form.window.CustomForm;
import org.powernukkitx.form.window.SimpleForm;
import org.powernukkitx.item.Item;
import org.powernukkitx.utils.TextFormat;
import net.zernyth.ffa.arena.model.ArenaDefinition;
import net.zernyth.ffa.arena.model.ArenaStatus;
import net.zernyth.ffa.arena.service.ArenaCatalogService;
import net.zernyth.ffa.arena.service.ArenaSessionService;
import net.zernyth.ffa.arena.service.SafeZonePlacementService;
import net.zernyth.ffa.i18n.LocalizationService;
import net.zernyth.ffa.kit.model.KitDefinition;
import net.zernyth.ffa.kit.model.KitItem;
import net.zernyth.ffa.kit.service.KitApplicationService;
import net.zernyth.ffa.kit.service.KitRegistry;
import net.zernyth.ffa.kit.service.KitSelectionService;
import net.zernyth.ffa.messaging.FfaMessageFormatter;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.model.PlayerProfile;
import net.zernyth.ffa.player.model.PlayerStatistics;
import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.service.LeaderboardService;
import net.zernyth.ffa.player.service.PlayerProfileService;
import net.zernyth.ffa.scoreboard.FfaScoreboardService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Personal statistics and the leaderboards. */
final class StatsMenus extends FormSupport {
    final Map<UUID, List<PlayerStatsAction>> playerStatsActions = new ConcurrentHashMap<>();

    StatsMenus(FfaFormController root) {
        super(root);
    }

    @Override
    void clear(UUID uniqueId) {
        playerStatsActions.remove(uniqueId);
    }

    void openPlayerStats(Player player) {
        if (!playerProfileService.isDataAvailable()) {
            openStatsUnavailable(player);
            return;
        }

        boolean inArena = arenaSessionService.isPlayerInArena(player.getUniqueId());
        SimpleForm form = new SimpleForm(
                title(player, "Statistics", "Statistiken"),
                section(player,
                        "Open your personal profile and competitive leaderboards.",
                        "Oeffne dein persoenliches Profil und kompetitive Leaderboards.")
        );

        List<PlayerStatsAction> actions = new ArrayList<>();
        form.addElement(new ElementButton(button(player, TextFormat.GREEN,
                "Personal Stats",
                "Your own combat profile",
                "Persoenliche Stats",
                "Dein eigenes Kampfprofil")));
        actions.add(PlayerStatsAction.PERSONAL);

        if (root.progressionService != null) {
            form.addElement(new ElementButton(button(player, TextFormat.GOLD,
                    "Level & Rank",
                    "Your XP and the next rank",
                    "Level & Rang",
                    "Deine EP und der naechste Rang")));
            actions.add(PlayerStatsAction.PROGRESS);
            form.addElement(new ElementButton(button(player, TextFormat.GREEN,
                    "Tasks",
                    "Daily and weekly goals",
                    "Aufgaben",
                    "Taegliche und woechentliche Ziele")));
            actions.add(PlayerStatsAction.TASKS);
            form.addElement(new ElementButton(button(player, TextFormat.LIGHT_PURPLE,
                    "Achievements",
                    "What you have unlocked",
                    "Erfolge",
                    "Was du freigeschaltet hast")));
            actions.add(PlayerStatsAction.ACHIEVEMENTS);
            if (root.progressionService.currentSeasonId() != null) {
                form.addElement(new ElementButton(button(player, TextFormat.AQUA,
                        "Season",
                        "This season's best and past seasons",
                        "Saison",
                        "Die Besten der Saison und vergangene Saisons")));
                actions.add(PlayerStatsAction.SEASON);
            }
        }

        form.addElement(new ElementButton(button(player, TextFormat.YELLOW,
                "Global Leaderboard",
                "Top players from stored database stats",
                "Globales Leaderboard",
                "Top-Spieler aus gespeicherten Daten")));
        actions.add(PlayerStatsAction.GLOBAL);

        if (inArena) {
            form.addElement(new ElementButton(button(player, TextFormat.AQUA,
                    "Arena Leaderboard",
                    "Top players currently in your arena",
                    "Arena-Leaderboard",
                    "Top-Spieler aktuell in deiner Arena")));
            actions.add(PlayerStatsAction.ARENA);
        }

        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        actions.add(PlayerStatsAction.BACK);
        playerStatsActions.put(player.getUniqueId(), actions);
        form.send(player, FORM_PLAYER_STATS);
    }

    void handlePlayerStatsMenu(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        List<PlayerStatsAction> actions = playerStatsActions.get(player.getUniqueId());
        if (actions == null || simple.buttonId() < 0 || simple.buttonId() >= actions.size()) {
            root.playerMenus.openPlayerMain(player);
            return;
        }
        PlayerStatsAction action = actions.get(simple.buttonId());
        switch (action) {
            case PERSONAL -> openPlayerStatsOverview(player);
            case GLOBAL -> openPlayerGlobalLeaderboard(player);
            case ARENA -> openPlayerArenaLeaderboard(player);
            case PROGRESS -> openProgress(player);
            case TASKS -> openTasks(player);
            case ACHIEVEMENTS -> openAchievements(player);
            case SEASON -> openSeason(player);
            case BACK -> root.playerMenus.openPlayerMain(player);
        }
    }

    void openProgress(Player player) {
        var progression = root.progressionService;
        var profile = playerProfileService.getProfile(player.getUniqueId());
        if (progression == null || profile.isEmpty()) {
            openPlayerStats(player);
            return;
        }
        var progress = profile.get().getProgress();
        int level = progression.level(profile.get());
        var curve = progressionConfigCurve();
        long[] within = curve.progressInLevel(progress.getXp());
        StringBuilder content = new StringBuilder();
        content.append(TextFormat.DARK_GRAY).append(t(player, "Rank: ", "Rang: ")).append(progression.rank(level).display()).append("\n")
                .append(TextFormat.DARK_GRAY).append("Level: ").append(TextFormat.WHITE).append(level).append("\n")
                .append(TextFormat.DARK_GRAY).append(t(player, "XP: ", "EP: ")).append(TextFormat.WHITE).append(progress.getXp()).append("\n");
        if (within[1] > 0) {
            int filled = (int) Math.round(20.0 * within[0] / within[1]);
            content.append(TextFormat.GREEN).append("|".repeat(filled)).append(TextFormat.DARK_GRAY).append("|".repeat(20 - filled))
                    .append(TextFormat.GRAY).append(" ").append(within[0]).append("/").append(within[1]).append("\n");
        } else {
            content.append(TextFormat.GOLD).append(t(player, "Maximum level reached.", "Hoechstes Level erreicht.")).append("\n");
        }
        content.append(TextFormat.DARK_GRAY).append(t(player, "Coins: ", "Muenzen: ")).append(TextFormat.YELLOW).append(progress.getCoins()).append("\n")
                .append(TextFormat.DARK_GRAY).append(t(player, "Shutdowns: ", "Shutdowns: ")).append(TextFormat.WHITE).append(progress.getShutdowns()).append("\n")
                .append(TextFormat.DARK_GRAY).append(t(player, "Duels won: ", "Duelle gewonnen: ")).append(TextFormat.WHITE).append(progress.getDuelWins());
        SimpleForm form = new SimpleForm(title(player, "Level & Rank", "Level & Rang"), content.toString());
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_PROGRESS);
    }

    private net.zernyth.ffa.progression.LevelCurve progressionConfigCurve() {
        return root.progressionCurve.get();
    }

    void openTasks(Player player) {
        var progression = root.progressionService;
        var profile = playerProfileService.getProfile(player.getUniqueId());
        if (progression == null || profile.isEmpty()) {
            openPlayerStats(player);
            return;
        }
        boolean german = playerProfileService.getLanguage(player.getUniqueId()) == net.zernyth.ffa.player.model.PlayerLanguage.GERMAN;
        var progress = profile.get().getProgress();
        StringBuilder content = new StringBuilder();
        appendTasks(content, t(player, "Today", "Heute"), progression.dailyTasks(player.getUniqueId()), progress.getDailyTasks(), german);
        appendTasks(content, t(player, "This week", "Diese Woche"), progression.weeklyTasks(player.getUniqueId()), progress.getWeeklyTasks(), german);
        if (content.isEmpty()) {
            content.append(TextFormat.GRAY).append(t(player, "No tasks right now.", "Gerade keine Aufgaben."));
        }
        SimpleForm form = new SimpleForm(title(player, "Tasks", "Aufgaben"), content.toString().trim());
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_TASKS);
    }

    private void appendTasks(StringBuilder content, String heading, List<net.zernyth.ffa.progression.TaskDefinition> tasks,
                             net.zernyth.ffa.player.model.PlayerProgress.TaskState state, boolean german) {
        if (tasks.isEmpty()) {
            return;
        }
        content.append(TextFormat.GOLD).append(heading).append("\n");
        for (var task : tasks) {
            boolean done = state.getCompleted().contains(task.getId());
            int value = Math.min(state.getProgress().getOrDefault(task.getId(), 0), task.getTarget());
            content.append(done ? TextFormat.GREEN + "[x] " : TextFormat.GRAY + "[ ] ")
                    .append(TextFormat.WHITE).append(task.name(german))
                    .append(TextFormat.DARK_GRAY).append("  ").append(value).append("/").append(task.getTarget())
                    .append(TextFormat.GRAY).append("  +").append(task.getXp()).append(german ? " EP" : " XP")
                    .append(task.getCoins() > 0 ? ", +" + task.getCoins() + (german ? " Muenzen" : " coins") : "")
                    .append("\n");
        }
        content.append("\n");
    }

    void openAchievements(Player player) {
        var progression = root.progressionService;
        var profile = playerProfileService.getProfile(player.getUniqueId());
        if (progression == null || profile.isEmpty()) {
            openPlayerStats(player);
            return;
        }
        boolean german = playerProfileService.getLanguage(player.getUniqueId()) == net.zernyth.ffa.player.model.PlayerLanguage.GERMAN;
        var unlocked = profile.get().getProgress().getAchievements();
        var all = progression.achievements();
        StringBuilder content = new StringBuilder();
        content.append(TextFormat.GRAY).append(unlocked.size()).append(" / ").append(all.size()).append("\n\n");
        for (var achievement : all) {
            boolean has = unlocked.containsKey(achievement.getId());
            content.append(has ? TextFormat.LIGHT_PURPLE + "* " + TextFormat.WHITE : TextFormat.DARK_GRAY + "- ")
                    .append(achievement.name(german))
                    .append(achievement.getCoins() > 0 ? TextFormat.GRAY + "  (" + achievement.getCoins() + (german ? " Muenzen)" : " coins)") : "")
                    .append("\n");
        }
        SimpleForm form = new SimpleForm(title(player, "Achievements", "Erfolge"), content.toString().trim());
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_ACHIEVEMENTS);
    }

    void openSeason(Player player) {
        var progression = root.progressionService;
        if (progression == null || progression.currentSeasonId() == null) {
            openPlayerStats(player);
            return;
        }
        var seasons = progression.seasons();
        StringBuilder content = new StringBuilder();
        content.append(TextFormat.GOLD).append(t(player, "Season ", "Saison ")).append(seasons.currentNumber())
                .append(TextFormat.GRAY).append(t(player, " since ", " seit "))
                .append(java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(seasons.startedAt()), java.time.ZoneId.systemDefault()))
                .append("\n\n");
        appendLeaderboardSection(content, t(player, "Season Kills", "Saison-Kills"),
                leaderboardService.top(LeaderboardCategory.SEASON_KILLS, 10),
                profile -> profile.getProgress().getSeasonKills());
        var archive = seasons.archive();
        for (int i = archive.size() - 1; i >= Math.max(0, archive.size() - 3); i--) {
            var past = archive.get(i);
            content.append(TextFormat.AQUA).append(t(player, "Season ", "Saison ")).append(past.number())
                    .append(TextFormat.GRAY).append(t(player, " - winners", " - Sieger")).append("\n");
            int place = 1;
            for (var entry : past.top().subList(0, Math.min(3, past.top().size()))) {
                content.append(TextFormat.WHITE).append(place++).append(". ").append(entry.name())
                        .append(TextFormat.GRAY).append(" ").append(entry.kills()).append(" Kills").append("\n");
            }
            content.append("\n");
        }
        SimpleForm form = new SimpleForm(title(player, "Season", "Saison"), content.toString().trim());
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_SEASON);
    }

    void openPlayerStatsOverview(Player player) {
        if (!playerProfileService.isDataAvailable()) {
            openStatsUnavailable(player);
            return;
        }

        PlayerStatistics statistics = playerProfileService.getStatisticsOrDefault(player.getUniqueId());
        double kd = statistics.getDeaths() == 0 ? statistics.getKills() : (double) statistics.getKills() / statistics.getDeaths();
        String kdText = String.format(Locale.ROOT, "%.2f", kd);
        String content = TextFormat.GRAY + t(player, "Combat Statistics", "Kampfstatistiken") + "\n"
                + TextFormat.DARK_GRAY + t(player, "Kills: ", "Kills: ") + TextFormat.GREEN + statistics.getKills() + "\n"
                + TextFormat.DARK_GRAY + t(player, "Deaths: ", "Tode: ") + TextFormat.RED + statistics.getDeaths() + "\n"
                + TextFormat.DARK_GRAY + t(player, "Killstreak: ", "Killstreak: ") + TextFormat.AQUA + statistics.getCurrentKillstreak() + "\n"
                + TextFormat.DARK_GRAY + t(player, "Best streak: ", "Beste Streak: ") + TextFormat.YELLOW + statistics.getBestKillstreak() + "\n"
                + TextFormat.DARK_GRAY + "K/D: " + TextFormat.WHITE + kdText;
        SimpleForm form = new SimpleForm(title(player, "Personal Stats", "Persoenliche Stats"), content);
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_STATS_OVERVIEW);
    }

    void openPlayerGlobalLeaderboard(Player player) {
        if (!playerProfileService.isDataAvailable()) {
            openStatsUnavailable(player);
            return;
        }

        // From the cache: refreshed in the background every minute, merged with who is online now.
        StringBuilder content = new StringBuilder();

        appendLeaderboardSection(
                content,
                t(player, "Top Kills", "Top Kills"),
                leaderboardService.top(LeaderboardCategory.KILLS, 10),
                profile -> profile.getStatistics().getKills()
        );
        appendLeaderboardSection(
                content,
                t(player, "Top Streak", "Top Streak"),
                leaderboardService.top(LeaderboardCategory.BEST_STREAK, 10),
                profile -> profile.getStatistics().getBestKillstreak()
        );
        appendLeaderboardSection(
                content,
                t(player, "Top K/D", "Top K/D"),
                leaderboardService.top(LeaderboardCategory.KD, 10),
                profile -> String.format(Locale.ROOT, "%.2f", kdOf(profile.getStatistics()))
        );
        if (root.progressionService != null) {
            appendLeaderboardSection(
                    content,
                    t(player, "Top Level", "Top Level"),
                    leaderboardService.top(LeaderboardCategory.LEVEL, 10),
                    profile -> root.progressionService.level(profile)
            );
        }

        if (content.isEmpty()) {
            content.append(TextFormat.GRAY).append(t(player, "No leaderboard data available.", "Keine Leaderboard-Daten verfuegbar."));
        }

        SimpleForm form = new SimpleForm(title(player, "Global Leaderboard", "Globales Leaderboard"), content.toString().trim());
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_STATS_GLOBAL);
    }

    void openPlayerArenaLeaderboard(Player player) {
        if (!playerProfileService.isDataAvailable()) {
            openStatsUnavailable(player);
            return;
        }

        Optional<String> arenaIdOptional = arenaSessionService.getArenaIdForPlayer(player.getUniqueId());
        if (arenaIdOptional.isEmpty()) {
            sendWarning(player, t(player, "You are not inside an arena.", "Du bist aktuell in keiner Arena."));
            openPlayerStats(player);
            return;
        }

        String arenaId = arenaIdOptional.get();
        String arenaName = resolveArenaName(player, arenaId);
        List<UUID> arenaPlayers = new ArrayList<>(arenaSessionService.getPlayersInArena(arenaId));
        // Everyone in an arena is online, so their profiles are in memory: no storage access needed.
        List<PlayerProfile> arenaProfiles = arenaPlayers.stream()
                .map(playerProfileService::getProfile)
                .flatMap(Optional::stream)
                .toList();

        StringBuilder content = new StringBuilder();
        appendLeaderboardSection(
                content,
                t(player, "Arena Kills", "Arena-Kills"),
                arenaProfiles.stream()
                        .sorted(Comparator.comparingInt((PlayerProfile profile) -> profile.getStatistics().getKills()).reversed())
                        .limit(10)
                        .toList(),
                profile -> profile.getStatistics().getKills()
        );
        appendLeaderboardSection(
                content,
                t(player, "Arena Streak", "Arena-Streak"),
                arenaProfiles.stream()
                        .sorted(Comparator.comparingInt((PlayerProfile profile) -> profile.getStatistics().getCurrentKillstreak()).reversed())
                        .limit(10)
                        .toList(),
                profile -> profile.getStatistics().getCurrentKillstreak()
        );

        if (content.isEmpty()) {
            content.append(TextFormat.GRAY).append(t(player, "No arena leaderboard data available.", "Keine Arena-Leaderboard-Daten verfuegbar."));
        }

        SimpleForm form = new SimpleForm(
                title(player, "Arena Leaderboard", "Arena-Leaderboard"),
                TextFormat.DARK_GRAY + t(player, "Arena", "Arena") + ": " + TextFormat.WHITE + arenaName + "\n\n" + content.toString().trim()
        );
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_STATS_ARENA);
    }

    void openStatsUnavailable(Player player) {
        SimpleForm form = new SimpleForm(
                title(player, "Statistics Unavailable", "Statistiken nicht verfuegbar"),
                section(player,
                        "The statistics service is currently unavailable because the database connection was lost.",
                        "Der Statistikdienst ist aktuell nicht verfuegbar, weil die Datenbankverbindung verloren wurde.")
                        + "\n\n" + TextFormat.GRAY + t(player,
                        "Try again later or ask an administrator to check database connectivity.",
                        "Versuche es spaeter erneut oder informiere einen Administrator zur Datenbankpruefung.")
        );
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_STATS_UNAVAILABLE);
    }

    void appendLeaderboardSection(
            StringBuilder builder,
            String title,
            List<PlayerProfile> entries,
            java.util.function.Function<PlayerProfile, Object> valueExtractor
    ) {
        if (entries.isEmpty()) {
            return;
        }
        builder.append(TextFormat.GOLD).append(title).append("\n");
        int rank = 1;
        for (PlayerProfile entry : entries) {
            builder.append(TextFormat.YELLOW).append("#").append(rank).append(" ")
                    .append(TextFormat.WHITE).append(entry.getLastKnownName())
                    .append(TextFormat.GRAY).append(" -> ")
                    .append(TextFormat.AQUA).append(valueExtractor.apply(entry))
                    .append("\n");
            rank++;
        }
        builder.append("\n");
    }

    double kdOf(PlayerStatistics statistics) {
        if (statistics.getDeaths() == 0) {
            return statistics.getKills();
        }
        return (double) statistics.getKills() / statistics.getDeaths();
    }

    enum PlayerStatsAction {
        PERSONAL,
        GLOBAL,
        ARENA,
        PROGRESS,
        TASKS,
        ACHIEVEMENTS,
        SEASON,
        BACK
    }
}

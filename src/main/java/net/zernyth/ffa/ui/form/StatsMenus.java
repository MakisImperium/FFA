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
            case BACK -> root.playerMenus.openPlayerMain(player);
        }
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
        BACK
    }
}

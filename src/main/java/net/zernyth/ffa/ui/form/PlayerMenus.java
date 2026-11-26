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

/** The player's own menus: the control centre, arena choice and the in-arena menu. */
final class PlayerMenus extends FormSupport {
    final Map<UUID, List<PlayerMainAction>> playerMainActions = new ConcurrentHashMap<>();

    PlayerMenus(FfaFormController root) {
        super(root);
    }

    @Override
    void clear(UUID uniqueId) {
        playerMainActions.remove(uniqueId);
    }

    public void openPlayerMain(Player player) {
        boolean inArena = arenaSessionService.isPlayerInArena(player.getUniqueId());
        if (inArena) {
            openInArenaCommandMenu(player);
            return;
        }

        SimpleForm form = new SimpleForm(
                title(player, "FFA Control Center", "FFA Kontrollzentrum"),
                section(player,
                        "Choose a sector. Navigation stays strictly inside the selected flow.",
                        "Waehle einen Sektor. Die Navigation bleibt strikt im gewaehlten Ablauf.")
        );

        List<PlayerMainAction> actions = new ArrayList<>();
        form.addElement(new ElementButton(button(player, TextFormat.GOLD,
                "Arena",
                "Join, leave, and arena overview",
                "Arena",
                "Beitreten, verlassen, Arena-Uebersicht")));
        actions.add(PlayerMainAction.ARENA);

        form.addElement(new ElementButton(button(player, TextFormat.GREEN,
                "Stats",
                "View your combat statistics",
                "Stats",
                "Zeige deine Kampfstatistiken")));
        actions.add(PlayerMainAction.STATS);

        form.addElement(new ElementButton(button(player, TextFormat.YELLOW,
                "Settings",
                "Language and scoreboard preferences",
                "Einstellungen",
                "Sprache und Scoreboard-Einstellungen")));
        actions.add(PlayerMainAction.SETTINGS);

        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Close", "Schliessen")));
        actions.add(PlayerMainAction.CLOSE);

        playerMainActions.put(player.getUniqueId(), actions);
        form.send(player, FORM_PLAYER_MAIN);
    }

    void openInArenaCommandMenu(Player player) {
        Optional<ArenaDefinition> arena = arenaSessionService.getArenaForPlayer(player.getUniqueId());
        String arenaName = arena.map(ArenaDefinition::getDisplayName).orElse("-");
        int shieldSeconds = arenaSessionService.getRespawnProtectionSeconds(player.getUniqueId());

        SimpleForm form = new SimpleForm(
                title(player, "Arena Session", "Arena-Session"),
                TextFormat.GRAY + t(player,
                        "You are currently fighting in a live arena.",
                        "Du kaempfst aktuell in einer Live-Arena.")
                        + "\n" + TextFormat.DARK_GRAY + t(player, "Arena", "Arena") + ": " + TextFormat.WHITE + arenaName
                        + (shieldSeconds > 0
                        ? "\n" + TextFormat.DARK_GRAY + t(player, "Respawn Shield", "Respawn-Schutz") + ": "
                        + TextFormat.GREEN + shieldSeconds + "s"
                        : "")
        );
        form.addElement(new ElementButton(button(player, TextFormat.RED,
                "Leave Arena",
                "Exit combat and return to lobby",
                "Arena verlassen",
                "Kampf verlassen und zur Lobby gehen")));
        form.addElement(new ElementButton(button(player, TextFormat.AQUA,
                "Kits",
                "Change your active combat kit",
                "Kits",
                "Aktives Kampf-Kit wechseln")));
        form.addElement(new ElementButton(button(player, TextFormat.YELLOW,
                "Settings",
                "Language and scoreboard preferences",
                "Einstellungen",
                "Sprache und Scoreboard-Einstellungen")));
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Close", "Schliessen")));
        form.send(player, FORM_PLAYER_IN_ARENA_COMMAND);
    }

    void handlePlayerMain(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        List<PlayerMainAction> actions = playerMainActions.get(player.getUniqueId());
        if (actions == null || simple.buttonId() < 0 || simple.buttonId() >= actions.size()) {
            openPlayerMain(player);
            return;
        }
        PlayerMainAction action = actions.get(simple.buttonId());
        switch (action) {
            case ARENA -> openPlayerArenaMenu(player);
            case KITS -> root.playerKitMenus.openPlayerKitMenu(player);
            case STATS -> root.statsMenus.openPlayerStats(player);
            case SETTINGS -> root.settingsMenus.openPlayerSettings(player);
            case CLOSE -> {
            }
        }
    }

    void handleInArenaCommandMenu(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        switch (simple.buttonId()) {
            case 0 -> {
                leaveArenaByChoice(player);
                scoreboardService.refreshPlayer(player);
                sendInfo(player, t(player, "Arena session ended.", "Arena-Session beendet."));
            }
            case 1 -> root.playerKitMenus.openPlayerKitMenu(player);
            case 2 -> root.settingsMenus.openPlayerSettings(player);
            default -> {
            }
        }
    }

    void openPlayerArenaMenu(Player player) {
        Optional<ArenaDefinition> arena = arenaSessionService.getArenaForPlayer(player.getUniqueId());
        String currentArena = arena.map(ArenaDefinition::getDisplayName).orElse(t(player, "No active arena", "Keine aktive Arena"));

        SimpleForm form = new SimpleForm(
                title(player, "Arena Hub", "Arena-Hub"),
                TextFormat.GRAY + t(player, "Current Arena: ", "Aktuelle Arena: ")
                        + TextFormat.WHITE + currentArena
        );
        form.addElement(new ElementButton(button(player, TextFormat.GREEN,
                "Join Arena",
                "Browse all available arenas",
                "Arena beitreten",
                "Alle verfuegbaren Arenen anzeigen")));
        form.addElement(new ElementButton(button(player, TextFormat.RED,
                "Leave Arena",
                "Exit your current arena session",
                "Arena verlassen",
                "Aktuelle Arena-Session verlassen")));
        form.addElement(new ElementButton(button(player, TextFormat.YELLOW,
                "Arena Overview",
                "Live status of all arenas",
                "Arena Uebersicht",
                "Live-Status aller Arenen")));
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_ARENA_MENU);
    }

    void handlePlayerArenaMenu(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        switch (simple.buttonId()) {
            case 0 -> openPlayerArenaSelect(player);
            case 1 -> {
                if (!arenaSessionService.isPlayerInArena(player.getUniqueId())) {
                    sendWarning(player, t(player, "You are not inside an arena.", "Du bist aktuell in keiner Arena."));
                } else {
                    leaveArenaByChoice(player);
                    scoreboardService.refreshPlayer(player);
                    sendInfo(player, t(player, "Arena session ended.", "Arena-Session beendet."));
                }
                openPlayerArenaMenu(player);
            }
            case 2 -> openPlayerArenaOverview(player);
            default -> openPlayerMain(player);
        }
    }

    void openPlayerArenaSelect(Player player) {
        List<ArenaDefinition> arenas = sortedArenas();
        SimpleForm form = new SimpleForm(
                title(player, "Join Arena", "Arena beitreten"),
                section(player,
                        "Select one arena. Joining is only available while status is Live.",
                        "Waehle eine Arena. Beitritt ist nur moeglich, wenn der Status Live ist.")
        );

        if (arenas.isEmpty()) {
            form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "No arenas available", "Keine Arenen verfuegbar")));
        } else {
            for (ArenaDefinition arena : arenas) {
                int players = arenaSessionService.getArenaPlayerCount(arena.getArenaId());
                form.addElement(new ElementButton(TextFormat.YELLOW + arena.getDisplayName()
                        + "\n" + TextFormat.GRAY + arena.getWorldName()
                        + " | " + formatStatusBadge(player, arena.getStatus())
                        + TextFormat.GRAY + " | " + formatJoinBadge(player, arena.getStatus())
                        + " | " + players + " " + t(player, "players", "Spieler")));
            }
        }
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_ARENA_SELECT);
    }

    void handlePlayerArenaSelect(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        List<ArenaDefinition> arenas = sortedArenas();
        int selected = simple.buttonId();
        int backIndex = arenas.isEmpty() ? 1 : arenas.size();
        if (selected < 0 || selected >= backIndex) {
            openPlayerArenaMenu(player);
            return;
        }
        if (arenas.isEmpty()) {
            openPlayerArenaMenu(player);
            return;
        }

        ArenaDefinition arena = arenas.get(selected);
        if (arena.getStatus() != ArenaStatus.ACTIVE) {
            sendWarning(player, t(player,
                    "You can only join arenas with status Live.",
                    "Du kannst nur Arenen mit Status Live betreten."));
            openPlayerArenaSelect(player);
            return;
        }
        if (!arenaSessionService.joinArena(player, arena.getArenaId())) {
            sendError(player, t(player,
                    "Could not join this arena. Check spawn setup and world loading.",
                    "Arena-Beitritt fehlgeschlagen. Pruefe Spawn-Setup und Welt."));
            openPlayerArenaSelect(player);
            return;
        }

        scoreboardService.refreshPlayer(player);
        sendSuccess(player, t(player,
                "Joined arena " + arena.getDisplayName() + ".",
                "Arena " + arena.getDisplayName() + " betreten."));
        openArenaJoinedForm(player, arena);
    }

    void openArenaJoinedForm(Player player, ArenaDefinition arena) {
        boolean scoreboardEnabled = playerProfileService.isScoreboardEnabled(player.getUniqueId());
        String selectedKit = resolveKitName(player, kitSelectionService.resolveKitForPlayer(player.getUniqueId()));
        CustomForm form = new CustomForm(title(player, "Arena Session Ready", "Arena-Session bereit"));
        form.addElement(new ElementLabel(
                TextFormat.GREEN + t(player, "You are now fighting in", "Du kaempfst jetzt in")
                        + " " + TextFormat.YELLOW + arena.getDisplayName() + "\n"
                        + TextFormat.GRAY + t(player, "World", "Welt") + ": " + arena.getWorldName() + "\n"
                        + TextFormat.GRAY + t(player, "Kit", "Kit") + ": "
                        + (selectedKit == null ? t(player, "None selected", "Keins ausgewaehlt") : selectedKit)
        ));
        form.addElement(new ElementDropdown(
                t(player, "Quick Action", "Schnellaktion"),
                List.of(
                        t(player, "Arena Menu", "Arena-Menue"),
                        t(player, "Open Kits", "Kits oeffnen"),
                        t(player, "Open Stats", "Stats oeffnen"),
                        t(player, "Open Settings", "Einstellungen oeffnen")
                ),
                0
        ));
        form.addElement(new ElementToggle(t(player, "Enable scoreboard", "Scoreboard aktivieren"), scoreboardEnabled));
        form.send(player, FORM_PLAYER_ARENA_JOINED);
    }

    void handleArenaJoinedForm(Player player, Response response) {
        if (!(response instanceof CustomResponse custom)) {
            return;
        }
        boolean scoreboardEnabled = custom.getToggleResponse(2);
        playerProfileService.setScoreboardEnabled(player.getUniqueId(), scoreboardEnabled);
        scoreboardService.refreshPlayer(player);

        int action = getDropdownIndex(custom, 1);
        switch (action) {
            case 1 -> root.playerKitMenus.openPlayerKitMenu(player);
            case 2 -> root.statsMenus.openPlayerStats(player);
            case 3 -> root.settingsMenus.openPlayerSettings(player);
            default -> openPlayerArenaMenu(player);
        }
    }

    void openPlayerArenaOverview(Player player) {
        List<ArenaDefinition> arenas = sortedArenas();
        StringBuilder content = new StringBuilder();
        if (arenas.isEmpty()) {
            content.append(TextFormat.GRAY).append(t(player, "No arenas configured.", "Keine Arenen konfiguriert."));
        } else {
            for (ArenaDefinition arena : arenas) {
                int players = arenaSessionService.getArenaPlayerCount(arena.getArenaId());
                boolean hasSafeZone = arena.getSafeZone().isPresent();
                content.append(TextFormat.YELLOW).append(arena.getDisplayName()).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "World", "Welt")).append(": ")
                        .append(TextFormat.GRAY).append(arena.getWorldName()).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Status", "Status")).append(": ")
                        .append(formatStatusBadge(player, arena.getStatus())).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Join", "Beitritt")).append(": ")
                        .append(formatJoinBadge(player, arena.getStatus())).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Readiness", "Bereitschaft")).append(": ")
                        .append(formatReadinessBadge(player, arena)).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Players", "Spieler")).append(": ")
                        .append(TextFormat.GRAY).append(players).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Spawns", "Spawns")).append(": ")
                        .append(TextFormat.GRAY).append(arena.getSpawnPoints().size()).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Zones", "Zonen")).append(": ")
                        .append(TextFormat.GRAY).append(arena.getZones().size()).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Safe-Zone", "Safe-Zone")).append(": ")
                        .append(hasSafeZone ? TextFormat.GREEN + t(player, "Configured", "Konfiguriert")
                                : TextFormat.RED + t(player, "Not configured", "Nicht konfiguriert"))
                        .append("\n\n");
            }
        }

        SimpleForm form = new SimpleForm(title(player, "Arena Overview", "Arena Uebersicht"), content.toString().trim());
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_ARENA_OVERVIEW);
    }

    enum PlayerMainAction {
        ARENA,
        KITS,
        STATS,
        SETTINGS,
        CLOSE
    }
}

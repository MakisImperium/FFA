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

/** The admin console and everything about arenas: create, delete, spawns, status, zones. */
final class ArenaAdminMenus extends FormSupport {
    final Map<UUID, String> pendingArenaStatusTarget = new ConcurrentHashMap<>();
    final Map<UUID, String> pendingArenaDeleteTarget = new ConcurrentHashMap<>();
    volatile ArenaDefinition lastDeletedArena;

    ArenaAdminMenus(FfaFormController root) {
        super(root);
    }

    @Override
    void clear(UUID uniqueId) {
        pendingArenaStatusTarget.remove(uniqueId);
        pendingArenaDeleteTarget.remove(uniqueId);
    }

    public void openAdminMain(Player player) {
        SimpleForm form = new SimpleForm(
                title(player, "FFA Admin Console", "FFA Admin Konsole"),
                section(player,
                        "Arena and kit administration are strictly separated.",
                        "Arena- und Kit-Verwaltung sind strikt getrennt.")
        );
        form.addElement(new ElementButton(button(player, TextFormat.GOLD,
                "Arena Management",
                "Arenas, zones, status and safe-zone mode",
                "Arena Verwaltung",
                "Arenen, Zonen, Status und Safe-Zone Modus")));
        form.addElement(new ElementButton(button(player, TextFormat.AQUA,
                "Kit Management",
                "Create, delete, inspect kits",
                "Kit Verwaltung",
                "Kits erstellen, loeschen, pruefen")));
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Close", "Schliessen")));
        form.send(player, FORM_ADMIN_MAIN);
    }

    void handleAdminMain(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        switch (simple.buttonId()) {
            case 0 -> openAdminArenaMenu(player);
            case 1 -> root.kitAdminMenus.openAdminKitMenu(player);
            default -> {
            }
        }
    }

    void openAdminArenaMenu(Player player) {
        SimpleForm form = new SimpleForm(
                title(player, "Arena Management", "Arena Verwaltung"),
                section(player,
                        "Arena tools only. No kit controls are mixed in.",
                        "Nur Arena-Werkzeuge. Keine vermischten Kit-Funktionen.")
        );
        form.addElement(new ElementButton(button(player, TextFormat.GREEN,
                "Create Arena",
                "Create a new arena definition",
                "Arena erstellen",
                "Neue Arena-Definition erstellen")));
        form.addElement(new ElementButton(button(player, TextFormat.RED,
                "Delete Arena",
                "Remove an existing arena",
                "Arena loeschen",
                "Bestehende Arena entfernen")));
        form.addElement(new ElementButton(button(player, TextFormat.YELLOW,
                "Add Spawn",
                "Store your current location as spawn",
                "Spawn hinzufuegen",
                "Aktuelle Position als Spawn speichern")));
        form.addElement(new ElementButton(button(player, TextFormat.AQUA,
                "Set Arena Status",
                "Standby, live, offline, maintenance",
                "Arena-Status setzen",
                "Standby, live, offline, wartung")));
        form.addElement(new ElementButton(button(player, TextFormat.GOLD,
                "Zone Tools",
                "Custom zones and safe-zone placement mode",
                "Zone Tools",
                "Eigene Zonen und Safe-Zone Modus")));
        form.addElement(new ElementButton(button(player, TextFormat.WHITE,
                "Arena Overview",
                "Inspect all arena technical details",
                "Arena Uebersicht",
                "Alle Arena-Details anzeigen")));
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_ARENA_MENU);
    }

    void handleAdminArenaMenu(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        switch (simple.buttonId()) {
            case 0 -> openAdminArenaCreate(player);
            case 1 -> openAdminArenaDelete(player);
            case 2 -> openAdminArenaAddSpawn(player);
            case 3 -> openAdminArenaStatus(player);
            case 4 -> openAdminZoneMenu(player);
            case 5 -> openAdminArenaList(player);
            default -> openAdminMain(player);
        }
    }

    void openAdminArenaCreate(Player player) {
        CustomForm form = new CustomForm(title(player, "Create Arena", "Arena erstellen"));
        form.addElement(new ElementInput(t(player, "Arena ID", "Arena ID"), "city"));
        form.addElement(new ElementInput(t(player, "Display Name (optional)", "Anzeigename (optional)"), "City Arena"));
        form.addElement(new ElementInput(t(player, "World Name", "Weltname"), player.getLevel().getName()));
        form.addElement(new ElementDropdown(
                t(player, "Action", "Aktion"),
                List.of(t(player, "Create Arena", "Arena erstellen"), t(player, "Back", "Zurueck")),
                0
        ));
        form.send(player, FORM_ADMIN_ARENA_CREATE);
    }

    void handleAdminArenaCreate(Player player, Response response) {
        if (!(response instanceof CustomResponse custom)) {
            return;
        }
        int action = getDropdownIndex(custom, 3);
        if (action == 1) {
            openAdminArenaMenu(player);
            return;
        }

        String arenaId = normalizeIdentifier(custom.getInputResponse(0));
        String displayName = trimInput(custom.getInputResponse(1));
        String worldName = trimInput(custom.getInputResponse(2));
        if (arenaId == null || worldName == null) {
            sendError(player, t(player, "Arena ID and world are required.", "Arena ID und Welt sind Pflicht."));
            openAdminArenaCreate(player);
            return;
        }
        boolean created = arenaCatalogService.createArena(arenaId, displayName, worldName);
        String arenaName = displayName == null ? arenaId : displayName;
        if (created) {
            sendSuccess(player, t(player, "Arena created: ", "Arena erstellt: ") + arenaName);
        } else {
            sendError(player, t(player, "Arena could not be created.", "Arena konnte nicht erstellt werden."));
        }
        openAdminArenaMenu(player);
    }

    void openAdminArenaDelete(Player player) {
        List<ArenaDefinition> arenas = sortedArenas();
        SimpleForm form = new SimpleForm(
                title(player, "Delete Arena", "Arena loeschen"),
                section(player, "Select an arena to delete.", "Waehle eine Arena zum Loeschen.")
        );
        if (arenas.isEmpty()) {
            form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "No arenas available", "Keine Arenen verfuegbar")));
        } else {
            for (ArenaDefinition arena : arenas) {
                form.addElement(new ElementButton(TextFormat.YELLOW + arena.getDisplayName() + "\n"
                        + TextFormat.GRAY + arena.getWorldName()));
            }
        }
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_ARENA_DELETE);
    }

    void handleAdminArenaDelete(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        List<ArenaDefinition> arenas = sortedArenas();
        int selected = simple.buttonId();
        int backIndex = arenas.isEmpty() ? 1 : arenas.size();
        if (selected < 0 || selected >= backIndex) {
            openAdminArenaMenu(player);
            return;
        }
        if (arenas.isEmpty()) {
            openAdminArenaMenu(player);
            return;
        }
        ArenaDefinition selectedArena = arenas.get(selected);
        pendingArenaDeleteTarget.put(player.getUniqueId(), selectedArena.getArenaId());
        openAdminArenaDeleteConfirm(player, selectedArena.getDisplayName());
    }

    void openAdminArenaDeleteConfirm(Player player, String arenaName) {
        SimpleForm form = new SimpleForm(
                title(player, "Confirm Arena Deletion", "Arena-Loeschung bestaetigen"),
                TextFormat.RED + t(player, "This will permanently delete arena ", "Diese Aktion loescht die Arena dauerhaft ")
                        + TextFormat.YELLOW + arenaName + TextFormat.RED + "."
        );
        form.addElement(new ElementButton(TextFormat.RED + t(player, "Delete Now", "Jetzt loeschen")));
        form.addElement(new ElementButton(TextFormat.YELLOW + t(player, "Undo Last Delete", "Letzte Loeschung rueckgaengig")));
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_ARENA_DELETE_CONFIRM);
    }

    void handleAdminArenaDeleteConfirm(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        if (simple.buttonId() == 1) {
            if (lastDeletedArena != null && arenaCatalogService.restoreArena(lastDeletedArena)) {
                sendSuccess(player, t(player, "Last deleted arena restored.", "Letzte geloeschte Arena wiederhergestellt."));
            } else {
                sendWarning(player, t(player, "No deletable arena in undo buffer.", "Kein Arena-Eintrag im Undo-Puffer."));
            }
            openAdminArenaDelete(player);
            return;
        }
        if (simple.buttonId() != 0) {
            pendingArenaDeleteTarget.remove(player.getUniqueId());
            openAdminArenaDelete(player);
            return;
        }

        String arenaId = pendingArenaDeleteTarget.remove(player.getUniqueId());
        if (arenaId == null) {
            openAdminArenaDelete(player);
            return;
        }

        Optional<ArenaDefinition> removed = arenaCatalogService.removeArena(arenaId);
        if (removed.isPresent()) {
            lastDeletedArena = removed.get();
            sendSuccess(player, t(player, "Arena deleted. Undo is available.", "Arena geloescht. Undo ist verfuegbar."));
        } else {
            sendError(player, t(player, "Arena deletion failed.", "Arena konnte nicht geloescht werden."));
        }
        openAdminArenaDelete(player);
    }

    void openAdminArenaAddSpawn(Player player) {
        List<ArenaDefinition> arenas = sortedArenas();
        SimpleForm form = new SimpleForm(
                title(player, "Add Spawn", "Spawn hinzufuegen"),
                section(player,
                        "Select one arena. Your current location becomes a spawn point.",
                        "Waehle eine Arena. Deine aktuelle Position wird als Spawn gespeichert.")
        );
        if (arenas.isEmpty()) {
            form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "No arenas available", "Keine Arenen verfuegbar")));
        } else {
            for (ArenaDefinition arena : arenas) {
                form.addElement(new ElementButton(TextFormat.YELLOW + arena.getDisplayName() + "\n"
                        + TextFormat.GRAY + arena.getWorldName()));
            }
        }
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_ARENA_ADD_SPAWN);
    }

    void handleAdminArenaAddSpawn(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        List<ArenaDefinition> arenas = sortedArenas();
        int selected = simple.buttonId();
        int backIndex = arenas.isEmpty() ? 1 : arenas.size();
        if (selected < 0 || selected >= backIndex) {
            openAdminArenaMenu(player);
            return;
        }
        if (arenas.isEmpty()) {
            openAdminArenaMenu(player);
            return;
        }
        boolean added = arenaCatalogService.addSpawnPoint(arenas.get(selected).getArenaId(), player.getLocation());
        if (added) {
            sendSuccess(player, t(player, "Spawn saved.", "Spawn gespeichert."));
        } else {
            sendError(player, t(player, "Spawn could not be saved.", "Spawn konnte nicht gespeichert werden."));
        }
        openAdminArenaAddSpawn(player);
    }

    void openAdminArenaStatus(Player player) {
        List<ArenaDefinition> arenas = sortedArenas();
        SimpleForm form = new SimpleForm(
                title(player, "Set Arena Status", "Arena-Status setzen"),
                section(player, "Select one arena. Players can join only Live arenas.", "Waehle eine Arena. Spieler koennen nur Live-Arenen betreten.")
        );
        if (arenas.isEmpty()) {
            form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "No arenas available", "Keine Arenen verfuegbar")));
        } else {
            for (ArenaDefinition arena : arenas) {
                form.addElement(new ElementButton(TextFormat.YELLOW + arena.getDisplayName() + "\n"
                        + formatStatusBadge(player, arena.getStatus())
                        + TextFormat.GRAY + " | " + formatJoinBadge(player, arena.getStatus()) + "\n"
                        + formatReadinessBadge(player, arena)));
            }
        }
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_ARENA_STATUS);
    }

    void handleAdminArenaStatus(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        List<ArenaDefinition> arenas = sortedArenas();
        int selected = simple.buttonId();
        int backIndex = arenas.isEmpty() ? 1 : arenas.size();
        if (selected < 0 || selected >= backIndex) {
            openAdminArenaMenu(player);
            return;
        }
        if (arenas.isEmpty()) {
            openAdminArenaMenu(player);
            return;
        }

        ArenaDefinition selectedArena = arenas.get(selected);
        String arenaId = selectedArena.getArenaId();
        pendingArenaStatusTarget.put(player.getUniqueId(), arenaId);

        SimpleForm form = new SimpleForm(
                title(player, "Select Status", "Status auswaehlen"),
                TextFormat.GRAY + t(player, "Arena", "Arena") + ": " + TextFormat.YELLOW + selectedArena.getDisplayName()
        );
        for (ArenaStatus status : ArenaStatus.values()) {
            form.addElement(new ElementButton(formatStatusBadge(player, status) + "\n"
                    + TextFormat.GRAY + formatJoinBadge(player, status)));
        }
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_ARENA_STATUS_VALUE);
    }

    void handleAdminArenaStatusValue(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        int selected = simple.buttonId();
        int backIndex = ArenaStatus.values().length;
        if (selected < 0 || selected >= backIndex) {
            pendingArenaStatusTarget.remove(player.getUniqueId());
            openAdminArenaStatus(player);
            return;
        }

        String arenaId = pendingArenaStatusTarget.remove(player.getUniqueId());
        if (arenaId == null) {
            openAdminArenaStatus(player);
            return;
        }

        ArenaStatus status = ArenaStatus.values()[selected];
        boolean updated = arenaCatalogService.setArenaStatus(arenaId, status);
        if (updated) {
            sendSuccess(player, t(player, "Status updated to ", "Status aktualisiert auf ") + formatStatus(player, status) + ".");
        } else {
            if (status == ArenaStatus.ACTIVE) {
                String reason = arenaCatalogService.validateActivationReadiness(arenaId)
                        .map(error -> localizeReadinessReason(player, error))
                        .orElse(t(player, "Unknown readiness error.", "Unbekannter Bereitschaftsfehler."));
                sendError(player, t(player, "Arena is not ready for Live: ", "Arena ist nicht bereit fuer Live: ") + reason);
            } else {
                sendError(player, t(player, "Status update failed.", "Status konnte nicht gesetzt werden."));
            }
        }
        openAdminArenaStatus(player);
    }

    public void openAdminZoneMenu(Player player) {
        boolean safeModeActive = safeZonePlacementService.isPlacing(player.getUniqueId());
        String safeModeState = safeModeActive ? TextFormat.GREEN + t(player, "ACTIVE", "AKTIV")
                : TextFormat.RED + t(player, "INACTIVE", "INAKTIV");

        SimpleForm form = new SimpleForm(
                title(player, "Zone Tools", "Zone Tools"),
                TextFormat.GRAY + t(player,
                "Safe-Zone setup uses 2-click mode and saves automatically.",
                "Safe-Zone Setup nutzt den 2-Klick-Modus und speichert automatisch.")
                        + "\n" + TextFormat.DARK_GRAY + t(player, "Mode: ", "Modus: ") + safeModeState
        );
        form.addElement(new ElementButton(button(player, TextFormat.AQUA,
                "Start Safe-Zone 2-Click Mode",
                "Select arena and click two blocks in world",
                "Safe-Zone 2-Klick Modus starten",
                "Arena waehlen und zwei Bloecke anklicken")));
        form.addElement(new ElementButton(button(player, TextFormat.YELLOW,
                "Cancel Safe-Zone Mode",
                "Disable current click-mode session",
                "Safe-Zone Modus abbrechen",
                "Aktive Click-Mode Session beenden")));
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_ZONE_MENU);
    }

    void handleAdminZoneMenu(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        switch (simple.buttonId()) {
            case 0 -> openAdminSafeZoneMode(player);
            case 1 -> {
                safeZonePlacementService.cancel(player.getUniqueId());
                sendInfo(player, t(player, "Safe-Zone mode cancelled.", "Safe-Zone Modus abgebrochen."));
                openAdminZoneMenu(player);
            }
            default -> openAdminArenaMenu(player);
        }
    }

    void openAdminSafeZoneMode(Player player) {
        List<ArenaDefinition> arenas = sortedArenas();
        SimpleForm form = new SimpleForm(
                title(player, "Safe-Zone 2-Click Mode", "Safe-Zone 2-Klick Modus"),
                section(player,
                        "Select an arena. Then click two blocks in the arena world.",
                        "Waehle eine Arena. Danach klickst du zwei Bloecke in der Arena-Welt.")
        );
        if (arenas.isEmpty()) {
            form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "No arenas available", "Keine Arenen verfuegbar")));
        } else {
            for (ArenaDefinition arena : arenas) {
                form.addElement(new ElementButton(TextFormat.YELLOW + arena.getDisplayName() + "\n"
                        + TextFormat.GRAY + arena.getWorldName()));
            }
        }
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_ZONE_SAFE_MODE);
    }

    void handleAdminSafeZoneMode(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        List<ArenaDefinition> arenas = sortedArenas();
        int selected = simple.buttonId();
        int backIndex = arenas.isEmpty() ? 1 : arenas.size();
        if (selected < 0 || selected >= backIndex) {
            openAdminZoneMenu(player);
            return;
        }
        if (arenas.isEmpty()) {
            openAdminZoneMenu(player);
            return;
        }

        ArenaDefinition selectedArena = arenas.get(selected);
        String arenaId = selectedArena.getArenaId();
        if (!safeZonePlacementService.beginPlacement(player, arenaId)) {
            sendError(player, t(player, "Safe-Zone mode could not start.", "Safe-Zone Modus konnte nicht gestartet werden."));
            openAdminZoneMenu(player);
        } else {
            sendSuccess(player, t(player,
                    "Safe-Zone mode started for arena " + selectedArena.getDisplayName() + ".",
                    "Safe-Zone Modus fuer Arena " + selectedArena.getDisplayName() + " gestartet."));
        }
    }

    void openAdminArenaList(Player player) {
        List<ArenaDefinition> arenas = sortedArenas();
        StringBuilder content = new StringBuilder();
        if (arenas.isEmpty()) {
            content.append(TextFormat.GRAY).append(t(player, "No arenas available.", "Keine Arenen verfuegbar."));
        } else {
            for (ArenaDefinition arena : arenas) {
                content.append(TextFormat.YELLOW).append(arena.getDisplayName()).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "World", "Welt"))
                        .append(": ").append(TextFormat.GRAY).append(arena.getWorldName()).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Status", "Status"))
                        .append(": ").append(formatStatusBadge(player, arena.getStatus())).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Join", "Beitritt"))
                        .append(": ").append(formatJoinBadge(player, arena.getStatus())).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Readiness", "Bereitschaft"))
                        .append(": ").append(formatReadinessBadge(player, arena)).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Spawn Points", "Spawnpunkte"))
                        .append(": ").append(TextFormat.GRAY).append(arena.getSpawnPoints().size()).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Zones", "Zonen"))
                        .append(": ").append(TextFormat.GRAY).append(arena.getZones().size()).append("\n")
                        .append(TextFormat.DARK_GRAY).append(" - ").append(t(player, "Safe-Zone", "Safe-Zone"))
                        .append(": ").append(arena.getSafeZone().isPresent()
                                ? TextFormat.GREEN + t(player, "Configured", "Konfiguriert")
                                : TextFormat.RED + t(player, "None", "Keine"))
                        .append("\n\n");
            }
        }
        SimpleForm form = new SimpleForm(title(player, "Arena Overview", "Arena Uebersicht"), content.toString().trim());
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_ARENA_LIST);
    }
}

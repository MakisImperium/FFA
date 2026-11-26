package net.zernyth.ffa.admin;

import net.zernyth.ffa.arena.model.ArenaDefinition;
import net.zernyth.ffa.arena.model.ArenaStatus;
import net.zernyth.ffa.arena.model.CuboidZone;
import net.zernyth.ffa.arena.model.SpawnPoint;
import net.zernyth.ffa.arena.service.ArenaCatalogService;
import net.zernyth.ffa.arena.service.ArenaSessionService;
import net.zernyth.ffa.combat.KillService;
import net.zernyth.ffa.command.Subcommands;
import net.zernyth.ffa.config.ConfigurationService;
import net.zernyth.ffa.config.PluginConfiguration;
import net.zernyth.ffa.event.EventService;
import net.zernyth.ffa.i18n.LocalizationService;
import net.zernyth.ffa.kit.model.KitDefinition;
import net.zernyth.ffa.kit.service.KitRegistry;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.service.PlayerProfileService;
import net.zernyth.ffa.player.storage.JsonPlayerProfileRepository;
import net.zernyth.ffa.util.JsonFileService;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.event.player.PlayerTeleportEvent;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.particle.DustParticle;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.plugin.PluginBase;
import org.powernukkitx.scheduler.TaskHandler;
import org.powernukkitx.utils.TextFormat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The {@code /ffaadmin} tools: reload, arena tools, kick, announcements, boost events, the JSON to
 * MySQL migration and kit levels. Slow work (files, database) runs on its own thread; results come
 * back to the main thread.
 */
public final class AdminCommands {
    /** What the tools work on, handed over by the bootstrap. */
    public record Context(PluginBase plugin, JsonFileService json, Path dataPath, ConfigurationService configuration,
                          Consumer<PluginConfiguration> swapConfiguration, ArenaCatalogService catalog,
                          ArenaSessionService arenas, KitRegistry kits, LocalizationService localization,
                          PlayerProfileService profiles, KillService kills, EventService events, BooleanSupplier mysql) {}

    private final Context ctx;

    public AdminCommands(Context ctx) {
        this.ctx = ctx;
    }

    public void register(Subcommands commands) {
        commands.add("reload", "reload", (player, args) -> reload(player));
        commands.add("arena", "arena <id> spawns|tp <n>|delspawn <n>|zones|showzone <zone>|delzone <zone>|copy <newId> [name]", this::arena);
        commands.add("kick", "kick <player>", this::kick);
        commands.add("announce", "announce <arena|all> <message>", this::announce);
        commands.add("event", "event start <xp> <coins> <minutes> | stop | status", this::event);
        commands.add("migrate", "migrate json-to-mysql [dry] [overwrite]", this::migrate);
        commands.add("kitlevel", "kitlevel [<kit> <level>]", this::kitLevel);
    }

    // ----- reload

    private void reload(Player admin) {
        admin.sendMessage(TextFormat.GRAY + t(admin, "Reloading config, arenas, kits and languages...", "Lade Konfiguration, Arenen, Kits und Sprachen neu..."));
        background("ffa-reload", () -> {
            try {
                PluginConfiguration configuration = ctx.configuration().readForReload();
                List<ArenaDefinition> arenas = ctx.catalog().readAllFromDisk();
                List<KitDefinition> kits = ctx.kits().readAllFromDisk();
                Runnable languages = ctx.localization().prepareReload();
                main(() -> {
                    ctx.swapConfiguration().accept(configuration);
                    ctx.catalog().replaceAll(arenas);
                    boolean kitsSwapped = ctx.kits().replaceAll(kits);
                    languages.run();
                    int moved = removeStrandedPlayers();
                    admin.sendMessage(TextFormat.GREEN + t(admin,
                            "Reloaded: " + arenas.size() + " arenas, " + (kitsSwapped ? kits.size() : 0) + " kits.",
                            "Neu geladen: " + arenas.size() + " Arenen, " + (kitsSwapped ? kits.size() : 0) + " Kits."));
                    if (!kitsSwapped) {
                        admin.sendMessage(TextFormat.YELLOW + t(admin, "No kit files found; the current kits stay.", "Keine Kit-Dateien gefunden; die aktuellen Kits bleiben."));
                    }
                    if (moved > 0) {
                        admin.sendMessage(TextFormat.YELLOW + t(admin,
                                moved + " players were in arenas that are gone or not active and went to the lobby.",
                                moved + " Spieler waren in Arenen, die fehlen oder nicht aktiv sind, und sind in der Lobby."));
                    }
                    admin.sendMessage(TextFormat.GRAY + t(admin,
                            "Storage, database, scoreboard interval, combat tag and farm limits change only with a restart.",
                            "Speicher, Datenbank, Scoreboard-Intervall, Kampf-Markierung und Farm-Grenzen aendern sich erst nach einem Neustart."));
                });
            } catch (Exception failure) {
                main(() -> admin.sendMessage(TextFormat.RED + t(admin, "Reload failed, nothing changed: ", "Neu laden fehlgeschlagen, nichts geaendert: ")
                        + failure.getClass().getSimpleName() + ": " + failure.getMessage()));
            }
        });
    }

    private int removeStrandedPlayers() {
        int moved = 0;
        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            if (!ctx.arenas().isPlayerInArena(player.getUniqueId())) {
                continue;
            }
            Optional<ArenaDefinition> arena = ctx.arenas().getArenaForPlayer(player.getUniqueId());
            if (arena.isEmpty() || arena.get().getStatus() != ArenaStatus.ACTIVE) {
                ctx.kills().combat().clear(player.getUniqueId());
                ctx.arenas().leaveArena(player, true);
                moved++;
            }
        }
        return moved;
    }

    // ----- arena tools

    private void arena(Player admin, String[] args) {
        if (args.length < 2) {
            admin.sendMessage(TextFormat.GRAY + "/ffaadmin arena <id> spawns|tp <n>|delspawn <n>|zones|showzone <zone>|delzone <zone>|copy <newId> [name]");
            return;
        }
        Optional<ArenaDefinition> found = ctx.catalog().findArena(args[0]);
        if (found.isEmpty()) {
            admin.sendMessage(TextFormat.RED + t(admin, "Unknown arena: ", "Unbekannte Arena: ") + args[0]);
            return;
        }
        ArenaDefinition arena = found.get();
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "spawns" -> {
                List<SpawnPoint> spawns = arena.getSpawnPoints();
                admin.sendMessage(TextFormat.GOLD + arena.getDisplayName() + TextFormat.GRAY + " - " + spawns.size() + " spawns");
                for (int i = 0; i < spawns.size(); i++) {
                    admin.sendMessage(TextFormat.GRAY + "#" + (i + 1) + TextFormat.WHITE + " " + describe(spawns.get(i)));
                }
            }
            case "tp" -> spawnIndex(admin, args, arena).ifPresent(index -> {
                Level level = Server.getInstance().getLevelByName(arena.getWorldName());
                if (level == null && Server.getInstance().loadLevel(arena.getWorldName())) {
                    level = Server.getInstance().getLevelByName(arena.getWorldName());
                }
                if (level == null) {
                    admin.sendMessage(TextFormat.RED + t(admin, "The arena world cannot be loaded.", "Die Arena-Welt laesst sich nicht laden."));
                    return;
                }
                admin.teleport(arena.getSpawnPoints().get(index).toLocation(level), PlayerTeleportEvent.TeleportCause.PLUGIN);
            });
            case "delspawn" -> spawnIndex(admin, args, arena).ifPresent(index -> {
                if (ctx.catalog().removeSpawnPoint(arena.getArenaId(), index)) {
                    admin.sendMessage(TextFormat.GREEN + t(admin, "Spawn #" + (index + 1) + " removed.", "Spawn #" + (index + 1) + " entfernt."));
                    if (arena.getSpawnPoints().isEmpty()) {
                        admin.sendMessage(TextFormat.YELLOW + t(admin, "No spawns left: the arena is waiting now.", "Keine Spawns mehr: Die Arena wartet jetzt."));
                    }
                }
            });
            case "zones" -> {
                if (arena.getZones().isEmpty()) {
                    admin.sendMessage(TextFormat.GRAY + t(admin, "No zones.", "Keine Zonen."));
                }
                arena.getZones().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> admin.sendMessage(
                        TextFormat.WHITE + entry.getKey()
                                + (entry.getKey().equals(arena.getSafeZoneId()) ? TextFormat.GREEN + " [safe]" : "")
                                + TextFormat.GRAY + " " + describe(entry.getValue())));
            }
            case "showzone" -> zone(admin, args, arena).ifPresent(zone -> showZone(admin, zone));
            case "delzone" -> {
                if (args.length < 3 || !ctx.catalog().removeZone(arena.getArenaId(), args[2])) {
                    admin.sendMessage(TextFormat.RED + t(admin, "No such zone.", "Diese Zone gibt es nicht."));
                } else {
                    admin.sendMessage(TextFormat.GREEN + t(admin, "Zone removed.", "Zone entfernt."));
                }
            }
            case "copy" -> {
                if (args.length < 3) {
                    admin.sendMessage(TextFormat.GRAY + "/ffaadmin arena <id> copy <newId> [name]");
                    return;
                }
                String name = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
                ctx.catalog().copyArena(arena.getArenaId(), args[2], name).ifPresentOrElse(
                        copy -> admin.sendMessage(TextFormat.GREEN + t(admin,
                                "Copied to " + copy.getArenaId() + " (waiting; activate it in the menu).",
                                "Kopiert nach " + copy.getArenaId() + " (wartet; im Menue aktivieren).")),
                        () -> admin.sendMessage(TextFormat.RED + t(admin, "That id is taken or invalid.", "Diese ID ist vergeben oder ungueltig.")));
            }
            default -> admin.sendMessage(TextFormat.GRAY + "/ffaadmin arena <id> spawns|tp <n>|delspawn <n>|zones|showzone <zone>|delzone <zone>|copy <newId> [name]");
        }
    }

    private Optional<Integer> spawnIndex(Player admin, String[] args, ArenaDefinition arena) {
        try {
            int index = Integer.parseInt(args[2]) - 1;
            if (index >= 0 && index < arena.getSpawnPoints().size()) {
                return Optional.of(index);
            }
        } catch (RuntimeException ignored) {
            // falls through to the message
        }
        admin.sendMessage(TextFormat.RED + t(admin, "Give a spawn number from 1 to ", "Gib eine Spawn-Nummer von 1 bis ")
                + arena.getSpawnPoints().size() + t(admin, " (see spawns).", " an (siehe spawns)."));
        return Optional.empty();
    }

    private Optional<CuboidZone> zone(Player admin, String[] args, ArenaDefinition arena) {
        CuboidZone zone = args.length < 3 ? null : arena.getZones().get(args[2].toLowerCase(Locale.ROOT));
        if (zone == null) {
            admin.sendMessage(TextFormat.RED + t(admin, "No such zone (see zones).", "Diese Zone gibt es nicht (siehe zones)."));
        }
        return Optional.ofNullable(zone);
    }

    /** Draws the zone's edges for the admin alone, for 10 seconds. */
    private void showZone(Player admin, CuboidZone zone) {
        Level level = Server.getInstance().getLevelByName(zone.getWorldName());
        if (level == null || admin.getLevel() != level) {
            admin.sendMessage(TextFormat.YELLOW + t(admin, "Go to the zone's world to see it: ", "Geh in die Welt der Zone, um sie zu sehen: ") + zone.getWorldName());
            return;
        }
        List<Vector3> points = ZoneOutline.points(zone, 600);
        TaskHandler[] handle = new TaskHandler[1];
        int[] runs = {0};
        handle[0] = Server.getInstance().getScheduler().scheduleRepeatingTask(ctx.plugin(), () -> {
            if (++runs[0] > 20 || !admin.isOnline()) {
                handle[0].cancel();
                return;
            }
            for (Vector3 point : points) {
                level.addParticle(new DustParticle(point, 80, 255, 120), admin);
            }
        }, 10);
        admin.sendMessage(TextFormat.GREEN + t(admin, "Showing the zone for 10 seconds.", "Zeige die Zone 10 Sekunden lang."));
    }

    // ----- players and announcements

    private void kick(Player admin, String[] args) {
        Player target = args.length == 0 ? null : Server.getInstance().getPlayerExact(args[0]);
        if (target == null || !ctx.arenas().isPlayerInArena(target.getUniqueId())) {
            admin.sendMessage(TextFormat.RED + t(admin, "That player is not in an arena.", "Dieser Spieler ist in keiner Arena."));
            return;
        }
        ctx.kills().combat().clear(target.getUniqueId());
        ctx.arenas().leaveArena(target, true);
        target.sendMessage(TextFormat.RED + t(target, "An admin removed you from the arena.", "Ein Admin hat dich aus der Arena entfernt."));
        admin.sendMessage(TextFormat.GREEN + t(admin, target.getName() + " removed from the arena.", target.getName() + " aus der Arena entfernt."));
    }

    private void announce(Player admin, String[] args) {
        if (args.length < 2) {
            admin.sendMessage(TextFormat.GRAY + "/ffaadmin announce <arena|all> <message>  (&-colour codes work)");
            return;
        }
        String message = TextFormat.GOLD + "[FFA] " + TextFormat.RESET
                + TextFormat.colorize('&', String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        Set<UUID> audience;
        if (args[0].equalsIgnoreCase("all")) {
            audience = ctx.catalog().listArenas().stream()
                    .flatMap(arena -> ctx.arenas().getPlayersInArena(arena.getArenaId()).stream())
                    .collect(Collectors.toSet());
        } else if (ctx.catalog().findArena(args[0]).isPresent()) {
            audience = ctx.arenas().getPlayersInArena(ctx.catalog().findArena(args[0]).get().getArenaId());
        } else {
            admin.sendMessage(TextFormat.RED + t(admin, "Unknown arena: ", "Unbekannte Arena: ") + args[0]);
            return;
        }
        int sent = 0;
        for (UUID id : audience) {
            Optional<Player> player = Server.getInstance().getPlayer(id).filter(Player::isOnline);
            if (player.isPresent()) {
                player.get().sendMessage(message);
                sent++;
            }
        }
        admin.sendMessage(TextFormat.GREEN + t(admin, "Sent to " + sent + " players.", "An " + sent + " Spieler gesendet."));
    }

    // ----- boost events

    private void event(Player admin, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "start" -> {
                try {
                    double xp = Double.parseDouble(args[1]);
                    double coins = Double.parseDouble(args[2]);
                    int minutes = Integer.parseInt(args[3]);
                    if (xp < 0 || coins < 0 || xp > 10 || coins > 10 || minutes <= 0 || minutes > 24 * 60) {
                        throw new IllegalArgumentException();
                    }
                    ctx.events().start(xp, coins, minutes);
                    announceEvents();
                } catch (RuntimeException invalid) {
                    admin.sendMessage(TextFormat.GRAY + "/ffaadmin event start <xp 0-10> <coins 0-10> <minutes>  e.g. start 2 2 60");
                }
            }
            case "stop" -> {
                if (ctx.events().stop()) {
                    announceEvents();
                } else {
                    admin.sendMessage(TextFormat.GRAY + t(admin, "No event started by an admin is running.", "Es laeuft kein von einem Admin gestartetes Event."));
                }
            }
            default -> admin.sendMessage(ctx.events().active()
                    .map(active -> TextFormat.LIGHT_PURPLE + active.name(german(admin)) + TextFormat.WHITE + ": " + multiplier(active.xp()) + "x XP, "
                            + multiplier(active.coins()) + "x Coins" + TextFormat.GRAY + " (" + minutesLeft(active) + " min"
                            + (active.manual() ? "" : ", " + t(admin, "scheduled", "geplant")) + ")")
                    .orElse(TextFormat.GRAY + t(admin, "No event running.", "Kein Event aktiv.")));
        }
    }

    /** Tells everyone when a boost starts or ends; called after admin changes and every 30 seconds. */
    public void announceEvents() {
        ctx.events().poll().ifPresent(transition -> {
            EventService.Active active = transition.active();
            for (Player player : Server.getInstance().getOnlinePlayers().values()) {
                boolean german = german(player);
                if (transition.change() == EventService.Change.STARTED) {
                    player.sendMessage(TextFormat.LIGHT_PURPLE + "" + TextFormat.BOLD + active.name(german) + "! " + TextFormat.RESET
                            + TextFormat.WHITE + multiplier(active.xp()) + (german ? "x EP, " : "x XP, ") + multiplier(active.coins())
                            + (german ? "x Muenzen" : "x coins") + TextFormat.GRAY + " - " + minutesLeft(active) + " min");
                } else {
                    player.sendMessage(TextFormat.LIGHT_PURPLE + active.name(german) + TextFormat.GRAY + (german ? " ist vorbei." : " has ended."));
                }
            }
        });
    }

    public static String multiplier(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public static long minutesLeft(EventService.Active active) {
        return Math.max(1, (active.endsAt() - System.currentTimeMillis() + 59_999) / 60_000);
    }

    // ----- migration

    private void migrate(Player admin, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("json-to-mysql")) {
            admin.sendMessage(TextFormat.GRAY + "/ffaadmin migrate json-to-mysql [dry] [overwrite]");
            return;
        }
        if (!ctx.mysql().getAsBoolean()) {
            admin.sendMessage(TextFormat.RED + t(admin, "MySQL is not the active storage; set it up in config.json and restart first.",
                    "MySQL ist nicht der aktive Speicher; richte es in config.json ein und starte zuerst neu."));
            return;
        }
        Set<String> flags = Arrays.stream(args).skip(1).map(a -> a.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        boolean dry = flags.contains("dry");
        boolean overwrite = flags.contains("overwrite");
        Set<UUID> online = Server.getInstance().getOnlinePlayers().values().stream().map(Player::getUniqueId).collect(Collectors.toSet());
        admin.sendMessage(TextFormat.GRAY + t(admin, "Migration started", "Migration gestartet") + (dry ? t(admin, " (dry run)", " (Probelauf)") : "") + "...");
        background("ffa-migrate", () -> {
            try {
                JsonPlayerProfileRepository source = new JsonPlayerProfileRepository(ctx.json(), ctx.dataPath().resolve("players"));
                ProfileMigration.Report report = ProfileMigration.run(source, ctx.profiles().repository(), online, dry, overwrite,
                        done -> main(() -> admin.sendMessage(TextFormat.GRAY + "... " + done)));
                main(() -> {
                    admin.sendMessage(TextFormat.GREEN + report.summary(german(admin)));
                    if (report.existing() > 0 && !overwrite) {
                        admin.sendMessage(TextFormat.GRAY + t(admin, "Add 'overwrite' to replace profiles that are already in MySQL.",
                                "Mit 'overwrite' werden Profile ersetzt, die schon in MySQL sind."));
                    }
                });
            } catch (Exception failure) {
                main(() -> admin.sendMessage(TextFormat.RED + t(admin, "Migration failed: ", "Migration fehlgeschlagen: ") + failure.getMessage()));
            }
        });
    }

    // ----- kit levels

    private void kitLevel(Player admin, String[] args) {
        if (args.length < 2) {
            admin.sendMessage(TextFormat.GOLD + t(admin, "Kit levels", "Kit-Level") + TextFormat.GRAY + " - /ffaadmin kitlevel <kit> <level>");
            ctx.kits().listKits().stream().sorted(Comparator.comparing(KitDefinition::getKitId)).forEach(kit -> admin.sendMessage(
                    TextFormat.WHITE + kit.getKitId() + TextFormat.GRAY + ": Level " + kit.getRequiredLevel()));
            return;
        }
        try {
            int level = Integer.parseInt(args[1]);
            if (ctx.kits().setRequiredLevel(args[0], level)) {
                admin.sendMessage(TextFormat.GREEN + t(admin, "Kit " + args[0] + " now needs level " + Math.max(1, level) + ".",
                        "Kit " + args[0] + " braucht jetzt Level " + Math.max(1, level) + "."));
            } else {
                admin.sendMessage(TextFormat.RED + t(admin, "Unknown kit: ", "Unbekanntes Kit: ") + args[0]);
            }
        } catch (NumberFormatException invalid) {
            admin.sendMessage(TextFormat.RED + t(admin, "The level must be a number.", "Das Level muss eine Zahl sein."));
        }
    }

    // ----- helpers

    private static String describe(SpawnPoint spawn) {
        return String.format(Locale.ROOT, "%.1f / %.1f / %.1f", spawn.getX(), spawn.getY(), spawn.getZ());
    }

    private static String describe(CuboidZone zone) {
        return String.format(Locale.ROOT, "%s (%.0f %.0f %.0f) - (%.0f %.0f %.0f)", zone.getWorldName(),
                zone.getMinX(), zone.getMinY(), zone.getMinZ(), zone.getMaxX(), zone.getMaxY(), zone.getMaxZ());
    }

    private void background(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private void main(Runnable task) {
        Server.getInstance().getScheduler().scheduleTask(ctx.plugin(), task);
    }

    private boolean german(Player player) {
        return ctx.profiles().getLanguage(player.getUniqueId()) == PlayerLanguage.GERMAN;
    }

    private String t(Player player, String english, String german) {
        return german(player) ? german : english;
    }
}

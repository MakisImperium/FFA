package net.zernyth.ffa;

import org.powernukkitx.Player;
import org.powernukkitx.plugin.PluginBase;
import org.powernukkitx.scheduler.TaskHandler;
import org.powernukkitx.utils.TextFormat;
import net.zernyth.ffa.arena.model.ArenaStatus;
import net.zernyth.ffa.arena.persistence.ArenaRepository;
import net.zernyth.ffa.arena.persistence.JsonArenaRepository;
import net.zernyth.ffa.arena.service.ArenaCatalogService;
import net.zernyth.ffa.arena.service.ArenaSessionService;
import net.zernyth.ffa.arena.service.SafeZonePlacementService;
import net.zernyth.ffa.combat.CombatTagService;
import net.zernyth.ffa.combat.KillFarmGuard;
import net.zernyth.ffa.combat.KillService;
import net.zernyth.ffa.command.FfaAdminCommand;
import net.zernyth.ffa.command.FfaCommand;
import net.zernyth.ffa.config.ConfigurationService;
import net.zernyth.ffa.config.PluginConfiguration;
import net.zernyth.ffa.config.StorageBackend;
import net.zernyth.ffa.kit.persistence.JsonKitRepository;
import net.zernyth.ffa.kit.persistence.KitRepository;
import net.zernyth.ffa.kit.service.KitApplicationService;
import net.zernyth.ffa.kit.service.KitRegistry;
import net.zernyth.ffa.kit.service.KitSelectionService;
import net.zernyth.ffa.i18n.LocalizationService;
import net.zernyth.ffa.listener.ArenaProtectionListener;
import net.zernyth.ffa.listener.CombatListener;
import net.zernyth.ffa.listener.FormInteractionListener;
import net.zernyth.ffa.listener.PlayerLifecycleListener;
import net.zernyth.ffa.listener.ZonePlacementListener;
import net.zernyth.ffa.player.service.LeaderboardService;
import net.zernyth.ffa.player.service.PlayerProfileService;
import net.zernyth.ffa.player.storage.JsonPlayerProfileRepository;
import net.zernyth.ffa.player.storage.MySqlPlayerProfileRepository;
import net.zernyth.ffa.player.storage.PlayerProfileRepository;
import net.zernyth.ffa.scoreboard.FfaScoreboardService;
import net.zernyth.ffa.ui.form.FfaFormController;
import net.zernyth.ffa.util.JsonFileService;

import java.nio.file.Path;

public final class FFABootstrap extends PluginBase {

    private static final int MYSQL_RECONNECT_INTERVAL_TICKS = 20 * 15;
    private static final int MYSQL_MAX_RECONNECT_ATTEMPTS = 3;
    private static final int LEADERBOARD_REFRESH_TICKS = 20 * 60;

    private PluginConfiguration pluginConfiguration;
    private JsonFileService jsonFileService;
    private LocalizationService localizationService;
    private StorageBackend resolvedStorageBackend = StorageBackend.JSON;
    private boolean strictMySqlMode;
    private boolean databaseOutageMode;
    private int reconnectAttempts;
    private TaskHandler mysqlReconnectTask;
    private final Object databaseStateLock = new Object();

    private PlayerProfileService playerProfileService;
    private LeaderboardService leaderboardService;
    private TaskHandler leaderboardTask;
    private ArenaCatalogService arenaCatalogService;
    private ArenaSessionService arenaSessionService;
    private SafeZonePlacementService safeZonePlacementService;
    private KitRegistry kitRegistry;
    private KitSelectionService kitSelectionService;
    private KitApplicationService kitApplicationService;
    private FfaScoreboardService scoreboardService;
    private FfaFormController formController;
    private KillService killService;
    private KillFarmGuard killFarmGuard;
    private net.zernyth.ffa.progression.ProgressionService progressionService;
    private net.zernyth.ffa.mode.SpectatorService spectatorService;
    private net.zernyth.ffa.progression.SeasonService seasonService;
    /** Small file writes (seasons) away from the main thread. */
    private final java.util.concurrent.ExecutorService backgroundWriter = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ffa-writer");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void onEnable() {
        try {
            bootstrap();
            getLogger().info("FFAGameV2 has been enabled.");
        } catch (Exception exception) {
            if (strictMySqlMode && resolvedStorageBackend == StorageBackend.MYSQL) {
                getLogger().warning("FFAGameV2 startup halted because MySQL is unreachable.");
                getLogger().warning("Set storageBackend to JSON in config.json and restart.");
            } else {
                getLogger().error("Failed to enable FFAGameV2 plugin", exception);
            }
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (spectatorService != null) {
            spectatorService.restoreAll();
        }
        stopReconnectTask();
        if (leaderboardTask != null) {
            leaderboardTask.cancel();
        }
        if (scoreboardService != null) {
            scoreboardService.stop();
        }
        if (playerProfileService != null) {
            playerProfileService.persistAllCachedProfiles();
            playerProfileService.closeRepository();
        }
        if (localizationService != null) {
            localizationService.shutdown();
        }
        backgroundWriter.shutdown();
        try {
            backgroundWriter.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void bootstrap() throws Exception {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Unable to create plugin data folder");
        }

        this.jsonFileService = new JsonFileService();
        this.localizationService = new LocalizationService(this, jsonFileService, getDataFolder().toPath());
        this.localizationService.loadOrCreate();
        ConfigurationService configurationService = new ConfigurationService(this, jsonFileService);
        this.pluginConfiguration = configurationService.loadOrCreate();

        Path dataPath = getDataFolder().toPath();

        ArenaRepository arenaRepository = new JsonArenaRepository(jsonFileService, dataPath.resolve("arenas"));
        this.arenaCatalogService = new ArenaCatalogService(this, arenaRepository);
        this.arenaCatalogService.loadArenas();

        PlayerProfileRepository playerProfileRepository;
        try {
            playerProfileRepository = createPlayerRepository(pluginConfiguration, dataPath);
        } catch (Exception exception) {
            if (strictMySqlMode) {
                forceArenasOffline();
                getLogger().warning("MySQL could not be reached after 3 attempts.");
                getLogger().warning("Switch storageBackend to JSON in config.json and restart the server.");
            }
            throw exception;
        }
        this.playerProfileService = new PlayerProfileService(
                playerProfileRepository,
                (message, cause) -> getLogger().error(message, cause),
                task -> getServer().getScheduler().scheduleTask(this, task),
                pluginConfiguration.getScoreboard().isEnabledByDefault(),
                strictMySqlMode,
                this::handleDatabaseOutageTriggered
        );
        this.leaderboardService = new LeaderboardService(playerProfileService);
        this.leaderboardService.refresh();
        this.leaderboardTask = getServer().getScheduler().scheduleRepeatingTask(this, leaderboardService::refresh, LEADERBOARD_REFRESH_TICKS);

        KitRepository kitRepository = new JsonKitRepository(jsonFileService, dataPath.resolve("kits"));
        this.kitRegistry = new KitRegistry(this, kitRepository);
        this.kitRegistry.loadKits();
        this.kitSelectionService = new KitSelectionService(kitRegistry, playerProfileService);
        this.kitApplicationService = new KitApplicationService(kitRegistry);

        this.arenaSessionService = new ArenaSessionService(
                this,
                arenaCatalogService,
                kitSelectionService,
                kitApplicationService,
                playerProfileService
        );
        this.safeZonePlacementService = new SafeZonePlacementService(arenaCatalogService, playerProfileService);

        this.scoreboardService = new FfaScoreboardService(this, playerProfileService, arenaSessionService);
        this.scoreboardService.start(pluginConfiguration.getScoreboard().getUpdateIntervalTicks());

        var fairness = pluginConfiguration.getFairness();
        CombatTagService combatTags = new CombatTagService(fairness.getCombatTagSeconds() * 1000L, System::currentTimeMillis);
        this.killFarmGuard = new KillFarmGuard(fairness.getFarmMaxKills(), fairness.getFarmWindowMinutes() * 60_000L, System::currentTimeMillis);
        this.killService = new KillService(arenaSessionService, playerProfileService, scoreboardService, combatTags, killFarmGuard);
        getServer().getScheduler().scheduleRepeatingTask(this, killFarmGuard::prune, 20 * 60 * 5);
        killService.addListener(new net.zernyth.ffa.streak.StreakService(
                arenaCatalogService, arenaSessionService, playerProfileService, () -> pluginConfiguration.getStreaks()));

        // Progression: XP and levels, seasons, tasks, achievements, kits by level.
        this.progressionService = new net.zernyth.ffa.progression.ProgressionService(playerProfileService, kitSelectionService,
                () -> pluginConfiguration.getProgression(), () -> pluginConfiguration.getStreaks(), System::currentTimeMillis);
        this.seasonService = new net.zernyth.ffa.progression.SeasonService(jsonFileService, dataPath.resolve("seasons"),
                () -> pluginConfiguration.getProgression(), System::currentTimeMillis, backgroundWriter,
                new net.zernyth.ffa.progression.SeasonService.Ranking() {
                    @Override
                    public void storedTop(String seasonId, int limit, java.util.function.Consumer<java.util.List<net.zernyth.ffa.player.model.PlayerProfile>> result) {
                        playerProfileService.fetchTop(net.zernyth.ffa.player.model.LeaderboardCategory.SEASON_KILLS, limit, seasonId, result);
                    }

                    @Override
                    public java.util.List<net.zernyth.ffa.player.model.PlayerProfile> online(String seasonId) {
                        return playerProfileService.onlineSnapshot().stream()
                                .filter(profile -> seasonId.equals(profile.getProgress().getSeasonId()))
                                .toList();
                    }
                }, progressionService::onNewSeason);
        this.seasonService.load();
        this.seasonService.checkRollover(); // the server may have been off when the period ended
        progressionService.setSeasons(seasonService);
        leaderboardService.setCurrentSeason(seasonService::currentId);
        kitSelectionService.setLevels(progressionService::level);
        playerProfileService.addLoadListener(progressionService::onProfileLoaded);
        killService.addListener(progressionService);
        scoreboardService.addLineProvider(progressionService::scoreboardLines);
        getServer().getPluginManager().registerEvents(progressionService, this);
        getServer().getScheduler().scheduleRepeatingTask(this, seasonService::checkRollover, 20 * 60 * 5);
        scoreboardService.addLineProvider((viewer, language) -> {
            int seconds = combatTags.remainingSeconds(viewer.getUniqueId());
            if (seconds <= 0) {
                return java.util.List.of();
            }
            String label = language == net.zernyth.ffa.player.model.PlayerLanguage.GERMAN ? "Im Kampf " : "In combat ";
            return java.util.List.of(TextFormat.RED + label + TextFormat.WHITE + seconds + "s");
        });
        this.formController = new FfaFormController(
                arenaCatalogService,
                arenaSessionService,
                safeZonePlacementService,
                kitRegistry,
                kitSelectionService,
                kitApplicationService,
                playerProfileService,
                leaderboardService,
                scoreboardService,
                localizationService,
                killService
        );

        getServer().getPluginManager().registerEvents(
                new PlayerLifecycleListener(this, playerProfileService, arenaSessionService, scoreboardService, formController, killService),
                this
        );
        getServer().getPluginManager().registerEvents(new ArenaProtectionListener(arenaSessionService), this);
        getServer().getPluginManager().registerEvents(new CombatListener(arenaSessionService, killService.combat()), this);
        getServer().getPluginManager().registerEvents(new FormInteractionListener(formController), this);
        getServer().getPluginManager().registerEvents(new ZonePlacementListener(safeZonePlacementService, formController), this);

        formController.setProgressionService(progressionService, () -> pluginConfiguration.getProgression().levelCurve());

        // Economy: coins for kills, the cosmetics shop, kill effects and messages.
        net.zernyth.ffa.economy.EconomyService economyService = new net.zernyth.ffa.economy.EconomyService(playerProfileService,
                arenaSessionService, () -> pluginConfiguration.getEconomy(), () -> pluginConfiguration.getStreaks());
        killService.addListener(economyService);
        arenaSessionService.addJoinListener(economyService::onArenaJoin);
        formController.setEconomyService(economyService);
        scoreboardService.addLineProvider((viewer, language) -> playerProfileService.getProfile(viewer.getUniqueId())
                .map(profile -> java.util.List.of(TextFormat.GRAY
                        + (language == net.zernyth.ffa.player.model.PlayerLanguage.GERMAN ? "Muenzen: " : "Coins: ")
                        + TextFormat.YELLOW + profile.getProgress().getCoins()))
                .orElse(java.util.List.of()));
        FfaCommand ffaCommand = new FfaCommand(formController);
        FfaAdminCommand ffaAdminCommand = new FfaAdminCommand(formController);
        registerModes(ffaCommand, ffaAdminCommand);
        getServer().getCommandMap().register("ffa", ffaCommand);
        getServer().getCommandMap().register("ffaadmin", ffaAdminCommand);

        for (Player player : getServer().getOnlinePlayers().values()) {
            playerProfileService.loadProfile(player, profile -> {
                if (player.isOnline()) {
                    scoreboardService.handleJoin(player);
                }
            });
        }
    }

    /** Duels, spectating and team arenas, with their commands. */
    private void registerModes(FfaCommand ffa, FfaAdminCommand admin) {
        var duels = new net.zernyth.ffa.mode.DuelService(
                new net.zernyth.ffa.mode.DuelBook(net.zernyth.ffa.mode.DuelService.REQUEST_MILLIS, System::currentTimeMillis),
                arenaSessionService, playerProfileService);
        duels.setOnWin(progressionService::checkAchievements);
        arenaSessionService.addHitFilter(duels::hitAllowed);
        arenaSessionService.addLeaveListener(duels::onLeave);
        killService.addDeathListener(duels::onDeath);
        scoreboardService.addLineProvider(duels::scoreboardLines);
        getServer().getPluginManager().registerEvents(duels, this);
        formController.setDuelService(duels);

        this.spectatorService = new net.zernyth.ffa.mode.SpectatorService(arenaCatalogService, arenaSessionService, playerProfileService);
        arenaSessionService.addBeforeJoinListener(spectatorService::beforeJoin);
        getServer().getPluginManager().registerEvents(spectatorService, this);

        var teams = new net.zernyth.ffa.mode.TeamService(arenaSessionService, playerProfileService,
                new net.zernyth.ffa.mode.PartyBook(4, net.zernyth.ffa.mode.TeamService.INVITE_MILLIS, System::currentTimeMillis));
        arenaSessionService.addJoinListener(teams::onJoin);
        arenaSessionService.addLeaveListener(teams::onLeave);
        arenaSessionService.addHitFilter(teams::hitAllowed);
        killService.addListener(teams);
        scoreboardService.addLineProvider(teams::scoreboardLines);
        getServer().getPluginManager().registerEvents(teams, this);

        ffa.subcommands().add("duel", "duel <player> | accept | decline", (player, args) -> {
            if (args.length == 0) {
                formController.openPlayerMain(player);
            } else if (args[0].equalsIgnoreCase("accept")) {
                duels.accept(player);
            } else if (args[0].equalsIgnoreCase("decline")) {
                duels.decline(player);
            } else {
                duels.challenge(player, getServer().getPlayerExact(args[0]));
            }
        });
        ffa.subcommands().add("spectate", "spectate <arena>  (again to stop)", (player, args) -> {
            if (args.length == 0 || spectatorService.isSpectating(player.getUniqueId())) {
                if (!spectatorService.stop(player, true)) {
                    player.sendMessage(TextFormat.GRAY + "/ffa spectate <arena>");
                }
            } else {
                spectatorService.start(player, args[0]);
            }
        });
        ffa.subcommands().add("team", "team invite <player> | accept | leave | list", (player, args) -> {
            String action = args.length == 0 ? "list" : args[0].toLowerCase(java.util.Locale.ROOT);
            switch (action) {
                case "invite" -> {
                    if (args.length < 2) {
                        player.sendMessage(TextFormat.GRAY + "/ffa team invite <player>");
                    } else {
                        teams.invite(player, getServer().getPlayerExact(args[1]));
                    }
                }
                case "accept" -> teams.accept(player);
                case "leave" -> teams.leaveGroup(player);
                default -> teams.listGroup(player);
            }
        });
        admin.subcommands().add("mode", "mode <arena> <ffa|team> [team size 2-4]", (player, args) -> {
            if (args.length < 2 || !(args[1].equalsIgnoreCase("ffa") || args[1].equalsIgnoreCase("team"))) {
                player.sendMessage(TextFormat.GRAY + "/ffaadmin mode <arena> <ffa|team> [team size 2-4]");
                return;
            }
            boolean team = args[1].equalsIgnoreCase("team");
            int size = 2;
            if (args.length > 2) {
                try {
                    size = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {
                    player.sendMessage(TextFormat.RED + "Team size must be a number.");
                    return;
                }
            }
            if (arenaCatalogService.setArenaMode(args[0], team, size)) {
                player.sendMessage(TextFormat.GREEN + "Arena " + args[0] + " is now " + (team ? "team mode (size " + Math.max(2, Math.min(4, size)) + ")" : "free for all") + ".");
            } else {
                player.sendMessage(TextFormat.RED + "Unknown arena: " + args[0]);
            }
        });
    }

    private PlayerProfileRepository createPlayerRepository(PluginConfiguration configuration, Path dataPath) throws Exception {
        StorageBackend storageBackend = configuration.resolveStorageBackend();
        this.resolvedStorageBackend = storageBackend;
        this.strictMySqlMode = false;
        if (storageBackend == StorageBackend.JSON) {
            getLogger().warning("Player storage backend is set to JSON. MySQL remains the recommended production option.");
            this.resolvedStorageBackend = StorageBackend.JSON;
            return new JsonPlayerProfileRepository(jsonFileService, dataPath.resolve("players"));
        }

        if (!configuration.getDatabase().isConfigured()) {
            getLogger().info("MySQL is not configured yet. Please update config.json database settings and restart the server.");
            getLogger().info("Using JSON player storage until MySQL is configured.");
            this.resolvedStorageBackend = StorageBackend.JSON;
            return new JsonPlayerProfileRepository(jsonFileService, dataPath.resolve("players"));
        }

        this.strictMySqlMode = true;
        this.resolvedStorageBackend = StorageBackend.MYSQL;

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MYSQL_MAX_RECONNECT_ATTEMPTS; attempt++) {
            try {
                return new MySqlPlayerProfileRepository(configuration.getDatabase());
            } catch (Exception exception) {
                lastFailure = exception;
                getLogger().warning("MySQL connection attempt " + attempt + "/" + MYSQL_MAX_RECONNECT_ATTEMPTS + " failed.");
                if (attempt < MYSQL_MAX_RECONNECT_ATTEMPTS) {
                    getLogger().warning("Retrying MySQL connection in 15 seconds...");
                    try {
                        Thread.sleep(15000L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw interruptedException;
                    }
                }
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalStateException("MySQL initialization failed without exception details.");
    }

    private void handleDatabaseOutageTriggered() {
        if (!strictMySqlMode || resolvedStorageBackend != StorageBackend.MYSQL) {
            return;
        }

        synchronized (databaseStateLock) {
            if (databaseOutageMode) {
                return;
            }
            databaseOutageMode = true;
            reconnectAttempts = 0;
        }

        forceArenasOffline();
        forcePlayersOutOfArenas();
        if (scoreboardService != null) {
            scoreboardService.refreshAll();
        }
        if (playerProfileService != null) {
            playerProfileService.setDataAvailable(false);
        }

        notifyOnlinePlayers(TextFormat.RED + "FFA data service is currently unavailable. Stats are offline.");
        startReconnectTask();
    }

    private void startReconnectTask() {
        if (mysqlReconnectTask != null) {
            return;
        }
        mysqlReconnectTask = getServer().getScheduler().scheduleRepeatingTask(this, this::attemptMySqlReconnect, MYSQL_RECONNECT_INTERVAL_TICKS);
    }

    private void stopReconnectTask() {
        if (mysqlReconnectTask != null) {
            mysqlReconnectTask.cancel();
            mysqlReconnectTask = null;
        }
    }

    private void attemptMySqlReconnect() {
        if (!strictMySqlMode || resolvedStorageBackend != StorageBackend.MYSQL || playerProfileService == null) {
            stopReconnectTask();
            return;
        }

        // The ping waits for the network; it runs in the background and reports back here.
        playerProfileService.pingRepositoryAsync(this::handleReconnectResult);
    }

    private void handleReconnectResult(boolean reachable) {
        if (!isEnabled() || playerProfileService == null) {
            return;
        }
        if (reachable) {
            synchronized (databaseStateLock) {
                databaseOutageMode = false;
                reconnectAttempts = 0;
            }
            playerProfileService.setDataAvailable(true);
            stopReconnectTask();
            getLogger().info("MySQL connection restored. Stats are available again.");
            getLogger().info("Arenas stay offline until an admin sets statuses back to Live.");
            notifyOnlinePlayers(TextFormat.YELLOW + "Database connection restored. Arenas remain offline until re-enabled by admin.");
            return;
        }

        int currentAttempt;
        synchronized (databaseStateLock) {
            reconnectAttempts++;
            currentAttempt = reconnectAttempts;
        }

        getLogger().warning("MySQL reconnect attempt " + currentAttempt + "/" + MYSQL_MAX_RECONNECT_ATTEMPTS + " failed.");
        if (currentAttempt < MYSQL_MAX_RECONNECT_ATTEMPTS) {
            return;
        }

        stopReconnectTask();
        getLogger().warning("MySQL remains unreachable. Please switch storageBackend to JSON and restart the server.");
        notifyOnlinePlayers(TextFormat.RED + "Database unreachable. Ask an admin to switch to JSON storage.");
        getServer().getPluginManager().disablePlugin(this);
    }

    private void forceArenasOffline() {
        if (arenaCatalogService == null) {
            return;
        }
        int updated = arenaCatalogService.setAllArenasStatus(ArenaStatus.DISABLED);
        getLogger().warning("Database outage mode active. " + updated + " arena(s) set to Offline.");
    }

    private void forcePlayersOutOfArenas() {
        if (arenaSessionService == null) {
            return;
        }
        for (Player player : getServer().getOnlinePlayers().values()) {
            if (arenaSessionService.isPlayerInArena(player.getUniqueId())) {
                arenaSessionService.leaveArena(player, true);
            }
        }
    }

    private void notifyOnlinePlayers(String message) {
        for (Player player : getServer().getOnlinePlayers().values()) {
            player.sendMessage(message);
        }
    }
}



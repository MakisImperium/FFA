package net.zernyth.ffa.arena.service;

import it.unimi.dsi.fastutil.Pair;
import org.powernukkitx.Player;
import org.powernukkitx.event.player.PlayerRespawnEvent;
import org.powernukkitx.event.player.PlayerTeleportEvent;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.Location;
import org.powernukkitx.level.Position;
import org.powernukkitx.plugin.PluginBase;
import net.zernyth.ffa.arena.model.ArenaDefinition;
import net.zernyth.ffa.arena.model.ArenaStatus;
import net.zernyth.ffa.arena.model.CuboidZone;
import net.zernyth.ffa.arena.model.SpawnPoint;
import net.zernyth.ffa.kit.service.KitApplicationService;
import net.zernyth.ffa.kit.service.KitSelectionService;
import net.zernyth.ffa.messaging.FfaMessageFormatter;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.service.PlayerProfileService;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class ArenaSessionService {

    private final PluginBase plugin;
    private final ArenaCatalogService arenaCatalogService;
    private final KitSelectionService kitSelectionService;
    private final KitApplicationService kitApplicationService;
    private final PlayerProfileService playerProfileService;

    private final ConcurrentHashMap<UUID, String> arenaByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<UUID>> playersByArena = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> respawnProtectionUntil = new ConcurrentHashMap<>();

    private static final long RESPAWN_PROTECTION_MILLIS = 5000L;
    private final java.util.List<java.util.function.Consumer<Player>> joinListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Called on the main thread after a player has entered an arena and got their kit. */
    public void addJoinListener(java.util.function.Consumer<Player> listener) {
        joinListeners.add(listener);
    }

    public ArenaSessionService(
            PluginBase plugin,
            ArenaCatalogService arenaCatalogService,
            KitSelectionService kitSelectionService,
            KitApplicationService kitApplicationService,
            PlayerProfileService playerProfileService
    ) {
        this.plugin = plugin;
        this.arenaCatalogService = arenaCatalogService;
        this.kitSelectionService = kitSelectionService;
        this.kitApplicationService = kitApplicationService;
        this.playerProfileService = playerProfileService;
    }

    public boolean joinArena(Player player, String arenaId) {
        if (!playerProfileService.isLoaded(player.getUniqueId())) {
            // Stats would be lost: kills and deaths are only recorded on a loaded profile.
            player.sendMessage(FfaMessageFormatter.info(t(player,
                    "Your profile is still loading. Try again in a moment.",
                    "Dein Profil wird noch geladen. Versuche es gleich noch einmal.")));
            return false;
        }
        Optional<ArenaDefinition> arenaOptional = arenaCatalogService.findArena(arenaId);
        if (arenaOptional.isEmpty()) {
            return false;
        }

        ArenaDefinition arena = arenaOptional.get();
        if (arena.getStatus() != ArenaStatus.ACTIVE) {
            return false;
        }
        if (arena.getSpawnPoints().isEmpty()) {
            return false;
        }

        Optional<Level> level = ensureLevelLoaded(arena.getWorldName());
        if (level.isEmpty()) {
            return false;
        }

        leaveArena(player, false);

        String normalizedArenaId = arena.getArenaId();
        arenaByPlayer.put(player.getUniqueId(), normalizedArenaId);
        playersByArena.computeIfAbsent(normalizedArenaId, ignored -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());

        SpawnPoint spawnPoint = pickRandomSpawn(arena);
        player.teleport(spawnPoint.toLocation(level.get()), PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.getInventory().clearAll();
        player.setHealthCurrent(player.getHealthMax());
        player.getFoodData().setFood(20);

        String selectedKit = kitSelectionService.resolveKitForPlayer(player.getUniqueId());
        if (selectedKit != null) {
            if (!kitApplicationService.applyKit(player, selectedKit)) {
                player.sendMessage(FfaMessageFormatter.warning(t(player,
                        "Selected kit could not be loaded.",
                        "Ausgewaehltes Kit konnte nicht geladen werden.")));
            }
        }
        for (java.util.function.Consumer<Player> listener : joinListeners) {
            try {
                listener.accept(player);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("An arena join listener failed: " + exception.getMessage());
            }
        }
        return true;
    }

    public void leaveArena(Player player, boolean teleportToLobby) {
        UUID uniqueId = player.getUniqueId();
        String arenaId = arenaByPlayer.remove(uniqueId);
        respawnProtectionUntil.remove(uniqueId);
        if (arenaId != null) {
            Set<UUID> players = playersByArena.get(arenaId);
            if (players != null) {
                players.remove(uniqueId);
                if (players.isEmpty()) {
                    playersByArena.remove(arenaId);
                }
            }
        }

        player.getInventory().clearAll();
        player.getInventory().sendContents(player);

        if (teleportToLobby) {
            Level defaultLevel = plugin.getServer().getDefaultLevel();
            if (defaultLevel != null) {
                Position safeSpawn = defaultLevel.getSafeSpawn();
                player.teleport(safeSpawn.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                player.sendMessage(FfaMessageFormatter.info(t(player,
                        "Returned to lobby.",
                        "Zur Lobby zurueckgebracht.")));
            }
        }
    }

    public void handleRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Optional<ArenaDefinition> arenaOptional = getArenaForPlayer(player.getUniqueId());
        if (arenaOptional.isEmpty()) {
            return;
        }

        ArenaDefinition arena = arenaOptional.get();
        Optional<Level> level = ensureLevelLoaded(arena.getWorldName());
        if (level.isEmpty() || arena.getSpawnPoints().isEmpty()) {
            return;
        }

        SpawnPoint spawnPoint = pickRandomSpawn(arena);
        event.setRespawnPosition(Pair.of(spawnPoint.toLocation(level.get()), Player.SpawnPointType.WORLD));
        respawnProtectionUntil.put(player.getUniqueId(), System.currentTimeMillis() + RESPAWN_PROTECTION_MILLIS);

        plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
            String selectedKit = kitSelectionService.resolveKitForPlayer(player.getUniqueId());
            if (selectedKit != null && player.isOnline()) {
                if (!kitApplicationService.applyKit(player, selectedKit)) {
                    player.sendMessage(FfaMessageFormatter.warning(t(player,
                            "Selected kit could not be loaded after respawn.",
                            "Ausgewaehltes Kit konnte nach Respawn nicht geladen werden.")));
                }
            }
        }, 10);
    }

    public void replenishPlayerAfterKill(Player player) {
        if (!isPlayerInArena(player.getUniqueId())) {
            return;
        }

        player.setHealthCurrent(player.getHealthMax());
        player.getFoodData().setFood(20);

        String selectedKit = kitSelectionService.resolveKitForPlayer(player.getUniqueId());
        if (selectedKit == null) {
            return;
        }
        if (!kitApplicationService.applyKit(player, selectedKit)) {
            player.sendMessage(FfaMessageFormatter.warning(t(player,
                    "Your kit could not be reapplied after this kill.",
                    "Dein Kit konnte nach diesem Kill nicht erneut gesetzt werden.")));
        }
    }

    public Optional<ArenaDefinition> getArenaForPlayer(UUID uniqueId) {
        String arenaId = arenaByPlayer.get(uniqueId);
        if (arenaId == null) {
            return Optional.empty();
        }
        return arenaCatalogService.findArena(arenaId);
    }

    public Optional<String> getArenaIdForPlayer(UUID uniqueId) {
        return Optional.ofNullable(arenaByPlayer.get(uniqueId));
    }

    public boolean isPlayerInArena(UUID uniqueId) {
        return arenaByPlayer.containsKey(uniqueId);
    }

    public boolean areInSameArena(UUID firstPlayer, UUID secondPlayer) {
        String firstArena = arenaByPlayer.get(firstPlayer);
        String secondArena = arenaByPlayer.get(secondPlayer);
        return firstArena != null && firstArena.equals(secondArena);
    }

    public int getArenaPlayerCount(String arenaId) {
        Set<UUID> players = playersByArena.get(arenaId);
        return players == null ? 0 : players.size();
    }

    public Set<UUID> getPlayersInArena(String arenaId) {
        Set<UUID> players = playersByArena.get(arenaId);
        return players == null ? Set.of() : new HashSet<>(players);
    }

    public boolean isInsideSafeZone(Player player) {
        Optional<ArenaDefinition> arena = getArenaForPlayer(player.getUniqueId());
        if (arena.isEmpty()) {
            return false;
        }
        return arena.get().getSafeZone().map(zone -> zone.contains(player.getLocation())).orElse(false);
    }

    public boolean hasRespawnProtection(UUID uniqueId) {
        Long expiresAt = respawnProtectionUntil.get(uniqueId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            respawnProtectionUntil.remove(uniqueId, expiresAt);
            return false;
        }
        return true;
    }

    public int getRespawnProtectionSeconds(UUID uniqueId) {
        Long expiresAt = respawnProtectionUntil.get(uniqueId);
        if (expiresAt == null) {
            return 0;
        }
        long remainingMillis = expiresAt - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            respawnProtectionUntil.remove(uniqueId, expiresAt);
            return 0;
        }
        return (int) Math.ceil(remainingMillis / 1000.0D);
    }

    public void removePlayerSession(UUID uniqueId) {
        String arenaId = arenaByPlayer.remove(uniqueId);
        respawnProtectionUntil.remove(uniqueId);
        if (arenaId == null) {
            return;
        }
        Set<UUID> players = playersByArena.get(arenaId);
        if (players != null) {
            players.remove(uniqueId);
            if (players.isEmpty()) {
                playersByArena.remove(arenaId);
            }
        }
    }

    private SpawnPoint pickRandomSpawn(ArenaDefinition arenaDefinition) {
        int index = ThreadLocalRandom.current().nextInt(arenaDefinition.getSpawnPoints().size());
        return arenaDefinition.getSpawnPoints().get(index);
    }

    private Optional<Level> ensureLevelLoaded(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return Optional.empty();
        }
        if (!plugin.getServer().isLevelLoaded(worldName) && !plugin.getServer().loadLevel(worldName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(plugin.getServer().getLevelByName(worldName));
    }

    private String t(Player player, String english, String german) {
        PlayerLanguage language = playerProfileService.getLanguage(player.getUniqueId());
        return language == PlayerLanguage.GERMAN ? german : english;
    }
}



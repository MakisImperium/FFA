package net.zernyth.ffa.mode;

import net.zernyth.ffa.arena.model.ArenaDefinition;
import net.zernyth.ffa.arena.service.ArenaCatalogService;
import net.zernyth.ffa.arena.service.ArenaSessionService;
import net.zernyth.ffa.messaging.FfaMessageFormatter;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.service.PlayerProfileService;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.player.PlayerQuitEvent;
import org.powernukkitx.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watching an arena in spectator mode. The game mode a player had before comes back when they stop,
 * join an arena, log out, or the server stops.
 */
public final class SpectatorService implements Listener {
    private final ArenaCatalogService catalog;
    private final ArenaSessionService arenas;
    private final PlayerProfileService profiles;
    private final Map<UUID, Integer> previousGamemode = new ConcurrentHashMap<>();

    public SpectatorService(ArenaCatalogService catalog, ArenaSessionService arenas, PlayerProfileService profiles) {
        this.catalog = catalog;
        this.arenas = arenas;
        this.profiles = profiles;
    }

    public boolean isSpectating(UUID player) {
        return previousGamemode.containsKey(player);
    }

    public void start(Player player, String arenaId) {
        if (arenas.isPlayerInArena(player.getUniqueId())) {
            player.sendMessage(FfaMessageFormatter.warning(t(player,
                    "Leave your arena first to spectate.", "Verlasse zuerst deine Arena, um zuzuschauen.")));
            return;
        }
        Optional<ArenaDefinition> arena = catalog.findArena(arenaId);
        Optional<org.powernukkitx.level.Location> spawn = arena.flatMap(a -> arenas.randomSpawnLocation(a.getArenaId()));
        if (spawn.isEmpty()) {
            player.sendMessage(FfaMessageFormatter.warning(t(player,
                    "That arena does not exist or has no spawn yet.", "Diese Arena gibt es nicht oder sie hat noch keinen Spawn.")));
            return;
        }
        previousGamemode.putIfAbsent(player.getUniqueId(), player.getGamemode());
        player.setGamemode(Player.SPECTATOR);
        player.teleport(spawn.get(), PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.sendMessage(FfaMessageFormatter.info(t(player,
                "Spectating " + arena.get().getDisplayName() + ". /ffa spectate to stop.",
                "Du schaust " + arena.get().getDisplayName() + " zu. /ffa spectate zum Beenden.")));
    }

    /** Stops spectating; {@code toLobby} also teleports back. Returns false if they were not spectating. */
    public boolean stop(Player player, boolean toLobby) {
        Integer gamemode = previousGamemode.remove(player.getUniqueId());
        if (gamemode == null) {
            return false;
        }
        player.setGamemode(gamemode);
        if (toLobby) {
            arenas.teleportToLobby(player);
        }
        return true;
    }

    /** Joining an arena ends spectating; the arena puts them in place. */
    public void beforeJoin(Player player) {
        stop(player, false);
    }

    public void restoreAll() {
        for (UUID id : Map.copyOf(previousGamemode).keySet()) {
            Server.getInstance().getPlayer(id).ifPresent(player -> stop(player, false));
        }
        previousGamemode.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer(), false);
    }

    private String t(Player player, String english, String german) {
        return profiles.getLanguage(player.getUniqueId()) == PlayerLanguage.GERMAN ? german : english;
    }
}

package net.zernyth.ffa.listener;

import org.powernukkitx.Player;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.entity.projectile.EntityProjectile;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.entity.EntityDamageByEntityEvent;
import org.powernukkitx.event.entity.EntityDamageEvent;
import org.powernukkitx.event.player.PlayerDeathEvent;
import org.powernukkitx.event.player.PlayerJoinEvent;
import org.powernukkitx.event.player.PlayerQuitEvent;
import org.powernukkitx.event.player.PlayerRespawnEvent;
import org.powernukkitx.item.Item;
import org.powernukkitx.plugin.PluginBase;
import net.zernyth.ffa.arena.service.ArenaSessionService;
import net.zernyth.ffa.combat.KillService;
import net.zernyth.ffa.messaging.FfaMessageFormatter;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.service.PlayerProfileService;
import net.zernyth.ffa.scoreboard.FfaScoreboardService;
import net.zernyth.ffa.ui.form.FfaFormController;

public final class PlayerLifecycleListener implements Listener {

    private final PluginBase plugin;
    private final PlayerProfileService playerProfileService;
    private final ArenaSessionService arenaSessionService;
    private final FfaScoreboardService scoreboardService;
    private final FfaFormController formController;
    private final KillService killService;

    public PlayerLifecycleListener(
            PluginBase plugin,
            PlayerProfileService playerProfileService,
            ArenaSessionService arenaSessionService,
            FfaScoreboardService scoreboardService,
            FfaFormController formController,
            KillService killService
    ) {
        this.killService = killService;
        this.plugin = plugin;
        this.playerProfileService = playerProfileService;
        this.arenaSessionService = arenaSessionService;
        this.scoreboardService = scoreboardService;
        this.formController = formController;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // The profile arrives from storage in the background; the scoreboard depends on its settings.
        playerProfileService.loadProfile(player, profile -> {
            if (player.isOnline()) {
                scoreboardService.handleJoin(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Logging out to escape a lost fight counts as a death; the attacker gets the kill.
        killService.forfeit(player, KillService.Cause.LOGOUT);
        killService.combat().clear(player.getUniqueId());
        arenaSessionService.removePlayerSession(player.getUniqueId());
        formController.clearPlayerState(player.getUniqueId());
        scoreboardService.handleQuit(player);
        playerProfileService.unloadProfile(player, true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!arenaSessionService.isPlayerInArena(victim.getUniqueId())) {
            return;
        }

        event.setKeepInventory(true);
        event.setKeepExperience(true);
        event.setDrops(new Item[0]);

        // Stats, rewards and messages for the killer: all in the kill service.
        killService.onDeath(victim, resolveKiller(victim.getLastDamageCause()));

        victim.sendMessage(FfaMessageFormatter.error(t(victim,
                "You were eliminated. Redeploying...",
                "Du wurdest eliminiert. Neuer Einsatz laeuft...")));
        scoreboardService.refreshPlayer(victim);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        arenaSessionService.handleRespawn(event);
        plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> scoreboardService.refreshPlayer(event.getPlayer()), 10);
        plugin.getServer().getScheduler().scheduleDelayedTask(plugin, () -> {
            if (event.getPlayer().isOnline() && arenaSessionService.isPlayerInArena(event.getPlayer().getUniqueId())) {
                event.getPlayer().sendMessage(FfaMessageFormatter.info(t(event.getPlayer(),
                        "Back in combat. Respawn shield active for 5 seconds.",
                        "Zurueck im Kampf. Respawn-Schutz fuer 5 Sekunden aktiv.")));
            }
        }, 12);
    }

    private Player resolveKiller(EntityDamageEvent lastDamageCause) {
        if (!(lastDamageCause instanceof EntityDamageByEntityEvent damageByEntityEvent)) {
            return null;
        }

        Entity damager = damageByEntityEvent.getDamager();
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof EntityProjectile projectile && projectile.shootingEntity instanceof Player player) {
            return player;
        }
        return null;
    }

    private String t(Player player, String english, String german) {
        PlayerLanguage language = playerProfileService.getLanguage(player.getUniqueId());
        return language == PlayerLanguage.GERMAN ? german : english;
    }
}



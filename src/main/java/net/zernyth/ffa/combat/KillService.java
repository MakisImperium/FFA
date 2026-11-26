package net.zernyth.ffa.combat;

import net.zernyth.ffa.arena.service.ArenaSessionService;
import net.zernyth.ffa.messaging.FfaMessageFormatter;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.service.PlayerProfileService;
import net.zernyth.ffa.scoreboard.FfaScoreboardService;
import org.powernukkitx.Player;
import org.powernukkitx.Server;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The one place a fight ends: a death in the arena, a logout or a leave during combat.
 *
 * <p>Everything that reacts to kills - streak rewards, XP, coins, tasks, achievements,
 * announcements - registers a {@link KillListener} here instead of listening to deaths itself, so
 * all of them see the same kills, with farming already filtered out.
 */
public final class KillService {

    /** How a fight ended for the victim. */
    public enum Cause { DEATH, LOGOUT, LEAVE }

    /**
     * One counted kill. {@code killer} is null when nobody gets credit (a fall with no recent
     * attacker); {@code victimStreakBefore} is the streak the victim just lost.
     */
    public record Kill(Player killer, Player victim, Cause cause, int killerStreak, int victimStreakBefore, String arenaId) {}

    @FunctionalInterface
    public interface KillListener {
        void onKill(Kill kill);
    }

    private final ArenaSessionService arenas;
    private final PlayerProfileService profiles;
    private final FfaScoreboardService scoreboards;
    private final CombatTagService combat;
    private final KillFarmGuard farmGuard;
    private final List<KillListener> listeners = new CopyOnWriteArrayList<>();
    private final List<java.util.function.BiConsumer<Player, Player>> deathListeners = new CopyOnWriteArrayList<>();

    public KillService(ArenaSessionService arenas, PlayerProfileService profiles, FfaScoreboardService scoreboards,
                       CombatTagService combat, KillFarmGuard farmGuard) {
        this.arenas = arenas;
        this.profiles = profiles;
        this.scoreboards = scoreboards;
        this.combat = combat;
        this.farmGuard = farmGuard;
    }

    public void addListener(KillListener listener) {
        listeners.add(listener);
    }

    /**
     * Sees every death in an arena (victim, credited killer or null), also the ones the farm guard
     * does not count - for duels, which end on any death.
     */
    public void addDeathListener(java.util.function.BiConsumer<Player, Player> listener) {
        deathListeners.add(listener);
    }

    public CombatTagService combat() {
        return combat;
    }

    /** A player in an arena died; {@code directKiller} is who dealt the last blow, if a player did. */
    public void onDeath(Player victim, Player directKiller) {
        Player killer = directKiller;
        if (killer == null) {
            // Knocked into the void or off a ledge: the last attacker earned it.
            killer = combat.lastAttacker(victim.getUniqueId()).map(this::onlinePlayer).orElse(null);
        }
        conclude(victim, killer, Cause.DEATH);
    }

    /**
     * A tagged player logs out or leaves the arena: counted as their death, and the last attacker
     * gets the kill. Does nothing for a player who is not in combat.
     */
    public boolean forfeit(Player victim, Cause cause) {
        if (!arenas.isPlayerInArena(victim.getUniqueId()) || !combat.isTagged(victim.getUniqueId())) {
            return false;
        }
        Player killer = combat.lastAttacker(victim.getUniqueId()).map(this::onlinePlayer).orElse(null);
        conclude(victim, killer, cause);
        return true;
    }

    private void conclude(Player victim, Player killer, Cause cause) {
        UUID victimId = victim.getUniqueId();
        combat.clear(victimId);
        String arenaId = arenas.getArenaIdForPlayer(victimId).orElse(null);
        if (killer != null && (killer.getUniqueId().equals(victimId) || !arenas.areInSameArena(killer.getUniqueId(), victimId))) {
            killer = null;
        }
        for (java.util.function.BiConsumer<Player, Player> listener : deathListeners) {
            try {
                listener.accept(victim, killer);
            } catch (RuntimeException failure) {
                Server.getInstance().getLogger().error("FFA: a death listener failed", failure);
            }
        }

        if (killer != null && !farmGuard.counts(killer.getUniqueId(), victimId)) {
            killer.sendMessage(FfaMessageFormatter.warning(t(killer,
                    "This kill does not count: you killed " + victim.getName() + " too often in a short time.",
                    "Dieser Kill zaehlt nicht: Du hast " + victim.getName() + " zu oft in kurzer Zeit getoetet.")));
            arenas.replenishPlayerAfterKill(killer);
            return;
        }

        int victimStreakBefore = profiles.getStatisticsOrDefault(victimId).getCurrentKillstreak();
        profiles.registerDeath(victimId);
        int killerStreak = 0;
        if (killer != null) {
            profiles.registerKill(killer.getUniqueId());
            arenas.replenishPlayerAfterKill(killer);
            killerStreak = profiles.getStatisticsOrDefault(killer.getUniqueId()).getCurrentKillstreak();
            String note = switch (cause) {
                case DEATH -> t(killer, "Elimination confirmed. Loadout refilled. Streak: " + killerStreak + ".",
                        "Eliminierung bestaetigt. Loadout aufgefuellt. Streak: " + killerStreak + ".");
                case LOGOUT -> t(killer, victim.getName() + " logged out mid-fight. The kill is yours. Streak: " + killerStreak + ".",
                        victim.getName() + " hat sich im Kampf ausgeloggt. Der Kill gehoert dir. Streak: " + killerStreak + ".");
                case LEAVE -> t(killer, victim.getName() + " fled the fight. The kill is yours. Streak: " + killerStreak + ".",
                        victim.getName() + " ist aus dem Kampf geflohen. Der Kill gehoert dir. Streak: " + killerStreak + ".");
            };
            killer.sendMessage(FfaMessageFormatter.success(note));
            scoreboards.refreshPlayer(killer);
        }
        if (cause != Cause.DEATH) {
            victim.sendMessage(FfaMessageFormatter.warning(t(victim,
                    "You left during combat. That counts as a death.",
                    "Du hast den Kampf verlassen. Das zaehlt als Tod.")));
        }

        Kill kill = new Kill(killer, victim, cause, killerStreak, victimStreakBefore, arenaId);
        for (KillListener listener : listeners) {
            try {
                listener.onKill(kill);
            } catch (RuntimeException failure) {
                Server.getInstance().getLogger().error("FFA: a kill listener failed", failure);
            }
        }
    }

    private Player onlinePlayer(UUID id) {
        Optional<Player> player = Server.getInstance().getPlayer(id);
        return player.filter(Player::isOnline).orElse(null);
    }

    private String t(Player player, String english, String german) {
        return profiles.getLanguage(player.getUniqueId()) == PlayerLanguage.GERMAN ? german : english;
    }
}

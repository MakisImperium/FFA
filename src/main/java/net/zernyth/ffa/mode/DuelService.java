package net.zernyth.ffa.mode;

import net.zernyth.ffa.arena.service.ArenaSessionService;
import net.zernyth.ffa.messaging.FfaMessageFormatter;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.service.PlayerProfileService;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.player.PlayerQuitEvent;
import org.powernukkitx.utils.TextFormat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 1v1 duels inside an arena: a request (form or {@code /ffa duel accept}, valid 30 s), then the two
 * only fight each other until one dies, leaves or logs out. Wins and losses go into the profile.
 */
public final class DuelService implements Listener {
    public static final long REQUEST_MILLIS = 30_000L;

    private final DuelBook book;
    private final ArenaSessionService arenas;
    private final PlayerProfileService profiles;
    private BiConsumer<Player, Player> requestForm = (target, challenger) -> { };
    private Consumer<Player> onWin = player -> { };

    public DuelService(DuelBook book, ArenaSessionService arenas, PlayerProfileService profiles) {
        this.book = book;
        this.arenas = arenas;
        this.profiles = profiles;
    }

    /** Shows the target the accept/decline form (target, challenger). */
    public void setRequestForm(BiConsumer<Player, Player> requestForm) {
        this.requestForm = requestForm;
    }

    /** Called for the winner after a duel, e.g. to check achievements. */
    public void setOnWin(Consumer<Player> onWin) {
        this.onWin = onWin;
    }

    public boolean hitAllowed(UUID attacker, UUID victim) {
        return book.hitAllowed(attacker, victim);
    }

    public Optional<UUID> partner(UUID player) {
        return book.partner(player);
    }

    public void challenge(Player challenger, Player target) {
        Optional<String> arena = arenas.getArenaIdForPlayer(challenger.getUniqueId());
        if (arena.isEmpty()) {
            challenger.sendMessage(FfaMessageFormatter.warning(t(challenger,
                    "Join an arena first; the duel takes place there.",
                    "Tritt zuerst einer Arena bei; das Duell findet dort statt.")));
            return;
        }
        if (target == null || !target.isOnline()) {
            challenger.sendMessage(FfaMessageFormatter.warning(t(challenger, "That player is not online.", "Dieser Spieler ist nicht online.")));
            return;
        }
        switch (book.request(challenger.getUniqueId(), target.getUniqueId(), arena.get())) {
            case SELF -> challenger.sendMessage(FfaMessageFormatter.warning(t(challenger,
                    "You cannot duel yourself.", "Du kannst dich nicht selbst herausfordern.")));
            case CHALLENGER_BUSY -> challenger.sendMessage(FfaMessageFormatter.warning(t(challenger,
                    "Finish your current duel first.", "Beende zuerst dein laufendes Duell.")));
            case TARGET_BUSY -> challenger.sendMessage(FfaMessageFormatter.warning(t(challenger,
                    target.getName() + " is already in a duel.", target.getName() + " ist schon in einem Duell.")));
            case SENT -> {
                challenger.sendMessage(FfaMessageFormatter.info(t(challenger,
                        "Duel request sent to " + target.getName() + ". It is valid for 30 seconds.",
                        "Duell-Anfrage an " + target.getName() + " gesendet. Sie gilt 30 Sekunden.")));
                target.sendMessage(FfaMessageFormatter.info(t(target,
                        challenger.getName() + " challenges you to a duel. /ffa duel accept or /ffa duel decline",
                        challenger.getName() + " fordert dich zum Duell. /ffa duel accept oder /ffa duel decline")));
                requestForm.accept(target, challenger);
            }
        }
    }

    public void accept(Player target) {
        Optional<DuelBook.Request> request = book.pending(target.getUniqueId());
        if (request.isEmpty()) {
            target.sendMessage(FfaMessageFormatter.warning(t(target,
                    "You have no open duel request (they expire after 30 seconds).",
                    "Du hast keine offene Duell-Anfrage (sie verfallen nach 30 Sekunden).")));
            return;
        }
        Player challenger = online(request.get().challenger());
        String arenaId = request.get().arenaId();
        if (challenger == null || !arenaId.equals(arenas.getArenaIdForPlayer(challenger.getUniqueId()).orElse(null))) {
            book.decline(target.getUniqueId());
            target.sendMessage(FfaMessageFormatter.warning(t(target,
                    "The challenger has left the arena.", "Der Herausforderer hat die Arena verlassen.")));
            return;
        }
        // Into the challenger's arena first: leaving another arena must not end the new duel.
        if (!arenaId.equals(arenas.getArenaIdForPlayer(target.getUniqueId()).orElse(null)) && !arenas.joinArena(target, arenaId)) {
            book.decline(target.getUniqueId());
            target.sendMessage(FfaMessageFormatter.warning(t(target,
                    "You could not join the duel arena.", "Du konntest der Duell-Arena nicht beitreten.")));
            return;
        }
        if (book.accept(target.getUniqueId()).isEmpty()) {
            target.sendMessage(FfaMessageFormatter.warning(t(target, "The duel could not start.", "Das Duell konnte nicht starten.")));
            return;
        }
        for (Player player : List.of(challenger, target)) {
            Player other = player == challenger ? target : challenger;
            player.sendTitle(TextFormat.GOLD + t(player, "Duel!", "Duell!"), TextFormat.WHITE + "vs " + other.getName(), 5, 40, 10);
            player.sendMessage(FfaMessageFormatter.success(t(player,
                    "Duel against " + other.getName() + " started. Only you two can hit each other.",
                    "Duell gegen " + other.getName() + " gestartet. Nur ihr zwei koennt euch treffen.")));
        }
    }

    public void decline(Player target) {
        book.decline(target.getUniqueId()).ifPresentOrElse(request -> {
            target.sendMessage(FfaMessageFormatter.info(t(target, "Duel declined.", "Duell abgelehnt.")));
            Player challenger = online(request.challenger());
            if (challenger != null) {
                challenger.sendMessage(FfaMessageFormatter.info(t(challenger,
                        target.getName() + " declined your duel.", target.getName() + " hat dein Duell abgelehnt.")));
            }
        }, () -> target.sendMessage(FfaMessageFormatter.warning(t(target, "No open duel request.", "Keine offene Duell-Anfrage."))));
    }

    /** Any death in an arena: a duelist who dies loses. */
    public void onDeath(Player victim, Player killer) {
        book.end(victim.getUniqueId()).ifPresent(winner -> finish(winner, victim.getUniqueId(), false));
    }

    /** Leaving the arena (or logging out) gives the duel away. */
    public void onLeave(UUID player, String arenaId) {
        book.end(player).ifPresent(winner -> finish(winner, player, true));
    }

    private void finish(UUID winnerId, UUID loserId, boolean forfeit) {
        profiles.update(winnerId, profile -> profile.getProgress().registerDuel(true));
        profiles.update(loserId, profile -> profile.getProgress().registerDuel(false));
        Player winner = online(winnerId);
        Player loser = online(loserId);
        String loserName = loser != null ? loser.getName() : profiles.getProfile(loserId).map(p -> p.getLastKnownName()).orElse("?");
        if (winner != null) {
            winner.sendTitle(TextFormat.GREEN + t(winner, "Duel won", "Duell gewonnen"), TextFormat.GRAY + "vs " + loserName, 5, 40, 10);
            winner.sendMessage(FfaMessageFormatter.success(forfeit
                    ? t(winner, loserName + " gave up the duel. You win.", loserName + " hat das Duell aufgegeben. Du gewinnst.")
                    : t(winner, "You won the duel against " + loserName + ".", "Du hast das Duell gegen " + loserName + " gewonnen.")));
            onWin.accept(winner);
        }
        if (loser != null && loser.isOnline()) {
            String winnerName = winner != null ? winner.getName() : "?";
            loser.sendMessage(FfaMessageFormatter.warning(t(loser,
                    "You lost the duel against " + winnerName + ".", "Du hast das Duell gegen " + winnerName + " verloren.")));
        }
    }

    public List<String> scoreboardLines(Player viewer, PlayerLanguage language) {
        return book.partner(viewer.getUniqueId())
                .map(this::online)
                .map(other -> List.of(TextFormat.GOLD + (language == PlayerLanguage.GERMAN ? "Duell: " : "Duel: ") + TextFormat.WHITE + other.getName()))
                .orElse(List.of());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        book.forgetRequests(event.getPlayer().getUniqueId());
    }

    private Player online(UUID id) {
        return Server.getInstance().getPlayer(id).filter(Player::isOnline).orElse(null);
    }

    private String t(Player player, String english, String german) {
        return profiles.getLanguage(player.getUniqueId()) == PlayerLanguage.GERMAN ? german : english;
    }
}

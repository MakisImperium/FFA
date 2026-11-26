package net.zernyth.ffa.ui.form;

import net.zernyth.ffa.mode.DuelService;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.form.element.simple.ElementButton;
import org.powernukkitx.form.response.Response;
import org.powernukkitx.form.response.SimpleResponse;
import org.powernukkitx.form.window.SimpleForm;
import org.powernukkitx.utils.TextFormat;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Picking a duel opponent from the arena, and the request a challenged player answers. */
final class DuelMenus extends FormSupport {
    final Map<UUID, List<UUID>> listedOpponents = new ConcurrentHashMap<>();

    DuelMenus(FfaFormController root) {
        super(root);
    }

    @Override
    void clear(UUID uniqueId) {
        listedOpponents.remove(uniqueId);
    }

    private DuelService duels() {
        return root.duelService;
    }

    void openDuelSelect(Player player) {
        var arena = arenaSessionService.getArenaIdForPlayer(player.getUniqueId());
        if (duels() == null || arena.isEmpty()) {
            root.playerMenus.openPlayerMain(player);
            return;
        }
        List<Player> opponents = arenaSessionService.getPlayersInArena(arena.get()).stream()
                .filter(id -> !id.equals(player.getUniqueId()) && duels().partner(id).isEmpty())
                .map(id -> Server.getInstance().getPlayer(id).filter(Player::isOnline).orElse(null))
                .filter(p -> p != null)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        SimpleForm form = new SimpleForm(title(player, "Duel", "Duell"), opponents.isEmpty()
                ? section(player, "Nobody in this arena is free for a duel.", "In dieser Arena ist niemand frei fuer ein Duell.")
                : section(player, "Challenge a player here to a 1v1.", "Fordere hier einen Spieler zum 1v1."));
        for (Player opponent : opponents) {
            form.addElement(new ElementButton(TextFormat.WHITE + opponent.getName()));
        }
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        listedOpponents.put(player.getUniqueId(), opponents.stream().map(Player::getUniqueId).toList());
        form.send(player, FORM_PLAYER_DUEL_SELECT);
    }

    void handleDuelSelect(Player player, Response response) {
        List<UUID> listed = listedOpponents.remove(player.getUniqueId());
        if (!(response instanceof SimpleResponse simple) || listed == null || simple.buttonId() < 0 || simple.buttonId() >= listed.size()) {
            root.playerMenus.openPlayerMain(player);
            return;
        }
        Player target = Server.getInstance().getPlayer(listed.get(simple.buttonId())).filter(Player::isOnline).orElse(null);
        duels().challenge(player, target);
    }

    /** Shown to the challenged player. */
    void sendDuelRequest(Player target, Player challenger) {
        SimpleForm form = new SimpleForm(title(target, "Duel request", "Duell-Anfrage"),
                TextFormat.WHITE + challenger.getName() + TextFormat.GRAY + t(target,
                        " challenges you to a 1v1 duel. Valid for 30 seconds.",
                        " fordert dich zu einem 1v1-Duell. Gilt 30 Sekunden."));
        form.addElement(new ElementButton(TextFormat.GREEN + t(target, "Accept", "Annehmen")));
        form.addElement(new ElementButton(TextFormat.RED + t(target, "Decline", "Ablehnen")));
        form.send(target, FORM_PLAYER_DUEL_REQUEST);
    }

    void handleDuelRequest(Player player, Response response, boolean closed) {
        if (duels() == null) {
            return;
        }
        if (!closed && response instanceof SimpleResponse simple && simple.buttonId() == 0) {
            duels().accept(player);
        } else if (!closed) {
            duels().decline(player);
        }
        // Closing the form leaves the request open for /ffa duel accept.
    }
}

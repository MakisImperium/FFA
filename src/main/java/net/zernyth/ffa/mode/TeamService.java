package net.zernyth.ffa.mode;

import net.zernyth.ffa.arena.model.ArenaDefinition;
import net.zernyth.ffa.arena.service.ArenaSessionService;
import net.zernyth.ffa.combat.KillService;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Team arenas: teams form on joining (with group mates, otherwise evenly), teammates cannot hurt
 * each other, names carry the team colour and team kills show on the scoreboard. Groups come from
 * {@code /ffa team invite}.
 */
public final class TeamService implements KillService.KillListener, Listener {
    public static final long INVITE_MILLIS = 60_000L;

    private final ArenaSessionService arenas;
    private final PlayerProfileService profiles;
    private final PartyBook parties;
    /** Arena id -> player -> team. */
    private final Map<String, Map<UUID, Integer>> teams = new ConcurrentHashMap<>();
    /** Arena id -> team -> kills since the team formed. */
    private final Map<String, Map<Integer, Integer>> teamKills = new ConcurrentHashMap<>();

    public TeamService(ArenaSessionService arenas, PlayerProfileService profiles, PartyBook parties) {
        this.arenas = arenas;
        this.profiles = profiles;
        this.parties = parties;
    }

    public Optional<Integer> teamOf(UUID player) {
        return arenas.getArenaIdForPlayer(player)
                .map(teams::get)
                .map(members -> members.get(player));
    }

    public void onJoin(Player player) {
        Optional<ArenaDefinition> arena = arenas.getArenaForPlayer(player.getUniqueId());
        if (arena.isEmpty() || !arena.get().isTeamMode()) {
            return;
        }
        Map<UUID, Integer> members = teams.computeIfAbsent(arena.get().getArenaId(), id -> new ConcurrentHashMap<>());
        members.remove(player.getUniqueId());
        int team = TeamRules.assign(members, arena.get().getTeamSize(), parties.mates(player.getUniqueId()));
        members.put(player.getUniqueId(), team);
        player.setNameTag(TeamRules.color(team) + player.getName());
        boolean german = german(player);
        String mates = members.entrySet().stream()
                .filter(e -> e.getValue() == team && !e.getKey().equals(player.getUniqueId()))
                .map(e -> Server.getInstance().getPlayer(e.getKey()).map(Player::getName).orElse("?"))
                .collect(Collectors.joining(", "));
        player.sendMessage(FfaMessageFormatter.info((german ? "Du bist in Team " : "You are on team ")
                + TeamRules.color(team) + TeamRules.name(team, german) + TextFormat.RESET
                + (mates.isEmpty() ? "" : (german ? " mit " : " with ") + mates) + "."));
    }

    public void onLeave(UUID player, String arenaId) {
        Map<UUID, Integer> members = teams.get(arenaId);
        if (members == null) {
            return;
        }
        Integer team = members.remove(player);
        if (team != null && !members.containsValue(team)) {
            Map<Integer, Integer> kills = teamKills.get(arenaId);
            if (kills != null) {
                kills.remove(team);
            }
        }
        if (members.isEmpty()) {
            teams.remove(arenaId);
            teamKills.remove(arenaId);
        }
        Server.getInstance().getPlayer(player).ifPresent(p -> p.setNameTag(p.getName()));
    }

    /** No friendly fire. */
    public boolean hitAllowed(UUID attacker, UUID victim) {
        Optional<Integer> attackerTeam = teamOf(attacker);
        return attackerTeam.isEmpty() || !attackerTeam.equals(teamOf(victim));
    }

    @Override
    public void onKill(KillService.Kill kill) {
        if (kill.killer() == null || kill.arenaId() == null) {
            return;
        }
        teamOf(kill.killer().getUniqueId()).ifPresent(team ->
                teamKills.computeIfAbsent(kill.arenaId(), id -> new ConcurrentHashMap<>()).merge(team, 1, Integer::sum));
    }

    public List<String> scoreboardLines(Player viewer, PlayerLanguage language) {
        Optional<String> arena = arenas.getArenaIdForPlayer(viewer.getUniqueId());
        Optional<Integer> team = teamOf(viewer.getUniqueId());
        if (arena.isEmpty() || team.isEmpty()) {
            return List.of();
        }
        boolean german = language == PlayerLanguage.GERMAN;
        int kills = teamKills.getOrDefault(arena.get(), Map.of()).getOrDefault(team.get(), 0);
        return List.of(TextFormat.GRAY + "Team: " + TeamRules.color(team.get()) + TeamRules.name(team.get(), german),
                TextFormat.GRAY + (german ? "Team-Kills: " : "Team kills: ") + TextFormat.WHITE + kills);
    }

    // ----- groups

    public void invite(Player inviter, Player invitee) {
        if (invitee == null || !invitee.isOnline()) {
            inviter.sendMessage(FfaMessageFormatter.warning(t(inviter, "That player is not online.", "Dieser Spieler ist nicht online.")));
            return;
        }
        switch (parties.invite(inviter.getUniqueId(), invitee.getUniqueId())) {
            case SELF -> inviter.sendMessage(FfaMessageFormatter.warning(t(inviter, "You cannot invite yourself.", "Du kannst dich nicht selbst einladen.")));
            case ALREADY_TOGETHER -> inviter.sendMessage(FfaMessageFormatter.info(t(inviter,
                    invitee.getName() + " is already in your group.", invitee.getName() + " ist schon in deiner Gruppe.")));
            case IN_OTHER_GROUP -> inviter.sendMessage(FfaMessageFormatter.warning(t(inviter,
                    invitee.getName() + " is in another group.", invitee.getName() + " ist in einer anderen Gruppe.")));
            case FULL -> inviter.sendMessage(FfaMessageFormatter.warning(t(inviter, "Your group is full.", "Deine Gruppe ist voll.")));
            case SENT -> {
                inviter.sendMessage(FfaMessageFormatter.info(t(inviter,
                        "Invited " + invitee.getName() + " to your group.", invitee.getName() + " in deine Gruppe eingeladen.")));
                invitee.sendMessage(FfaMessageFormatter.info(t(invitee,
                        inviter.getName() + " invites you to a group for team arenas. /ffa team accept",
                        inviter.getName() + " laedt dich in eine Gruppe fuer Team-Arenen ein. /ffa team accept")));
            }
        }
    }

    public void accept(Player invitee) {
        parties.accept(invitee.getUniqueId()).ifPresentOrElse(group -> {
            for (UUID member : group) {
                Server.getInstance().getPlayer(member).filter(Player::isOnline).ifPresent(p -> p.sendMessage(FfaMessageFormatter.success(
                        p == invitee ? t(p, "You joined the group.", "Du bist der Gruppe beigetreten.")
                                : t(p, invitee.getName() + " joined your group.", invitee.getName() + " ist deiner Gruppe beigetreten."))));
            }
        }, () -> invitee.sendMessage(FfaMessageFormatter.warning(t(invitee,
                "No open group invite (or the group is full).", "Keine offene Gruppen-Einladung (oder die Gruppe ist voll)."))));
    }

    public void leaveGroup(Player player) {
        Set<UUID> rest = parties.leave(player.getUniqueId());
        if (rest.isEmpty()) {
            player.sendMessage(FfaMessageFormatter.warning(t(player, "You are not in a group.", "Du bist in keiner Gruppe.")));
            return;
        }
        player.sendMessage(FfaMessageFormatter.info(t(player, "You left the group.", "Du hast die Gruppe verlassen.")));
        notifyRest(rest, player.getName());
    }

    public void listGroup(Player player) {
        Set<UUID> mates = parties.mates(player.getUniqueId());
        if (mates.isEmpty()) {
            player.sendMessage(FfaMessageFormatter.info(t(player,
                    "You are not in a group. /ffa team invite <player>", "Du bist in keiner Gruppe. /ffa team invite <Spieler>")));
            return;
        }
        String names = mates.stream()
                .map(id -> Server.getInstance().getPlayer(id).map(Player::getName)
                        .orElseGet(() -> profiles.getProfile(id).map(p -> p.getLastKnownName()).orElse("?")))
                .collect(Collectors.joining(", "));
        player.sendMessage(FfaMessageFormatter.info(t(player, "Your group: ", "Deine Gruppe: ") + names));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Set<UUID> rest = parties.leave(event.getPlayer().getUniqueId());
        notifyRest(rest, event.getPlayer().getName());
    }

    private void notifyRest(Set<UUID> rest, String name) {
        for (UUID member : rest) {
            Server.getInstance().getPlayer(member).filter(Player::isOnline).ifPresent(p -> p.sendMessage(FfaMessageFormatter.info(
                    t(p, name + " left your group.", name + " hat deine Gruppe verlassen."))));
        }
    }

    private boolean german(Player player) {
        return profiles.getLanguage(player.getUniqueId()) == PlayerLanguage.GERMAN;
    }

    private String t(Player player, String english, String german) {
        return german(player) ? german : english;
    }
}

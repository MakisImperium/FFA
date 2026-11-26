package net.zernyth.ffa.mode;

import com.google.gson.Gson;
import net.zernyth.ffa.arena.model.ArenaDefinition;
import net.zernyth.ffa.command.Subcommands;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModesTest {
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    @Test
    void duelRequestsExpireAndStartOnce() {
        DuelBook book = new DuelBook(30_000, now::get);
        assertEquals(DuelBook.RequestResult.SELF, book.request(a, a, "x"));
        assertEquals(DuelBook.RequestResult.SENT, book.request(a, b, "x"));
        now.addAndGet(31_000);
        assertTrue(book.accept(b).isEmpty(), "expired after 30 s");

        book.request(a, b, "x");
        book.request(c, a, "x"); // a has an incoming request too
        assertEquals("x", book.accept(b).orElseThrow().arenaId());
        assertEquals(b, book.partner(a).orElseThrow());
        assertTrue(book.pending(a).isEmpty(), "requests to a duelist are void");
        assertEquals(DuelBook.RequestResult.TARGET_BUSY, book.request(c, a, "x"));
        assertEquals(DuelBook.RequestResult.CHALLENGER_BUSY, book.request(b, c, "x"));
        assertTrue(book.accept(b).isEmpty(), "nothing left to accept");
    }

    @Test
    void duelistsOnlyHitEachOther() {
        DuelBook book = new DuelBook(30_000, now::get);
        book.request(a, b, "x");
        book.accept(b);
        assertTrue(book.hitAllowed(a, b));
        assertTrue(book.hitAllowed(b, a));
        assertFalse(book.hitAllowed(a, c), "a duelist cannot hit others");
        assertFalse(book.hitAllowed(c, b), "others cannot hit a duelist");
        assertTrue(book.hitAllowed(c, d));

        assertEquals(a, book.end(b).orElseThrow(), "b lost, a wins");
        assertTrue(book.partner(a).isEmpty());
        assertTrue(book.end(a).isEmpty(), "ends once");
        assertTrue(book.hitAllowed(a, c));
    }

    @Test
    void declineAndForget() {
        DuelBook book = new DuelBook(30_000, now::get);
        book.request(a, b, "x");
        assertEquals(a, book.decline(b).orElseThrow().challenger());
        book.request(a, b, "x");
        book.forgetRequests(a);
        assertTrue(book.pending(b).isEmpty(), "the challenger logged out");
    }

    @Test
    void groupsFormAndDissolve() {
        PartyBook parties = new PartyBook(3, 60_000, now::get);
        assertEquals(PartyBook.InviteResult.SENT, parties.invite(a, b));
        assertEquals(Set.of(a, b), parties.accept(b).orElseThrow());
        assertEquals(PartyBook.InviteResult.ALREADY_TOGETHER, parties.invite(a, b));
        assertEquals(PartyBook.InviteResult.IN_OTHER_GROUP, parties.invite(c, a));
        parties.invite(b, c);
        parties.accept(c);
        assertEquals(PartyBook.InviteResult.FULL, parties.invite(a, d));
        assertEquals(Set.of(b, c), parties.mates(a));

        assertEquals(Set.of(b, c), parties.leave(a));
        assertEquals(Set.of(c), parties.mates(b));
        parties.leave(b);
        assertTrue(parties.mates(c).isEmpty(), "a group of one dissolves");
    }

    @Test
    void groupInvitesExpire() {
        PartyBook parties = new PartyBook(3, 60_000, now::get);
        parties.invite(a, b);
        now.addAndGet(61_000);
        assertTrue(parties.accept(b).isEmpty());
        assertTrue(parties.pendingInviter(b).isEmpty());
    }

    @Test
    void teamsStayEvenAndFull() {
        Map<UUID, Integer> teamOf = new HashMap<>();
        List<UUID> players = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            UUID id = UUID.randomUUID();
            teamOf.put(id, TeamRules.assign(teamOf, 2, Set.of()));
            players.add(id);
        }
        assertEquals(1, (int) teamOf.get(players.get(1)), "the second player is an opponent, not a mate");
        Map<Integer, Integer> sizes = new HashMap<>();
        teamOf.values().forEach(team -> sizes.merge(team, 1, Integer::sum));
        assertTrue(sizes.values().stream().allMatch(size -> size <= 2), "no team over its size: " + sizes);
        assertEquals(4, sizes.size(), "7 players in teams of 2 need 4 teams");
    }

    @Test
    void groupMatesPlayTogether() {
        Map<UUID, Integer> teamOf = new HashMap<>();
        teamOf.put(a, 0);
        teamOf.put(b, 1);
        assertEquals(1, TeamRules.assign(teamOf, 2, Set.of(b)), "joins the mate's team");
        teamOf.put(c, 1);
        assertEquals(0, TeamRules.assign(teamOf, 2, Set.of(b)), "the mate's team is full");
    }

    @Test
    void arenaModeFromJson() {
        ArenaDefinition plain = new Gson().fromJson("{\"arenaId\":\"x\"}", ArenaDefinition.class);
        assertFalse(plain.isTeamMode(), "old arenas stay free for all");
        ArenaDefinition team = new Gson().fromJson("{\"arenaId\":\"x\",\"mode\":\"TEAM\",\"teamSize\":9}", ArenaDefinition.class);
        assertTrue(team.isTeamMode());
        assertEquals(4, team.getTeamSize());
        team.setMode(false, 3);
        assertFalse(team.isTeamMode());
    }

    @Test
    void subcommandsDispatchTheRest() {
        Subcommands subcommands = new Subcommands();
        List<String> seen = new ArrayList<>();
        subcommands.add("duel", "duel <player>", (player, args) -> seen.add(String.join(",", args)));
        assertTrue(subcommands.dispatch(null, new String[]{"DUEL", "Alex"}));
        assertFalse(subcommands.dispatch(null, new String[]{"nope"}));
        assertFalse(subcommands.dispatch(null, new String[0]));
        assertEquals(List.of("Alex"), seen);
        assertTrue(subcommands.usage("ffa").contains("/ffa duel <player>"));
    }
}

package net.zernyth.ffa.combat;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Combat tags and the farming guard, with a clock the test moves by hand. */
class FairnessTest {
    final AtomicLong now = new AtomicLong(1_000_000);
    final UUID alex = UUID.randomUUID();
    final UUID robin = UUID.randomUUID();
    final UUID kai = UUID.randomUUID();

    @Test
    void aHitTagsBothPlayersForTheConfiguredTime() {
        CombatTagService combat = new CombatTagService(10_000, now::get);
        combat.tag(alex, robin);
        assertTrue(combat.isTagged(alex));
        assertTrue(combat.isTagged(robin));
        assertEquals(Optional.of(alex), combat.lastAttacker(robin));
        assertEquals(Optional.empty(), combat.lastAttacker(alex), "the attacker was not hit by anyone");
        assertEquals(10, combat.remainingSeconds(robin));

        now.addAndGet(9_001);
        assertEquals(1, combat.remainingSeconds(robin), "rounded up");
        now.addAndGet(1_000);
        assertFalse(combat.isTagged(robin));
        assertEquals(Optional.empty(), combat.lastAttacker(robin), "an old fight earns nobody a kill");
    }

    @Test
    void theLastAttackerWinsAndHittingBackKeepsTheOwnAttacker() {
        CombatTagService combat = new CombatTagService(10_000, now::get);
        combat.tag(alex, robin);
        combat.tag(kai, robin);
        assertEquals(Optional.of(kai), combat.lastAttacker(robin));
        combat.tag(robin, alex); // Robin hits back at Alex
        assertEquals(Optional.of(kai), combat.lastAttacker(robin), "hitting someone does not change who hit you");
        assertEquals(Optional.of(robin), combat.lastAttacker(alex));
    }

    @Test
    void clearingAndSwitchingOffWork() {
        CombatTagService combat = new CombatTagService(10_000, now::get);
        combat.tag(alex, robin);
        combat.clear(robin);
        assertFalse(combat.isTagged(robin));
        combat.setTagMillis(0);
        combat.tag(alex, kai);
        assertFalse(combat.isTagged(kai), "0 seconds switches tagging off");
        combat.setTagMillis(10_000);
        combat.tag(alex, alex);
        assertFalse(combat.isTagged(alex) && combat.lastAttacker(alex).isPresent(), "hitting yourself starts nothing");
    }

    @Test
    void theSameVictimStopsCountingAfterTheLimit() {
        KillFarmGuard guard = new KillFarmGuard(3, 600_000, now::get);
        assertTrue(guard.counts(alex, robin));
        assertTrue(guard.counts(alex, robin));
        assertTrue(guard.counts(alex, robin));
        assertFalse(guard.counts(alex, robin), "the fourth kill in ten minutes");
        assertTrue(guard.counts(alex, kai), "other victims are not affected");
        assertTrue(guard.counts(robin, alex), "the other direction is its own pair");

        now.addAndGet(600_001);
        assertTrue(guard.counts(alex, robin), "after the window it counts again");
    }

    @Test
    void pruningKeepsRecentPairsSoRelogsDoNotReset() {
        KillFarmGuard guard = new KillFarmGuard(1, 600_000, now::get);
        assertTrue(guard.counts(alex, robin));
        guard.prune();
        assertFalse(guard.counts(alex, robin), "a recent pair survives pruning");
        now.addAndGet(600_001);
        guard.prune();
        assertTrue(guard.counts(alex, robin));
    }

    @Test
    void zeroSwitchesTheGuardOff() {
        KillFarmGuard guard = new KillFarmGuard(0, 600_000, now::get);
        for (int i = 0; i < 20; i++) {
            assertTrue(guard.counts(alex, robin));
        }
    }
}

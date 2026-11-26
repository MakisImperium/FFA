package net.zernyth.ffa.economy;

import com.google.gson.Gson;
import net.zernyth.ffa.player.model.PlayerProgress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyTest {
    private final EconomyConfiguration config = new EconomyConfiguration();

    private Cosmetic cosmetic(String id) {
        return config.find(id).orElseThrow();
    }

    @Test
    void coinsGrowWithStreakAndShutdown() {
        assertEquals(5, config.coinsFor(0, 0, 5, 1.0));
        assertEquals(8, config.coinsFor(3, 0, 5, 1.0));
        assertEquals(15, config.coinsFor(50, 0, 5, 1.0), "the streak bonus is capped at 10 steps");
        assertEquals(18, config.coinsFor(3, 5, 5, 1.0), "breaking a streak of 5 is a shutdown");
        assertEquals(8, config.coinsFor(3, 5, 0, 1.0), "shutdownFrom 0 turns the bonus off");
        assertEquals(16, config.coinsFor(3, 0, 5, 2.0), "event multiplier");
        assertEquals(0, config.coinsFor(3, 0, 5, -1.0));
    }

    @Test
    void buyingPaysOnceAndEquips() {
        PlayerProgress progress = new PlayerProgress();
        Cosmetic flames = cosmetic("flames");
        assertEquals(Shop.Result.TOO_EXPENSIVE, Shop.buy(progress, flames));
        assertEquals(0, progress.getCoins());

        progress.addCoins(1000);
        assertEquals(Shop.Result.BOUGHT, Shop.buy(progress, flames));
        assertEquals(700, progress.getCoins());
        assertEquals("flames", progress.getEquippedCosmetics().get("kill_effect"));
        assertEquals(Shop.Result.ALREADY_OWNED, Shop.buy(progress, flames));
        assertEquals(700, progress.getCoins(), "no second payment");
    }

    @Test
    void oneCosmeticPerSlotAndToggling() {
        PlayerProgress progress = new PlayerProgress();
        progress.addCoins(10_000);
        Shop.buy(progress, cosmetic("flames"));
        Shop.buy(progress, cosmetic("hearts"));
        Shop.buy(progress, cosmetic("wrecked"));
        assertEquals("hearts", progress.getEquippedCosmetics().get("kill_effect"), "the newer buy replaces the worn one");
        assertEquals("wrecked", progress.getEquippedCosmetics().get("death_message"), "other slots stay");

        assertEquals(Shop.Result.EQUIPPED, Shop.toggle(progress, cosmetic("flames")));
        assertEquals(Shop.Result.UNEQUIPPED, Shop.toggle(progress, cosmetic("flames")));
        assertFalse(progress.getEquippedCosmetics().containsKey("kill_effect"));
        assertEquals(Shop.Result.NOT_OWNED, Shop.toggle(progress, cosmetic("challenger")));
        assertTrue(Shop.equipped(progress, Cosmetic.Slot.DEATH_MESSAGE, config).isPresent());
    }

    @Test
    void removedCosmeticsAreNoLongerWorn() {
        PlayerProgress progress = new PlayerProgress();
        progress.getOwnedCosmetics().add("gone");
        progress.getEquippedCosmetics().put("kill_effect", "gone");
        assertTrue(Shop.equipped(progress, Cosmetic.Slot.KILL_EFFECT, config).isEmpty());
    }

    @Test
    void configSkipsBrokenEntries() {
        EconomyConfiguration loaded = new Gson().fromJson("""
                {"coinsPerKill":-3,"cosmetics":[
                  {"id":"ok","slot":"kill_effect","price":10,"effect":"Flame"},
                  {"id":"ok","slot":"kill_effect","price":99,"effect":"hearts"},
                  {"id":"bad_effect","slot":"kill_effect","effect":"rainbow"},
                  {"id":"bad_slot","slot":"hat","effect":"flame"},
                  {"id":"no_victim","slot":"death_message","text":"{killer} won"},
                  {"id":"join","slot":"join_message","price":-5,"text":"{player} is here"}
                ]}""", EconomyConfiguration.class);
        assertEquals(0, loaded.getCoinsPerKill());
        assertEquals(java.util.List.of("ok", "join"), loaded.getCosmetics().stream().map(Cosmetic::getId).toList());
        assertEquals(10, loaded.find("ok").orElseThrow().getPrice(), "the first entry with an id wins");
        assertEquals(0, loaded.find("join").orElseThrow().getPrice());
        assertEquals(10, new EconomyConfiguration().getCosmetics().size(), "all defaults are valid");
    }

    @Test
    void messagesStripColoursFromNames() {
        assertEquals("§cBob §7wrecked §cAmy", Shop.fill("§c{killer} §7wrecked §c{victim}", "§kBob", "Amy", null));
        assertEquals(" joined", Shop.fill("{player} joined", null, null, null));
    }
}

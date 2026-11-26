package net.zernyth.ffa.economy;

import net.zernyth.ffa.player.model.PlayerProgress;

import java.util.Locale;

/** Buying and wearing cosmetics. Plain rules on a player's progress; tested without a server. */
public final class Shop {
    public enum Result { BOUGHT, EQUIPPED, UNEQUIPPED, ALREADY_OWNED, NOT_OWNED, TOO_EXPENSIVE }

    private Shop() {
    }

    public static String slotKey(Cosmetic.Slot slot) {
        return slot.name().toLowerCase(Locale.ROOT);
    }

    /** Pays and adds it to the collection; it is equipped right away, which is what a buyer wants. */
    public static Result buy(PlayerProgress progress, Cosmetic cosmetic) {
        if (progress.getOwnedCosmetics().contains(cosmetic.getId())) {
            return Result.ALREADY_OWNED;
        }
        if (progress.getCoins() < cosmetic.getPrice()) {
            return Result.TOO_EXPENSIVE;
        }
        progress.addCoins(-cosmetic.getPrice());
        progress.getOwnedCosmetics().add(cosmetic.getId());
        progress.getEquippedCosmetics().put(slotKey(cosmetic.getSlot()), cosmetic.getId());
        return Result.BOUGHT;
    }

    /** Wears an owned cosmetic, or takes it off when it is already worn. */
    public static Result toggle(PlayerProgress progress, Cosmetic cosmetic) {
        if (!progress.getOwnedCosmetics().contains(cosmetic.getId())) {
            return Result.NOT_OWNED;
        }
        String slot = slotKey(cosmetic.getSlot());
        if (cosmetic.getId().equals(progress.getEquippedCosmetics().get(slot))) {
            progress.getEquippedCosmetics().remove(slot);
            return Result.UNEQUIPPED;
        }
        progress.getEquippedCosmetics().put(slot, cosmetic.getId());
        return Result.EQUIPPED;
    }

    /** The equipped cosmetic of a slot, if it still exists in the shop. */
    public static java.util.Optional<Cosmetic> equipped(PlayerProgress progress, Cosmetic.Slot slot, EconomyConfiguration config) {
        String id = progress.getEquippedCosmetics().get(slotKey(slot));
        return id == null ? java.util.Optional.empty() : config.find(id).filter(c -> c.getSlot() == slot);
    }

    /** Fills a message template; names keep their colour codes out, so nobody styles the text with their name. */
    public static String fill(String template, String killer, String victim, String player) {
        return template
                .replace("{killer}", plain(killer))
                .replace("{victim}", plain(victim))
                .replace("{player}", plain(player));
    }

    private static String plain(String name) {
        return name == null ? "" : name.replaceAll("§.", "");
    }
}

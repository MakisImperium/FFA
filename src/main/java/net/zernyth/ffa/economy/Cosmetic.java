package net.zernyth.ffa.economy;

import java.util.List;
import java.util.Locale;

/**
 * Something cosmetic to buy with FFA coins:
 * <pre>{"id":"flames","slot":"kill_effect","price":300,"effect":"flame","nameEn":"Flames","nameDe":"Flammen"}
 * {"id":"rekt","slot":"death_message","price":150,"text":"{killer} wrecked {victim}","nameEn":"Wrecked","nameDe":"Zerlegt"}
 * {"id":"entrance","slot":"join_message","price":100,"text":"{player} enters the arena!","nameEn":"Grand entrance"}</pre>
 * Kill effects: flame, hearts, explosion, portal, lava, enchant, critical, happy, smoke, red, gold, aqua.
 */
public final class Cosmetic {
    public enum Slot { KILL_EFFECT, DEATH_MESSAGE, JOIN_MESSAGE }

    public static final List<String> EFFECTS = List.of(
            "flame", "hearts", "explosion", "portal", "lava", "enchant", "critical", "happy", "smoke", "red", "gold", "aqua");

    private String id;
    private String slot;
    private int price;
    private String effect;
    private String text;
    private String nameEn;
    private String nameDe;

    public Cosmetic() {
    }

    private static Cosmetic of(String id, Slot slot, int price, String effect, String text, String nameEn, String nameDe) {
        Cosmetic cosmetic = new Cosmetic();
        cosmetic.id = id;
        cosmetic.slot = slot.name().toLowerCase(Locale.ROOT);
        cosmetic.price = price;
        cosmetic.effect = effect;
        cosmetic.text = text;
        cosmetic.nameEn = nameEn;
        cosmetic.nameDe = nameDe;
        return cosmetic;
    }

    public static List<Cosmetic> defaults() {
        return List.of(
                of("flames", Slot.KILL_EFFECT, 300, "flame", null, "Flames", "Flammen"),
                of("hearts", Slot.KILL_EFFECT, 250, "hearts", null, "Hearts", "Herzen"),
                of("blast", Slot.KILL_EFFECT, 600, "explosion", null, "Blast", "Explosion"),
                of("void", Slot.KILL_EFFECT, 450, "portal", null, "Void swirl", "Leerenwirbel"),
                of("gold_dust", Slot.KILL_EFFECT, 800, "gold", null, "Gold dust", "Goldstaub"),
                of("wrecked", Slot.DEATH_MESSAGE, 200, null, "§c{killer} §7wrecked §c{victim}", "Wrecked", "Zerlegt"),
                of("outplayed", Slot.DEATH_MESSAGE, 200, null, "§6{killer} §7outplayed §6{victim}", "Outplayed", "Ausgespielt"),
                of("deleted", Slot.DEATH_MESSAGE, 350, null, "§d{victim} §7was deleted by §d{killer}", "Deleted", "Geloescht"),
                of("entrance", Slot.JOIN_MESSAGE, 150, null, "§e{player} §7enters the arena!", "Grand entrance", "Grosser Auftritt"),
                of("challenger", Slot.JOIN_MESSAGE, 300, null, "§cA challenger appears: §f{player}", "Challenger", "Herausforderer"));
    }

    public String getId() {
        return id;
    }

    /** The slot, or null for an unknown one (the entry is then not offered). */
    public Slot getSlot() {
        try {
            return slot == null ? null : Slot.valueOf(slot.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    public int getPrice() {
        return Math.max(0, price);
    }

    public String getEffect() {
        return effect == null ? null : effect.trim().toLowerCase(Locale.ROOT);
    }

    public String getText() {
        return text;
    }

    public String name(boolean german) {
        String chosen = german ? nameDe : nameEn;
        return chosen == null || chosen.isBlank() ? (nameEn == null ? id : nameEn) : chosen;
    }

    /** Usable: an id, a known slot and what the slot needs. */
    public boolean valid() {
        Slot s = getSlot();
        if (id == null || id.isBlank() || s == null) {
            return false;
        }
        return switch (s) {
            case KILL_EFFECT -> EFFECTS.contains(getEffect());
            case DEATH_MESSAGE -> text != null && text.contains("{killer}") && text.contains("{victim}");
            case JOIN_MESSAGE -> text != null && text.contains("{player}");
        };
    }
}

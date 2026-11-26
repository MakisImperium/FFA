package net.zernyth.ffa.progression;

import java.util.Comparator;
import java.util.List;

/** A named level band, e.g. "Veteran" from level 10, shown in the scoreboard and before chat names. */
public final class Rank {
    private int fromLevel;
    private String name;
    /** A Minecraft colour code such as "§b". */
    private String color;

    public Rank() {
    }

    public Rank(int fromLevel, String name, String color) {
        this.fromLevel = fromLevel;
        this.name = name;
        this.color = color;
    }

    public static List<Rank> defaults() {
        return List.of(
                new Rank(1, "Rookie", "§7"),
                new Rank(5, "Fighter", "§a"),
                new Rank(10, "Veteran", "§b"),
                new Rank(20, "Elite", "§9"),
                new Rank(30, "Master", "§5"),
                new Rank(50, "Legend", "§6"));
    }

    /** The highest rank this level has reached. */
    public static Rank forLevel(int level, List<Rank> ranks) {
        return ranks.stream()
                .filter(rank -> rank.getFromLevel() <= level)
                .max(Comparator.comparingInt(Rank::getFromLevel))
                .orElse(new Rank(1, "Rookie", "§7"));
    }

    public int getFromLevel() {
        return Math.max(1, fromLevel);
    }

    public String getName() {
        return name == null || name.isBlank() ? "Rank" : name;
    }

    public String getColor() {
        return color == null || !color.matches("§[0-9a-fk-or]") ? "§7" : color;
    }

    public String display() {
        return getColor() + getName();
    }
}

package net.zernyth.ffa.mode;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Which team a player joins in a team arena. */
public final class TeamRules {
    public static final String[] NAMES_EN = {"Red", "Blue", "Green", "Yellow", "Aqua", "Purple", "Orange", "Gray"};
    public static final String[] NAMES_DE = {"Rot", "Blau", "Gruen", "Gelb", "Tuerkis", "Lila", "Orange", "Grau"};
    /** Colour codes matching the names. */
    public static final String[] COLORS = {"§c", "§9", "§a", "§e", "§b", "§d", "§6", "§7"};

    private TeamRules() {
    }

    /**
     * With a group mate in a team that has room, join them. Otherwise keep the teams even: there are
     * as many teams as the players need (at least two, so nobody starts alone with nobody to fight),
     * and the newcomer goes to the smallest one.
     *
     * @param teamOf  the team of every player already in the arena
     * @param mates   the newcomer's group mates (in the arena or not)
     */
    public static int assign(Map<UUID, Integer> teamOf, int teamSize, Collection<UUID> mates) {
        int size = Math.max(1, teamSize);
        for (UUID mate : mates) {
            Integer team = teamOf.get(mate);
            if (team != null && count(teamOf, team) < size) {
                return team;
            }
        }
        int teams = Math.max(2, (teamOf.size() + 1 + size - 1) / size);
        int best = 0;
        int bestCount = Integer.MAX_VALUE;
        for (int team = 0; team < teams; team++) {
            int count = count(teamOf, team);
            if (count < size && count < bestCount) {
                best = team;
                bestCount = count;
            }
        }
        return best;
    }

    public static String name(int team, boolean german) {
        String[] names = german ? NAMES_DE : NAMES_EN;
        return team < names.length ? names[team] : (german ? "Team " : "Team ") + (team + 1);
    }

    public static String color(int team) {
        return COLORS[Math.floorMod(team, COLORS.length)];
    }

    private static int count(Map<UUID, Integer> teamOf, int team) {
        int count = 0;
        for (int value : teamOf.values()) {
            if (value == team) {
                count++;
            }
        }
        return count;
    }
}

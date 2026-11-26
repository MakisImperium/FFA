package net.zernyth.ffa.progression;

/**
 * Levels from XP. Level 1 starts at 0 XP; reaching level 2 takes {@code firstLevelXp}; each further
 * level takes {@code growth} times as much as the one before, rounded. Capped at {@code maxLevel}.
 */
public record LevelCurve(int firstLevelXp, double growth, int maxLevel) {

    /** XP needed to go from {@code level} to the next. */
    public long xpForNext(int level) {
        return Math.round(firstLevelXp * Math.pow(growth, Math.max(0, level - 1)));
    }

    /** Total XP at which {@code level} begins. */
    public long xpAtLevel(int level) {
        long total = 0;
        for (int l = 1; l < level; l++) {
            total += xpForNext(l);
        }
        return total;
    }

    public int levelFor(long xp) {
        int level = 1;
        long needed = xpForNext(level);
        long left = xp;
        while (level < maxLevel && left >= needed) {
            left -= needed;
            level++;
            needed = xpForNext(level);
        }
        return level;
    }

    /** XP gathered inside the current level, and what the level needs; for a progress bar. */
    public long[] progressInLevel(long xp) {
        int level = levelFor(xp);
        if (level >= maxLevel) {
            return new long[]{0, 0};
        }
        return new long[]{xp - xpAtLevel(level), xpForNext(level)};
    }
}

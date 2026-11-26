package net.zernyth.ffa.streak;

/**
 * One reward for reaching a streak, as written in config.json or an arena file:
 * <pre>{"type":"effect","id":"speed","amplifier":0,"seconds":10}
 * {"type":"item","id":"minecraft:golden_apple","count":2}
 * {"type":"heal"}   {"type":"repair"}   {"type":"feed"}</pre>
 */
public final class StreakReward {
    public enum Type { EFFECT, ITEM, HEAL, REPAIR, FEED }

    private String type;
    private String id;
    private int amplifier;
    private int seconds = 10;
    private int count = 1;

    public StreakReward() {
    }

    public static StreakReward effect(String id, int amplifier, int seconds) {
        StreakReward reward = new StreakReward();
        reward.type = "effect";
        reward.id = id;
        reward.amplifier = amplifier;
        reward.seconds = seconds;
        return reward;
    }

    public static StreakReward item(String id, int count) {
        StreakReward reward = new StreakReward();
        reward.type = "item";
        reward.id = id;
        reward.count = count;
        return reward;
    }

    public static StreakReward of(Type type) {
        StreakReward reward = new StreakReward();
        reward.type = type.name().toLowerCase(java.util.Locale.ROOT);
        return reward;
    }

    /** The type, or null when the entry is not one of the known ones (it is then skipped). */
    public Type getType() {
        if (type == null) {
            return null;
        }
        try {
            return Type.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    public String getId() {
        return id;
    }

    public int getAmplifier() {
        return Math.max(0, Math.min(10, amplifier));
    }

    public int getSeconds() {
        return Math.max(1, Math.min(600, seconds));
    }

    public int getCount() {
        return Math.max(1, Math.min(64, count));
    }
}

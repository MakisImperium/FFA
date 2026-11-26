package net.zernyth.ffa.command;

import org.powernukkitx.Player;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Subcommands of a command ("/ffa duel ..."), registered by the services that own them. */
public final class Subcommands {
    @FunctionalInterface
    public interface Handler {
        /** {@code args} are the words after the subcommand name. */
        void run(Player player, String[] args);
    }

    private record Entry(String usage, Handler handler) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public void add(String name, String usage, Handler handler) {
        entries.put(name.toLowerCase(Locale.ROOT), new Entry(usage, handler));
    }

    /** Runs the subcommand named by {@code args[0]}; false when there is none of that name. */
    public boolean dispatch(Player player, String[] args) {
        if (args.length == 0) {
            return false;
        }
        Entry entry = entries.get(args[0].toLowerCase(Locale.ROOT));
        if (entry == null) {
            return false;
        }
        entry.handler().run(player, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    public String usage(String command) {
        StringBuilder text = new StringBuilder();
        for (Entry entry : entries.values()) {
            text.append("\n§7/").append(command).append(' ').append(entry.usage());
        }
        return text.toString();
    }
}

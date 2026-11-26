package net.zernyth.ffa.visibility;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Messages waiting for Discord, sent at most one per interval. When the queue is full new messages
 * are dropped: a burst of achievements must not pile up for minutes.
 */
public final class WebhookQueue {
    private final int capacity;
    private final Deque<String> waiting = new ArrayDeque<>();
    private long nextAllowed;

    public WebhookQueue(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized boolean offer(String message) {
        if (message == null || message.isBlank() || waiting.size() >= capacity) {
            return false;
        }
        waiting.addLast(message);
        return true;
    }

    /** The next message to send now, if one waits and the interval has passed. */
    public synchronized Optional<String> next(long now, long intervalMillis) {
        if (waiting.isEmpty() || now < nextAllowed) {
            return Optional.empty();
        }
        nextAllowed = now + intervalMillis;
        return Optional.of(waiting.removeFirst());
    }

    public synchronized int size() {
        return waiting.size();
    }

    /** Keeps player names from turning into Discord formatting. */
    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("§.", "").replaceAll("([\\\\*_~`|>#\\[\\]()-])", "\\\\$1");
    }
}

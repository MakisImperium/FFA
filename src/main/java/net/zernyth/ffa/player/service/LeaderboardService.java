package net.zernyth.ffa.player.service;

import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.model.PlayerProfile;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leaderboards without touching storage when someone opens them.
 *
 * <p>The best {@link #DEPTH} stored profiles per category are fetched in the background every
 * refresh (about a minute) and kept here. Reading merges them with the players online right now, whose
 * numbers are newer than what was last saved, so a kill shows up at once rather than a minute later.
 */
public final class LeaderboardService {
    /** How many stored profiles per category are kept; more than shown, so online players can move in. */
    static final int DEPTH = 50;

    private final PlayerProfileService profiles;
    private final Map<LeaderboardCategory, List<PlayerProfile>> stored = new EnumMap<>(LeaderboardCategory.class);

    public LeaderboardService(PlayerProfileService profiles) {
        this.profiles = profiles;
        for (LeaderboardCategory category : LeaderboardCategory.values()) {
            stored.put(category, List.of());
        }
    }

    /** Asks storage for fresh top lists; returns at once. */
    public void refresh() {
        for (LeaderboardCategory category : LeaderboardCategory.values()) {
            profiles.fetchTop(category, DEPTH, top -> {
                synchronized (stored) {
                    stored.put(category, List.copyOf(top));
                }
            });
        }
    }

    /** The best {@code limit} profiles for a category, stored and online together. */
    public List<PlayerProfile> top(LeaderboardCategory category, int limit) {
        Map<UUID, PlayerProfile> merged = new LinkedHashMap<>();
        synchronized (stored) {
            for (PlayerProfile profile : stored.get(category)) {
                merged.put(profile.getUniqueId(), profile);
            }
        }
        for (PlayerProfile online : profiles.onlineSnapshot()) {
            merged.put(online.getUniqueId(), online);
        }
        List<PlayerProfile> all = new ArrayList<>(merged.values());
        all.sort(category.comparator());
        return all.size() > limit ? List.copyOf(all.subList(0, limit)) : List.copyOf(all);
    }
}

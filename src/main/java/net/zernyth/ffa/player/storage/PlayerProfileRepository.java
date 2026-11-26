package net.zernyth.ffa.player.storage;

import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.model.PlayerProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerProfileRepository extends AutoCloseable {

    Optional<PlayerProfile> findByUniqueId(UUID uniqueId) throws Exception;

    List<PlayerProfile> loadAllProfiles() throws Exception;

    void save(PlayerProfile profile) throws Exception;

    /**
     * The best {@code limit} profiles for a category. The default reads everything and sorts, which is
     * fine for files; a database answers it with one ordered, limited query.
     */
    default List<PlayerProfile> findTop(LeaderboardCategory category, int limit) throws Exception {
        return loadAllProfiles().stream().sorted(category.comparator()).limit(limit).toList();
    }

    default boolean ping() throws Exception {
        return true;
    }

    @Override
    default void close() throws Exception {
    }
}



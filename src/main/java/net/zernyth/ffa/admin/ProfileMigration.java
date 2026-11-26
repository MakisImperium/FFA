package net.zernyth.ffa.admin;

import net.zernyth.ffa.player.model.PlayerProfile;
import net.zernyth.ffa.player.storage.PlayerProfileRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Copies JSON player profiles into MySQL. Runs off the main thread. Profiles of online players are
 * left out (their cached profile would overwrite the copy); profiles that already exist in MySQL
 * are kept unless {@code overwrite} is set. A dry run only counts.
 */
public final class ProfileMigration {
    public record Report(int total, int copied, int existing, int online, int failed, boolean dryRun) {
        public String summary(boolean german) {
            String verb = dryRun ? (german ? "wuerden kopiert" : "would be copied") : (german ? "kopiert" : "copied");
            return german
                    ? total + " Profile gefunden: " + copied + " " + verb + ", " + existing + " schon in MySQL, "
                    + online + " online (spaeter erneut), " + failed + " fehlgeschlagen."
                    : total + " profiles found: " + copied + " " + verb + ", " + existing + " already in MySQL, "
                    + online + " online (run again later), " + failed + " failed.";
        }
    }

    private ProfileMigration() {
    }

    /** {@code progress} gets the number of profiles handled so far, every 100. */
    public static Report run(PlayerProfileRepository source, PlayerProfileRepository target, Set<UUID> online,
                             boolean dryRun, boolean overwrite, IntConsumer progress) throws Exception {
        List<PlayerProfile> profiles = source.loadAllProfiles();
        int copied = 0;
        int existing = 0;
        int skippedOnline = 0;
        int failed = 0;
        int handled = 0;
        for (PlayerProfile profile : profiles) {
            handled++;
            if (handled % 100 == 0) {
                progress.accept(handled);
            }
            if (profile.getUniqueId() == null) {
                failed++;
                continue;
            }
            if (online.contains(profile.getUniqueId())) {
                skippedOnline++;
                continue;
            }
            try {
                if (!overwrite && target.findByUniqueId(profile.getUniqueId()).isPresent()) {
                    existing++;
                    continue;
                }
                if (!dryRun) {
                    target.save(profile);
                }
                copied++;
            } catch (Exception failure) {
                failed++;
            }
        }
        return new Report(profiles.size(), copied, existing, skippedOnline, failed, dryRun);
    }
}

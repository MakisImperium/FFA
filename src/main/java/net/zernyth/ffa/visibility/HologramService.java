package net.zernyth.ffa.visibility;

import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.service.LeaderboardService;
import net.zernyth.ffa.player.service.PlayerProfileService;
import net.zernyth.ffa.util.JsonFileService;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.player.PlayerQuitEvent;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.particle.FloatingTextParticle;
import org.powernukkitx.math.Vector3;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * A floating top list at a place an admin chose. Cycles through the configured categories, shown
 * in each viewer's language to players nearby. Reads only the leaderboard cache; runs once a second.
 */
public final class HologramService implements Listener {
    /** Where the board stands, stored in hologram.json. */
    public static final class Place {
        String world;
        double x;
        double y;
        double z;
    }

    private record Shown(boolean german, String text) {}

    private final Supplier<VisibilityConfiguration.Hologram> config;
    private final LeaderboardService leaderboards;
    private final PlayerProfileService profiles;
    private final ToIntFunction<net.zernyth.ffa.player.model.PlayerProfile> level;
    private final JsonFileService json;
    private final Path file;
    private final Executor io;
    private final LongSupplier clock;
    private final Map<UUID, Shown> viewers = new ConcurrentHashMap<>();
    private volatile Place place;
    private FloatingTextParticle english;
    private FloatingTextParticle german;

    public HologramService(Supplier<VisibilityConfiguration.Hologram> config, LeaderboardService leaderboards,
                           PlayerProfileService profiles, ToIntFunction<net.zernyth.ffa.player.model.PlayerProfile> level,
                           JsonFileService json, Path file, Executor io, LongSupplier clock) {
        this.config = config;
        this.leaderboards = leaderboards;
        this.profiles = profiles;
        this.level = level;
        this.json = json;
        this.file = file;
        this.io = io;
        this.clock = clock;
    }

    public void load() {
        try {
            place = json.read(file, Place.class).filter(p -> p.world != null).orElse(null);
        } catch (Exception broken) {
            place = null;
        }
        rebuildParticles();
    }

    public Optional<String> describe() {
        Place current = place;
        return current == null ? Optional.empty()
                : Optional.of(String.format(java.util.Locale.ROOT, "%s %.1f %.1f %.1f", current.world, current.x, current.y, current.z));
    }

    /** Puts the board where the admin stands (a little above the head). */
    public void setPlace(Player admin) {
        hideAll();
        Place next = new Place();
        next.world = admin.getLevel().getName();
        next.x = admin.getX();
        next.y = admin.getY() + 2.5;
        next.z = admin.getZ();
        place = next;
        rebuildParticles();
        io.execute(() -> {
            try {
                json.write(file, next);
            } catch (Exception failure) {
                Server.getInstance().getLogger().warning("FFA: could not store the hologram place: " + failure.getMessage());
            }
        });
    }

    public boolean removePlace() {
        if (place == null) {
            return false;
        }
        hideAll();
        place = null;
        english = null;
        german = null;
        io.execute(() -> {
            try {
                java.nio.file.Files.deleteIfExists(file);
            } catch (Exception ignored) {
                // a stale file only brings the board back after a restart
            }
        });
        return true;
    }

    /** Once a second on the main thread. */
    public void tick() {
        Place current = place;
        VisibilityConfiguration.Hologram settings = config.get();
        if (current == null || english == null || !settings.isEnabled()) {
            hideAll();
            return;
        }
        List<LeaderboardCategory> categories = settings.categories();
        LeaderboardCategory category = categories.get((int) ((clock.getAsLong() / 1000 / settings.getRotateSeconds()) % categories.size()));
        var top = leaderboards.top(category, settings.getSize());
        String textEn = BoardText.title(category, false) + "\n" + BoardText.lines(category, top, false, level);
        String textDe = BoardText.title(category, true) + "\n" + BoardText.lines(category, top, true, level);
        Level world = Server.getInstance().getLevelByName(current.world);
        double range = settings.getViewDistance();
        Vector3 at = new Vector3(current.x, current.y, current.z);

        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            boolean near = world != null && player.getLevel() == world && player.getPosition().distance(at) <= range;
            Shown shown = viewers.get(player.getUniqueId());
            if (!near) {
                if (shown != null) {
                    hide(player, shown.german());
                }
                continue;
            }
            boolean inGerman = profiles.getLanguage(player.getUniqueId()) == PlayerLanguage.GERMAN;
            String text = inGerman ? textDe : textEn;
            if (shown != null && shown.german() == inGerman && shown.text().equals(text)) {
                continue; // nothing new to send
            }
            if (shown != null && shown.german() != inGerman) {
                hide(player, shown.german());
            }
            FloatingTextParticle particle = inGerman ? german : english;
            particle.setTitle("");
            particle.setText(text);
            world.addParticle(particle, player);
            viewers.put(player.getUniqueId(), new Shown(inGerman, text));
        }
    }

    private void rebuildParticles() {
        Place current = place;
        if (current == null) {
            english = null;
            german = null;
            return;
        }
        Vector3 at = new Vector3(current.x, current.y, current.z);
        english = new FloatingTextParticle(at, "", "");
        german = new FloatingTextParticle(at, "", "");
    }

    private void hide(Player player, boolean inGerman) {
        viewers.remove(player.getUniqueId());
        FloatingTextParticle particle = inGerman ? german : english;
        if (particle == null || !player.isOnline()) {
            return;
        }
        particle.setInvisible(true);
        player.getLevel().addParticle(particle, player);
        particle.setInvisible(false);
    }

    public void hideAll() {
        for (UUID id : List.copyOf(viewers.keySet())) {
            Shown shown = viewers.get(id);
            Optional<Player> player = Server.getInstance().getPlayer(id);
            if (player.isPresent() && shown != null) {
                hide(player.get(), shown.german());
            } else {
                viewers.remove(id);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }
}

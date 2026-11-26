package net.zernyth.ffa.economy;

import net.zernyth.ffa.arena.service.ArenaSessionService;
import net.zernyth.ffa.combat.KillService;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.model.PlayerProgress;
import net.zernyth.ffa.player.service.PlayerProfileService;
import net.zernyth.ffa.streak.StreakConfiguration;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.particle.CriticalParticle;
import org.powernukkitx.level.particle.DustParticle;
import org.powernukkitx.level.particle.EnchantmentTableParticle;
import org.powernukkitx.level.particle.FlameParticle;
import org.powernukkitx.level.particle.HappyVillagerParticle;
import org.powernukkitx.level.particle.HeartParticle;
import org.powernukkitx.level.particle.HugeExplodeParticle;
import org.powernukkitx.level.particle.LavaParticle;
import org.powernukkitx.level.particle.Particle;
import org.powernukkitx.level.particle.PortalParticle;
import org.powernukkitx.level.particle.SmokeParticle;
import org.powernukkitx.math.Vector3;
import org.powernukkitx.utils.TextFormat;

import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Coins for counted kills, and the cosmetics bought with them: kill effects at the victim, a death
 * message to the arena, a join message when the owner enters an arena.
 */
public final class EconomyService implements KillService.KillListener {
    private final PlayerProfileService profiles;
    private final ArenaSessionService arenas;
    private final Supplier<EconomyConfiguration> config;
    private final Supplier<StreakConfiguration> streaks;
    private DoubleSupplier coinMultiplier = () -> 1.0;

    public EconomyService(PlayerProfileService profiles, ArenaSessionService arenas, Supplier<EconomyConfiguration> config,
                          Supplier<StreakConfiguration> streaks) {
        this.profiles = profiles;
        this.arenas = arenas;
        this.config = config;
        this.streaks = streaks;
    }

    public void setCoinMultiplier(DoubleSupplier coinMultiplier) {
        this.coinMultiplier = coinMultiplier;
    }

    public EconomyConfiguration configuration() {
        return config.get();
    }

    @Override
    public void onKill(KillService.Kill kill) {
        Player killer = kill.killer();
        if (killer == null) {
            return;
        }
        EconomyConfiguration settings = config.get();
        long paid = settings.coinsFor(kill.killerStreak(), kill.victimStreakBefore(), streaks.get().getShutdownFrom(),
                coinMultiplier.getAsDouble());
        if (paid > 0) {
            profiles.update(killer.getUniqueId(), profile -> profile.getProgress().addCoins(paid));
            killer.sendPopup(TextFormat.YELLOW + "+" + paid + (german(killer) ? " Muenzen" : " coins"));
        }

        Optional<PlayerProgress> progress = profiles.getProfile(killer.getUniqueId()).map(p -> p.getProgress());
        progress.flatMap(p -> Shop.equipped(p, Cosmetic.Slot.KILL_EFFECT, settings))
                .ifPresent(effect -> playKillEffect(effect.getEffect(), kill.victim()));
        progress.flatMap(p -> Shop.equipped(p, Cosmetic.Slot.DEATH_MESSAGE, settings))
                .ifPresent(message -> toArena(kill.arenaId(), Shop.fill(message.getText(), killer.getName(), kill.victim().getName(), null)));
    }

    /** A player entered an arena: their join message, if they wear one. */
    public void onArenaJoin(Player player) {
        profiles.getProfile(player.getUniqueId())
                .flatMap(profile -> Shop.equipped(profile.getProgress(), Cosmetic.Slot.JOIN_MESSAGE, config.get()))
                .ifPresent(message -> arenas.getArenaIdForPlayer(player.getUniqueId())
                        .ifPresent(arena -> toArena(arena, Shop.fill(message.getText(), null, null, player.getName()))));
    }

    /** A few seconds of particles where the victim fell. */
    static void playKillEffect(String effect, Player victim) {
        Level level = victim.getLevel();
        if (level == null) {
            return;
        }
        Vector3 at = victim.getPosition().add(0, 1, 0);
        if ("explosion".equals(effect)) {
            level.addParticle(new HugeExplodeParticle(at));
            return;
        }
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24;
            Vector3 point = at.add(Math.cos(angle) * 0.8, (i % 6) * 0.25 - 0.5, Math.sin(angle) * 0.8);
            Particle particle = switch (effect) {
                case "flame" -> new FlameParticle(point);
                case "hearts" -> new HeartParticle(point);
                case "portal" -> new PortalParticle(point);
                case "lava" -> new LavaParticle(point);
                case "enchant" -> new EnchantmentTableParticle(point);
                case "critical" -> new CriticalParticle(point);
                case "happy" -> new HappyVillagerParticle(point);
                case "smoke" -> new SmokeParticle(point);
                case "red" -> new DustParticle(point, 220, 40, 40);
                case "gold" -> new DustParticle(point, 255, 200, 40);
                case "aqua" -> new DustParticle(point, 60, 220, 230);
                default -> null;
            };
            if (particle != null) {
                level.addParticle(particle);
            }
        }
    }

    private void toArena(String arenaId, String message) {
        if (arenaId == null) {
            return;
        }
        List<Player> viewers = arenas.getPlayersInArena(arenaId).stream()
                .map(id -> Server.getInstance().getPlayer(id).orElse(null))
                .filter(p -> p != null && p.isOnline())
                .toList();
        viewers.forEach(viewer -> viewer.sendMessage(message));
    }

    private boolean german(Player player) {
        return profiles.getLanguage(player.getUniqueId()) == PlayerLanguage.GERMAN;
    }
}

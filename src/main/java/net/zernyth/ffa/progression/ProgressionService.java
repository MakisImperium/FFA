package net.zernyth.ffa.progression;

import net.zernyth.ffa.combat.KillService;
import net.zernyth.ffa.kit.service.KitSelectionService;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.model.PlayerProfile;
import net.zernyth.ffa.player.model.PlayerProgress;
import net.zernyth.ffa.player.service.PlayerProfileService;
import net.zernyth.ffa.streak.StreakConfiguration;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.event.EventHandler;
import org.powernukkitx.event.EventPriority;
import org.powernukkitx.event.Listener;
import org.powernukkitx.event.player.PlayerChatEvent;
import org.powernukkitx.utils.TextFormat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * XP, levels and ranks, season numbers, daily and weekly tasks and achievements - all driven by the
 * counted kills from {@link KillService}, so farming earns none of it.
 */
public final class ProgressionService implements KillService.KillListener, Listener {
    private final PlayerProfileService profiles;
    private final KitSelectionService kits;
    private final Supplier<ProgressionConfiguration> config;
    private final Supplier<StreakConfiguration> streaks;
    private final LongSupplier clock;
    private SeasonService seasons;
    /** Event multiplier for XP (Double XP hour); 1 normally. */
    private DoubleSupplier xpMultiplier = () -> 1.0;
    private final List<java.util.function.BiConsumer<Player, Long>> coinListeners = new ArrayList<>();
    private final List<java.util.function.BiConsumer<Player, AchievementDefinition>> achievementListeners = new ArrayList<>();

    /** Called on the main thread when a player unlocks an achievement. */
    public void addAchievementListener(java.util.function.BiConsumer<Player, AchievementDefinition> listener) {
        achievementListeners.add(listener);
    }

    public ProgressionService(PlayerProfileService profiles, KitSelectionService kits, Supplier<ProgressionConfiguration> config,
                              Supplier<StreakConfiguration> streaks, LongSupplier clock) {
        this.profiles = profiles;
        this.kits = kits;
        this.config = config;
        this.streaks = streaks;
        this.clock = clock;
    }

    public void setSeasons(SeasonService seasons) {
        this.seasons = seasons;
    }

    public void setXpMultiplier(DoubleSupplier xpMultiplier) {
        this.xpMultiplier = xpMultiplier;
    }

    // --- reading -----------------------------------------------------------------------------------

    public int level(PlayerProfile profile) {
        return config.get().levelCurve().levelFor(profile.getProgress().getXp());
    }

    public int level(UUID player) {
        return profiles.getProfile(player).map(this::level).orElse(1);
    }

    public Rank rank(int level) {
        return Rank.forLevel(level, config.get().getRanks());
    }

    public String currentSeasonId() {
        return seasons == null ? null : seasons.currentId();
    }

    public SeasonService seasons() {
        return seasons;
    }

    public List<TaskDefinition> dailyTasks(UUID player) {
        ProgressionConfiguration settings = config.get();
        return TaskRules.assigned(player, TaskRules.dailyKey(Instant.ofEpochMilli(clock.getAsLong()), settings.zone()),
                TaskDefinition.Period.DAILY, settings.getTaskPool(), settings.getDailyTaskCount());
    }

    public List<TaskDefinition> weeklyTasks(UUID player) {
        ProgressionConfiguration settings = config.get();
        return TaskRules.assigned(player, TaskRules.weeklyKey(Instant.ofEpochMilli(clock.getAsLong()), settings.zone()),
                TaskDefinition.Period.WEEKLY, settings.getTaskPool(), settings.getWeeklyTaskCount());
    }

    public List<AchievementDefinition> achievements() {
        return AchievementDefinition.all(config.get().getCustomAchievements());
    }

    // --- lifecycle -----------------------------------------------------------------------------------

    /** A profile just arrived: move it into the current season and the current task periods. */
    public void onProfileLoaded(UUID player) {
        profiles.update(player, this::refreshPeriods);
    }

    /** A new season started: everyone online starts it with zero; offline players when they return. */
    public void onNewSeason(String seasonId) {
        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
            profiles.update(player.getUniqueId(), profile -> profile.getProgress().enterSeason(seasonId));
            player.sendMessage(TextFormat.GOLD + t(player, "A new season has begun: " + seasonId + ". Good luck!",
                    "Eine neue Saison hat begonnen: " + seasonId + ". Viel Erfolg!"));
        }
    }

    private void refreshPeriods(PlayerProfile profile) {
        PlayerProgress progress = profile.getProgress();
        String season = currentSeasonId();
        if (season != null) {
            progress.enterSeason(season);
        }
        Instant now = Instant.ofEpochMilli(clock.getAsLong());
        TaskRules.roll(progress.getDailyTasks(), TaskRules.dailyKey(now, config.get().zone()));
        TaskRules.roll(progress.getWeeklyTasks(), TaskRules.weeklyKey(now, config.get().zone()));
    }

    // --- kills ---------------------------------------------------------------------------------------

    @Override
    public void onKill(KillService.Kill kill) {
        ProgressionConfiguration settings = config.get();
        int shutdownFrom = streaks.get().getShutdownFrom();
        boolean shutdown = kill.killer() != null && shutdownFrom > 0 && kill.victimStreakBefore() >= shutdownFrom;

        profiles.update(kill.victim().getUniqueId(), profile -> {
            refreshPeriods(profile);
            profile.getProgress().registerSeasonDeath();
        });

        Player killer = kill.killer();
        if (killer == null) {
            return;
        }
        long xp = settings.getXpPerKill()
                + (long) Math.min(kill.killerStreak(), settings.getStreakBonusCap()) * settings.getXpPerStreakStep()
                + (shutdown ? settings.getXpPerShutdown() : 0);
        long gained = Math.round(xp * Math.max(0, xpMultiplier.getAsDouble()));
        String kitId = kits.resolveKitForPlayer(killer.getUniqueId());
        TaskRules.Event event = new TaskRules.Event(true, kill.killerStreak(), shutdown, kitId, kill.arenaId());

        List<String> messages = new ArrayList<>();
        long[] coinsEarned = {0};
        profiles.update(killer.getUniqueId(), profile -> {
            refreshPeriods(profile);
            PlayerProgress progress = profile.getProgress();
            int levelBefore = level(profile);
            progress.registerSeasonKill(kill.killerStreak());
            if (shutdown) {
                progress.registerShutdown();
            }
            progress.addXp(gained);

            List<TaskRules.Completion> done = new ArrayList<>();
            done.addAll(TaskRules.apply(progress.getDailyTasks(), dailyTasks(killer.getUniqueId()), event));
            done.addAll(TaskRules.apply(progress.getWeeklyTasks(), weeklyTasks(killer.getUniqueId()), event));
            for (TaskRules.Completion completion : done) {
                TaskDefinition task = completion.task();
                progress.registerTaskCompleted();
                progress.addXp(task.getXp());
                progress.addCoins(task.getCoins());
                coinsEarned[0] += task.getCoins();
                messages.add(TextFormat.GREEN + t(killer, "Task done: ", "Aufgabe erledigt: ") + TextFormat.WHITE + task.name(german(killer))
                        + TextFormat.GRAY + " (+" + task.getXp() + " XP, +" + task.getCoins() + t(killer, " coins)", " Muenzen)"));
            }

            int levelAfter = level(profile);
            unlockAchievements(killer, profile, levelAfter, messages, coinsEarned);
            if (levelAfter > levelBefore) {
                Rank before = rank(levelBefore);
                Rank after = rank(levelAfter);
                messages.add(TextFormat.GOLD + "" + TextFormat.BOLD + t(killer, "LEVEL UP! ", "LEVEL UP! ") + TextFormat.RESET
                        + TextFormat.YELLOW + t(killer, "You are now level ", "Du bist jetzt Level ") + levelAfter
                        + (after.getFromLevel() != before.getFromLevel() ? TextFormat.YELLOW + " - " + after.display() : ""));
            }
        });
        killer.sendTip(TextFormat.AQUA + "+" + gained + " XP");
        messages.forEach(killer::sendMessage);
        if (coinsEarned[0] > 0) {
            coinListeners.forEach(listener -> listener.accept(killer, coinsEarned[0]));
        }
    }

    /** Checks achievements outside of kills too, e.g. after a duel. */
    public void checkAchievements(Player player) {
        List<String> messages = new ArrayList<>();
        profiles.update(player.getUniqueId(), profile -> unlockAchievements(player, profile, level(profile), messages, new long[]{0}));
        messages.forEach(player::sendMessage);
    }

    private void unlockAchievements(Player player, PlayerProfile profile, int level, List<String> messages, long[] coins) {
        for (AchievementDefinition achievement : achievements()) {
            if (profile.getProgress().getAchievements().containsKey(achievement.getId()) || !achievement.reached(profile, level)) {
                continue;
            }
            profile.getProgress().getAchievements().put(achievement.getId(), clock.getAsLong());
            achievementListeners.forEach(listener -> listener.accept(player, achievement));
            profile.getProgress().addCoins(achievement.getCoins());
            coins[0] += achievement.getCoins();
            messages.add(TextFormat.LIGHT_PURPLE + "" + TextFormat.BOLD + t(player, "ACHIEVEMENT: ", "ERFOLG: ") + TextFormat.RESET
                    + TextFormat.WHITE + achievement.name(german(player))
                    + (achievement.getCoins() > 0 ? TextFormat.GRAY + " (+" + achievement.getCoins() + t(player, " coins)", " Muenzen)") : ""));
        }
    }

    // --- display -------------------------------------------------------------------------------------

    /** Scoreboard lines: level, rank and progress to the next level. */
    public List<String> scoreboardLines(Player player, PlayerLanguage language) {
        return profiles.getProfile(player.getUniqueId()).map(profile -> {
            int level = level(profile);
            long[] progress = config.get().levelCurve().progressInLevel(profile.getProgress().getXp());
            String next = progress[1] == 0 ? "MAX" : progress[0] + "/" + progress[1];
            return List.of(
                    TextFormat.GRAY + "Level " + TextFormat.WHITE + level + " " + rank(level).display(),
                    TextFormat.GRAY + "XP " + TextFormat.WHITE + next);
        }).orElse(List.of());
    }

    /** The rank before the name in chat; leaves formats of other chat plugins alone. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(PlayerChatEvent event) {
        if (!config.get().isChatPrefix() || !"chat.type.text".equals(event.getFormat())) {
            return;
        }
        Player player = event.getPlayer();
        if (!profiles.isLoaded(player.getUniqueId())) {
            return;
        }
        int level = level(player.getUniqueId());
        event.setFormat(TextFormat.DARK_GRAY + "[" + rank(level).display() + TextFormat.DARK_GRAY + " " + level + "] "
                + TextFormat.RESET + "<{%0}> {%1}");
    }

    /** Gets notified about coins from tasks and achievements (the scoreboard shows them). */
    public void onCoins(java.util.function.BiConsumer<Player, Long> listener) {
        coinListeners.add(listener);
    }

    private boolean german(Player player) {
        return profiles.getLanguage(player.getUniqueId()) == PlayerLanguage.GERMAN;
    }

    private String t(Player player, String english, String german) {
        return german(player) ? german : english;
    }
}

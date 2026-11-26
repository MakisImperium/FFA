package net.zernyth.ffa.streak;

import net.zernyth.ffa.arena.model.ArenaDefinition;
import net.zernyth.ffa.arena.service.ArenaCatalogService;
import net.zernyth.ffa.arena.service.ArenaSessionService;
import net.zernyth.ffa.combat.KillService;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.service.PlayerProfileService;
import org.powernukkitx.Player;
import org.powernukkitx.Server;
import org.powernukkitx.entity.effect.Effect;
import org.powernukkitx.item.Item;
import org.powernukkitx.utils.TextFormat;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Streak rewards and announcements: reacts to every counted kill from {@link KillService}.
 * Rewards come from the arena's own table or the server's; see {@link StreakRules}.
 */
public final class StreakService implements KillService.KillListener {
    private final ArenaCatalogService catalog;
    private final ArenaSessionService sessions;
    private final PlayerProfileService profiles;
    private final Supplier<StreakConfiguration> config;

    public StreakService(ArenaCatalogService catalog, ArenaSessionService sessions, PlayerProfileService profiles,
                         Supplier<StreakConfiguration> config) {
        this.catalog = catalog;
        this.sessions = sessions;
        this.profiles = profiles;
        this.config = config;
    }

    @Override
    public void onKill(KillService.Kill kill) {
        StreakConfiguration settings = config.get();
        Player killer = kill.killer();
        if (killer != null && kill.killerStreak() > 0) {
            Map<String, List<StreakReward>> arenaTable = kill.arenaId() == null ? null
                    : catalog.findArena(kill.arenaId()).map(ArenaDefinition::getStreakRewards).orElse(null);
            List<StreakReward> rewards = StreakRules.rewardsFor(kill.killerStreak(), arenaTable, settings.getRewards());
            rewards.forEach(reward -> grant(killer, reward));
            if (!rewards.isEmpty()) {
                killer.sendMessage(TextFormat.GOLD + t(killer, "Streak reward for " + kill.killerStreak() + " kills!",
                        "Streak-Belohnung fuer " + kill.killerStreak() + " Kills!"));
            }
            announce(StreakRules.streakAudience(kill.killerStreak(), settings), kill.arenaId(), viewer -> t(viewer,
                    TextFormat.GOLD + killer.getName() + TextFormat.YELLOW + " is on a streak of " + TextFormat.GOLD + kill.killerStreak() + TextFormat.YELLOW + "!",
                    TextFormat.GOLD + killer.getName() + TextFormat.YELLOW + " hat eine Serie von " + TextFormat.GOLD + kill.killerStreak() + TextFormat.YELLOW + "!"));
        }
        if (killer != null) {
            int ended = kill.victimStreakBefore();
            Player victim = kill.victim();
            announce(StreakRules.shutdownAudience(ended, settings), kill.arenaId(), viewer -> t(viewer,
                    TextFormat.RED + "" + TextFormat.BOLD + "SHUTDOWN! " + TextFormat.RESET + TextFormat.GOLD + killer.getName()
                            + TextFormat.YELLOW + " ended " + TextFormat.GOLD + victim.getName() + TextFormat.YELLOW + "'s streak of " + ended + ".",
                    TextFormat.RED + "" + TextFormat.BOLD + "SHUTDOWN! " + TextFormat.RESET + TextFormat.GOLD + killer.getName()
                            + TextFormat.YELLOW + " beendet die Serie von " + TextFormat.GOLD + victim.getName() + TextFormat.YELLOW + " (" + ended + ")."));
        }
    }

    private void announce(StreakRules.Audience audience, String arenaId, java.util.function.Function<Player, String> message) {
        Collection<Player> viewers = switch (audience) {
            case NONE -> List.of();
            case SERVER -> Server.getInstance().getOnlinePlayers().values();
            case ARENA -> arenaId == null ? List.of() : sessions.getPlayersInArena(arenaId).stream()
                    .map(id -> Server.getInstance().getPlayer(id).orElse(null))
                    .filter(p -> p != null && p.isOnline())
                    .toList();
        };
        viewers.forEach(viewer -> viewer.sendMessage(message.apply(viewer)));
    }

    /** Applies one reward; an unknown effect or item id is skipped rather than failing the kill. */
    static void grant(Player player, StreakReward reward) {
        switch (reward.getType()) {
            case EFFECT -> {
                Effect effect = effect(reward.getId());
                if (effect != null) {
                    player.addEffect(effect.setAmplifier(reward.getAmplifier()).setDuration(reward.getSeconds() * 20));
                }
            }
            case ITEM -> {
                Item item = item(reward.getId(), reward.getCount());
                if (item != null) {
                    player.getInventory().addItem(item);
                }
            }
            case HEAL -> player.setHealthCurrent(player.getHealthMax());
            case FEED -> {
                player.getFoodData().setFood(20);
                player.getFoodData().setSaturation(20);
            }
            case REPAIR -> repair(player);
        }
    }

    private static Effect effect(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String name = id.trim().toLowerCase(java.util.Locale.ROOT);
        for (String candidate : List.of(name, name.startsWith("minecraft:") ? name.substring(10) : "minecraft:" + name)) {
            try {
                Effect effect = Effect.get(candidate);
                if (effect != null) {
                    return effect;
                }
            } catch (RuntimeException ignored) {
                // try the next spelling
            }
        }
        return null;
    }

    private static Item item(String id, int count) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String name = id.contains(":") ? id.trim() : "minecraft:" + id.trim();
        try {
            Item item = Item.get(name, 0, count);
            return item == null || item.isNull() ? null : item;
        } catch (RuntimeException unknown) {
            return null;
        }
    }

    /** Full durability for armour and everything in the inventory that wears. */
    private static void repair(Player player) {
        var inventory = player.getInventory();
        for (Map.Entry<Integer, Item> slot : inventory.getContents().entrySet()) {
            Item item = slot.getValue();
            if (item.getMaxDurability() > 0 && item.getDamage() > 0) {
                item.setDamage(0);
                inventory.setItem(slot.getKey(), item);
            }
        }
        Item[] armour = inventory.getArmorContents();
        boolean changed = false;
        for (Item piece : armour) {
            if (piece != null && piece.getMaxDurability() > 0 && piece.getDamage() > 0) {
                piece.setDamage(0);
                changed = true;
            }
        }
        if (changed) {
            inventory.setArmorContents(armour);
        }
    }

    private String t(Player player, String english, String german) {
        UUID id = player.getUniqueId();
        return profiles.getLanguage(id) == PlayerLanguage.GERMAN ? german : english;
    }
}

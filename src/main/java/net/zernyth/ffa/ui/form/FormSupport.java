package net.zernyth.ffa.ui.form;

import org.powernukkitx.Player;
import org.powernukkitx.form.element.ElementLabel;
import org.powernukkitx.form.element.custom.ElementDropdown;
import org.powernukkitx.form.element.custom.ElementInput;
import org.powernukkitx.form.element.custom.ElementToggle;
import org.powernukkitx.form.element.simple.ElementButton;
import org.powernukkitx.form.response.CustomResponse;
import org.powernukkitx.form.response.ElementResponse;
import org.powernukkitx.form.response.Response;
import org.powernukkitx.form.response.SimpleResponse;
import org.powernukkitx.form.window.CustomForm;
import org.powernukkitx.form.window.SimpleForm;
import org.powernukkitx.item.Item;
import org.powernukkitx.utils.TextFormat;
import net.zernyth.ffa.arena.model.ArenaDefinition;
import net.zernyth.ffa.arena.model.ArenaStatus;
import net.zernyth.ffa.arena.service.ArenaCatalogService;
import net.zernyth.ffa.arena.service.ArenaSessionService;
import net.zernyth.ffa.arena.service.SafeZonePlacementService;
import net.zernyth.ffa.i18n.LocalizationService;
import net.zernyth.ffa.kit.model.KitDefinition;
import net.zernyth.ffa.kit.model.KitItem;
import net.zernyth.ffa.kit.service.KitApplicationService;
import net.zernyth.ffa.kit.service.KitRegistry;
import net.zernyth.ffa.kit.service.KitSelectionService;
import net.zernyth.ffa.messaging.FfaMessageFormatter;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.model.PlayerProfile;
import net.zernyth.ffa.player.model.PlayerStatistics;
import net.zernyth.ffa.player.model.LeaderboardCategory;
import net.zernyth.ffa.player.service.LeaderboardService;
import net.zernyth.ffa.player.service.PlayerProfileService;
import net.zernyth.ffa.scoreboard.FfaScoreboardService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What every menu module shares: the services, the form ids and the small helpers for titles,
 * buttons, translation and messages. Each module extends it; {@link FfaFormController} wires them
 * together and routes responses.
 */
abstract class FormSupport implements FormIds {
    protected final FfaFormController root;
    protected final ArenaCatalogService arenaCatalogService;
    protected final ArenaSessionService arenaSessionService;
    protected final SafeZonePlacementService safeZonePlacementService;
    protected final KitRegistry kitRegistry;
    protected final KitSelectionService kitSelectionService;
    protected final KitApplicationService kitApplicationService;
    protected final PlayerProfileService playerProfileService;
    protected final LeaderboardService leaderboardService;
    protected final FfaScoreboardService scoreboardService;
    protected final LocalizationService localizationService;

    protected FormSupport(FfaFormController root) {
        this.root = root;
        this.arenaCatalogService = root.arenaCatalogService;
        this.arenaSessionService = root.arenaSessionService;
        this.safeZonePlacementService = root.safeZonePlacementService;
        this.kitRegistry = root.kitRegistry;
        this.kitSelectionService = root.kitSelectionService;
        this.kitApplicationService = root.kitApplicationService;
        this.playerProfileService = root.playerProfileService;
        this.leaderboardService = root.leaderboardService;
        this.scoreboardService = root.scoreboardService;
        this.localizationService = root.localizationService;
    }

    /** Forgets what this module remembered about a player between two forms. */
    void clear(java.util.UUID uniqueId) {
    }

    protected String title(Player player, String english, String german) {
        return TextFormat.GOLD + "FFA" + TextFormat.GRAY + " | " + TextFormat.WHITE + t(player, english, german);
    }

    protected String section(Player player, String english, String german) {
        return TextFormat.GRAY + t(player, english, german);
    }

    protected String button(Player player, TextFormat color, String enTitle, String enSubtitle, String deTitle, String deSubtitle) {
        return color + t(player, enTitle, deTitle) + "\n" + TextFormat.GRAY + t(player, enSubtitle, deSubtitle);
    }

    protected String formatStatus(Player player, ArenaStatus status) {
        return switch (status) {
            case WAITING -> t(player, "Standby", "Standby");
            case ACTIVE -> t(player, "Live", "Live");
            case DISABLED -> t(player, "Offline", "Offline");
            case MAINTENANCE -> t(player, "Maintenance", "Wartung");
        };
    }

    protected String formatStatusBadge(Player player, ArenaStatus status) {
        return switch (status) {
            case ACTIVE -> TextFormat.GREEN + "[LIVE]";
            case WAITING -> TextFormat.YELLOW + "[STANDBY]";
            case MAINTENANCE -> TextFormat.GOLD + "[MAINT]";
            case DISABLED -> TextFormat.RED + "[OFFLINE]";
        } + " " + TextFormat.WHITE + formatStatus(player, status);
    }

    protected String formatJoinBadge(Player player, ArenaStatus status) {
        if (status == ArenaStatus.ACTIVE) {
            return TextFormat.GREEN + t(player, "Open", "Offen");
        }
        return TextFormat.RED + t(player, "Closed", "Gesperrt");
    }

    protected String formatReadinessBadge(Player player, ArenaDefinition arena) {
        Optional<String> readinessError = arenaCatalogService.validateActivationReadiness(arena);
        if (readinessError.isEmpty()) {
            return TextFormat.GREEN + t(player, "Ready", "Bereit");
        }
        return TextFormat.RED + t(player, "Not Ready", "Nicht bereit")
                + TextFormat.GRAY + " (" + localizeReadinessReason(player, readinessError.get()) + ")";
    }

    protected String localizeReadinessReason(Player player, String reason) {
        if (reason == null || reason.isBlank()) {
            return t(player, "Unknown readiness error.", "Unbekannter Bereitschaftsfehler.");
        }
        return switch (reason) {
            case "Arena not found." -> t(player, "Arena not found.", "Arena nicht gefunden.");
            case "No spawn points configured." -> t(player, "No spawn points configured.", "Keine Spawnpunkte konfiguriert.");
            case "World name is missing." -> t(player, "World name is missing.", "Weltname fehlt.");
            case "Safe-zone reference is invalid." -> t(player, "Safe-zone reference is invalid.", "Safe-Zone Referenz ist ungueltig.");
            default -> {
                if (reason.startsWith("World '") && reason.endsWith("' is not loadable.")) {
                    String worldName = reason.substring("World '".length(), reason.length() - "' is not loadable.".length());
                    yield t(player,
                            "World '" + worldName + "' is not loadable.",
                            "Welt '" + worldName + "' kann nicht geladen werden.");
                }
                yield reason;
            }
        };
    }

    protected String t(Player player, String english, String german) {
        PlayerLanguage language = playerProfileService.getLanguage(player.getUniqueId());
        return localizationService.resolve(language, english, german);
    }

    protected void sendSuccess(Player player, String message) {
        player.sendMessage(FfaMessageFormatter.success(message));
    }

    protected void sendInfo(Player player, String message) {
        player.sendMessage(FfaMessageFormatter.info(message));
    }

    protected void sendWarning(Player player, String message) {
        player.sendMessage(FfaMessageFormatter.warning(message));
    }

    protected void sendError(Player player, String message) {
        player.sendMessage(FfaMessageFormatter.error(message));
    }

    protected int getDropdownIndex(CustomResponse custom, int elementIndex) {
        ElementResponse data = custom.getDropdownResponse(elementIndex);
        return data == null ? -1 : data.elementId();
    }

    protected Integer parseHotbarSlot(String rawInput) {
        String normalized = trimInput(rawInput);
        if (normalized == null) {
            return null;
        }
        try {
            int slot = Integer.parseInt(normalized);
            if (slot < 0 || slot > 35) {
                return null;
            }
            return slot;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    protected KitRegistry.ArmorSlot resolveArmorSlot(int dropdownIndex) {
        return switch (dropdownIndex) {
            case 0 -> KitRegistry.ArmorSlot.HELMET;
            case 1 -> KitRegistry.ArmorSlot.CHESTPLATE;
            case 2 -> KitRegistry.ArmorSlot.LEGGINGS;
            case 3 -> KitRegistry.ArmorSlot.BOOTS;
            default -> null;
        };
    }

    protected String describeHandItem(Player player) {
        var hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.isNull()) {
            return t(player, "empty", "leer");
        }
        return hand.getName() + " x" + hand.getCount();
    }

    protected String resolveArenaName(Player player, String arenaId) {
        if (arenaId == null || arenaId.isBlank()) {
            return t(player, "Unknown Arena", "Unbekannte Arena");
        }
        return arenaCatalogService.findArena(arenaId)
                .map(ArenaDefinition::getDisplayName)
                .orElse(t(player, "Unknown Arena", "Unbekannte Arena"));
    }

    protected String resolveKitName(Player player, String kitId) {
        if (kitId == null || kitId.isBlank()) {
            return null;
        }
        return kitRegistry.findKit(kitId)
                .map(KitDefinition::getDisplayName)
                .orElse(t(player, "Unknown Kit", "Unbekanntes Kit"));
    }

    protected String formatKitItemLabel(Player player, KitItem item) {
        Item preview = item.toItem();
        String itemName = (preview == null || preview.isNull()) ? t(player, "Unknown Item", "Unbekanntes Item") : preview.getName();
        return itemName + " x" + item.getAmount();
    }

    protected String normalizeIdentifier(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    protected String trimInput(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected List<ArenaDefinition> sortedArenas() {
        return arenaCatalogService.listArenas().stream()
                .sorted(Comparator.comparing(ArenaDefinition::getArenaId, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    protected List<KitDefinition> sortedKits() {
        return kitRegistry.listKits().stream()
                .sorted(Comparator.comparing(KitDefinition::getKitId, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}

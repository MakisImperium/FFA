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
 * Entry point of all FFA menus: opens the main menus and routes every form response to the module
 * that owns the form.
 *
 * <p>The menus themselves live in modules - {@link PlayerMenus}, {@link PlayerKitMenus},
 * {@link StatsMenus}, {@link SettingsMenus}, {@link ArenaAdminMenus}, {@link KitAdminMenus} -
 * which share services and helpers through {@link FormSupport}.
 */
public final class FfaFormController implements FormIds {

    final ArenaCatalogService arenaCatalogService;
    final ArenaSessionService arenaSessionService;
    final SafeZonePlacementService safeZonePlacementService;
    final KitRegistry kitRegistry;
    final KitSelectionService kitSelectionService;
    final KitApplicationService kitApplicationService;
    final PlayerProfileService playerProfileService;
    final LeaderboardService leaderboardService;
    final FfaScoreboardService scoreboardService;
    final LocalizationService localizationService;

    final PlayerMenus playerMenus;
    final PlayerKitMenus playerKitMenus;
    final StatsMenus statsMenus;
    final SettingsMenus settingsMenus;
    final ArenaAdminMenus arenaAdminMenus;
    final KitAdminMenus kitAdminMenus;

    public FfaFormController(
            ArenaCatalogService arenaCatalogService,
            ArenaSessionService arenaSessionService,
            SafeZonePlacementService safeZonePlacementService,
            KitRegistry kitRegistry,
            KitSelectionService kitSelectionService,
            KitApplicationService kitApplicationService,
            PlayerProfileService playerProfileService,
            LeaderboardService leaderboardService,
            FfaScoreboardService scoreboardService,
            LocalizationService localizationService
    ) {
        this.leaderboardService = leaderboardService;
        this.arenaCatalogService = arenaCatalogService;
        this.arenaSessionService = arenaSessionService;
        this.safeZonePlacementService = safeZonePlacementService;
        this.kitRegistry = kitRegistry;
        this.kitSelectionService = kitSelectionService;
        this.kitApplicationService = kitApplicationService;
        this.playerProfileService = playerProfileService;
        this.scoreboardService = scoreboardService;
        this.localizationService = localizationService;
        this.playerMenus = new PlayerMenus(this);
        this.playerKitMenus = new PlayerKitMenus(this);
        this.statsMenus = new StatsMenus(this);
        this.settingsMenus = new SettingsMenus(this);
        this.arenaAdminMenus = new ArenaAdminMenus(this);
        this.kitAdminMenus = new KitAdminMenus(this);
    }

    public void openPlayerMain(Player player) {
        playerMenus.openPlayerMain(player);
    }

    public void openAdminMain(Player player) {
        arenaAdminMenus.openAdminMain(player);
    }

    public void openAdminZoneMenu(Player player) {
        arenaAdminMenus.openAdminZoneMenu(player);
    }

    public void handleResponse(Player player, int formId, Response response, boolean wasClosed) {
        if (wasClosed || response == null) {
            routeClosedForm(player, formId);
            return;
        }
        switch (formId) {
            case FORM_PLAYER_MAIN -> playerMenus.handlePlayerMain(player, response);
            case FORM_PLAYER_IN_ARENA_COMMAND -> playerMenus.handleInArenaCommandMenu(player, response);
            case FORM_PLAYER_ARENA_MENU -> playerMenus.handlePlayerArenaMenu(player, response);
            case FORM_PLAYER_ARENA_SELECT -> playerMenus.handlePlayerArenaSelect(player, response);
            case FORM_PLAYER_ARENA_OVERVIEW -> playerMenus.openPlayerArenaMenu(player);
            case FORM_PLAYER_KIT_MENU -> playerKitMenus.handlePlayerKitMenu(player, response);
            case FORM_PLAYER_KIT_SELECT -> playerKitMenus.handlePlayerKitSelect(player, response);
            case FORM_PLAYER_KIT_CURRENT -> playerKitMenus.openPlayerKitMenu(player);
            case FORM_PLAYER_STATS -> statsMenus.handlePlayerStatsMenu(player, response);
            case FORM_PLAYER_STATS_OVERVIEW, FORM_PLAYER_STATS_GLOBAL, FORM_PLAYER_STATS_ARENA -> statsMenus.openPlayerStats(player);
            case FORM_PLAYER_STATS_UNAVAILABLE -> playerMenus.openPlayerMain(player);
            case FORM_PLAYER_SETTINGS -> settingsMenus.handlePlayerSettings(player, response);
            case FORM_PLAYER_ARENA_JOINED -> playerMenus.handleArenaJoinedForm(player, response);
            case FORM_ADMIN_MAIN -> arenaAdminMenus.handleAdminMain(player, response);
            case FORM_ADMIN_ARENA_MENU -> arenaAdminMenus.handleAdminArenaMenu(player, response);
            case FORM_ADMIN_ARENA_CREATE -> arenaAdminMenus.handleAdminArenaCreate(player, response);
            case FORM_ADMIN_ARENA_DELETE -> arenaAdminMenus.handleAdminArenaDelete(player, response);
            case FORM_ADMIN_ARENA_DELETE_CONFIRM -> arenaAdminMenus.handleAdminArenaDeleteConfirm(player, response);
            case FORM_ADMIN_ARENA_ADD_SPAWN -> arenaAdminMenus.handleAdminArenaAddSpawn(player, response);
            case FORM_ADMIN_ARENA_STATUS -> arenaAdminMenus.handleAdminArenaStatus(player, response);
            case FORM_ADMIN_ARENA_STATUS_VALUE -> arenaAdminMenus.handleAdminArenaStatusValue(player, response);
            case FORM_ADMIN_ZONE_MENU -> arenaAdminMenus.handleAdminZoneMenu(player, response);
            case FORM_ADMIN_ZONE_SAFE_MODE -> arenaAdminMenus.handleAdminSafeZoneMode(player, response);
            case FORM_ADMIN_ARENA_LIST -> arenaAdminMenus.openAdminArenaMenu(player);
            case FORM_ADMIN_KIT_MENU -> kitAdminMenus.handleAdminKitMenu(player, response);
            case FORM_ADMIN_KIT_CREATE -> kitAdminMenus.handleAdminKitCreate(player, response);
            case FORM_ADMIN_KIT_DELETE -> kitAdminMenus.handleAdminKitDelete(player, response);
            case FORM_ADMIN_KIT_DELETE_CONFIRM -> kitAdminMenus.handleAdminKitDeleteConfirm(player, response);
            case FORM_ADMIN_KIT_LIST -> kitAdminMenus.openAdminKitMenu(player);
            case FORM_ADMIN_KIT_ITEM_KIT_SELECT -> kitAdminMenus.handleAdminKitItemKitSelect(player, response);
            case FORM_ADMIN_KIT_ITEM_MENU -> kitAdminMenus.handleAdminKitItemMenu(player, response);
            case FORM_ADMIN_KIT_ITEM_ADD -> kitAdminMenus.handleAdminKitItemAdd(player, response);
            case FORM_ADMIN_KIT_ITEM_REMOVE -> kitAdminMenus.handleAdminKitItemRemove(player, response);
            case FORM_ADMIN_KIT_ITEM_OVERVIEW -> kitAdminMenus.openAdminKitItemMenu(player);
            case FORM_ADMIN_KIT_ARMOR_MENU -> kitAdminMenus.handleAdminKitArmorMenu(player, response);
            case FORM_ADMIN_KIT_ARMOR_SET -> kitAdminMenus.handleAdminKitArmorSet(player, response);
            case FORM_ADMIN_KIT_ARMOR_REMOVE -> kitAdminMenus.handleAdminKitArmorRemove(player, response);
            default -> {
            }
        }
    }

    private void routeClosedForm(Player player, int formId) {
        switch (formId) {
            case FORM_PLAYER_ARENA_SELECT, FORM_PLAYER_ARENA_OVERVIEW, FORM_PLAYER_ARENA_JOINED -> playerMenus.openPlayerArenaMenu(player);
            case FORM_PLAYER_KIT_SELECT, FORM_PLAYER_KIT_CURRENT -> {
                if (arenaSessionService.isPlayerInArena(player.getUniqueId())) {
                    playerKitMenus.openPlayerKitMenu(player);
                } else {
                    playerMenus.openPlayerMain(player);
                }
            }
            case FORM_PLAYER_STATS_OVERVIEW, FORM_PLAYER_STATS_GLOBAL, FORM_PLAYER_STATS_ARENA -> statsMenus.openPlayerStats(player);
            case FORM_PLAYER_STATS_UNAVAILABLE -> playerMenus.openPlayerMain(player);
            case FORM_PLAYER_ARENA_MENU, FORM_PLAYER_KIT_MENU, FORM_PLAYER_STATS, FORM_PLAYER_SETTINGS -> playerMenus.openPlayerMain(player);
            case FORM_ADMIN_ARENA_CREATE, FORM_ADMIN_ARENA_DELETE, FORM_ADMIN_ARENA_ADD_SPAWN,
                    FORM_ADMIN_ARENA_STATUS, FORM_ADMIN_ZONE_MENU, FORM_ADMIN_ARENA_LIST -> arenaAdminMenus.openAdminArenaMenu(player);
            case FORM_ADMIN_ARENA_DELETE_CONFIRM -> arenaAdminMenus.openAdminArenaDelete(player);
            case FORM_ADMIN_ZONE_SAFE_MODE -> arenaAdminMenus.openAdminZoneMenu(player);
            case FORM_ADMIN_ARENA_STATUS_VALUE -> {
                arenaAdminMenus.pendingArenaStatusTarget.remove(player.getUniqueId());
                arenaAdminMenus.openAdminArenaStatus(player);
            }
            case FORM_ADMIN_KIT_CREATE, FORM_ADMIN_KIT_DELETE, FORM_ADMIN_KIT_LIST, FORM_ADMIN_KIT_ITEM_KIT_SELECT -> kitAdminMenus.openAdminKitMenu(player);
            case FORM_ADMIN_KIT_DELETE_CONFIRM -> kitAdminMenus.openAdminKitDelete(player);
            case FORM_ADMIN_KIT_ITEM_ADD, FORM_ADMIN_KIT_ITEM_OVERVIEW -> kitAdminMenus.openAdminKitItemMenu(player);
            case FORM_ADMIN_KIT_ITEM_REMOVE -> {
                kitAdminMenus.pendingKitRemoveSlots.remove(player.getUniqueId());
                kitAdminMenus.openAdminKitItemMenu(player);
            }
            case FORM_ADMIN_KIT_ARMOR_MENU, FORM_ADMIN_KIT_ARMOR_SET, FORM_ADMIN_KIT_ARMOR_REMOVE -> kitAdminMenus.openAdminKitItemMenu(player);
            case FORM_ADMIN_KIT_ITEM_MENU -> {
                kitAdminMenus.pendingKitEditTarget.remove(player.getUniqueId());
                kitAdminMenus.pendingKitRemoveSlots.remove(player.getUniqueId());
                kitAdminMenus.openAdminKitMenu(player);
            }
            case FORM_ADMIN_ARENA_MENU, FORM_ADMIN_KIT_MENU -> arenaAdminMenus.openAdminMain(player);
            default -> {
            }
        }
    }

    public void clearPlayerState(UUID uniqueId) {
        for (FormSupport module : List.of(playerMenus, playerKitMenus, statsMenus, settingsMenus, arenaAdminMenus, kitAdminMenus)) {
            module.clear(uniqueId);
        }
    }
}

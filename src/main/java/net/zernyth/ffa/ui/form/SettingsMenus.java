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

/** Language and scoreboard preferences. */
final class SettingsMenus extends FormSupport {


    SettingsMenus(FfaFormController root) {
        super(root);
    }

    void openPlayerSettings(Player player) {
        boolean scoreboardEnabled = playerProfileService.isScoreboardEnabled(player.getUniqueId());
        PlayerLanguage language = playerProfileService.getLanguage(player.getUniqueId());
        SimpleForm form = new SimpleForm(
                title(player, "Settings", "Einstellungen"),
                TextFormat.GRAY + t(player, "Language", "Sprache") + ": "
                        + TextFormat.WHITE + (language == PlayerLanguage.GERMAN ? "Deutsch" : "English") + "\n"
                        + TextFormat.GRAY + t(player, "Scoreboard", "Scoreboard") + ": "
                        + (scoreboardEnabled ? TextFormat.GREEN + t(player, "Enabled", "Aktiv")
                        : TextFormat.RED + t(player, "Disabled", "Deaktiviert"))
        );
        form.addElement(new ElementButton(button(player, TextFormat.YELLOW,
                "Toggle Scoreboard",
                "Enable or disable arena scoreboard",
                "Scoreboard umschalten",
                "Arena-Scoreboard aktivieren oder deaktivieren")));
        form.addElement(new ElementButton(button(player, TextFormat.AQUA,
                "Switch Language",
                "English <-> Deutsch",
                "Sprache wechseln",
                "English <-> Deutsch")));
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_SETTINGS);
    }

    void handlePlayerSettings(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        if (!playerProfileService.isDataAvailable()) {
            sendWarning(player, t(player,
                    "Settings are temporarily unavailable while database connectivity is down.",
                    "Einstellungen sind voruebergehend nicht verfuegbar, solange die Datenbankverbindung ausfaellt."));
            root.playerMenus.openPlayerMain(player);
            return;
        }
        if (simple.buttonId() == 0) {
            boolean enabled = playerProfileService.toggleScoreboard(player.getUniqueId()).orElse(true);
            scoreboardService.refreshPlayer(player);
            sendInfo(player, enabled
                    ? t(player, "Scoreboard enabled.", "Scoreboard aktiviert.")
                    : t(player, "Scoreboard disabled.", "Scoreboard deaktiviert."));
            openPlayerSettings(player);
            return;
        }
        if (simple.buttonId() == 1) {
            PlayerLanguage current = playerProfileService.getLanguage(player.getUniqueId());
            PlayerLanguage next = current == PlayerLanguage.GERMAN ? PlayerLanguage.ENGLISH : PlayerLanguage.GERMAN;
            playerProfileService.setLanguage(player.getUniqueId(), next);
            sendSuccess(player, next == PlayerLanguage.GERMAN
                    ? "Sprache auf Deutsch gesetzt."
                    : "Language switched to English.");
            openPlayerSettings(player);
            return;
        }
        root.playerMenus.openPlayerMain(player);
    }
}

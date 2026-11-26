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

/** Choosing and inspecting a kit as a player. */
final class PlayerKitMenus extends FormSupport {


    PlayerKitMenus(FfaFormController root) {
        super(root);
    }

    void openPlayerKitMenu(Player player) {
        if (!arenaSessionService.isPlayerInArena(player.getUniqueId())) {
            sendWarning(player, t(player, "Kits are available only inside an arena.", "Kits sind nur in einer Arena verfuegbar."));
            root.playerMenus.openPlayerArenaMenu(player);
            return;
        }

        String selectedKit = resolveKitName(player, kitSelectionService.resolveKitForPlayer(player.getUniqueId()));
        SimpleForm form = new SimpleForm(
                title(player, "Kit Sector", "Kit Sektor"),
                TextFormat.GRAY + t(player, "Current kit: ", "Aktuelles Kit: ")
                        + TextFormat.WHITE + (selectedKit == null ? t(player, "None", "Keins") : selectedKit)
        );
        form.addElement(new ElementButton(button(player, TextFormat.GREEN,
                "Select Kit",
                "Choose your active loadout",
                "Kit waehlen",
                "Aktives Loadout auswaehlen")));
        form.addElement(new ElementButton(button(player, TextFormat.YELLOW,
                "Current Kit Details",
                "Show your current selection",
                "Aktuelles Kit",
                "Zeige deine aktuelle Auswahl")));
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_KIT_MENU);
    }

    void handlePlayerKitMenu(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        switch (simple.buttonId()) {
            case 0 -> openPlayerKitSelect(player);
            case 1 -> openPlayerCurrentKit(player);
            default -> root.playerMenus.openPlayerMain(player);
        }
    }

    void openPlayerCurrentKit(Player player) {
        if (!arenaSessionService.isPlayerInArena(player.getUniqueId())) {
            sendWarning(player, t(player, "Kits are available only inside an arena.", "Kits sind nur in einer Arena verfuegbar."));
            root.playerMenus.openPlayerArenaMenu(player);
            return;
        }

        String selectedKit = resolveKitName(player, kitSelectionService.resolveKitForPlayer(player.getUniqueId()));
        SimpleForm form = new SimpleForm(
                title(player, "Current Kit", "Aktuelles Kit"),
                TextFormat.GRAY + t(player, "Selected kit:", "Ausgewaehltes Kit:")
                        + "\n" + TextFormat.YELLOW + (selectedKit == null ? t(player, "No kit selected", "Kein Kit ausgewaehlt") : selectedKit)
        );
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_KIT_CURRENT);
    }

    void openPlayerKitSelect(Player player) {
        if (!arenaSessionService.isPlayerInArena(player.getUniqueId())) {
            sendWarning(player, t(player, "Kits are available only inside an arena.", "Kits sind nur in einer Arena verfuegbar."));
            root.playerMenus.openPlayerArenaMenu(player);
            return;
        }

        List<KitDefinition> kits = sortedKits();
        SimpleForm form = new SimpleForm(
                title(player, "Select Kit", "Kit Auswahl"),
                section(player,
                        "Choose one kit. It will be applied immediately in the arena.",
                        "Waehle ein Kit. Es wird direkt in der Arena angewendet.")
        );
        if (kits.isEmpty()) {
            form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "No kits available", "Keine Kits verfuegbar")));
        } else {
            for (KitDefinition kit : kits) {
                boolean unlocked = kitSelectionService.isUnlocked(player.getUniqueId(), kit);
                form.addElement(new ElementButton(unlocked
                        ? TextFormat.YELLOW + kit.getDisplayName()
                        : TextFormat.DARK_GRAY + kit.getDisplayName() + "\n" + TextFormat.RED
                        + t(player, "Locked - level ", "Gesperrt - Level ") + kit.getRequiredLevel()));
            }
        }
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_KIT_SELECT);
    }

    void handlePlayerKitSelect(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        List<KitDefinition> kits = sortedKits();
        int selected = simple.buttonId();
        int backIndex = kits.isEmpty() ? 1 : kits.size();
        if (selected < 0 || selected >= backIndex) {
            openPlayerKitMenu(player);
            return;
        }
        if (kits.isEmpty()) {
            openPlayerKitMenu(player);
            return;
        }

        KitDefinition kit = kits.get(selected);
        if (!kitSelectionService.isUnlocked(player.getUniqueId(), kit)) {
            sendWarning(player, t(player, "This kit unlocks at level " + kit.getRequiredLevel() + ".",
                    "Dieses Kit wird ab Level " + kit.getRequiredLevel() + " freigeschaltet."));
            openPlayerKitSelect(player);
            return;
        }
        if (!kitSelectionService.selectKit(player.getUniqueId(), kit.getKitId())) {
            sendError(player, t(player, "Kit could not be selected.", "Kit konnte nicht gesetzt werden."));
            openPlayerKitSelect(player);
            return;
        }
        kitApplicationService.applyKit(player, kit.getKitId());
        sendSuccess(player, t(player, "Kit activated: ", "Kit aktiviert: ") + kit.getDisplayName());
        openPlayerKitMenu(player);
    }
}

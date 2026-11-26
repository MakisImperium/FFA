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

/** Kit administration: create, edit items and armour, delete. */
final class KitAdminMenus extends FormSupport {
    final Map<UUID, String> pendingKitDeleteTarget = new ConcurrentHashMap<>();
    final Map<UUID, String> pendingKitEditTarget = new ConcurrentHashMap<>();
    final Map<UUID, List<Integer>> pendingKitRemoveSlots = new ConcurrentHashMap<>();
    volatile KitDefinition lastDeletedKit;

    KitAdminMenus(FfaFormController root) {
        super(root);
    }

    @Override
    void clear(UUID uniqueId) {
        pendingKitDeleteTarget.remove(uniqueId);
        pendingKitEditTarget.remove(uniqueId);
        pendingKitRemoveSlots.remove(uniqueId);
    }

    void openAdminKitMenu(Player player) {
        SimpleForm form = new SimpleForm(
                title(player, "Kit Management", "Kit Verwaltung"),
                section(player,
                        "Kit tools only. No arena controls are mixed in.",
                        "Nur Kit-Werkzeuge. Keine vermischten Arena-Funktionen.")
        );
        form.addElement(new ElementButton(button(player, TextFormat.GREEN,
                "Create / Update Kit",
                "Save your current inventory and armor as kit",
                "Kit erstellen / aktualisieren",
                "Aktuelles Inventar und Ruestung als Kit speichern")));
        form.addElement(new ElementButton(button(player, TextFormat.AQUA,
                "Manage Kit Items",
                "Add, replace, or remove individual items",
                "Kit-Items verwalten",
                "Einzelne Items hinzufuegen, ersetzen oder entfernen")));
        form.addElement(new ElementButton(button(player, TextFormat.RED,
                "Delete Kit",
                "Remove an existing kit",
                "Kit loeschen",
                "Bestehendes Kit entfernen")));
        form.addElement(new ElementButton(button(player, TextFormat.YELLOW,
                "Kit List",
                "Show all registered kits",
                "Kit Liste",
                "Alle registrierten Kits anzeigen")));
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_KIT_MENU);
    }

    void handleAdminKitMenu(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        switch (simple.buttonId()) {
            case 0 -> openAdminKitCreate(player);
            case 1 -> openAdminKitItemKitSelect(player);
            case 2 -> openAdminKitDelete(player);
            case 3 -> openAdminKitList(player);
            default -> root.arenaAdminMenus.openAdminMain(player);
        }
    }

    void openAdminKitCreate(Player player) {
        CustomForm form = new CustomForm(title(player, "Create / Update Kit", "Kit erstellen / aktualisieren"));
        form.addElement(new ElementInput(t(player, "Kit ID", "Kit ID"), "archer"));
        form.addElement(new ElementInput(t(player, "Display Name", "Anzeigename"), "Archer"));
        form.addElement(new ElementDropdown(
                t(player, "Save Mode", "Speichermodus"),
                List.of(
                        t(player, "Create/Rename only", "Nur erstellen/umbenennen"),
                        t(player, "Snapshot from inventory", "Snapshot aus Inventar"),
                        t(player, "Back", "Zurueck")
                ),
                0
        ));
        form.send(player, FORM_ADMIN_KIT_CREATE);
    }

    void handleAdminKitCreate(Player player, Response response) {
        if (!(response instanceof CustomResponse custom)) {
            return;
        }
        int action = getDropdownIndex(custom, 2);
        if (action == 2) {
            openAdminKitMenu(player);
            return;
        }

        String kitId = normalizeIdentifier(custom.getInputResponse(0));
        String displayName = trimInput(custom.getInputResponse(1));
        if (kitId == null) {
            sendError(player, t(player, "Kit ID is required.", "Kit ID ist Pflicht."));
            openAdminKitCreate(player);
            return;
        }

        boolean created;
        if (action == 0) {
            created = kitRegistry.createOrRenameKit(kitId, displayName == null ? kitId : displayName);
        } else {
            created = kitRegistry.createOrUpdateKitFromInventory(
                    kitId,
                    displayName == null ? kitId : displayName,
                    player
            );
        }
        if (created) {
            sendSuccess(player, action == 0
                    ? t(player, "Kit metadata saved.", "Kit-Metadaten gespeichert.")
                    : t(player, "Kit snapshot saved from inventory.", "Kit-Snapshot aus Inventar gespeichert."));
        } else {
            sendError(player, t(player, "Kit could not be saved.", "Kit konnte nicht gespeichert werden."));
        }
        openAdminKitMenu(player);
    }

    void openAdminKitItemKitSelect(Player player) {
        List<KitDefinition> kits = sortedKits();
        SimpleForm form = new SimpleForm(
                title(player, "Select Kit", "Kit auswaehlen"),
                section(player,
                        "Choose a kit to manage individual items.",
                        "Waehle ein Kit, um einzelne Items zu verwalten.")
        );
        if (kits.isEmpty()) {
            form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "No kits available", "Keine Kits verfuegbar")));
        } else {
            for (KitDefinition kit : kits) {
                form.addElement(new ElementButton(TextFormat.YELLOW + kit.getDisplayName()));
            }
        }
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_KIT_ITEM_KIT_SELECT);
    }

    void handleAdminKitItemKitSelect(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        List<KitDefinition> kits = sortedKits();
        int selected = simple.buttonId();
        int backIndex = kits.isEmpty() ? 1 : kits.size();
        if (selected < 0 || selected >= backIndex) {
            pendingKitEditTarget.remove(player.getUniqueId());
            openAdminKitMenu(player);
            return;
        }
        if (kits.isEmpty()) {
            openAdminKitMenu(player);
            return;
        }

        KitDefinition kit = kits.get(selected);
        pendingKitEditTarget.put(player.getUniqueId(), kit.getKitId());
        openAdminKitItemMenu(player);
    }

    void openAdminKitItemMenu(Player player) {
        pendingKitRemoveSlots.remove(player.getUniqueId());
        String kitId = pendingKitEditTarget.get(player.getUniqueId());
        if (kitId == null) {
            openAdminKitItemKitSelect(player);
            return;
        }
        Optional<KitDefinition> kitOptional = kitRegistry.findKit(kitId);
        if (kitOptional.isEmpty()) {
            pendingKitEditTarget.remove(player.getUniqueId());
            sendWarning(player, t(player, "Selected kit no longer exists.", "Ausgewaehltes Kit existiert nicht mehr."));
            openAdminKitItemKitSelect(player);
            return;
        }

        KitDefinition kit = kitOptional.get();
        SimpleForm form = new SimpleForm(
                title(player, "Kit Item Manager", "Kit-Item Manager"),
                TextFormat.GRAY + t(player, "Kit", "Kit") + ": " + TextFormat.YELLOW + kit.getDisplayName()
                        + "\n"
                        + TextFormat.GRAY + t(player, "Stored items", "Gespeicherte Items") + ": "
                        + TextFormat.WHITE + kit.getInventoryItems().size()
        );
        form.addElement(new ElementButton(button(player, TextFormat.GREEN,
                "Apply Current Inventory + Armor",
                "Overwrite this kit with your current loadout",
                "Aktuelles Inventar + Ruestung uebernehmen",
                "Dieses Kit mit deinem aktuellen Loadout ueberschreiben")));
        form.addElement(new ElementButton(button(player, TextFormat.GREEN,
                "Add / Replace Item",
                "Use held item and assign a target slot",
                "Item hinzufuegen / ersetzen",
                "Hand-Item auf Zielslot speichern")));
        form.addElement(new ElementButton(button(player, TextFormat.RED,
                "Remove Item",
                "Remove one item directly from ordered list",
                "Item entfernen",
                "Ein Item direkt aus sortierter Liste entfernen")));
        form.addElement(new ElementButton(button(player, TextFormat.YELLOW,
                "Item Overview",
                "Inspect all slots of this kit",
                "Item-Uebersicht",
                "Alle Slots dieses Kits anzeigen")));
        form.addElement(new ElementButton(button(player, TextFormat.AQUA,
                "Armor Management",
                "Set or remove helmet/chest/legs/boots",
                "Ruestungsverwaltung",
                "Helm/Brust/Hose/Schuhe setzen oder entfernen")));
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_KIT_ITEM_MENU);
    }

    void handleAdminKitItemMenu(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        switch (simple.buttonId()) {
            case 0 -> applyCurrentLoadoutToEditingKit(player);
            case 1 -> openAdminKitItemAdd(player);
            case 2 -> openAdminKitItemRemove(player);
            case 3 -> openAdminKitItemOverview(player);
            case 4 -> openAdminKitArmorMenu(player);
            default -> {
                pendingKitEditTarget.remove(player.getUniqueId());
                openAdminKitMenu(player);
            }
        }
    }

    void applyCurrentLoadoutToEditingKit(Player player) {
        String kitId = pendingKitEditTarget.get(player.getUniqueId());
        if (kitId == null) {
            openAdminKitItemKitSelect(player);
            return;
        }
        Optional<KitDefinition> kitOptional = kitRegistry.findKit(kitId);
        if (kitOptional.isEmpty()) {
            pendingKitEditTarget.remove(player.getUniqueId());
            sendWarning(player, t(player, "Selected kit no longer exists.", "Ausgewaehltes Kit existiert nicht mehr."));
            openAdminKitItemKitSelect(player);
            return;
        }

        KitDefinition kit = kitOptional.get();
        boolean updated = kitRegistry.createOrUpdateKitFromInventory(kit.getKitId(), kit.getDisplayName(), player);
        if (updated) {
            sendSuccess(player, t(player,
                    "Kit updated from current inventory and armor.",
                    "Kit mit aktuellem Inventar und Ruestung aktualisiert."));
        } else {
            sendError(player, t(player,
                    "Kit update failed.",
                    "Kit-Aktualisierung fehlgeschlagen."));
        }
        openAdminKitItemMenu(player);
    }

    void openAdminKitItemAdd(Player player) {
        String kitId = pendingKitEditTarget.get(player.getUniqueId());
        if (kitId == null) {
            openAdminKitItemKitSelect(player);
            return;
        }
        CustomForm form = new CustomForm(title(player, "Add / Replace Kit Item", "Kit-Item hinzufuegen / ersetzen"));
        form.addElement(new ElementLabel(TextFormat.GRAY + t(player,
                "Hold the item in your hand, set slot 0-35, then apply.",
                "Halte das Item in der Hand, setze Slot 0-35 und bestaetige.")
                + "\n" + TextFormat.DARK_GRAY + t(player, "Current hand", "Aktuelle Hand") + ": "
                + TextFormat.WHITE + describeHandItem(player)));
        form.addElement(new ElementInput(t(player, "Target Slot (0-35)", "Zielslot (0-35)"), "0"));
        form.addElement(new ElementDropdown(
                t(player, "Action", "Aktion"),
                List.of(t(player, "Apply Item", "Item anwenden"), t(player, "Back", "Zurueck")),
                0
        ));
        form.send(player, FORM_ADMIN_KIT_ITEM_ADD);
    }

    void handleAdminKitItemAdd(Player player, Response response) {
        if (!(response instanceof CustomResponse custom)) {
            return;
        }
        int action = getDropdownIndex(custom, 2);
        if (action == 1) {
            openAdminKitItemMenu(player);
            return;
        }

        String kitId = pendingKitEditTarget.get(player.getUniqueId());
        if (kitId == null || kitRegistry.findKit(kitId).isEmpty()) {
            pendingKitEditTarget.remove(player.getUniqueId());
            sendWarning(player, t(player, "Selected kit no longer exists.", "Ausgewaehltes Kit existiert nicht mehr."));
            openAdminKitItemKitSelect(player);
            return;
        }

        Integer slot = parseHotbarSlot(custom.getInputResponse(1));
        if (slot == null) {
            sendError(player, t(player, "Slot must be between 0 and 35.", "Slot muss zwischen 0 und 35 liegen."));
            openAdminKitItemAdd(player);
            return;
        }

        var handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || handItem.isNull()) {
            sendWarning(player, t(player, "Hold an item in your hand first.", "Halte zuerst ein Item in der Hand."));
            openAdminKitItemAdd(player);
            return;
        }

        boolean updated = kitRegistry.upsertInventoryItem(kitId, slot, handItem);
        if (updated) {
            sendSuccess(player, t(player, "Item saved in slot ", "Item auf Slot ") + slot + ".");
            openAdminKitItemMenu(player);
        } else {
            sendError(player, t(player, "Item could not be saved.", "Item konnte nicht gespeichert werden."));
            openAdminKitItemAdd(player);
        }
    }

    void openAdminKitItemRemove(Player player) {
        String kitId = pendingKitEditTarget.get(player.getUniqueId());
        if (kitId == null) {
            openAdminKitItemKitSelect(player);
            return;
        }
        Optional<KitDefinition> kitOptional = kitRegistry.findKit(kitId);
        if (kitOptional.isEmpty()) {
            pendingKitEditTarget.remove(player.getUniqueId());
            sendWarning(player, t(player, "Selected kit no longer exists.", "Ausgewaehltes Kit existiert nicht mehr."));
            openAdminKitItemKitSelect(player);
            return;
        }

        KitDefinition kit = kitOptional.get();
        List<KitItem> orderedItems = kit.getInventoryItems().stream()
                .filter(item -> item.getSlot() != null)
                .sorted(Comparator.comparing(KitItem::getSlot))
                .toList();

        SimpleForm form = new SimpleForm(
                title(player, "Remove Kit Item", "Kit-Item entfernen"),
                section(player,
                        "Select one stored item to remove.",
                        "Waehle ein gespeichertes Item zum Entfernen.")
        );

        List<Integer> slots = new ArrayList<>();
        if (orderedItems.isEmpty()) {
            form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "No stored inventory items", "Keine gespeicherten Inventar-Items")));
        } else {
            for (KitItem item : orderedItems) {
                int slot = item.getSlot();
                slots.add(slot);
                form.addElement(new ElementButton(
                        TextFormat.YELLOW + "Slot " + slot + "\n"
                                + TextFormat.GRAY + formatKitItemLabel(player, item)
                ));
            }
        }
        pendingKitRemoveSlots.put(player.getUniqueId(), slots);
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_KIT_ITEM_REMOVE);
    }

    void handleAdminKitItemRemove(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }

        String kitId = pendingKitEditTarget.get(player.getUniqueId());
        if (kitId == null || kitRegistry.findKit(kitId).isEmpty()) {
            pendingKitEditTarget.remove(player.getUniqueId());
            pendingKitRemoveSlots.remove(player.getUniqueId());
            sendWarning(player, t(player, "Selected kit no longer exists.", "Ausgewaehltes Kit existiert nicht mehr."));
            openAdminKitItemKitSelect(player);
            return;
        }

        List<Integer> slots = pendingKitRemoveSlots.getOrDefault(player.getUniqueId(), List.of());
        int selected = simple.buttonId();
        int backIndex = slots.isEmpty() ? 1 : slots.size();
        if (selected < 0 || selected >= backIndex) {
            pendingKitRemoveSlots.remove(player.getUniqueId());
            openAdminKitItemMenu(player);
            return;
        }

        if (slots.isEmpty()) {
            openAdminKitItemMenu(player);
            return;
        }

        int slot = slots.get(selected);
        boolean removed = kitRegistry.removeInventoryItem(kitId, slot);
        if (removed) {
            sendSuccess(player, t(player, "Item removed from slot ", "Item von Slot entfernt ") + slot + ".");
            openAdminKitItemRemove(player);
        } else {
            sendWarning(player, t(player, "No item stored in this slot.", "In diesem Slot ist kein Item gespeichert."));
            openAdminKitItemRemove(player);
        }
    }

    void openAdminKitItemOverview(Player player) {
        String kitId = pendingKitEditTarget.get(player.getUniqueId());
        if (kitId == null) {
            openAdminKitItemKitSelect(player);
            return;
        }
        Optional<KitDefinition> kitOptional = kitRegistry.findKit(kitId);
        if (kitOptional.isEmpty()) {
            pendingKitEditTarget.remove(player.getUniqueId());
            sendWarning(player, t(player, "Selected kit no longer exists.", "Ausgewaehltes Kit existiert nicht mehr."));
            openAdminKitItemKitSelect(player);
            return;
        }

        KitDefinition kit = kitOptional.get();
        StringBuilder content = new StringBuilder();
        if (kit.getInventoryItems().isEmpty()) {
            content.append(TextFormat.GRAY).append(t(player, "No inventory items stored.", "Keine Inventar-Items gespeichert."));
        } else {
            kit.getInventoryItems().stream()
                    .sorted(Comparator.comparing(item -> item.getSlot() == null ? Integer.MAX_VALUE : item.getSlot()))
                    .forEach(item -> content.append(TextFormat.YELLOW)
                            .append("Slot ").append(item.getSlot() == null ? "-" : item.getSlot())
                            .append(TextFormat.GRAY)
                            .append(" -> ").append(formatKitItemLabel(player, item))
                            .append("\n"));
        }

        SimpleForm form = new SimpleForm(
                title(player, "Kit Item Overview", "Kit-Item Uebersicht"),
                content.toString().trim()
        );
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_KIT_ITEM_OVERVIEW);
    }

    void openAdminKitArmorMenu(Player player) {
        String kitId = pendingKitEditTarget.get(player.getUniqueId());
        if (kitId == null) {
            openAdminKitItemKitSelect(player);
            return;
        }
        Optional<KitDefinition> kitOptional = kitRegistry.findKit(kitId);
        if (kitOptional.isEmpty()) {
            pendingKitEditTarget.remove(player.getUniqueId());
            sendWarning(player, t(player, "Selected kit no longer exists.", "Ausgewaehltes Kit existiert nicht mehr."));
            openAdminKitItemKitSelect(player);
            return;
        }

        SimpleForm form = new SimpleForm(
                title(player, "Armor Management", "Ruestungsverwaltung"),
                TextFormat.GRAY + t(player, "Current armor setup:", "Aktuelles Ruestungs-Setup:") + "\n"
                        + TextFormat.WHITE + kitRegistry.describeArmor(kitOptional.get())
        );
        form.addElement(new ElementButton(button(player, TextFormat.GREEN,
                "Apply Equipped Armor",
                "Copy your currently worn armor into kit",
                "Getragene Ruestung uebernehmen",
                "Aktuell getragene Ruestung ins Kit uebernehmen")));
        form.addElement(new ElementButton(button(player, TextFormat.AQUA,
                "Set Single Armor Slot",
                "Use held item for one armor slot",
                "Einzelnen Ruestungsslot setzen",
                "Hand-Item fuer einen Ruestungsslot nutzen")));
        form.addElement(new ElementButton(button(player, TextFormat.RED,
                "Remove Single Armor Slot",
                "Clear one armor slot from kit",
                "Einzelnen Ruestungsslot entfernen",
                "Einen Ruestungsslot aus Kit entfernen")));
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_KIT_ARMOR_MENU);
    }

    void handleAdminKitArmorMenu(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }

        String kitId = pendingKitEditTarget.get(player.getUniqueId());
        if (kitId == null) {
            openAdminKitItemKitSelect(player);
            return;
        }

        switch (simple.buttonId()) {
            case 0 -> {
                boolean applied = kitRegistry.applyEquippedArmor(kitId, player);
                if (applied) {
                    sendSuccess(player, t(player, "Equipped armor copied into kit.", "Getragene Ruestung ins Kit kopiert."));
                } else {
                    sendError(player, t(player, "Armor snapshot failed.", "Ruestungs-Snapshot fehlgeschlagen."));
                }
                openAdminKitArmorMenu(player);
            }
            case 1 -> openAdminKitArmorSet(player);
            case 2 -> openAdminKitArmorRemove(player);
            default -> openAdminKitItemMenu(player);
        }
    }

    void openAdminKitArmorSet(Player player) {
        String kitId = pendingKitEditTarget.get(player.getUniqueId());
        if (kitId == null) {
            openAdminKitItemKitSelect(player);
            return;
        }
        CustomForm form = new CustomForm(title(player, "Set Armor Slot", "Ruestungsslot setzen"));
        form.addElement(new ElementLabel(TextFormat.GRAY + t(player,
                "Hold an armor item in your hand and choose target slot.",
                "Halte ein Ruestungsitem in der Hand und waehle den Zielslot.")
                + "\n" + TextFormat.DARK_GRAY + t(player, "Current hand", "Aktuelle Hand") + ": "
                + TextFormat.WHITE + describeHandItem(player)));
        form.addElement(new ElementDropdown(
                t(player, "Armor Slot", "Ruestungsslot"),
                List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"),
                0
        ));
        form.addElement(new ElementDropdown(
                t(player, "Action", "Aktion"),
                List.of(t(player, "Apply", "Anwenden"), t(player, "Back", "Zurueck")),
                0
        ));
        form.send(player, FORM_ADMIN_KIT_ARMOR_SET);
    }

    void handleAdminKitArmorSet(Player player, Response response) {
        if (!(response instanceof CustomResponse custom)) {
            return;
        }
        int action = getDropdownIndex(custom, 2);
        if (action == 1) {
            openAdminKitArmorMenu(player);
            return;
        }

        String kitId = pendingKitEditTarget.get(player.getUniqueId());
        if (kitId == null) {
            openAdminKitItemKitSelect(player);
            return;
        }

        KitRegistry.ArmorSlot slot = resolveArmorSlot(getDropdownIndex(custom, 1));
        if (slot == null) {
            sendError(player, t(player, "Invalid armor slot.", "Ungueltiger Ruestungsslot."));
            openAdminKitArmorSet(player);
            return;
        }

        var hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.isNull()) {
            sendWarning(player, t(player, "Hold an item in your hand first.", "Halte zuerst ein Item in der Hand."));
            openAdminKitArmorSet(player);
            return;
        }

        boolean updated = kitRegistry.setArmorItemFromHand(kitId, slot, hand);
        if (updated) {
            sendSuccess(player, t(player, "Armor slot updated: ", "Ruestungsslot aktualisiert: ") + slot.name());
            openAdminKitArmorMenu(player);
        } else {
            sendError(player, t(player, "Armor slot update failed.", "Ruestungsslot-Aktualisierung fehlgeschlagen."));
            openAdminKitArmorSet(player);
        }
    }

    void openAdminKitArmorRemove(Player player) {
        CustomForm form = new CustomForm(title(player, "Remove Armor Slot", "Ruestungsslot entfernen"));
        form.addElement(new ElementDropdown(
                t(player, "Armor Slot", "Ruestungsslot"),
                List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"),
                0
        ));
        form.addElement(new ElementDropdown(
                t(player, "Action", "Aktion"),
                List.of(t(player, "Remove", "Entfernen"), t(player, "Back", "Zurueck")),
                0
        ));
        form.send(player, FORM_ADMIN_KIT_ARMOR_REMOVE);
    }

    void handleAdminKitArmorRemove(Player player, Response response) {
        if (!(response instanceof CustomResponse custom)) {
            return;
        }
        int action = getDropdownIndex(custom, 1);
        if (action == 1) {
            openAdminKitArmorMenu(player);
            return;
        }

        String kitId = pendingKitEditTarget.get(player.getUniqueId());
        if (kitId == null) {
            openAdminKitItemKitSelect(player);
            return;
        }
        KitRegistry.ArmorSlot slot = resolveArmorSlot(getDropdownIndex(custom, 0));
        if (slot == null) {
            sendError(player, t(player, "Invalid armor slot.", "Ungueltiger Ruestungsslot."));
            openAdminKitArmorRemove(player);
            return;
        }

        boolean removed = kitRegistry.removeArmorItem(kitId, slot);
        if (removed) {
            sendSuccess(player, t(player, "Armor slot cleared: ", "Ruestungsslot geleert: ") + slot.name());
            openAdminKitArmorMenu(player);
        } else {
            sendWarning(player, t(player, "No armor item stored in this slot.", "In diesem Ruestungsslot ist kein Item gespeichert."));
            openAdminKitArmorRemove(player);
        }
    }

    void openAdminKitDelete(Player player) {
        List<KitDefinition> kits = sortedKits();
        SimpleForm form = new SimpleForm(
                title(player, "Delete Kit", "Kit loeschen"),
                section(player, "Select a kit to delete.", "Waehle ein Kit zum Loeschen.")
        );
        if (kits.isEmpty()) {
            form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "No kits available", "Keine Kits verfuegbar")));
        } else {
            for (KitDefinition kit : kits) {
                form.addElement(new ElementButton(TextFormat.YELLOW + kit.getDisplayName()));
            }
        }
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_KIT_DELETE);
    }

    void handleAdminKitDelete(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        List<KitDefinition> kits = sortedKits();
        int selected = simple.buttonId();
        int backIndex = kits.isEmpty() ? 1 : kits.size();
        if (selected < 0 || selected >= backIndex) {
            openAdminKitMenu(player);
            return;
        }
        if (kits.isEmpty()) {
            openAdminKitMenu(player);
            return;
        }
        KitDefinition selectedKit = kits.get(selected);
        pendingKitDeleteTarget.put(player.getUniqueId(), selectedKit.getKitId());
        openAdminKitDeleteConfirm(player, selectedKit.getDisplayName());
    }

    void openAdminKitDeleteConfirm(Player player, String kitName) {
        SimpleForm form = new SimpleForm(
                title(player, "Confirm Kit Deletion", "Kit-Loeschung bestaetigen"),
                TextFormat.RED + t(player, "This will permanently delete kit ", "Diese Aktion loescht das Kit dauerhaft ")
                        + TextFormat.YELLOW + kitName + TextFormat.RED + "."
        );
        form.addElement(new ElementButton(TextFormat.RED + t(player, "Delete Now", "Jetzt loeschen")));
        form.addElement(new ElementButton(TextFormat.YELLOW + t(player, "Undo Last Delete", "Letzte Loeschung rueckgaengig")));
        form.addElement(new ElementButton(TextFormat.DARK_GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_KIT_DELETE_CONFIRM);
    }

    void handleAdminKitDeleteConfirm(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        if (simple.buttonId() == 1) {
            if (lastDeletedKit != null && kitRegistry.restoreKit(lastDeletedKit)) {
                sendSuccess(player, t(player, "Last deleted kit restored.", "Letztes geloeschtes Kit wiederhergestellt."));
            } else {
                sendWarning(player, t(player, "No deletable kit in undo buffer.", "Kein Kit-Eintrag im Undo-Puffer."));
            }
            openAdminKitDelete(player);
            return;
        }
        if (simple.buttonId() != 0) {
            pendingKitDeleteTarget.remove(player.getUniqueId());
            openAdminKitDelete(player);
            return;
        }

        String kitId = pendingKitDeleteTarget.remove(player.getUniqueId());
        if (kitId == null) {
            openAdminKitDelete(player);
            return;
        }

        Optional<KitDefinition> removed = kitRegistry.removeKit(kitId);
        if (removed.isPresent()) {
            lastDeletedKit = removed.get();
            sendSuccess(player, t(player, "Kit deleted. Undo is available.", "Kit geloescht. Undo ist verfuegbar."));
        } else {
            sendError(player, t(player, "Kit could not be deleted.", "Kit konnte nicht geloescht werden."));
        }
        openAdminKitDelete(player);
    }

    void openAdminKitList(Player player) {
        List<KitDefinition> kits = sortedKits();
        StringBuilder content = new StringBuilder();
        if (kits.isEmpty()) {
            content.append(TextFormat.GRAY).append(t(player, "No kits available.", "Keine Kits verfuegbar."));
        } else {
            for (KitDefinition kit : kits) {
                content.append(TextFormat.YELLOW).append(kit.getDisplayName()).append("\n");
            }
        }
        SimpleForm form = new SimpleForm(title(player, "Kit List", "Kit Liste"), content.toString().trim());
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_ADMIN_KIT_LIST);
    }
}

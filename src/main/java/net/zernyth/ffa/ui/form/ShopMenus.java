package net.zernyth.ffa.ui.form;

import net.zernyth.ffa.economy.Cosmetic;
import net.zernyth.ffa.economy.EconomyService;
import net.zernyth.ffa.economy.Shop;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.player.model.PlayerProgress;
import org.powernukkitx.Player;
import org.powernukkitx.form.element.simple.ElementButton;
import org.powernukkitx.form.response.Response;
import org.powernukkitx.form.response.SimpleResponse;
import org.powernukkitx.form.window.SimpleForm;
import org.powernukkitx.utils.TextFormat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The cosmetics shop: categories, then the items of one, then buying or wearing. */
final class ShopMenus extends FormSupport {
    private static final List<Cosmetic.Slot> SLOTS = List.of(Cosmetic.Slot.KILL_EFFECT, Cosmetic.Slot.DEATH_MESSAGE, Cosmetic.Slot.JOIN_MESSAGE);

    final Map<UUID, Cosmetic.Slot> openSlot = new ConcurrentHashMap<>();
    final Map<UUID, List<Cosmetic>> listed = new ConcurrentHashMap<>();
    final Map<UUID, Cosmetic> pendingPurchase = new ConcurrentHashMap<>();

    ShopMenus(FfaFormController root) {
        super(root);
    }

    @Override
    void clear(UUID uniqueId) {
        openSlot.remove(uniqueId);
        listed.remove(uniqueId);
        pendingPurchase.remove(uniqueId);
    }

    private EconomyService economy() {
        return root.economyService;
    }

    private PlayerProgress progress(Player player) {
        return playerProfileService.getProfile(player.getUniqueId()).map(p -> p.getProgress()).orElse(null);
    }

    void openShop(Player player) {
        PlayerProgress progress = progress(player);
        if (economy() == null || progress == null) {
            root.playerMenus.openPlayerMain(player);
            return;
        }
        SimpleForm form = new SimpleForm(title(player, "Shop", "Shop"),
                TextFormat.DARK_GRAY + t(player, "Your coins: ", "Deine Muenzen: ") + TextFormat.YELLOW + progress.getCoins() + "\n"
                        + TextFormat.GRAY + t(player,
                        "Earn coins with kills, streaks, tasks and achievements.",
                        "Muenzen gibt es fuer Kills, Streaks, Aufgaben und Erfolge."));
        for (Cosmetic.Slot slot : SLOTS) {
            long count = economy().configuration().getCosmetics().stream().filter(c -> c.getSlot() == slot).count();
            form.addElement(new ElementButton(slotColor(slot) + slotName(slot, isGerman(player)) + "\n"
                    + TextFormat.GRAY + count + t(player, " items", " Artikel")));
        }
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        form.send(player, FORM_PLAYER_SHOP);
    }

    void handleShop(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        int id = simple.buttonId();
        if (id >= 0 && id < SLOTS.size()) {
            openCategory(player, SLOTS.get(id));
        } else {
            root.playerMenus.openPlayerMain(player);
        }
    }

    void openCategory(Player player, Cosmetic.Slot slot) {
        PlayerProgress progress = progress(player);
        if (economy() == null || progress == null) {
            root.playerMenus.openPlayerMain(player);
            return;
        }
        boolean german = isGerman(player);
        List<Cosmetic> items = economy().configuration().getCosmetics().stream().filter(c -> c.getSlot() == slot).toList();
        String worn = progress.getEquippedCosmetics().get(Shop.slotKey(slot));
        SimpleForm form = new SimpleForm(title(player, slotName(slot, false), slotName(slot, true)),
                TextFormat.DARK_GRAY + t(player, "Your coins: ", "Deine Muenzen: ") + TextFormat.YELLOW + progress.getCoins() + "\n"
                        + TextFormat.GRAY + t(player,
                        "Tap an owned item to wear it or take it off.",
                        "Tippe ein gekauftes Item an, um es an- oder abzulegen."));
        for (Cosmetic cosmetic : items) {
            String state;
            if (cosmetic.getId().equals(worn)) {
                state = TextFormat.GREEN + t(player, "Equipped", "Ausgeruestet");
            } else if (progress.getOwnedCosmetics().contains(cosmetic.getId())) {
                state = TextFormat.AQUA + t(player, "Owned", "Gekauft");
            } else {
                state = (progress.getCoins() >= cosmetic.getPrice() ? TextFormat.YELLOW : TextFormat.RED)
                        + String.valueOf(cosmetic.getPrice()) + t(player, " coins", " Muenzen");
            }
            String preview = slot == Cosmetic.Slot.KILL_EFFECT ? "" : "\n" + TextFormat.DARK_GRAY + preview(player, cosmetic);
            form.addElement(new ElementButton(TextFormat.WHITE + cosmetic.name(german) + "\n" + state + preview));
        }
        form.addElement(new ElementButton(TextFormat.GRAY + t(player, "Back", "Zurueck")));
        openSlot.put(player.getUniqueId(), slot);
        listed.put(player.getUniqueId(), items);
        form.send(player, FORM_PLAYER_SHOP_CATEGORY);
    }

    void handleCategory(Player player, Response response) {
        if (!(response instanceof SimpleResponse simple)) {
            return;
        }
        List<Cosmetic> items = listed.get(player.getUniqueId());
        if (items == null || simple.buttonId() < 0 || simple.buttonId() >= items.size()) {
            openShop(player);
            return;
        }
        Cosmetic cosmetic = items.get(simple.buttonId());
        PlayerProgress progress = progress(player);
        if (progress == null) {
            openShop(player);
            return;
        }
        if (progress.getOwnedCosmetics().contains(cosmetic.getId())) {
            Shop.Result[] result = new Shop.Result[1];
            playerProfileService.update(player.getUniqueId(), profile -> result[0] = Shop.toggle(profile.getProgress(), cosmetic));
            if (result[0] == Shop.Result.EQUIPPED) {
                sendSuccess(player, t(player, "Equipped: ", "Ausgeruestet: ") + cosmetic.name(isGerman(player)));
            } else if (result[0] == Shop.Result.UNEQUIPPED) {
                sendInfo(player, t(player, "Taken off: ", "Abgelegt: ") + cosmetic.name(isGerman(player)));
            }
            openCategory(player, cosmetic.getSlot());
            return;
        }
        if (progress.getCoins() < cosmetic.getPrice()) {
            sendWarning(player, t(player, "You need ", "Dir fehlen ") + (cosmetic.getPrice() - progress.getCoins())
                    + t(player, " more coins.", " Muenzen."));
            openCategory(player, cosmetic.getSlot());
            return;
        }
        pendingPurchase.put(player.getUniqueId(), cosmetic);
        SimpleForm confirm = new SimpleForm(title(player, "Buy?", "Kaufen?"),
                TextFormat.WHITE + cosmetic.name(isGerman(player)) + "\n"
                        + TextFormat.DARK_GRAY + t(player, "Price: ", "Preis: ") + TextFormat.YELLOW + cosmetic.getPrice() + "\n"
                        + TextFormat.DARK_GRAY + t(player, "Coins after: ", "Muenzen danach: ") + TextFormat.WHITE
                        + (progress.getCoins() - cosmetic.getPrice()));
        confirm.addElement(new ElementButton(TextFormat.GREEN + t(player, "Buy", "Kaufen")));
        confirm.addElement(new ElementButton(TextFormat.GRAY + t(player, "Cancel", "Abbrechen")));
        confirm.send(player, FORM_PLAYER_SHOP_CONFIRM);
    }

    void handleConfirm(Player player, Response response) {
        Cosmetic cosmetic = pendingPurchase.remove(player.getUniqueId());
        if (cosmetic == null) {
            openShop(player);
            return;
        }
        if (response instanceof SimpleResponse simple && simple.buttonId() == 0) {
            Shop.Result[] result = new Shop.Result[1];
            playerProfileService.update(player.getUniqueId(), profile -> result[0] = Shop.buy(profile.getProgress(), cosmetic));
            if (result[0] == Shop.Result.BOUGHT) {
                sendSuccess(player, t(player, "Bought and equipped: ", "Gekauft und ausgeruestet: ") + cosmetic.name(isGerman(player)));
            } else if (result[0] == Shop.Result.TOO_EXPENSIVE) {
                sendWarning(player, t(player, "Not enough coins.", "Nicht genug Muenzen."));
            }
        }
        openCategory(player, cosmetic.getSlot());
    }

    /** Back from a closed list or confirmation. */
    void reopenCategory(Player player) {
        pendingPurchase.remove(player.getUniqueId());
        Cosmetic.Slot slot = openSlot.get(player.getUniqueId());
        if (slot == null) {
            openShop(player);
        } else {
            openCategory(player, slot);
        }
    }

    private String preview(Player player, Cosmetic cosmetic) {
        String sample = Shop.fill(cosmetic.getText(), "Alex", "Steve", player.getName());
        return sample.replaceAll("§.", "");
    }

    private boolean isGerman(Player player) {
        return playerProfileService.getLanguage(player.getUniqueId()) == PlayerLanguage.GERMAN;
    }

    private static TextFormat slotColor(Cosmetic.Slot slot) {
        return switch (slot) {
            case KILL_EFFECT -> TextFormat.RED;
            case DEATH_MESSAGE -> TextFormat.GOLD;
            case JOIN_MESSAGE -> TextFormat.AQUA;
        };
    }

    private static String slotName(Cosmetic.Slot slot, boolean german) {
        return switch (slot) {
            case KILL_EFFECT -> german ? "Kill-Effekte" : "Kill effects";
            case DEATH_MESSAGE -> german ? "Todesnachrichten" : "Death messages";
            case JOIN_MESSAGE -> german ? "Join-Nachrichten" : "Join messages";
        };
    }
}

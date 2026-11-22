package net.zernyth.ffa.kit.service;

import org.powernukkitx.Player;
import org.powernukkitx.inventory.HumanInventory;
import org.powernukkitx.item.Item;
import net.zernyth.ffa.kit.model.ArmorSet;
import net.zernyth.ffa.kit.model.KitDefinition;
import net.zernyth.ffa.kit.model.KitItem;

import java.util.Optional;

public final class KitApplicationService {

    private final KitRegistry kitRegistry;

    public KitApplicationService(KitRegistry kitRegistry) {
        this.kitRegistry = kitRegistry;
    }

    public boolean applyKit(Player player, String kitId) {
        Optional<KitDefinition> kitDefinition = kitRegistry.findKit(kitId);
        if (kitDefinition.isEmpty()) {
            return false;
        }

        player.getInventory().clearAll();
        for (KitItem item : kitDefinition.get().getInventoryItems()) {
            Item inventoryItem = item.toItem();
            if (inventoryItem.isNull()) {
                continue;
            }
            if (item.getSlot() == null) {
                player.getInventory().addItem(inventoryItem);
                continue;
            }
            int slot = item.getSlot();
            if (slot < 0 || slot >= HumanInventory.ARMORS_INDEX) {
                player.getInventory().addItem(inventoryItem);
                continue;
            }
            player.getInventory().setItem(slot, inventoryItem);
        }

        ArmorSet armorSet = kitDefinition.get().getArmorSet();
        if (armorSet.getHelmet() != null) {
            player.getInventory().setHelmet(armorSet.getHelmet().toItem());
        }
        if (armorSet.getChestplate() != null) {
            player.getInventory().setChestplate(armorSet.getChestplate().toItem());
        }
        if (armorSet.getLeggings() != null) {
            player.getInventory().setLeggings(armorSet.getLeggings().toItem());
        }
        if (armorSet.getBoots() != null) {
            player.getInventory().setBoots(armorSet.getBoots().toItem());
        }

        // Ensure the client receives the full inventory/armor snapshot immediately.
        player.getInventory().sendContents(player);
        player.getInventory().sendArmorContents(player);
        player.getInventory().setHeldItemSlot(0);
        return true;
    }
}



package net.zernyth.ffa.kit.model;

import org.powernukkitx.item.Item;

public final class KitItem {

    private String itemId;
    private int damage;
    private int amount;
    private Integer slot;

    public KitItem() {
    }

    public KitItem(String itemId, int damage, int amount, Integer slot) {
        this.itemId = itemId;
        this.damage = damage;
        this.amount = amount;
        this.slot = slot;
    }

    public static KitItem fromItem(Item item, Integer slot) {
        return new KitItem(item.getId(), item.getDamage(), item.getCount(), slot);
    }

    public Item toItem() {
        if (itemId == null || itemId.isBlank()) {
            return Item.AIR;
        }
        return Item.get(itemId, damage, Math.max(1, amount));
    }

    public String getItemId() {
        return itemId;
    }

    public int getDamage() {
        return damage;
    }

    public int getAmount() {
        return amount;
    }

    public Integer getSlot() {
        return slot;
    }
}

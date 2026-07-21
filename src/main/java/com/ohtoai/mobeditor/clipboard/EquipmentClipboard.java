package com.ohtoai.mobeditor.clipboard;

import org.bukkit.inventory.ItemStack;

/**
 * Stores copied equipment data from an entity.
 * Six equipment slots + optional inventory.
 */
public class EquipmentClipboard {

    // Equipment slots in standard order
    private ItemStack helmet;
    private ItemStack chestplate;
    private ItemStack leggings;
    private ItemStack boots;
    private ItemStack mainHand;
    private ItemStack offHand;

    // Optional inventory contents (for entities that are InventoryHolder)
    private ItemStack[] inventory;

    private final long timestamp;
    private final String entityType;
    private final String entityName;

    public EquipmentClipboard(String entityType, String entityName) {
        this.timestamp = System.currentTimeMillis();
        this.entityType = entityType;
        this.entityName = entityName;
    }

    // --- Getters / Setters ---

    public ItemStack getHelmet() { return helmet; }
    public void setHelmet(ItemStack item) { this.helmet = cloneIfNotNull(item); }

    public ItemStack getChestplate() { return chestplate; }
    public void setChestplate(ItemStack item) { this.chestplate = cloneIfNotNull(item); }

    public ItemStack getLeggings() { return leggings; }
    public void setLeggings(ItemStack item) { this.leggings = cloneIfNotNull(item); }

    public ItemStack getBoots() { return boots; }
    public void setBoots(ItemStack item) { this.boots = cloneIfNotNull(item); }

    public ItemStack getMainHand() { return mainHand; }
    public void setMainHand(ItemStack item) { this.mainHand = cloneIfNotNull(item); }

    public ItemStack getOffHand() { return offHand; }
    public void setOffHand(ItemStack item) { this.offHand = cloneIfNotNull(item); }

    public ItemStack[] getInventory() { return inventory; }
    public void setInventory(ItemStack[] inv) {
        if (inv == null) {
            this.inventory = null;
            return;
        }
        this.inventory = new ItemStack[inv.length];
        for (int i = 0; i < inv.length; i++) {
            this.inventory[i] = cloneIfNotNull(inv[i]);
        }
    }

    public long getTimestamp() { return timestamp; }
    public String getEntityType() { return entityType; }
    public String getEntityName() { return entityName; }

    /**
     * Check if all slots are empty.
     */
    public boolean isEmpty() {
        if (!isAir(helmet) || !isAir(chestplate) || !isAir(leggings)
                || !isAir(boots) || !isAir(mainHand) || !isAir(offHand))
            return false;
        if (inventory != null) {
            for (ItemStack item : inventory) {
                if (!isAir(item)) return false;
            }
        }
        return true;
    }

    private static ItemStack cloneIfNotNull(ItemStack item) {
        return (item != null && !item.getType().isAir()) ? item.clone() : null;
    }

    private static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}

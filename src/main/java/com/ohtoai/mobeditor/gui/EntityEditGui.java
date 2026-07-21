package com.ohtoai.mobeditor.gui;

import com.ohtoai.mobeditor.MobEditor;
import com.ohtoai.mobeditor.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Handles opening and syncing entity editing GUIs.
 *
 * Three view types based on entity capabilities:
 *   EQUIPMENT — only equipment, no inventory (Zombie, Skeleton, Creeper)
 *   INVENTORY — only inventory (Villager, ChestMinecart, Allay)
 *   COMBINED  — both equipment AND inventory (Horse, Donkey, Piglin, Llama)
 */
public final class EntityEditGui {

    private static final int GUI_SIZE = 54;

    private EntityEditGui() {}

    // ── public entry ────────────────────────────────────────────────────

    /**
     * Factory: detect what the entity supports via API and open the right GUI.
     * Every LivingEntity with EntityEquipment gets equipment slots shown;
     * every InventoryHolder gets inventory slots shown.
     */
    public static void open(Player editor, Entity entity) {
        boolean hasEquip = entity instanceof LivingEntity le && le.getEquipment() != null;
        boolean hasInv = entity instanceof InventoryHolder;
        int invSize = hasInv ? ((InventoryHolder) entity).getInventory().getSize() : 0;
        String name = resolveName(entity);

        if (hasEquip && hasInv && invSize > 0) {
            openCombined(editor, entity, (LivingEntity) entity, (InventoryHolder) entity, invSize, name);
        } else if (hasInv && invSize > 0) {
            openInventoryOnly(editor, entity, (InventoryHolder) entity, invSize, name);
        } else if (hasEquip) {
            openEquipmentOnly(editor, entity, (LivingEntity) entity, name);
        } else {
            editor.sendMessage("§c无法编辑此实体。");
        }
    }

    // ── EQUIPMENT only (Zombie, Skeleton, Creeper, etc.) ─────────────────

    private static void openEquipmentOnly(Player editor, Entity entity, LivingEntity living, String name) {
        Messages msg = msg();

        String title = buildTitle("§8" + msg.get("equipment_edit.title", "{name}", truncate(name, 16)));

        GuiHolders.EntityEditHolder holder = new GuiHolders.EntityEditHolder(
                entity.getUniqueId(), name, editor.getUniqueId(), false, 0);
        Inventory gui = Bukkit.createInventory(holder, GUI_SIZE, title);

        // Fill non-equipment slots with border
        fillEquipBorders(gui);

        EntityEquipment equip = living.getEquipment();
        setIfPresent(gui, 4, equip.getHelmet());
        setIfPresent(gui, 13, equip.getChestplate());
        setIfPresent(gui, 22, equip.getLeggings());
        setIfPresent(gui, 23, equip.getItemInMainHand());
        setIfPresent(gui, 31, equip.getBoots());
        setIfPresent(gui, 32, equip.getItemInOffHand());

        addNavRow(gui);
        addInfoItem(gui, 49, name, msg.get("equipment_edit.entity_info_lore"));
        editor.openInventory(gui);
    }

    // ── INVENTORY only (Villager, ChestMinecart, HopperMinecart, Allay) ──

    private static void openInventoryOnly(Player editor, Entity entity, InventoryHolder invHolder,
                                           int invSize, String name) {
        Messages msg = msg();

        // Calculate grid: ceiled to next multiple of 9
        int rows = Math.clamp((invSize + 8) / 9, 1, 6);
        int guiSize = rows * 9;

        String title = buildTitle("§8" + msg.get("equipment_edit.title", "{name}", truncate(name, 16)));

        GuiHolders.EntityEditHolder holder = new GuiHolders.EntityEditHolder(
                entity.getUniqueId(), name, editor.getUniqueId(), true, invSize);
        Inventory gui = Bukkit.createInventory(holder, guiSize, title);

        Inventory entityInv = invHolder.getInventory();
        ItemStack border = borderPane();

        for (int i = 0; i < guiSize; i++) {
            if (i < invSize) {
                ItemStack item = entityInv.getItem(i);
                gui.setItem(i, (item != null && !item.getType().isAir()) ? item.clone() : null);
            } else {
                gui.setItem(i, border.clone());
            }
        }

        editor.openInventory(gui);
    }

    // ── COMBINED (Horse, Donkey, Piglin, Llama, Camel) ──────────────────

    private static void openCombined(Player editor, Entity entity, LivingEntity living,
                                      InventoryHolder invHolder, int invSize, String name) {
        Messages msg = msg();

        int invRowSlots = Math.min(invSize, 9);
        String title = buildTitle("§8" + msg.get("equipment_edit.title", "{name}", truncate(name, 16)));

        GuiHolders.EntityEditHolder holder = new GuiHolders.EntityEditHolder(
                entity.getUniqueId(), name, editor.getUniqueId(), true, invSize);
        Inventory gui = Bukkit.createInventory(holder, GUI_SIZE, title);

        fillEquipBorders(gui);

        EntityEquipment equip = living.getEquipment();
        setIfPresent(gui, 4, equip.getHelmet());
        setIfPresent(gui, 13, equip.getChestplate());
        setIfPresent(gui, 22, equip.getLeggings());
        setIfPresent(gui, 23, equip.getItemInMainHand());
        setIfPresent(gui, 31, equip.getBoots());
        setIfPresent(gui, 32, equip.getItemInOffHand());

        // Inventory row (36-44): overwrite entity's slots, fill rest with border
        Inventory entityInv = invHolder.getInventory();
        ItemStack border = borderPane();
        for (int i = 0; i < 9; i++) {
            if (i < invRowSlots) {
                ItemStack item = entityInv.getItem(i);
                gui.setItem(36 + i, (item != null && !item.getType().isAir()) ? item.clone() : null);
            } else {
                gui.setItem(36 + i, border.clone());
            }
        }

        addNavRow(gui);
        addInfoItem(gui, 49, name,
                msg.get("equipment_edit.entity_info_lore") + " §8| §7物品栏: " + invSize + " 格");
        editor.openInventory(gui);
    }

    // ── Sync ────────────────────────────────────────────────────────────

    public static void syncToTarget(Player editor, Inventory gui, GuiHolders.EntityEditHolder holder) {
        Entity entity = Bukkit.getEntity(holder.entityUuid());
        if (entity == null) return;

        boolean isCombinedOrEquip = gui.getSize() >= GUI_SIZE;

        // Sync equipment (only for 54-slot equipment/combined GUIs)
        if (isCombinedOrEquip && entity instanceof LivingEntity living && living.getEquipment() != null) {
            EntityEquipment equip = living.getEquipment();
            equip.setHelmet(cloneOrNull(gui.getItem(4)));
            equip.setChestplate(cloneOrNull(gui.getItem(13)));
            equip.setLeggings(cloneOrNull(gui.getItem(22)));
            equip.setItemInMainHand(cloneOrNull(gui.getItem(23)));
            equip.setBoots(cloneOrNull(gui.getItem(31)));
            equip.setItemInOffHand(cloneOrNull(gui.getItem(32)));
        }

        // Sync inventory
        if (holder.hasInventory() && entity instanceof InventoryHolder invHolder) {
            Inventory entityInv = invHolder.getInventory();

            if (isCombinedOrEquip) {
                // Combined view: inventory at row 4 (36-44)
                int size = Math.min(holder.invSize(), Math.min(entityInv.getSize(), 9));
                for (int i = 0; i < size; i++) {
                    entityInv.setItem(i, cloneOrNull(gui.getItem(36 + i)));
                }
            } else {
                // Inventory-only grid view
                int size = Math.min(holder.invSize(), entityInv.getSize());
                for (int i = 0; i < size; i++) {
                    entityInv.setItem(i, cloneOrNull(gui.getItem(i)));
                }
            }
        }
    }

    // ── Refresh ─────────────────────────────────────────────────────────

    public static void refresh(Player editor, GuiHolders.EntityEditHolder holder) {
        Entity entity = Bukkit.getEntity(holder.entityUuid());
        if (entity != null) {
            open(editor, entity);
        }
    }

    // ── Slot validation ────────────────────────────────────────────────

    /**
     * Check if a slot is non-editable.
     * @param guiSize top inventory size (54 for equip/combined, 9/18/27/etc for inventory)
     */
    public static boolean isNonEditableSlot(GuiHolders.EntityEditHolder holder, int slot, int guiSize) {
        boolean isEquipLayout = guiSize >= GUI_SIZE; // 54-slot = equip or combined

        if (isEquipLayout) {
            if (slot >= 45) return true; // nav row — always protected
            if (!holder.hasInventory()) {
                // equipment only
                return slot != 4 && slot != 13 && slot != 22 && slot != 23 && slot != 31 && slot != 32;
            }
            // combined: equipment slots + inventory row
            int invEditable = Math.min(holder.invSize(), 9);
            return slot != 4 && slot != 13 && slot != 22 && slot != 23 && slot != 31 && slot != 32
                    && (slot < 36 || slot >= 36 + invEditable);
        }

        // Inventory-only grid: editable slots are 0..invSize-1
        return slot >= holder.invSize();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private static String resolveName(Entity entity) {
        if (entity.getCustomName() != null) return entity.getCustomName();
        return entity.getType().getKey().getKey();
    }

    private static void setIfPresent(Inventory gui, int slot, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            gui.setItem(slot, item.clone());
        }
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return (item != null && !item.getType().isAir()) ? item.clone() : null;
    }

    private static void fillEquipBorders(Inventory gui) {
        ItemStack border = borderPane();
        for (int i = 0; i < 45; i++) {
            if (i != 4 && i != 13 && i != 22 && i != 23 && i != 31 && i != 32) {
                gui.setItem(i, border.clone());
            }
        }
    }

    private static void addNavRow(Inventory gui) {
        ItemStack border = borderPane();
        for (int i = 45; i <= 53; i++) gui.setItem(i, border.clone());

        Messages msg = msg();
        ItemStack refreshBtn = new ItemStack(Material.CLOCK);
        ItemMeta rm = refreshBtn.getItemMeta();
        if (rm != null) {
            rm.setDisplayName(msg.get("equipment_edit.refresh"));
            rm.setLore(List.of(msg.get("equipment_edit.refresh_lore")));
            refreshBtn.setItemMeta(rm);
        }
        gui.setItem(48, refreshBtn);
    }

    private static void addInfoItem(Inventory gui, int slot, String name, String lore) {
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.setDisplayName("§e" + name);
            im.setLore(List.of(lore));
            info.setItemMeta(im);
        }
        gui.setItem(slot, info);
    }

    private static ItemStack borderPane() {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) { meta.setDisplayName("§7"); border.setItemMeta(meta); }
        return border;
    }

    private static Messages msg() { return MobEditor.getInstance().getMessages(); }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + "…" : s;
    }

    private static String buildTitle(String title) {
        if (title.length() > 32) title = title.substring(0, 32);
        return title;
    }
}

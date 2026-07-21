package com.ohtoai.mobeditor.gui;

import com.ohtoai.mobeditor.MobEditor;
import com.ohtoai.mobeditor.clipboard.EquipmentClipboard;
import com.ohtoai.mobeditor.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Read-only GUI displaying copied equipment items visually.
 */
public final class CopyDisplayGui {

    private static final int GUI_SIZE = 54;

    private CopyDisplayGui() {}

    private static ItemStack borderPane() {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) { meta.setDisplayName("§7"); border.setItemMeta(meta); }
        return border;
    }

    private static ItemStack slotLabel(String label) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(label); item.setItemMeta(meta); }
        return item;
    }

    public static void open(Player player, EquipmentClipboard clipboard) {
        MobEditor plugin = MobEditor.getInstance();
        Messages msg = plugin.getMessages();

        String title = buildTitle("§8" + msg.get("clipboard.preview_title",
                "{name}", truncate(clipboard.getEntityName(), 16)));

        GuiHolders.CopyDisplayHolder holder = new GuiHolders.CopyDisplayHolder(player.getUniqueId());
        Inventory gui = Bukkit.createInventory(holder, GUI_SIZE, title);

        ItemStack border = borderPane();

        // Fill all slots with border initially, then carve out equipment slots
        for (int i = 0; i < 45; i++) gui.setItem(i, border.clone());

        // Equipment display: helmet@4, chestplate@13, leggings@22, mainhand@23, boots@31, offhand@32
        setDisplayItem(gui, 4, clipboard.getHelmet());
        setDisplayItem(gui, 13, clipboard.getChestplate());
        setDisplayItem(gui, 22, clipboard.getLeggings());
        setDisplayItem(gui, 23, clipboard.getMainHand());
        setDisplayItem(gui, 31, clipboard.getBoots());
        setDisplayItem(gui, 32, clipboard.getOffHand());

        // Slot labels below each equipment slot (row 1: 0-8)
        gui.setItem(0, slotLabel(msg.get("clipboard.slot_label", "{slot}", msg.get("equipment_edit.slot_mainhand"))));
        gui.setItem(1, slotLabel(msg.get("clipboard.slot_label", "{slot}", msg.get("equipment_edit.slot_offhand"))));
        gui.setItem(3, slotLabel(msg.get("clipboard.slot_label", "{slot}", msg.get("equipment_edit.slot_helmet"))));
        gui.setItem(5, slotLabel(msg.get("clipboard.slot_label", "{slot}", msg.get("equipment_edit.slot_leggings"))));
        gui.setItem(7, slotLabel(msg.get("clipboard.slot_label", "{slot}", msg.get("equipment_edit.slot_boots"))));

        // Inventory items (if any) in row 4 (36-44)
        ItemStack[] inv = clipboard.getInventory();
        if (inv != null && inv.length > 0) {
            int displaySlots = Math.min(inv.length, 9);
            for (int i = 0; i < displaySlots; i++) {
                if (inv[i] != null && !inv[i].getType().isAir()) {
                    gui.setItem(36 + i, inv[i].clone());
                }
            }
            for (int i = 36 + displaySlots; i < 45; i++) gui.setItem(i, border.clone());
        }

        // Nav row (45-53)
        for (int i = 45; i <= 53; i++) gui.setItem(i, border.clone());

        // Entity info @49
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.setDisplayName("§e" + clipboard.getEntityName());
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            im.setLore(List.of(
                    msg.get("clipboard.entity_info",
                            "{type}", clipboard.getEntityType(),
                            "{name}", clipboard.getEntityName()),
                    msg.get("clipboard.timestamp",
                            "{time}", sdf.format(new Date(clipboard.getTimestamp())))));
            info.setItemMeta(im);
        }
        gui.setItem(49, info);

        player.openInventory(gui);
    }

    private static void setDisplayItem(Inventory gui, int slot, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            gui.setItem(slot, item.clone());
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() > maxLen ? s.substring(0, maxLen - 1) + "…" : s;
    }

    private static String buildTitle(String title) {
        if (title.length() > 32) title = title.substring(0, 32);
        return title;
    }
}

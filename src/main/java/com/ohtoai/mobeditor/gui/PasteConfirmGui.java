package com.ohtoai.mobeditor.gui;

import com.ohtoai.mobeditor.MobEditor;
import com.ohtoai.mobeditor.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Confirmation GUI for paste operations.
 * Shows paste blanks status and confirm/cancel buttons.
 */
public final class PasteConfirmGui {

    private static final int GUI_SIZE = 27;

    private PasteConfirmGui() {}

    private static ItemStack borderPane() {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) { meta.setDisplayName("§7"); border.setItemMeta(meta); }
        return border;
    }

    public static void open(Player editor, LivingEntity target) {
        open(editor, (Entity) target);
    }

    /**
     * Open paste confirmation for any entity (including InventoryHolder like minecarts).
     */
    public static void open(Player editor, Entity target) {
        MobEditor plugin = MobEditor.getInstance();
        Messages msg = plugin.getMessages();

        String entityName = target.getCustomName() != null
                ? target.getCustomName()
                : target.getType().getKey().getKey();
        if (entityName.length() > 16) entityName = entityName.substring(0, 15) + "…";

        String title = buildTitle("§8" + msg.get("paste.confirm_title", "{name}", entityName));

        GuiHolders.PasteConfirmHolder holder = new GuiHolders.PasteConfirmHolder(
                target.getUniqueId(), entityName, editor.getUniqueId());
        Inventory gui = Bukkit.createInventory(holder, GUI_SIZE, title);

        ItemStack border = borderPane();
        for (int i = 0; i < GUI_SIZE; i++) gui.setItem(i, border.clone());

        // Cancel button @11
        ItemStack cancelBtn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cm = cancelBtn.getItemMeta();
        if (cm != null) {
            cm.setDisplayName(msg.get("paste.cancel_button"));
            cm.setLore(List.of(msg.get("paste.cancel_button_lore")));
            cancelBtn.setItemMeta(cm);
        }
        gui.setItem(11, cancelBtn);

        // Paste blanks info @13
        boolean pasteBlanks = plugin.getModeManager().isPasteBlanks(editor);
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta im = infoItem.getItemMeta();
        if (im != null) {
            im.setDisplayName(msg.get("paste.paste_blanks_info",
                    "{value}", pasteBlanks ? msg.get("pasteblanks.on") : msg.get("pasteblanks.off")));
            im.setLore(List.of(pasteBlanks ? msg.get("paste.clear_empty") : msg.get("paste.skip_empty")));
            infoItem.setItemMeta(im);
        }
        gui.setItem(13, infoItem);

        // Confirm button @15
        ItemStack confirmBtn = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta cfm = confirmBtn.getItemMeta();
        if (cfm != null) {
            cfm.setDisplayName(msg.get("paste.confirm_button"));
            cfm.setLore(List.of(msg.get("paste.confirm_button_lore")));
            confirmBtn.setItemMeta(cfm);
        }
        gui.setItem(15, confirmBtn);

        editor.openInventory(gui);
    }

    /**
     * Execute the paste operation for any entity.
     */
    public static void executePaste(Player editor, GuiHolders.PasteConfirmHolder holder) {
        MobEditor plugin = MobEditor.getInstance();
        Messages msg = plugin.getMessages();

        Entity entity = Bukkit.getEntity(holder.entityUuid());
        if (entity == null) {
            editor.sendMessage(msg.get("paste.no_target"));
            return;
        }

        var clipboard = plugin.getClipboardManager().getClipboard(editor);
        if (clipboard == null || clipboard.isEmpty()) {
            editor.sendMessage(msg.get("clipboard.empty"));
            return;
        }

        boolean pasteBlanks = plugin.getModeManager().isPasteBlanks(editor);

        // Apply equipment slots (LivingEntity only)
        if (entity instanceof LivingEntity living && living.getEquipment() != null) {
            EntityEquipment equip = living.getEquipment();
            applySlot(equip::setHelmet, clipboard.getHelmet(), pasteBlanks);
            applySlot(equip::setChestplate, clipboard.getChestplate(), pasteBlanks);
            applySlot(equip::setLeggings, clipboard.getLeggings(), pasteBlanks);
            applySlot(equip::setBoots, clipboard.getBoots(), pasteBlanks);
            applySlot(equip::setItemInMainHand, clipboard.getMainHand(), pasteBlanks);
            applySlot(equip::setItemInOffHand, clipboard.getOffHand(), pasteBlanks);
        }

        // Apply inventory slots (any InventoryHolder)
        if (entity instanceof InventoryHolder invHolder && clipboard.getInventory() != null) {
            Inventory targetInv = invHolder.getInventory();
            ItemStack[] clipInv = clipboard.getInventory();
            int size = Math.min(clipInv.length, targetInv.getSize());
            for (int i = 0; i < size; i++) {
                final int slot = i;
                applySlot(s -> targetInv.setItem(slot, s), clipInv[i], pasteBlanks);
            }
        }

        String entityName = entity.getCustomName() != null
                ? entity.getCustomName()
                : entity.getType().getKey().getKey();
        editor.sendMessage(msg.get("paste.done", "{entity}", entityName));
    }

    @FunctionalInterface
    private interface SlotSetter {
        void set(ItemStack item);
    }

    private static void applySlot(SlotSetter setter, ItemStack clipItem, boolean pasteBlanks) {
        if (clipItem != null && !clipItem.getType().isAir()) {
            setter.set(clipItem.clone());
        } else if (pasteBlanks) {
            setter.set(null);
        }
        // else: skip — leave existing item
    }

    private static String buildTitle(String title) {
        if (title.length() > 32) title = title.substring(0, 32);
        return title;
    }
}

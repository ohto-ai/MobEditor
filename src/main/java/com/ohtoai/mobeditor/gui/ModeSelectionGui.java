package com.ohtoai.mobeditor.gui;

import com.ohtoai.mobeditor.MobEditor;
import com.ohtoai.mobeditor.config.Messages;
import com.ohtoai.mobeditor.mode.EditorMode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;

import java.util.List;

/**
 * Mode selection GUI shown on Shift+right-click.
 * 27-slot GUI with 4 mode buttons + paste blanks toggle.
 */
public final class ModeSelectionGui {

    private static final int GUI_SIZE = 27;

    private ModeSelectionGui() {}

    private static ItemStack borderPane() {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7");
            border.setItemMeta(meta);
        }
        return border;
    }

    private static ItemStack createModeButton(Material material, String name, List<String> lore, boolean selected) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            if (selected) {
                // Add enchantment glint to indicate selection
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void open(Player player, EditorMode currentMode, boolean pasteBlanks) {
        MobEditor plugin = MobEditor.getInstance();
        Messages msg = plugin.getMessages();

        GuiHolders.ModeSelectionHolder holder = new GuiHolders.ModeSelectionHolder(player.getUniqueId());
        Inventory gui = Bukkit.createInventory(holder, GUI_SIZE, buildTitle(msg.get("mode_selection.title")));

        ItemStack border = borderPane();

        // Fill with borders
        for (int i = 0; i < GUI_SIZE; i++) gui.setItem(i, border.clone());

        // Mode buttons on row 1 (slots 9-17)
        // OPEN_EQUIPMENT @10
        gui.setItem(10, createModeButton(
                Material.DIAMOND_CHESTPLATE,
                msg.get("mode_selection.open_equipment"),
                List.of(msg.get("mode_selection.open_equipment_lore")),
                currentMode == EditorMode.OPEN_EQUIPMENT));

        // COPY @12
        gui.setItem(12, createModeButton(
                Material.BOOK,
                msg.get("mode_selection.copy"),
                List.of(msg.get("mode_selection.copy_lore")),
                currentMode == EditorMode.COPY));

        // PASTE @14
        gui.setItem(14, createModeButton(
                Material.PAPER,
                msg.get("mode_selection.paste"),
                List.of(msg.get("mode_selection.paste_lore")),
                currentMode == EditorMode.PASTE));

        // EXTRACT_EGG @16
        Material eggMat = Material.matchMaterial("CREEPER_SPAWN_EGG");
        if (eggMat == null) eggMat = Material.EGG;
        gui.setItem(16, createModeButton(
                eggMat,
                msg.get("mode_selection.extract_egg"),
                List.of(msg.get("mode_selection.extract_egg_lore")),
                currentMode == EditorMode.EXTRACT_EGG));

        // Current mode indicator @22
        ItemStack infoItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta im = infoItem.getItemMeta();
        if (im != null) {
            im.setDisplayName(msg.get("mode_selection.current_mode", "{mode}", currentMode.getDisplayName()));
            infoItem.setItemMeta(im);
        }
        gui.setItem(22, infoItem);

        // Paste blanks toggle @4
        Material toggleMat = pasteBlanks ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack toggleItem = new ItemStack(toggleMat);
        ItemMeta tm = toggleItem.getItemMeta();
        if (tm != null) {
            tm.setDisplayName(msg.get("mode_selection.paste_blanks_toggle",
                    "{value}", pasteBlanks ? msg.get("pasteblanks.on") : msg.get("pasteblanks.off")));
            tm.setLore(List.of(msg.get("mode_selection.paste_blanks_toggle_lore")));
            toggleItem.setItemMeta(tm);
        }
        gui.setItem(4, toggleItem);

        player.openInventory(gui);
    }

    /**
     * Handle click in the mode selection GUI.
     * Returns true if the GUI should stay open, false if it should close.
     */
    public static boolean handleClick(Player player, int slot) {
        MobEditor plugin = MobEditor.getInstance();
        Messages msg = plugin.getMessages();

        return switch (slot) {
            case 10 -> { // OPEN_EQUIPMENT
                plugin.getModeManager().setMode(player, EditorMode.OPEN_EQUIPMENT);
                player.sendMessage(msg.get("mode.set", "{mode}", EditorMode.OPEN_EQUIPMENT.getDisplayName()));
                yield false;
            }
            case 12 -> { // COPY
                plugin.getModeManager().setMode(player, EditorMode.COPY);
                player.sendMessage(msg.get("mode.set", "{mode}", EditorMode.COPY.getDisplayName()));
                yield false;
            }
            case 14 -> { // PASTE
                plugin.getModeManager().setMode(player, EditorMode.PASTE);
                player.sendMessage(msg.get("mode.set", "{mode}", EditorMode.PASTE.getDisplayName()));
                yield false;
            }
            case 16 -> { // EXTRACT_EGG
                plugin.getModeManager().setMode(player, EditorMode.EXTRACT_EGG);
                player.sendMessage(msg.get("mode.set", "{mode}", EditorMode.EXTRACT_EGG.getDisplayName()));
                yield false;
            }
            case 4 -> { // Paste blanks toggle
                plugin.getModeManager().togglePasteBlanks(player);
                boolean newVal = plugin.getModeManager().isPasteBlanks(player);
                player.sendMessage(newVal ? msg.get("pasteblanks.set_on") : msg.get("pasteblanks.set_off"));
                // Reopen GUI with updated state
                open(player, plugin.getModeManager().getMode(player), newVal);
                yield true; // Keep GUI open (we just reopened it)
            }
            default -> true; // Clicking border — do nothing, keep open
        };
    }

    private static String buildTitle(String title) {
        if (title.length() > 32) title = title.substring(0, 32);
        return title;
    }
}

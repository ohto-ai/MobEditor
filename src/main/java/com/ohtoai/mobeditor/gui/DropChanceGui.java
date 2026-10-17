package com.ohtoai.mobeditor.gui;

import com.ohtoai.mobeditor.MobEditor;
import com.ohtoai.mobeditor.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens a Paper-native anvil GUI for editing drop chances of individual equipment slots.
 * <p>
 * Right-clicking an equipment slot in the entity edit GUI opens an anvil with the
 * current drop chance shown as a paper item. The player renames the paper to the
 * desired value (0.0–1.0) and either clicks the result or presses ESC to commit.
 * <p>
 * The typed value is read from the <b>result slot (slot 2)</b> item's display name
 * on inventory close — not from {@code getRenameText()}, which vanilla clears before
 * firing {@code InventoryCloseEvent}.
 *
 * @see <a href="https://minecraft.wiki/w/Mob#ArmorDropChances">Minecraft Wiki — Drop Chances</a>
 */
public final class DropChanceGui {

    /**
     * Maps GUI equipment slot indices to the corresponding {@link EquipmentSlot}
     * and a Chinese display name for the anvil title / messages.
     */
    private static final Map<Integer, SlotInfo> SLOT_MAP = Map.of(
            4,  new SlotInfo(EquipmentSlot.HEAD,      "头盔"),
            13, new SlotInfo(EquipmentSlot.CHEST,     "胸甲"),
            22, new SlotInfo(EquipmentSlot.LEGS,      "护腿"),
            23, new SlotInfo(EquipmentSlot.HAND,      "主手"),
            31, new SlotInfo(EquipmentSlot.FEET,      "靴子"),
            32, new SlotInfo(EquipmentSlot.OFF_HAND,  "副手")
    );

    /**
     * Tracks active drop chance editing sessions, keyed by editor UUID.
     * Populated when the anvil opens, consumed on close.
     */
    private static final Map<UUID, PendingEdit> PENDING = new ConcurrentHashMap<>();

    private record SlotInfo(EquipmentSlot equipSlot, String displayName) {}

    /**
     * Holds the target entity and slot while the anvil is open.
     */
    public record PendingEdit(UUID entityUuid, EquipmentSlot equipSlot, String slotName) {}

    private DropChanceGui() {}

    // ── Public API ────────────────────────────────────────────────────────

    public static boolean isEquipmentGuiSlot(int slot) {
        return SLOT_MAP.containsKey(slot);
    }

    /**
     * Returns and removes the pending edit for the given player, or {@code null}.
     */
    public static PendingEdit consumePending(Player player) {
        return PENDING.remove(player.getUniqueId());
    }

    /**
     * Opens the Paper-native anvil for the given entity's equipment slot.
     *
     * @param player  the editor
     * @param entity  the target entity (must be a LivingEntity with equipment)
     * @param guiSlot the GUI slot index (4/13/22/23/31/32)
     */
    public static void open(Player player, LivingEntity entity, int guiSlot) {
        SlotInfo info = SLOT_MAP.get(guiSlot);
        if (info == null) return;

        EntityEquipment equip = entity.getEquipment();
        if (equip == null) return;

        float current = equip.getDropChance(info.equipSlot);

        // Store pending edit so onClose knows what to apply
        PENDING.put(player.getUniqueId(),
                new PendingEdit(entity.getUniqueId(), info.equipSlot, info.displayName));

        // Paper-native anvil — gives us the real rename text field
        InventoryView anvilView = player.openAnvil(null, true);
        if (anvilView == null) {
            PENDING.remove(player.getUniqueId());
            return;
        }

        // Set the anvil title
        String title = "§8设置" + info.displayName + "掉落率";
        if (title.length() > 32) title = title.substring(0, 32);
        anvilView.setTitle(title);

        // Put the current value as a paper item in the first slot.
        // The player renames this in the anvil UI.
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text(String.valueOf(current)));
            meta.lore(List.of(
                    net.kyori.adventure.text.Component.text("§7当前值: §f" + current),
                    net.kyori.adventure.text.Component.text("§7在铁砧中重命名此项来设置新值"),
                    net.kyori.adventure.text.Component.text("§7范围: §f0.0 §7~ §f1.0")
            ));
            paper.setItemMeta(meta);
        }
        anvilView.setItem(0, paper);
    }

    /**
     * Called from onClose — reads the result slot (slot 2) display name,
     * parses it, and applies the drop chance if valid.
     *
     * @param player       the editor
     * @param closedInv    the anvil inventory being closed
     * @param pendingEdit  the pending edit info
     */
    public static void handleAnvilClose(Player player, Inventory closedInv, PendingEdit pendingEdit) {
        // Read the typed value from the result slot (slot 2).
        // The vanilla anvil copies the input item to slot 2 with the renamed display name.
        ItemStack result = closedInv.getItem(2);
        String displayName = null;
        if (result != null && result.hasItemMeta()) {
            ItemMeta meta = result.getItemMeta();
            if (meta.hasDisplayName()) {
                // Convert the Adventure displayName Component to legacy text
                displayName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection().serialize(meta.displayName()).trim();
            }
        }

        // Clear the anvil so the input paper isn't returned to the player
        closedInv.clear();

        if (displayName == null || displayName.isEmpty()) {
            // Player typed nothing or cleared the field — treat as cancel
            return;
        }

        // Strip Minecraft color codes (§x) before parsing
        String stripped = displayName != null
                ? displayName.replaceAll("§[0-9a-fk-or]", "").trim()
                : displayName;

        try {
            float value = Float.parseFloat(stripped.trim());
            if (value < 0f || value > 1f) return;
            applyDropChance(player, pendingEdit, value);
        } catch (NumberFormatException ignored) {
            // Invalid number — silently ignore
        }
    }

    /**
     * Applies the drop chance value to the target entity.
     */
    public static void applyDropChance(Player player, PendingEdit pendingEdit, float value) {
        Entity entity = Bukkit.getEntity(pendingEdit.entityUuid());
        if (!(entity instanceof LivingEntity living) || !living.isValid()) {
            player.sendMessage("§c目标实体已不存在。");
            return;
        }

        EntityEquipment equip = living.getEquipment();
        if (equip == null) {
            player.sendMessage("§c目标实体没有装备栏。");
            return;
        }

        equip.setDropChance(pendingEdit.equipSlot(), value);

        Messages msg = MobEditor.getInstance().getMessages();
        player.sendMessage(msg.get("drop_chance.set_done",
                "{slot}", pendingEdit.slotName(),
                "{value}", String.valueOf(value)));
    }

    /**
     * Reopens the entity edit GUI for the player after the drop chance anvil is closed.
     */
    public static void reopenEntityEdit(Player player, UUID entityUuid) {
        Entity entity = Bukkit.getEntity(entityUuid);
        if (entity != null) {
            EntityEditGui.open(player, entity);
        }
    }
}

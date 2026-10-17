package com.ohtoai.mobeditor.gui;

import com.ohtoai.mobeditor.MobEditor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/**
 * Single listener for ALL MobEditor custom GUIs.
 * Dispatches to appropriate handler based on holder type.
 */
public class GuiListener implements Listener {

    private final MobEditor plugin;
    private static final int FULL_GUI = 54; // equipment / combined views use 54-slot GUI

    /** GUI slots that hold equipment (right-click → drop chance editor). */
    private static final java.util.Set<Integer> EQUIP_SLOTS = java.util.Set.of(4, 13, 22, 23, 31, 32);

    public GuiListener(MobEditor plugin) {
        this.plugin = plugin;
    }

    // ── Click ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // ── ModeSelection GUI ──
        if (top.getHolder() instanceof GuiHolders.ModeSelectionHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= top.getSize()) return;
            boolean keepOpen = ModeSelectionGui.handleClick(player, slot);
            if (!keepOpen) {
                plugin.getServer().getScheduler().runTask(plugin, (Runnable) player::closeInventory);
            }
            return;
        }

        // ── EntityEdit GUI ──
        if (top.getHolder() instanceof GuiHolders.EntityEditHolder holder) {
            int slot = event.getRawSlot();
            int guiSize = top.getSize();
            boolean isTopInventory = slot >= 0 && slot < guiSize;
            boolean isFullGui = guiSize >= FULL_GUI;

            // Nav row — only in 54-slot equipment/combined views
            if (isFullGui && isTopInventory && slot >= 45) {
                event.setCancelled(true);
                if (slot == 48) { // Refresh
                    EntityEditGui.refresh(player, holder);
                }
                return;
            }

            // Block destructive actions
            ClickType click = event.getClick();
            if (click == ClickType.NUMBER_KEY || click == ClickType.DROP
                    || click == ClickType.CONTROL_DROP || click == ClickType.SWAP_OFFHAND) {
                event.setCancelled(true);
                return;
            }

            // Block non-editable slots
            if (isTopInventory && EntityEditGui.isNonEditableSlot(holder, slot, guiSize)) {
                event.setCancelled(true);
                return;
            }

            // Right-click on equipment slot → open drop chance editor
            if (click == ClickType.RIGHT && isTopInventory && isFullGui
                    && EQUIP_SLOTS.contains(slot)) {
                event.setCancelled(true);
                Entity entity = Bukkit.getEntity(holder.entityUuid());
                if (entity instanceof LivingEntity living && living.getEquipment() != null) {
                    // Sync any pending changes first
                    scheduleSync(player, top, holder);
                    // Open drop chance anvil (Paper-native)
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        DropChanceGui.open(player, living, slot);
                    });
                }
                return;
            }

            // Allowed edit — schedule sync
            scheduleSync(player, top, holder);
            return;
        }

        // ── CopyDisplay GUI (read-only) ──
        if (top.getHolder() instanceof GuiHolders.CopyDisplayHolder) {
            event.setCancelled(true);
            return;
        }

        // ── PasteConfirm GUI ──
        if (top.getHolder() instanceof GuiHolders.PasteConfirmHolder pasteHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == 11) {
                player.closeInventory();
            } else if (slot == 15) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    PasteConfirmGui.executePaste(player, pasteHolder);
                    player.closeInventory();
                });
            }
            return;
        }
    }

    // ── Drag ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        Player player = (Player) event.getWhoClicked();

        if (top.getHolder() instanceof GuiHolders.EntityEditHolder holder) {
            int guiSize = top.getSize();
            boolean isFullGui = guiSize >= FULL_GUI;
            int navStart = isFullGui ? 45 : guiSize; // nav row only in 54-slot GUIs

            boolean blocked = false;
            for (int slot : event.getRawSlots()) {
                if (slot >= guiSize) continue; // bottom inventory — allow
                if (slot >= navStart) { blocked = true; break; }
                if (EntityEditGui.isNonEditableSlot(holder, slot, guiSize)) { blocked = true; break; }
            }

            if (blocked) {
                event.setCancelled(true);
                return;
            }

            scheduleSync(player, top, holder);
            return;
        }

        // All other custom GUIs: cancel all drags
        if (top.getHolder() instanceof GuiHolders.ModeSelectionHolder
                || top.getHolder() instanceof GuiHolders.CopyDisplayHolder
                || top.getHolder() instanceof GuiHolders.PasteConfirmHolder) {
            event.setCancelled(true);
        }
    }

    // ── Close ────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        Player player = (Player) event.getPlayer();

        // ── Drop chance anvil (Paper-native, no custom holder) ──
        if (top.getType() == InventoryType.ANVIL) {
            DropChanceGui.PendingEdit pending = DropChanceGui.consumePending(player);
            if (pending != null) {
                DropChanceGui.handleAnvilClose(player, top, pending);
                // Reopen the entity edit GUI after anvil closes
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    DropChanceGui.reopenEntityEdit(player, pending.entityUuid());
                });
            }
            return;
        }

        if (top.getHolder() instanceof GuiHolders.EntityEditHolder holder) {
            // Final sync on close
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                EntityEditGui.syncToTarget(player, top, holder);
            });
        }
    }

    // ── Sync helper ─────────────────────────────────────────────────────

    private void scheduleSync(Player editor, Inventory top, GuiHolders.EntityEditHolder holder) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            InventoryView openInv = editor.getOpenInventory();
            if (openInv.getTopInventory().getHolder() instanceof GuiHolders.EntityEditHolder currentHolder
                    && currentHolder.equals(holder)) {
                EntityEditGui.syncToTarget(editor, top, holder);
            }
        });
    }
}

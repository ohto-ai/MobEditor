package com.ohtoai.mobeditor.listener;

import com.ohtoai.mobeditor.MobEditor;
import com.ohtoai.mobeditor.clipboard.EquipmentClipboard;
import com.ohtoai.mobeditor.config.Messages;
import com.ohtoai.mobeditor.egg.SpawnEggExtractor;
import com.ohtoai.mobeditor.gui.*;
import com.ohtoai.mobeditor.mode.EditorMode;
import com.ohtoai.mobeditor.tool.ToolConfig;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Core listener: intercepts right-click on entities when holding the configured tool.
 * Shift+right-click → mode selection GUI.
 * Normal right-click → execute current mode.
 */
public class ToolUseListener implements Listener {

    private final MobEditor plugin;

    public ToolUseListener(MobEditor plugin) {
        this.plugin = plugin;
    }

    // ── Entity right-click interception ───────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        // Only trigger from main hand
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (!shouldActivate(held, player)) return;

        // Cancel default interaction (shearing, trading, equipping, etc.)
        event.setCancelled(true);

        Entity target = event.getRightClicked();

        // Accept any entity that is editable: LivingEntity (equipment/inventory)
        // or InventoryHolder (minecarts, etc.)
        if (!(target instanceof LivingEntity) && !(target instanceof InventoryHolder)) return;

        if (player.isSneaking()) {
            // Shift+right-click → mode selection
            ModeSelectionGui.open(player,
                    plugin.getModeManager().getMode(player),
                    plugin.getModeManager().isPasteBlanks(player));
        } else {
            // Normal right-click → execute current mode
            EditorMode mode = plugin.getModeManager().getMode(player);
            executeMode(player, target, mode);
        }
    }

    // ── Prevent tool from being used for other purposes ───────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (shouldActivate(held, player)) {
            event.setCancelled(true); // Prevent block placement / item usage
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean shouldActivate(ItemStack held, Player player) {
        ToolConfig toolConfig = plugin.getToolConfig();
        if (!toolConfig.isEnabled()) return false;
        if (!toolConfig.matches(held)) return false;
        return player.hasPermission("mobeditor.use");
    }

    private void executeMode(Player player, Entity entity, EditorMode mode) {
        switch (mode) {
            case OPEN_EQUIPMENT -> executeOpenEquipment(player, entity);
            case COPY -> executeCopy(player, entity);
            case PASTE -> executePaste(player, entity);
            case EXTRACT_EGG -> executeExtractEgg(player, entity);
        }
    }

    private void executeOpenEquipment(Player player, Entity entity) {
        Messages msg = plugin.getMessages();

        boolean hasEquip = entity instanceof LivingEntity le && le.getEquipment() != null;
        boolean hasInv = entity instanceof InventoryHolder
                && ((InventoryHolder) entity).getInventory().getSize() > 0;

        if (!hasEquip && !hasInv) {
            player.sendMessage(msg.get("general.no_entity"));
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> EntityEditGui.open(player, entity));
    }

    private void executeCopy(Player player, Entity entity) {
        Messages msg = plugin.getMessages();

        String entityName = entity.getCustomName() != null
                ? entity.getCustomName()
                : entity.getType().getKey().getKey();

        EquipmentClipboard clipboard = new EquipmentClipboard(
                entity.getType().getKey().getKey(), entityName);

        // Copy equipment if available
        if (entity instanceof LivingEntity living && living.getEquipment() != null) {
            EntityEquipment equip = living.getEquipment();
            clipboard.setHelmet(equip.getHelmet());
            clipboard.setChestplate(equip.getChestplate());
            clipboard.setLeggings(equip.getLeggings());
            clipboard.setBoots(equip.getBoots());
            clipboard.setMainHand(equip.getItemInMainHand());
            clipboard.setOffHand(equip.getItemInOffHand());
        }

        // Copy inventory if available
        if (entity instanceof InventoryHolder invHolder) {
            Inventory inv = invHolder.getInventory();
            if (inv.getSize() > 0) {
                ItemStack[] items = new ItemStack[inv.getSize()];
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    items[i] = (item != null && !item.getType().isAir()) ? item.clone() : null;
                }
                clipboard.setInventory(items);
            }
        }

        if (clipboard.isEmpty()) {
            player.sendMessage(msg.get("general.no_entity"));
            return;
        }

        plugin.getClipboardManager().setClipboard(player, clipboard);
        player.sendMessage(msg.get("clipboard.copied", "{entity}", entityName));

        plugin.getServer().getScheduler().runTask(plugin, () ->
                CopyDisplayGui.open(player, clipboard));
    }

    private void executePaste(Player player, Entity entity) {
        Messages msg = plugin.getMessages();

        var clipboard = plugin.getClipboardManager().getClipboard(player);
        if (clipboard == null || clipboard.isEmpty()) {
            player.sendMessage(msg.get("clipboard.empty"));
            return;
        }

        // Entity must have either equipment or inventory to paste onto
        boolean canEquip = entity instanceof LivingEntity le && le.getEquipment() != null;
        boolean canInv = entity instanceof InventoryHolder;

        if (!canEquip && !canInv) {
            player.sendMessage(msg.get("paste.no_target"));
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            PasteConfirmGui.open(player, entity);
        });
    }

    private void executeExtractEgg(Player player, Entity entity) {
        Messages msg = plugin.getMessages();

        String entityName = entity.getCustomName() != null
                ? entity.getCustomName()
                : entity.getType().getKey().getKey();

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                ItemStack egg = SpawnEggExtractor.createSpawnEgg(entity);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    var overflow = player.getInventory().addItem(egg);
                    if (!overflow.isEmpty()) {
                        for (ItemStack drop : overflow.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                    }
                    player.sendMessage(msg.get("egg.extracted", "{entity}", entityName));
                    player.sendMessage(msg.get("egg.given"));
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to extract spawn egg: " + e.getMessage());
                e.printStackTrace();
                player.sendMessage("§c提取刷怪蛋失败: " + e.getMessage());
            }
        });
    }
}

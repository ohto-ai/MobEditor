package com.ohtoai.mobeditor.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Holder types for all MobEditor custom GUIs.
 */
public final class GuiHolders {

    /** Mode selection GUI (Shift+right-click) */
    public record ModeSelectionHolder(UUID playerUuid) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }

    /** Equipment editing GUI */
    public record EntityEditHolder(UUID entityUuid, String entityName, UUID editorUuid,
                                   boolean hasInventory, int invSize) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }

    /** Copy display GUI (read-only) */
    public record CopyDisplayHolder(UUID playerUuid) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }

    /** Paste confirmation GUI */
    public record PasteConfirmHolder(UUID entityUuid, String entityName, UUID editorUuid) implements InventoryHolder {
        @Override public @NotNull Inventory getInventory() { return null; }
    }
}

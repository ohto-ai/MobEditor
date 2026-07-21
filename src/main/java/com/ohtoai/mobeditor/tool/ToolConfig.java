package com.ohtoai.mobeditor.tool;

import com.ohtoai.mobeditor.config.ConfigManager;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Base64;

/**
 * Holds the configured tool item and enabled flag.
 * Loads from / persists to ConfigManager.
 */
public class ToolConfig {

    private final ConfigManager configManager;
    private ItemStack toolItem;
    private boolean enabled;

    public ToolConfig(ConfigManager configManager) {
        this.configManager = configManager;
        loadFromConfig();
    }

    private void loadFromConfig() {
        this.enabled = configManager.isToolEnabled();
        String base64 = configManager.getToolItemBase64();
        if (base64 != null && !base64.isEmpty()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(base64);
                this.toolItem = ItemStack.deserializeBytes(bytes);
            } catch (Exception e) {
                this.toolItem = null;
                this.enabled = false;
            }
        }
    }

    public void reload() {
        loadFromConfig();
    }

    public ItemStack getToolItem() {
        return toolItem;
    }

    public boolean isEnabled() {
        return enabled && toolItem != null && !toolItem.getType().isAir();
    }

    public void setToolItem(ItemStack item) {
        this.toolItem = item.clone();
        this.enabled = true;
        byte[] bytes = item.serializeAsBytes();
        configManager.setToolItemBase64(Base64.getEncoder().encodeToString(bytes));
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        configManager.setToolEnabled(enabled);
    }

    public void clear() {
        this.toolItem = null;
        this.enabled = false;
        configManager.clearTool();
    }

    /**
     * Checks if the held item exactly matches the configured tool.
     * Uses Paper's serializeAsBytes() for byte-level exact comparison.
     */
    public boolean matches(ItemStack held) {
        if (!enabled || toolItem == null || toolItem.getType().isAir()) return false;
        if (held == null || held.getType().isAir()) return false;
        // Byte-level exact comparison ensures all properties match
        return Arrays.equals(held.serializeAsBytes(), toolItem.serializeAsBytes());
    }
}

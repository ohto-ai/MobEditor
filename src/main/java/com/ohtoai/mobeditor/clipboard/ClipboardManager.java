package com.ohtoai.mobeditor.clipboard;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player in-memory clipboard storage.
 * Each player has ONE clipboard slot. Cleared on logout.
 */
public class ClipboardManager {

    private final Map<UUID, EquipmentClipboard> clipboards = new ConcurrentHashMap<>();

    public EquipmentClipboard getClipboard(Player player) {
        return clipboards.get(player.getUniqueId());
    }

    public void setClipboard(Player player, EquipmentClipboard clipboard) {
        clipboards.put(player.getUniqueId(), clipboard);
    }

    public boolean hasClipboard(Player player) {
        EquipmentClipboard cb = clipboards.get(player.getUniqueId());
        return cb != null && !cb.isEmpty();
    }

    public void clearClipboard(Player player) {
        clipboards.remove(player.getUniqueId());
    }

    public void clearAll() {
        clipboards.clear();
    }

    /** Called on PlayerQuitEvent to clean up */
    public void removePlayer(UUID uuid) {
        clipboards.remove(uuid);
    }
}

package com.ohtoai.mobeditor.mode;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player mode and paste-blanks toggle.
 * All state is in-memory and cleared on logout.
 */
public class ModeManager {

    private final Map<UUID, EditorMode> playerModes = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pasteBlanks = new ConcurrentHashMap<>();

    public EditorMode getMode(Player player) {
        return playerModes.getOrDefault(player.getUniqueId(), EditorMode.OPEN_EQUIPMENT);
    }

    public void setMode(Player player, EditorMode mode) {
        playerModes.put(player.getUniqueId(), mode);
    }

    public boolean isPasteBlanks(Player player) {
        return pasteBlanks.getOrDefault(player.getUniqueId(), false);
    }

    public void setPasteBlanks(Player player, boolean value) {
        pasteBlanks.put(player.getUniqueId(), value);
    }

    public void togglePasteBlanks(Player player) {
        setPasteBlanks(player, !isPasteBlanks(player));
    }

    public void removePlayer(UUID uuid) {
        playerModes.remove(uuid);
        pasteBlanks.remove(uuid);
    }
}

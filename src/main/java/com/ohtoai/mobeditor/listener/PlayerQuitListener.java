package com.ohtoai.mobeditor.listener;

import com.ohtoai.mobeditor.MobEditor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerKickEvent;

/**
 * Clean up per-player state on disconnect.
 * Clipboard and mode preferences are cleared.
 */
public class PlayerQuitListener implements Listener {

    private final MobEditor plugin;

    public PlayerQuitListener(MobEditor plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    private void cleanup(java.util.UUID uuid) {
        plugin.getClipboardManager().removePlayer(uuid);
        plugin.getModeManager().removePlayer(uuid);
    }
}

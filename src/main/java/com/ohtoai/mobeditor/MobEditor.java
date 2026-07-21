package com.ohtoai.mobeditor;

import com.ohtoai.mobeditor.clipboard.ClipboardManager;
import com.ohtoai.mobeditor.command.CommandRouter;
import com.ohtoai.mobeditor.command.MEditorTabCompleter;
import com.ohtoai.mobeditor.config.ConfigManager;
import com.ohtoai.mobeditor.config.Messages;
import com.ohtoai.mobeditor.gui.GuiListener;
import com.ohtoai.mobeditor.listener.PlayerQuitListener;
import com.ohtoai.mobeditor.listener.ToolUseListener;
import com.ohtoai.mobeditor.mode.ModeManager;
import com.ohtoai.mobeditor.tool.ToolConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class MobEditor extends JavaPlugin {

    private static MobEditor instance;

    private ConfigManager configManager;
    private Messages messages;
    private ToolConfig toolConfig;
    private ModeManager modeManager;
    private ClipboardManager clipboardManager;
    private CommandRouter commandRouter;

    public static MobEditor getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info(" __  __     _     ____    _____    _  __          _");
        getLogger().info("|  \\/  |   | |   |  _ \\  | ____|  | |/ /   ___   | |__     ___   _ __");
        getLogger().info("| |\\/| |   | |   | |_) | |  _|    | ' /   / _ \\  |  _ \\   / _ \\ | '__|");
        getLogger().info("| |  | |   | |   |  _ <  | |___   | . \\  | (_) | | |_) | |  __/ | |");
        getLogger().info("|_|  |_|   |_|   |_| \\_\\ |_____|  |_|\\_\\  \\___/  |_.__/   \\___| |_|");
        getLogger().info("");
        getLogger().info("  MobEditor v" + getPluginMeta().getVersion() + " — Ready");

        // 1. Load configuration
        try {
            configManager = new ConfigManager(this);
            configManager.load();
            messages = new Messages(this);
            getLogger().info(messages.getRaw("plugin.config_loaded"));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Config failed!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Initialize managers
        toolConfig = new ToolConfig(configManager);
        modeManager = new ModeManager();
        clipboardManager = new ClipboardManager();

        // 3. Register listeners
        ToolUseListener toolUseListener = new ToolUseListener(this);
        getServer().getPluginManager().registerEvents(toolUseListener, this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getLogger().info(messages.getRaw("plugin.listener_registered"));

        // 4. Register commands
        commandRouter = new CommandRouter(this);
        Objects.requireNonNull(getCommand("mobeditor")).setExecutor(commandRouter);
        Objects.requireNonNull(getCommand("mobeditor")).setTabCompleter(new MEditorTabCompleter());
        getLogger().info(messages.getRaw("plugin.commands_registered"));

        getLogger().info(messages.getRaw("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        if (clipboardManager != null) {
            clipboardManager.clearAll();
        }
        getLogger().info("MobEditor disabled.");
    }

    // --- Getters ---

    public ConfigManager getConfigManager() { return configManager; }
    public Messages getMessages() { return messages; }
    public ToolConfig getToolConfig() { return toolConfig; }
    public ModeManager getModeManager() { return modeManager; }
    public ClipboardManager getClipboardManager() { return clipboardManager; }
    public CommandRouter getCommandRouter() { return commandRouter; }
}

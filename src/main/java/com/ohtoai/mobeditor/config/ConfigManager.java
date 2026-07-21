package com.ohtoai.mobeditor.config;

import com.ohtoai.mobeditor.MobEditor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class ConfigManager {

    private final MobEditor plugin;
    private File configFile;
    private FileConfiguration config;

    public ConfigManager(MobEditor plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        configFile = new File(plugin.getDataFolder(), "config.yml");

        // Create default config if not exists
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Merge with defaults from jar
        InputStream defaultStream = plugin.getResource("config.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
            // Save any missing keys
            boolean needsSave = false;
            for (String key : defaultConfig.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defaultConfig.get(key));
                    needsSave = true;
                }
            }
            if (needsSave) {
                save();
            }
        }
    }

    public void reload() {
        if (configFile == null) {
            load();
            return;
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        InputStream defaultStream = plugin.getResource("config.yml");
        if (defaultStream != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
        }
    }

    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config.yml!", e);
        }
    }

    public FileConfiguration getConfigRaw() {
        return config;
    }

    public boolean isToolEnabled() {
        return config.getBoolean("tool.enabled", false);
    }

    public void setToolEnabled(boolean enabled) {
        config.set("tool.enabled", enabled);
        save();
    }

    public String getToolItemBase64() {
        return config.getString("tool.item", "");
    }

    public void setToolItemBase64(String base64) {
        config.set("tool.item", base64 != null ? base64 : "");
        config.set("tool.enabled", base64 != null && !base64.isEmpty());
        save();
    }

    public void clearTool() {
        config.set("tool.enabled", false);
        config.set("tool.item", "");
        save();
    }
}

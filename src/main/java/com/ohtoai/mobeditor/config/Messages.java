package com.ohtoai.mobeditor.config;

import com.ohtoai.mobeditor.MobEditor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Centralized Chinese (zh_CN) message provider.
 * Loads messages_zh.yml from the plugin jar.
 * Supports {key} placeholder replacement.
 */
public final class Messages {

    private final MobEditor plugin;
    private YamlConfiguration messages;

    public Messages(MobEditor plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        InputStream stream = plugin.getResource("messages_zh.yml");
        if (stream == null) {
            plugin.getLogger().warning("messages_zh.yml not found, falling back to hardcoded strings.");
            messages = new YamlConfiguration();
            return;
        }
        messages = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    /**
     * Get a message by dot-path key. Replace {key} placeholders.
     */
    public String get(String path, String... replacements) {
        String value = messages.getString(path);
        if (value == null) {
            return "<<" + path + ">>";
        }
        if (replacements != null) {
            for (int i = 0; i < replacements.length - 1; i += 2) {
                value = value.replace(replacements[i], replacements[i + 1]);
            }
        }
        return value;
    }

    /**
     * Get raw message without placeholder replacement.
     */
    public String getRaw(String path) {
        String value = messages.getString(path);
        return value != null ? value : "<<" + path + ">>";
    }

    /**
     * Reload messages from jar.
     */
    public void reload() {
        load();
    }
}

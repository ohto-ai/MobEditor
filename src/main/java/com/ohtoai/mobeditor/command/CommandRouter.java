package com.ohtoai.mobeditor.command;

import com.ohtoai.mobeditor.MobEditor;
import com.ohtoai.mobeditor.config.Messages;
import com.ohtoai.mobeditor.mode.EditorMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Routes /mobeditor (alias /me) commands to sub-handlers.
 * Follows the same pattern as the reference project's CommandRouter.
 */
public class CommandRouter implements CommandExecutor {

    private final MobEditor plugin;

    public CommandRouter(MobEditor plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "tool" -> handleTool(sender, shift(args));
            case "mode" -> handleMode(sender, shift(args));
            case "pasteblanks" -> handlePasteBlanks(sender, shift(args));
            case "reload" -> handleReload(sender);
            default -> { sendHelp(sender); yield true; }
        };
    }

    // ── tool ────────────────────────────────────────────────────────────

    private boolean handleTool(CommandSender sender, String[] args) {
        Messages msg = plugin.getMessages();

        if (!sender.hasPermission("mobeditor.admin")) {
            sender.sendMessage(msg.get("tool.no_perm"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§e用法: /me tool <set|disable|info|clear>");
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "set" -> handleToolSet(sender);
            case "disable" -> handleToolDisable(sender);
            case "info" -> handleToolInfo(sender);
            case "clear" -> handleToolClear(sender);
            default -> {
                sender.sendMessage("§e用法: /me tool <set|disable|info|clear>");
                yield true;
            }
        };
    }

    private boolean handleToolSet(CommandSender sender) {
        Messages msg = plugin.getMessages();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.get("general.player_only"));
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage(msg.get("tool.set_failed"));
            return true;
        }

        plugin.getToolConfig().setToolItem(held);
        player.sendMessage(msg.get("tool.set_done"));
        return true;
    }

    private boolean handleToolDisable(CommandSender sender) {
        Messages msg = plugin.getMessages();
        plugin.getToolConfig().setEnabled(false);
        sender.sendMessage(msg.get("tool.disabled"));
        return true;
    }

    private boolean handleToolInfo(CommandSender sender) {
        Messages msg = plugin.getMessages();
        var tc = plugin.getToolConfig();

        sender.sendMessage(msg.get("tool.info_header"));
        sender.sendMessage(msg.get("tool.info_status",
                "{status}", tc.isEnabled()
                        ? msg.get("tool.info_enabled")
                        : msg.get("tool.info_disabled")));

        ItemStack item = tc.getToolItem();
        if (item != null && !item.getType().isAir()) {
            String typeName = item.getType().getKey().getKey();
            if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                typeName += " (" + item.getItemMeta().getDisplayName() + "§f)";
            }
            sender.sendMessage(msg.get("tool.info_item", "{type}", typeName));
        }
        return true;
    }

    private boolean handleToolClear(CommandSender sender) {
        Messages msg = plugin.getMessages();
        plugin.getToolConfig().clear();
        sender.sendMessage(msg.get("tool.cleared"));
        return true;
    }

    // ── mode ─────────────────────────────────────────────────────────────

    private boolean handleMode(CommandSender sender, String[] args) {
        Messages msg = plugin.getMessages();

        if (!sender.hasPermission("mobeditor.use")) {
            sender.sendMessage(msg.get("general.no_permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.get("general.player_only"));
            return true;
        }

        if (args.length < 1) {
            EditorMode current = plugin.getModeManager().getMode(player);
            player.sendMessage(msg.get("mode.current", "{mode}", current.getDisplayName()));
            return true;
        }

        EditorMode mode = switch (args[0].toLowerCase()) {
            case "open" -> EditorMode.OPEN_EQUIPMENT;
            case "copy" -> EditorMode.COPY;
            case "paste" -> EditorMode.PASTE;
            case "egg" -> EditorMode.EXTRACT_EGG;
            default -> null;
        };

        if (mode == null) {
            player.sendMessage(msg.get("mode.unknown", "{mode}", args[0]));
            return true;
        }

        plugin.getModeManager().setMode(player, mode);
        player.sendMessage(msg.get("mode.set", "{mode}", mode.getDisplayName()));
        return true;
    }

    // ── pasteblanks ──────────────────────────────────────────────────────

    private boolean handlePasteBlanks(CommandSender sender, String[] args) {
        Messages msg = plugin.getMessages();

        if (!sender.hasPermission("mobeditor.use")) {
            sender.sendMessage(msg.get("general.no_permission"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg.get("general.player_only"));
            return true;
        }

        if (args.length < 1) {
            boolean current = plugin.getModeManager().isPasteBlanks(player);
            player.sendMessage(msg.get("pasteblanks.current",
                    "{value}", current ? msg.get("pasteblanks.on") : msg.get("pasteblanks.off")));
            return true;
        }

        boolean value = args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("true");
        plugin.getModeManager().setPasteBlanks(player, value);
        player.sendMessage(value ? msg.get("pasteblanks.set_on") : msg.get("pasteblanks.set_off"));
        return true;
    }

    // ── reload ───────────────────────────────────────────────────────────

    private boolean handleReload(CommandSender sender) {
        Messages msg = plugin.getMessages();

        if (!sender.hasPermission("mobeditor.admin")) {
            sender.sendMessage(msg.get("general.no_permission"));
            return true;
        }

        plugin.getConfigManager().reload();
        plugin.getMessages().reload();
        plugin.getToolConfig().reload();
        sender.sendMessage(msg.get("reload.done"));
        return true;
    }

    // ── help ────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        Messages msg = plugin.getMessages();
        sender.sendMessage(msg.get("help.header", "{version}",
                plugin.getPluginMeta().getVersion()));
        sender.sendMessage(msg.get("help.tool_set"));
        sender.sendMessage(msg.get("help.tool_disable"));
        sender.sendMessage(msg.get("help.tool_info"));
        sender.sendMessage(msg.get("help.tool_clear"));
        sender.sendMessage(msg.get("help.mode"));
        sender.sendMessage(msg.get("help.pasteblanks"));
        sender.sendMessage(msg.get("help.reload"));
        sender.sendMessage(msg.get("help.usage_hint"));
    }

    // ── Utility ──────────────────────────────────────────────────────────

    /** Shift args array by 1 (remove first element). */
    private static String[] shift(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] shifted = new String[args.length - 1];
        System.arraycopy(args, 1, shifted, 0, shifted.length);
        return shifted;
    }
}

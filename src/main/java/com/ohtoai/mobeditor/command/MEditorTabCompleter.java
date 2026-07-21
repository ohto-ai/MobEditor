package com.ohtoai.mobeditor.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.ArrayList;

/**
 * Tab completion for /mobeditor commands.
 * Follows the reference project's ISTabCompleter pattern.
 */
public class MEditorTabCompleter implements TabCompleter {

    private static final List<String> MAIN = List.of("tool", "mode", "pasteblanks", "reload");
    private static final List<String> TOOL_SUB = List.of("set", "disable", "info", "clear");
    private static final List<String> MODE_SUB = List.of("open", "copy", "paste", "egg");
    private static final List<String> PASTEBLANKS_SUB = List.of("on", "off");
    private static final List<String> EMPTY = List.of();

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(MAIN, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "tool" -> filter(TOOL_SUB, args[1]);
                case "mode" -> filter(MODE_SUB, args[1]);
                case "pasteblanks" -> filter(PASTEBLANKS_SUB, args[1]);
                default -> EMPTY;
            };
        }
        return EMPTY;
    }

    private static List<String> filter(List<String> source, String partial) {
        String lower = partial.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String s : source) {
            if (s.toLowerCase().startsWith(lower)) {
                result.add(s);
            }
        }
        return result;
    }
}

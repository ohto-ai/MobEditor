package com.ohtoai.mobeditor.command;

import org.bukkit.command.CommandSender;

/**
 * Interface for sub-command handlers.
 * Pattern matches the EquivementScanner reference project.
 */
@FunctionalInterface
public interface SubCommandHandler {
    /**
     * Handle a sub-command.
     * @param sender who issued the command
     * @param args remaining arguments after the sub-command name
     * @return true (Bukkit convention)
     */
    boolean handle(CommandSender sender, String[] args);
}

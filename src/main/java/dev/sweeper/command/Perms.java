package dev.sweeper.command;

import dev.sweeper.guard.DropGuard;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

/**
 * Permission nodes. Player commands are open by default, admin ones need op or the node.
 */
public final class Perms {

    public static final String ADMIN = "sweeper.admin";
    public static final String TIMER = "sweeper.command.timer";
    public static final String MESSAGES = "sweeper.command.messages";
    public static final String PROTECTION = "sweeper.command.protection";

    private Perms() {
    }

    public static void register(PluginManager manager) {
        add(manager, ADMIN, "Run and reload clean-ups", PermissionDefault.OP);
        add(manager, TIMER, "See the time until the next clean-up", PermissionDefault.TRUE);
        add(manager, MESSAGES, "Turn clean-up messages on or off for yourself", PermissionDefault.TRUE);
        add(manager, PROTECTION, "Turn drop protection on or off for yourself", PermissionDefault.TRUE);
        add(manager, DropGuard.BYPASS, "Drop items even right before a clean-up", PermissionDefault.FALSE);
    }

    private static void add(PluginManager manager, String node, String description, PermissionDefault value) {
        if (manager.getPermission(node) == null) {
            manager.addPermission(new Permission(node, description, value));
        }
    }
}

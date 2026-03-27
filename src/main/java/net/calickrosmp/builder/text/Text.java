package net.calickrosmp.builder.text;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public final class Text {
    private Text() {}

    public static String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    public static void send(CommandSender sender, String prefix, String message) {
        sender.sendMessage(color(prefix + message));
    }
}

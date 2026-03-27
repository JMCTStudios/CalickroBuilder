package net.calickrosmp.builder.listener;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.service.BuildService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class BuilderChatListener implements Listener {
    private final CalickroBuilderPlugin plugin;
    private final BuildService buildService;

    public BuilderChatListener(CalickroBuilderPlugin plugin, BuildService buildService) {
        this.plugin = plugin;
        this.buildService = buildService;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!event.getMessage().toLowerCase().startsWith(plugin.getConfig().getString("plugin.chat-trigger-prefix", "hey").toLowerCase())) {
            return;
        }
        if (plugin.settings().debug()) {
            plugin.log().info("Chat trigger seen from " + event.getPlayer().getName() + ": " + event.getMessage());
        }
        // Later: route this through a natural-language parser and builder memory system.
    }
}

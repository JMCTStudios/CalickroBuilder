package net.calickrosmp.builder.hook;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.npc.BuilderIdentity;
import net.calickrosmp.builder.npc.BuilderState;
import org.bukkit.Bukkit;

public final class CalickroNpcBridgeHook {
    private final CalickroBuilderPlugin plugin;

    public CalickroNpcBridgeHook(CalickroBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("hooks.calickronpcbridge.enabled", true)
                && Bukkit.getPluginManager().getPlugin("CalickroSMPHoloBridge") != null;
    }

    public void pushState(BuilderIdentity identity, BuilderState state, String detail) {
        if (plugin.settings().debug()) {
            plugin.log().info("[BridgeStub] " + identity.displayName() + " -> " + state + " :: " + detail);
        }
    }
}

package net.calickrosmp.builder.provider;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import org.bukkit.Bukkit;

import java.util.EnumMap;
import java.util.Map;

public final class NpcProviderRegistry {
    private final CalickroBuilderPlugin plugin;
    private final Map<NpcProviderType, NpcProvider> providers = new EnumMap<>(NpcProviderType.class);
    private final NpcProvider activeProvider;

    public NpcProviderRegistry(CalickroBuilderPlugin plugin) {
        this.plugin = plugin;

        providers.put(NpcProviderType.NONE, new NullNpcProvider());
        providers.put(NpcProviderType.CITIZENS, new CitizensNpcProvider(plugin));
        providers.put(NpcProviderType.FANCY_NPCS, new FancyNpcProvider());

        this.activeProvider = detectProvider();
        hookProvider(activeProvider);
    }

    private NpcProvider detectProvider() {
        String configured = plugin.getConfig().getString("plugin.default-provider", "auto").trim().toUpperCase();

        if (!"AUTO".equals(configured)) {
            try {
                NpcProviderType type = NpcProviderType.valueOf(configured);
                NpcProvider provider = providers.getOrDefault(type, providers.get(NpcProviderType.NONE));
                return provider.isAvailable() ? provider : providers.get(NpcProviderType.NONE);
            } catch (IllegalArgumentException ignored) {
                return providers.get(NpcProviderType.NONE);
            }
        }

        if (Bukkit.getPluginManager().getPlugin("Citizens") != null && Bukkit.getPluginManager().getPlugin("Citizens").isEnabled()) {
            return providers.get(NpcProviderType.CITIZENS);
        }
        if (Bukkit.getPluginManager().getPlugin("FancyNpcs") != null && Bukkit.getPluginManager().getPlugin("FancyNpcs").isEnabled()) {
            return providers.get(NpcProviderType.FANCY_NPCS);
        }
        return providers.get(NpcProviderType.NONE);
    }

    private void hookProvider(NpcProvider provider) {
        if (provider instanceof CitizensNpcProvider citizensNpcProvider) {
            citizensNpcProvider.registerListenerIfAvailable();
        }
    }

    public NpcProvider activeProvider() {
        return activeProvider;
    }

    public String getActiveProviderName() {
        return activeProvider.name();
    }
}
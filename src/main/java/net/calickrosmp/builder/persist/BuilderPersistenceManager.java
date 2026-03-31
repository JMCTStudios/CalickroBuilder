package net.calickrosmp.builder.persist;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.npc.BuilderIdentity;
import net.calickrosmp.builder.npc.BuilderMode;
import net.calickrosmp.builder.npc.BuilderNpcRegistry;
import net.calickrosmp.builder.npc.BuilderProfile;
import net.calickrosmp.builder.npc.BuilderSpeedMode;
import net.calickrosmp.builder.provider.NpcProviderType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class BuilderPersistenceManager {
    private final CalickroBuilderPlugin plugin;
    private final BuilderNpcRegistry registry;
    private final File file;

    public BuilderPersistenceManager(CalickroBuilderPlugin plugin, BuilderNpcRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.file = new File(plugin.getDataFolder(), "builders.yml");
    }

    public void loadBuilders() {
        registry.clear();
        if (!file.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection builders = yaml.getConfigurationSection("builders");
        if (builders == null) {
            return;
        }

        for (String key : builders.getKeys(false)) {
            ConfigurationSection section = builders.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                UUID npcId = UUID.fromString(key);
                String name = section.getString("display-name", "Builder");
                NpcProviderType providerType = NpcProviderType.valueOf(section.getString("provider", "CITIZENS").toUpperCase());
                BuilderProfile profile = registry.register(new BuilderIdentity(npcId, name, providerType));
                profile.setMode(parseMode(section.getString("mode", "BUILD")));
                profile.setAssignedRegion(section.getString("assigned-region"));
                profile.setLastBaselineName(section.getString("last-baseline-name"));
                profile.setSpeedMode(BuilderSpeedMode.fromString(section.getString("speed-mode", "SMART")));
                if (section.isSet("speed-override-ticks")) {
                    profile.setSpeedOverrideTicks(section.getLong("speed-override-ticks"));
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping invalid persisted builder entry: " + key);
            }
        }
    }

    public void saveBuilders() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("builders");
        for (BuilderProfile profile : registry.all()) {
            ConfigurationSection section = root.createSection(profile.identity().npcId().toString());
            section.set("display-name", profile.identity().displayName());
            section.set("provider", profile.identity().providerType().name());
            section.set("mode", profile.mode().name());
            section.set("assigned-region", profile.assignedRegion());
            section.set("last-baseline-name", profile.lastBaselineName());
            section.set("speed-mode", profile.speedMode().name());
            if (profile.speedOverrideTicks() != null) {
                section.set("speed-override-ticks", profile.speedOverrideTicks());
            }
        }

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save builders.yml: " + ex.getMessage());
        }
    }

    private BuilderMode parseMode(String value) {
        try {
            return BuilderMode.valueOf(value.toUpperCase());
        } catch (Exception ex) {
            return BuilderMode.BUILD;
        }
    }
}

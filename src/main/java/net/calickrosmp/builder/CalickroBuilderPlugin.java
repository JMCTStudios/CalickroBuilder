package net.calickrosmp.builder;

import net.calickrosmp.builder.command.CalickroBuilderCommand;
import net.calickrosmp.builder.config.BuilderSettings;
import net.calickrosmp.builder.hook.CalickroNpcBridgeHook;
import net.calickrosmp.builder.hook.GriefPreventionHook;
import net.calickrosmp.builder.hook.WorldGuardHook;
import net.calickrosmp.builder.job.BuildJobManager;
import net.calickrosmp.builder.listener.BuilderChatListener;
import net.calickrosmp.builder.npc.BuilderNpcRegistry;
import net.calickrosmp.builder.provider.NpcProviderRegistry;
import net.calickrosmp.builder.service.BuildService;
import net.calickrosmp.builder.validation.BuildValidator;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Logger;

public final class CalickroBuilderPlugin extends JavaPlugin {
    private BuilderSettings settings;
    private NpcProviderRegistry npcProviderRegistry;
    private BuilderNpcRegistry builderNpcRegistry;
    private BuildJobManager buildJobManager;
    private BuildValidator buildValidator;
    private BuildService buildService;
    private WorldGuardHook worldGuardHook;
    private GriefPreventionHook griefPreventionHook;
    private CalickroNpcBridgeHook bridgeHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.settings = new BuilderSettings(this);
        this.worldGuardHook = new WorldGuardHook(this);
        this.griefPreventionHook = new GriefPreventionHook(this);
        this.bridgeHook = new CalickroNpcBridgeHook(this);
        this.npcProviderRegistry = new NpcProviderRegistry(this);
        this.builderNpcRegistry = new BuilderNpcRegistry();
        this.buildJobManager = new BuildJobManager(this);
        this.buildValidator = new BuildValidator(this, worldGuardHook, griefPreventionHook);
        this.buildService = new BuildService(this, builderNpcRegistry, npcProviderRegistry, buildJobManager, buildValidator, bridgeHook);

        CalickroBuilderCommand commandHandler =
                new CalickroBuilderCommand(this, buildService, builderNpcRegistry, npcProviderRegistry);

        PluginCommand command = Objects.requireNonNull(getCommand("cali"), "cali command missing from plugin.yml");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getServer().getPluginManager().registerEvents(new BuilderChatListener(this, buildService), this);

        getLogger().info("CalickroBuilder enabled. Provider=" + npcProviderRegistry.getActiveProviderName());
    }

    @Override
    public void onDisable() {
        if (buildJobManager != null) {
            buildJobManager.shutdown();
        }
        getLogger().info("CalickroBuilder disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        this.settings.reload();
    }

    public BuilderSettings settings() {
        return settings;
    }

    public Logger log() {
        return getLogger();
    }
}
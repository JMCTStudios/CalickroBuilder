package net.calickrosmp.builder.config;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public final class BuilderSettings {
    private final CalickroBuilderPlugin plugin;
    private boolean debug;
    private boolean previewEnabled;
    private boolean requireFullFootprintClear;
    private boolean liveProtectionRecheck;
    private boolean allowAutoRelocate;
    private int collisionPadding;
    private int maxAutoRelocateDistance;
    private int blocksPerTick;
    private int blocksPerStep;
    private long buildIntervalTicks;
    private Material defaultPathMaterial;
    private String messagePrefix;

    public BuilderSettings(CalickroBuilderPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        this.debug = config.getBoolean("plugin.debug", true);
        this.previewEnabled = config.getBoolean("build.preview-enabled", true);
        this.requireFullFootprintClear = config.getBoolean("validation.require-full-footprint-clear", true);
        this.liveProtectionRecheck = config.getBoolean("validation.live-protection-recheck", true);
        this.allowAutoRelocate = config.getBoolean("validation.allow-auto-relocate", true);
        this.collisionPadding = config.getInt("validation.collision-padding", 1);
        this.maxAutoRelocateDistance = config.getInt("validation.max-auto-relocate-distance", 16);
        this.blocksPerTick = config.getInt("build.blocks-per-tick", 25);
        this.blocksPerStep = config.getInt("build.blocks-per-step", 1);
        this.buildIntervalTicks = config.getLong("build.interval-ticks", 8L);
        this.defaultPathMaterial = Material.matchMaterial(config.getString("build.default-path-material", "DIRT_PATH"));
        if (this.defaultPathMaterial == null) {
            this.defaultPathMaterial = Material.DIRT_PATH;
        }
        this.messagePrefix = config.getString("messages.prefix", "&6[CalickroBuilder]&r ");
    }

    public boolean debug() { return debug; }
    public boolean previewEnabled() { return previewEnabled; }
    public boolean requireFullFootprintClear() { return requireFullFootprintClear; }
    public boolean liveProtectionRecheck() { return liveProtectionRecheck; }
    public boolean allowAutoRelocate() { return allowAutoRelocate; }
    public int collisionPadding() { return collisionPadding; }
    public int maxAutoRelocateDistance() { return maxAutoRelocateDistance; }
    public int blocksPerTick() { return blocksPerTick; }
    public int blocksPerStep() { return blocksPerStep; }
    public long buildIntervalTicks() { return buildIntervalTicks; }
    public Material defaultPathMaterial() { return defaultPathMaterial; }
    public String messagePrefix() { return messagePrefix; }
}

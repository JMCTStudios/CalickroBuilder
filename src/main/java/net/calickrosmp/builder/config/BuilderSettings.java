package net.calickrosmp.builder.config;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

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
    private int siteSearchMinDistance;
    private int siteSearchMaxDistance;
    private int siteLateralStep;
    private int siteMaxSideOffset;
    private int preserveWorldSpawnRadius;
    private List<Material> siteAvoidGroundMaterials;
    private List<String> worldGuardAllowedBuildRegions;
    private List<String> worldGuardPreserveRegions;

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
        this.siteSearchMinDistance = config.getInt("site-planner.search-min-distance", 10);
        this.siteSearchMaxDistance = config.getInt("site-planner.search-max-distance", 28);
        this.siteLateralStep = config.getInt("site-planner.lateral-step", 2);
        this.siteMaxSideOffset = config.getInt("site-planner.max-side-offset", 16);
        this.preserveWorldSpawnRadius = config.getInt("site-planner.preserve-world-spawn-radius", 8);
        this.siteAvoidGroundMaterials = new ArrayList<>();
        for (String name : config.getStringList("site-planner.avoid-ground-materials")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                this.siteAvoidGroundMaterials.add(material);
            }
        }
        if (this.siteAvoidGroundMaterials.isEmpty()) {
            this.siteAvoidGroundMaterials.add(Material.DIRT_PATH);
            this.siteAvoidGroundMaterials.add(Material.OAK_SLAB);
            this.siteAvoidGroundMaterials.add(Material.SPRUCE_SLAB);
        }
        this.worldGuardAllowedBuildRegions = normalize(config.getStringList("hooks.worldguard.allowed-build-regions"));
        this.worldGuardPreserveRegions = normalize(config.getStringList("hooks.worldguard.preserve-regions"));
    }

    private List<String> normalize(List<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim().toLowerCase());
            }
        }
        return normalized;
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
    public int siteSearchMinDistance() { return siteSearchMinDistance; }
    public int siteSearchMaxDistance() { return siteSearchMaxDistance; }
    public int siteLateralStep() { return siteLateralStep; }
    public int siteMaxSideOffset() { return siteMaxSideOffset; }
    public int preserveWorldSpawnRadius() { return preserveWorldSpawnRadius; }
    public List<Material> siteAvoidGroundMaterials() { return List.copyOf(siteAvoidGroundMaterials); }
    public List<String> worldGuardAllowedBuildRegions() { return List.copyOf(worldGuardAllowedBuildRegions); }
    public List<String> worldGuardPreserveRegions() { return List.copyOf(worldGuardPreserveRegions); }
}

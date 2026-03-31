package net.calickrosmp.builder.config;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.npc.BuilderSpeedMode;
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
    private int siteMinHouseGap;
    private int siteRoadPreferenceDistance;
    private int siteRoadClearance;
    private List<Material> siteAvoidGroundMaterials;
    private List<String> worldGuardAllowedBuildRegions;
    private List<String> worldGuardPreserveRegions;
    private BuilderSpeedMode defaultSpeedMode;
    private long fixedTicksPerStep;
    private long cinematicBaseTicks;
    private long smartBaseTicks;
    private long fastBaseTicks;
    private long customBaseTicks;
    private double cinematicDetailMultiplier;
    private double cinematicBulkMultiplier;
    private double smartDetailMultiplier;
    private double smartBulkMultiplier;
    private double fastDetailMultiplier;
    private double fastBulkMultiplier;
    private double customDetailMultiplier;
    private double customBulkMultiplier;

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
        this.buildIntervalTicks = Math.max(1L, config.getLong("build.interval-ticks", 8L));
        this.defaultPathMaterial = Material.matchMaterial(config.getString("build.default-path-material", "DIRT_PATH"));
        if (this.defaultPathMaterial == null) {
            this.defaultPathMaterial = Material.DIRT_PATH;
        }
        this.messagePrefix = config.getString("messages.prefix", "&6[CalickroBuilder]&r ");
        this.siteSearchMinDistance = config.getInt("site-planner.search-min-distance", 8);
        this.siteSearchMaxDistance = config.getInt("site-planner.search-max-distance", 36);
        this.siteLateralStep = config.getInt("site-planner.lateral-step", 2);
        this.siteMaxSideOffset = config.getInt("site-planner.max-side-offset", 24);
        this.preserveWorldSpawnRadius = config.getInt("site-planner.preserve-world-spawn-radius", 8);
        this.siteMinHouseGap = Math.max(1, config.getInt("site-planner.min-house-gap", 2));
        this.siteRoadPreferenceDistance = Math.max(2, config.getInt("site-planner.road-preference-distance", 12));
        this.siteRoadClearance = Math.max(0, config.getInt("site-planner.road-clearance", 1));
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

        this.defaultSpeedMode = BuilderSpeedMode.fromString(config.getString("build-speed.mode", "smart"));
        this.fixedTicksPerStep = Math.max(1L, config.getLong("build-speed.fixed-ticks-per-step", 8L));

        this.cinematicBaseTicks = Math.max(1L, config.getLong("build-speed.presets.cinematic.base-ticks-per-step", 20L));
        this.cinematicDetailMultiplier = config.getDouble("build-speed.presets.cinematic.detail-multiplier", 1.5D);
        this.cinematicBulkMultiplier = config.getDouble("build-speed.presets.cinematic.bulk-multiplier", 1.0D);

        this.smartBaseTicks = Math.max(1L, config.getLong("build-speed.presets.smart.base-ticks-per-step", 10L));
        this.smartDetailMultiplier = config.getDouble("build-speed.presets.smart.detail-multiplier", 1.4D);
        this.smartBulkMultiplier = config.getDouble("build-speed.presets.smart.bulk-multiplier", 0.7D);

        this.fastBaseTicks = Math.max(1L, config.getLong("build-speed.presets.fast.base-ticks-per-step", 2L));
        this.fastDetailMultiplier = config.getDouble("build-speed.presets.fast.detail-multiplier", 1.0D);
        this.fastBulkMultiplier = config.getDouble("build-speed.presets.fast.bulk-multiplier", 0.5D);

        this.customBaseTicks = Math.max(1L, config.getLong("build-speed.presets.custom.base-ticks-per-step", 6L));
        this.customDetailMultiplier = config.getDouble("build-speed.presets.custom.detail-multiplier", 1.2D);
        this.customBulkMultiplier = config.getDouble("build-speed.presets.custom.bulk-multiplier", 0.8D);
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
    public int siteMinHouseGap() { return siteMinHouseGap; }
    public int siteRoadPreferenceDistance() { return siteRoadPreferenceDistance; }
    public int siteRoadClearance() { return siteRoadClearance; }
    public List<Material> siteAvoidGroundMaterials() { return List.copyOf(siteAvoidGroundMaterials); }
    public List<String> worldGuardAllowedBuildRegions() { return List.copyOf(worldGuardAllowedBuildRegions); }
    public List<String> worldGuardPreserveRegions() { return List.copyOf(worldGuardPreserveRegions); }
    public BuilderSpeedMode defaultSpeedMode() { return defaultSpeedMode; }
    public long fixedTicksPerStep() { return fixedTicksPerStep; }

    public void setBuildIntervalTicks(long buildIntervalTicks) {
        this.buildIntervalTicks = Math.max(1L, buildIntervalTicks);
    }

    public void setDefaultSpeedMode(BuilderSpeedMode mode) {
        this.defaultSpeedMode = mode == null ? BuilderSpeedMode.SMART : mode;
    }

    public void setFixedTicksPerStep(long ticks) {
        this.fixedTicksPerStep = Math.max(1L, ticks);
    }

    public long resolveDelayTicks(BuilderSpeedMode mode, int totalTasks, int currentIndex) {
        BuilderSpeedMode resolved = mode == null ? defaultSpeedMode : mode;
        double progress = totalTasks <= 0 ? 0.0D : Math.min(1.0D, Math.max(0.0D, (double) currentIndex / (double) totalTasks));
        return switch (resolved) {
            case FIXED -> Math.max(1L, fixedTicksPerStep);
            case CINEMATIC -> applyProgress(cinematicBaseTicks, cinematicDetailMultiplier, cinematicBulkMultiplier, progress);
            case FAST -> applyProgress(fastBaseTicks, fastDetailMultiplier, fastBulkMultiplier, progress);
            case CUSTOM -> applyProgress(customBaseTicks, customDetailMultiplier, customBulkMultiplier, progress);
            case SMART -> applyProgress(smartBaseTicks, smartDetailMultiplier, smartBulkMultiplier, progress);
        };
    }

    private long applyProgress(long base, double detailMultiplier, double bulkMultiplier, double progress) {
        double multiplier;
        if (progress < 0.65D) {
            multiplier = bulkMultiplier;
        } else if (progress < 0.9D) {
            multiplier = 1.0D;
        } else {
            multiplier = detailMultiplier;
        }
        return Math.max(1L, Math.round(base * multiplier));
    }
}

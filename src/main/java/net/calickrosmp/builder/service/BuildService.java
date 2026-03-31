package net.calickrosmp.builder.service;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.build.BuildExecutor;
import net.calickrosmp.builder.build.BuildSitePlanner;
import net.calickrosmp.builder.build.BuildSitePlanner.ScanReport;
import net.calickrosmp.builder.build.BuildSitePlanner.SiteSelection;
import net.calickrosmp.builder.hook.CalickroNpcBridgeHook;
import net.calickrosmp.builder.job.BuildJob;
import net.calickrosmp.builder.job.BuildJobManager;
import net.calickrosmp.builder.job.JobType;
import net.calickrosmp.builder.npc.BuilderIdentity;
import net.calickrosmp.builder.npc.BuilderNpcRegistry;
import net.calickrosmp.builder.npc.BuilderProfile;
import net.calickrosmp.builder.npc.BuilderState;
import net.calickrosmp.builder.plan.BuildPlan;
import net.calickrosmp.builder.plan.Footprint;
import net.calickrosmp.builder.plan.HouseSpec;
import net.calickrosmp.builder.plan.Orientation;
import net.calickrosmp.builder.plan.StructureType;
import net.calickrosmp.builder.provider.NpcProvider;
import net.calickrosmp.builder.provider.NpcProviderRegistry;
import net.calickrosmp.builder.text.Text;
import net.calickrosmp.builder.validation.BuildValidator;
import net.calickrosmp.builder.validation.ValidationIssue;
import net.calickrosmp.builder.validation.ValidationResult;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class BuildService {
    private final CalickroBuilderPlugin plugin;
    private final BuilderNpcRegistry builderNpcRegistry;
    private final NpcProviderRegistry npcProviderRegistry;
    private final BuildJobManager buildJobManager;
    private final BuildValidator buildValidator;
    private final CalickroNpcBridgeHook bridgeHook;
    private final BuildExecutor buildExecutor;
    private final BuildSitePlanner buildSitePlanner;

    public BuildService(
            CalickroBuilderPlugin plugin,
            BuilderNpcRegistry builderNpcRegistry,
            NpcProviderRegistry npcProviderRegistry,
            BuildJobManager buildJobManager,
            BuildValidator buildValidator,
            CalickroNpcBridgeHook bridgeHook
    ) {
        this.plugin = plugin;
        this.builderNpcRegistry = builderNpcRegistry;
        this.npcProviderRegistry = npcProviderRegistry;
        this.buildJobManager = buildJobManager;
        this.buildValidator = buildValidator;
        this.bridgeHook = bridgeHook;
        this.buildExecutor = new BuildExecutor(plugin, buildJobManager, bridgeHook);
        this.buildSitePlanner = new BuildSitePlanner(plugin, plugin.worldGuardHook());
    }

    public void bindSelectedNpc(Player player) {
        NpcProvider provider = npcProviderRegistry.activeProvider();

        if (!provider.isAvailable()) {
            Text.send(player, plugin.settings().messagePrefix(), plugin.getConfig().getString("messages.provider-not-ready", "That NPC provider is not ready yet."));
            return;
        }

        Optional<UUID> selectedId = provider.getSelectedNpcId(player);
        Optional<String> selectedName = provider.getSelectedNpcName(player);

        if (selectedId.isEmpty()) {
            Text.send(player, plugin.settings().messagePrefix(), plugin.getConfig().getString("messages.no-builder-selected"));
            return;
        }

        if (!provider.attachBuilderRole(player, selectedId.get())) {
            Text.send(player, plugin.settings().messagePrefix(), "I found the selected NPC, but could not attach the builder role yet.");
            return;
        }

        BuilderIdentity identity = new BuilderIdentity(selectedId.get(), selectedName.orElse("Builder"), provider.type());
        BuilderProfile profile = builderNpcRegistry.register(identity);
        profile.setState(BuilderState.IDLE);
        if (profile.speedMode() == null) {
            profile.setSpeedMode(plugin.settings().defaultSpeedMode());
        }
        plugin.builderPersistenceManager().saveBuilders();

        Text.send(player, plugin.settings().messagePrefix(), "Builder bound to NPC &e" + identity.displayName() + "&r with provider &b" + provider.name() + "&r.");
    }

    public void queueStarterHouse(Player player, UUID builderId) {
        Optional<BuilderProfile> profileOptional = builderNpcRegistry.find(builderId);
        if (profileOptional.isEmpty()) {
            Text.send(player, plugin.settings().messagePrefix(), "That NPC is not registered as a builder yet.");
            return;
        }

        BuilderProfile profile = profileOptional.get();
        Orientation preferredOrientation = orientationFor(player.getLocation());
        HouseSpec initialSpec = HouseSpec.starter(preferredOrientation);

        Optional<Location> builderLocation = resolveBuilderLocation(profile);
        if (builderLocation.isEmpty()) {
            profile.setState(BuilderState.ERROR);
            bridgeHook.pushState(profile.identity(), BuilderState.ERROR, "Builder NPC is not spawned");
            Text.send(player, plugin.settings().messagePrefix(), "&cYour builder NPC must be spawned before it can build.");
            return;
        }

        SiteSelection siteSelection = null;
        for (Location plannerOrigin : plannerOrigins(builderLocation.get(), player.getLocation())) {
            siteSelection = buildSitePlanner.selectStarterHouseSite(player, plannerOrigin, preferredOrientation, initialSpec);
            if (siteSelection.found() && siteSelection.anchor() != null && siteSelection.orientation() != null) {
                break;
            }
        }
        if (siteSelection == null || !siteSelection.found() || siteSelection.anchor() == null || siteSelection.orientation() == null) {
            Text.send(player, plugin.settings().messagePrefix(), "&c" + (siteSelection == null ? "I couldn't find a safe build spot nearby." : siteSelection.message()));
            profile.setState(BuilderState.ERROR);
            bridgeHook.pushState(profile.identity(), BuilderState.ERROR, "No safe site found");
            return;
        }

        HouseSpec houseSpec = HouseSpec.starter(siteSelection.orientation());
        BuildPlan plan = new BuildPlan(
                StructureType.HOUSE,
                "Starter 1-floor house facing the player",
                siteSelection.anchor(),
                new Footprint(houseSpec.width(), houseSpec.depth(), 6, plugin.settings().collisionPadding()),
                houseSpec,
                null
        );

        ValidationResult validation = buildValidator.validate(player, plan);
        if (!validation.isAllowed()) {
            profile.setState(BuilderState.ERROR);
            for (ValidationIssue issue : validation.issues()) {
                Text.send(player, plugin.settings().messagePrefix(), "&c" + issue.message());
            }
            bridgeHook.pushState(profile.identity(), BuilderState.ERROR, "Validation failed");
            return;
        }

        profile.setState(BuilderState.VALIDATING);
        bridgeHook.pushState(profile.identity(), BuilderState.VALIDATING, plan.summary());

        BuildJob job = new BuildJob(builderId, player.getUniqueId(), JobType.BUILD, plan);
        buildJobManager.enqueue(job);

        profile.setState(BuilderState.PREVIEWING);
        bridgeHook.pushState(profile.identity(), BuilderState.PREVIEWING, plan.summary());

        buildExecutor.executeStarterHouse(player, profile, plan, job);

        Text.send(player, plugin.settings().messagePrefix(), plugin.getConfig().getString("messages.build-queued") + " &7(" + plan.summary() + ")");
    }

    public void scanCurrentArea(Player player, UUID builderId) {
        Optional<BuilderProfile> profileOptional = builderNpcRegistry.find(builderId);
        if (profileOptional.isEmpty()) {
            Text.send(player, plugin.settings().messagePrefix(), "That NPC is not registered as a builder yet.");
            return;
        }

        BuilderProfile profile = profileOptional.get();
        Orientation preferredOrientation = orientationFor(player.getLocation());
        HouseSpec spec = HouseSpec.starter(preferredOrientation);
        Location origin = resolveBuilderLocation(profile).orElse(player.getLocation());
        ScanReport report = buildSitePlanner.scanArea(player, origin, preferredOrientation, spec);

        Text.send(player, plugin.settings().messagePrefix(), (report.success() ? "&a" : "&c") + report.summary());
        for (String line : report.details()) {
            Text.send(player, plugin.settings().messagePrefix(), "&7- " + line);
        }
    }

    public void reportStatus(Player player, UUID builderId) {
        Optional<BuilderProfile> profile = builderNpcRegistry.find(builderId);
        if (profile.isEmpty()) {
            Text.send(player, plugin.settings().messagePrefix(), "That NPC is not registered as a builder.");
            return;
        }

        String jobInfo = buildJobManager.current(builderId)
                .map(job -> job.type() + " / " + job.status())
                .orElse("No queued jobs");

        Text.send(
                player,
                plugin.settings().messagePrefix(),
                "&e" + profile.get().identity().displayName()
                        + "&r mode=&b" + profile.get().mode()
                        + "&r state=&b" + profile.get().state()
                        + "&r provider=&b" + profile.get().identity().providerType()
                        + "&r speedMode=&b" + profile.get().speedMode().name().toLowerCase()
                        + "&r speedOverride=&e" + (profile.get().speedOverrideTicks() == null ? "none" : profile.get().speedOverrideTicks())
                        + "&r job=&7" + jobInfo
        );
    }

    private Optional<Location> resolveBuilderLocation(BuilderProfile profile) {
        try {
            if (!CitizensAPI.hasImplementation()) {
                return Optional.empty();
            }
            int npcId = (int) profile.identity().npcId().getLeastSignificantBits();
            if (npcId <= 0) {
                return Optional.empty();
            }
            NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
            if (npc != null && npc.isSpawned() && npc.getEntity() != null) {
                return Optional.of(npc.getEntity().getLocation());
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }


    private java.util.List<Location> plannerOrigins(Location builderLocation, Location playerLocation) {
        java.util.List<Location> origins = new java.util.ArrayList<>();
        origins.add(builderLocation.clone());

        int[] ring = new int[]{4, 8, 12};
        for (int offset : ring) {
            origins.add(builderLocation.clone().add(offset, 0, 0));
            origins.add(builderLocation.clone().add(-offset, 0, 0));
            origins.add(builderLocation.clone().add(0, 0, offset));
            origins.add(builderLocation.clone().add(0, 0, -offset));
        }

        if (playerLocation != null) {
            origins.add(playerLocation.clone());
            origins.add(playerLocation.clone().add(4, 0, 0));
            origins.add(playerLocation.clone().add(-4, 0, 0));
            origins.add(playerLocation.clone().add(0, 0, 4));
            origins.add(playerLocation.clone().add(0, 0, -4));
        }
        return origins;
    }

    public Orientation orientationFor(Location location) {
        float yaw = location.getYaw();
        if (yaw >= -45 && yaw < 45) return Orientation.SOUTH;
        if (yaw >= 45 && yaw < 135) return Orientation.WEST;
        if (yaw >= -135 && yaw < -45) return Orientation.EAST;
        return Orientation.NORTH;
    }
}

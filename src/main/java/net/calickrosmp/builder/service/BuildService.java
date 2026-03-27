package net.calickrosmp.builder.service;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.build.BuildExecutor;
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
    }

    public void bindSelectedNpc(Player player) {
        NpcProvider provider = npcProviderRegistry.activeProvider();

        if (!provider.isAvailable()) {
            Text.send(
                    player,
                    plugin.settings().messagePrefix(),
                    plugin.getConfig().getString("messages.provider-not-ready", "That NPC provider is not ready yet.")
            );
            return;
        }

        Optional<UUID> selectedId = provider.getSelectedNpcId(player);
        Optional<String> selectedName = provider.getSelectedNpcName(player);

        if (selectedId.isEmpty()) {
            Text.send(player, plugin.settings().messagePrefix(), plugin.getConfig().getString("messages.no-builder-selected"));
            return;
        }

        if (!provider.attachBuilderRole(player, selectedId.get())) {
            Text.send(
                    player,
                    plugin.settings().messagePrefix(),
                    "I found the selected NPC, but could not attach the builder role yet."
            );
            return;
        }

        BuilderIdentity identity = new BuilderIdentity(selectedId.get(), selectedName.orElse("Builder"), provider.type());
        BuilderProfile profile = builderNpcRegistry.register(identity);
        profile.setState(BuilderState.IDLE);

        Text.send(
                player,
                plugin.settings().messagePrefix(),
                "Builder bound to NPC &e" + identity.displayName() + "&r with provider &b" + provider.name() + "&r."
        );
    }

    public void queueStarterHouse(Player player, UUID builderId) {
        Optional<BuilderProfile> profile = builderNpcRegistry.find(builderId);
        if (profile.isEmpty()) {
            Text.send(player, plugin.settings().messagePrefix(), "That NPC is not registered as a builder yet.");
            return;
        }

        NPC builderNpc = getCitizensNpc(profile.get());
        if (builderNpc == null || !builderNpc.isSpawned()) {
            Text.send(player, plugin.settings().messagePrefix(), "Spawn the builder NPC first so it can walk over and build.");
            return;
        }

        Orientation orientation = orientationFacingPlayer(builderNpc.getStoredLocation(), player.getLocation());
        HouseSpec houseSpec = HouseSpec.starter(orientation);
        Location anchor = starterHouseAnchor(builderNpc.getStoredLocation(), orientation, houseSpec);
        BuildPlan plan = new BuildPlan(
                StructureType.HOUSE,
                "Starter 1-floor house facing the player",
                anchor,
                new Footprint(houseSpec.width(), houseSpec.depth(), 6, plugin.settings().collisionPadding()),
                houseSpec,
                null
        );

        ValidationResult validation = buildValidator.validate(player, plan);
        if (!validation.isAllowed()) {
            profile.get().setState(BuilderState.ERROR);
            for (ValidationIssue issue : validation.issues()) {
                Text.send(player, plugin.settings().messagePrefix(), "&c" + issue.message());
            }
            bridgeHook.pushState(profile.get().identity(), BuilderState.ERROR, "Validation failed");
            return;
        }

        profile.get().setState(BuilderState.VALIDATING);
        bridgeHook.pushState(profile.get().identity(), BuilderState.VALIDATING, plan.summary());

        BuildJob job = new BuildJob(builderId, player.getUniqueId(), JobType.BUILD, plan);
        buildJobManager.enqueue(job);

        profile.get().setState(BuilderState.PREVIEWING);
        bridgeHook.pushState(profile.get().identity(), BuilderState.PREVIEWING, plan.summary());

        Text.send(
                player,
                plugin.settings().messagePrefix(),
                plugin.getConfig().getString("messages.build-queued") + " &7(" + plan.summary() + ")"
        );

        buildExecutor.executeStarterHouse(player, profile.get(), plan, job);
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
                        + "&r job=&7" + jobInfo
        );
    }

    private NPC getCitizensNpc(BuilderProfile profile) {
        if (!CitizensAPI.hasImplementation()) {
            return null;
        }
        long least = profile.identity().npcId().getLeastSignificantBits();
        int npcId = (int) least;
        return CitizensAPI.getNPCRegistry().getById(npcId);
    }

    private Location starterHouseAnchor(Location npcLocation, Orientation orientation, HouseSpec spec) {
        Location base = npcLocation.clone();
        int offset = 3;
        return switch (orientation) {
            case SOUTH -> base.add(-(spec.width() / 2.0), 0, offset);
            case NORTH -> base.add(spec.width() / 2.0, 0, -offset);
            case EAST -> base.add(offset, 0, spec.width() / 2.0);
            case WEST -> base.add(-offset, 0, -(spec.width() / 2.0));
        };
    }

    public Orientation orientationFor(Location location) {
        float yaw = location.getYaw();
        if (yaw >= -45 && yaw < 45) return Orientation.SOUTH;
        if (yaw >= 45 && yaw < 135) return Orientation.WEST;
        if (yaw >= -135 && yaw < -45) return Orientation.EAST;
        return Orientation.NORTH;
    }

    private Orientation orientationFacingPlayer(Location npcLocation, Location playerLocation) {
        double dx = playerLocation.getX() - npcLocation.getX();
        double dz = playerLocation.getZ() - npcLocation.getZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0 ? Orientation.EAST : Orientation.WEST;
        }
        return dz >= 0 ? Orientation.SOUTH : Orientation.NORTH;
    }
}

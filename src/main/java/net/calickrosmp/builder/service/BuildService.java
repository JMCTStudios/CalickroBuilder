package net.calickrosmp.builder.service;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.build.BuildExecutor;
import net.calickrosmp.builder.build.BuildTask;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
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
        this.buildExecutor = new BuildExecutor(plugin, bridgeHook);
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
        Optional<BuilderProfile> profileOptional = builderNpcRegistry.find(builderId);
        if (profileOptional.isEmpty()) {
            Text.send(player, plugin.settings().messagePrefix(), "That NPC is not registered as a builder yet.");
            return;
        }

        BuilderProfile profile = profileOptional.get();
        Orientation orientation = orientationFor(player.getLocation());
        HouseSpec houseSpec = HouseSpec.starter(orientation);
        Location anchor = player.getLocation().getBlock().getLocation().clone().add(3, 0, 3);
        BuildPlan plan = new BuildPlan(
                StructureType.HOUSE,
                "Starter 1-floor house facing the player",
                anchor,
                new Footprint(houseSpec.width(), houseSpec.depth(), 5, plugin.settings().collisionPadding()),
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

        Text.send(
                player,
                plugin.settings().messagePrefix(),
                plugin.getConfig().getString("messages.build-queued") + " &7(" + plan.summary() + ")"
        );

        List<BuildTask> tasks = createStarterHouseTasks(anchor, orientation);
        BoundingBox buildBox = createBoundingBox(tasks, plugin.settings().collisionPadding());
        buildExecutor.execute(player, profile, plan.summary(), tasks, buildBox);
    }

    private List<BuildTask> createStarterHouseTasks(Location anchor, Orientation orientation) {
        List<BuildTask> tasks = new ArrayList<>();
        Location base = anchor.clone();

        // floor / foundation
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                tasks.add(new BuildTask(base.clone().add(x, -1, z), Material.COBBLESTONE));
                tasks.add(new BuildTask(base.clone().add(x, 0, z), Material.OAK_PLANKS));
            }
        }

        // walls with doorway reserved
        for (int y = 1; y <= 3; y++) {
            for (int x = 0; x < 5; x++) {
                tasks.add(new BuildTask(base.clone().add(x, y, 0), Material.OAK_PLANKS));
                tasks.add(new BuildTask(base.clone().add(x, y, 4), Material.OAK_PLANKS));
            }
            for (int z = 1; z < 4; z++) {
                tasks.add(new BuildTask(base.clone().add(0, y, z), Material.OAK_PLANKS));
                tasks.add(new BuildTask(base.clone().add(4, y, z), Material.OAK_PLANKS));
            }
        }

        // doorway on front side nearest player orientation
        List<BuildTask> doorway = doorwayTasks(base, orientation);
        tasks.removeIf(task -> doorway.stream().anyMatch(door -> sameBlock(door.location(), task.location())));
        tasks.addAll(doorway);

        // windows
        addWindows(tasks, base, orientation);

        // roof
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                tasks.add(new BuildTask(base.clone().add(x, 4, z), Material.OAK_SLAB));
            }
        }

        // simple interior lighting
        tasks.add(new BuildTask(base.clone().add(2, 1, 2), Material.TORCH));
        return tasks;
    }

    private void addWindows(List<BuildTask> tasks, Location base, Orientation orientation) {
        List<Location> windows = new ArrayList<>();
        switch (orientation) {
            case NORTH -> {
                windows.add(base.clone().add(1, 2, 4));
                windows.add(base.clone().add(3, 2, 4));
            }
            case SOUTH -> {
                windows.add(base.clone().add(1, 2, 0));
                windows.add(base.clone().add(3, 2, 0));
            }
            case EAST -> {
                windows.add(base.clone().add(0, 2, 1));
                windows.add(base.clone().add(0, 2, 3));
            }
            case WEST -> {
                windows.add(base.clone().add(4, 2, 1));
                windows.add(base.clone().add(4, 2, 3));
            }
        }
        // side windows
        windows.add(base.clone().add(2, 2, 0));
        windows.add(base.clone().add(2, 2, 4));

        for (Location location : windows) {
            tasks.removeIf(task -> sameBlock(task.location(), location));
            tasks.add(new BuildTask(location, Material.GLASS_PANE));
        }
    }

    private List<BuildTask> doorwayTasks(Location base, Orientation orientation) {
        List<BuildTask> doorway = new ArrayList<>();
        switch (orientation) {
            case NORTH -> {
                doorway.add(new BuildTask(base.clone().add(2, 1, 4), Material.AIR));
                doorway.add(new BuildTask(base.clone().add(2, 2, 4), Material.AIR));
            }
            case SOUTH -> {
                doorway.add(new BuildTask(base.clone().add(2, 1, 0), Material.AIR));
                doorway.add(new BuildTask(base.clone().add(2, 2, 0), Material.AIR));
            }
            case EAST -> {
                doorway.add(new BuildTask(base.clone().add(0, 1, 2), Material.AIR));
                doorway.add(new BuildTask(base.clone().add(0, 2, 2), Material.AIR));
            }
            case WEST -> {
                doorway.add(new BuildTask(base.clone().add(4, 1, 2), Material.AIR));
                doorway.add(new BuildTask(base.clone().add(4, 2, 2), Material.AIR));
            }
        }
        return doorway;
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld() == b.getWorld()
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private BoundingBox createBoundingBox(List<BuildTask> tasks, int padding) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        for (BuildTask task : tasks) {
            Location location = task.location();
            minX = Math.min(minX, location.getBlockX());
            minY = Math.min(minY, location.getBlockY());
            minZ = Math.min(minZ, location.getBlockZ());
            maxX = Math.max(maxX, location.getBlockX() + 1);
            maxY = Math.max(maxY, location.getBlockY() + 1);
            maxZ = Math.max(maxZ, location.getBlockZ() + 1);
        }

        return new BoundingBox(minX - padding, minY - 1, minZ - padding, maxX + padding, maxY + 1, maxZ + padding);
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

    public Orientation orientationFor(Location location) {
        float yaw = location.getYaw();
        if (yaw >= -45 && yaw < 45) return Orientation.SOUTH;
        if (yaw >= 45 && yaw < 135) return Orientation.WEST;
        if (yaw >= -135 && yaw < -45) return Orientation.EAST;
        return Orientation.NORTH;
    }
}

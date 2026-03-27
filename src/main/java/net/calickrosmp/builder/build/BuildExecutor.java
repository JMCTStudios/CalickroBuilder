package net.calickrosmp.builder.build;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.hook.CalickroNpcBridgeHook;
import net.calickrosmp.builder.job.BuildJob;
import net.calickrosmp.builder.job.BuildJobManager;
import net.calickrosmp.builder.npc.BuilderProfile;
import net.calickrosmp.builder.npc.BuilderState;
import net.calickrosmp.builder.plan.BuildPlan;
import net.calickrosmp.builder.plan.Orientation;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BuildExecutor {
    private final CalickroBuilderPlugin plugin;
    private final BuildJobManager buildJobManager;
    private final CalickroNpcBridgeHook bridgeHook;

    public BuildExecutor(CalickroBuilderPlugin plugin, BuildJobManager buildJobManager, CalickroNpcBridgeHook bridgeHook) {
        this.plugin = plugin;
        this.buildJobManager = buildJobManager;
        this.bridgeHook = bridgeHook;
    }

    public void executeStarterHouse(Player requester, BuilderProfile profile, BuildPlan plan, BuildJob job) {
        NPC builderNpc = findNpc(profile);
        if (builderNpc == null || !builderNpc.isSpawned()) {
            profile.setState(BuilderState.ERROR);
            buildJobManager.failCurrent(profile.identity().npcId());
            bridgeHook.pushState(profile.identity(), BuilderState.ERROR, "Builder NPC is not spawned");
            requester.sendMessage("§cYour builder NPC must be spawned before it can build.");
            return;
        }

        List<BuildTask> tasks = BuildPatternFactory.createStarterHouse(plan);
        if (tasks.isEmpty()) {
            profile.setState(BuilderState.ERROR);
            buildJobManager.failCurrent(profile.identity().npcId());
            requester.sendMessage("§cNo build tasks were generated.");
            return;
        }

        BuildBounds bounds = BuildBounds.fromTasks(tasks);
        Location relocationSpot = safeRelocationSpot(plan, bounds);
        List<RelocatedNpc> movedNpcs = relocateAffectedNpcs(builderNpc, bounds, relocationSpot);
        Location workSpot = workSpot(plan);

        profile.setState(BuilderState.BUILDING);
        bridgeHook.pushState(profile.identity(), BuilderState.BUILDING, plan.summary());
        buildJobManager.startCurrent(profile.identity().npcId());

        navigateBuilderToSite(builderNpc, workSpot, () -> beginPlacement(requester, profile, builderNpc, movedNpcs, tasks, plan, bounds, job));
    }

    private void navigateBuilderToSite(NPC builderNpc, Location workSpot, Runnable onArrive) {
        builderNpc.getNavigator().setTarget(workSpot);

        new BukkitRunnable() {
            int checks;

            @Override
            public void run() {
                checks++;
                Entity entity = builderNpc.getEntity();
                if (entity == null) {
                    if (checks > 80) {
                        cancel();
                        onArrive.run();
                    }
                    return;
                }

                if (entity.getLocation().distanceSquared(workSpot) <= 2.25 || checks > 120) {
                    builderNpc.getNavigator().cancelNavigation();
                    if (entity.getLocation().distanceSquared(workSpot) > 2.25) {
                        builderNpc.teleport(workSpot, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    }
                    cancel();
                    onArrive.run();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void beginPlacement(Player requester,
                                BuilderProfile profile,
                                NPC builderNpc,
                                List<RelocatedNpc> movedNpcs,
                                List<BuildTask> tasks,
                                BuildPlan plan,
                                BuildBounds bounds,
                                BuildJob job) {
        new BukkitRunnable() {
            int index;

            @Override
            public void run() {
                if (!builderNpc.isSpawned() || builderNpc.getEntity() == null) {
                    cleanup(false);
                    cancel();
                    return;
                }

                int perStep = Math.max(1, plugin.settings().blocksPerStep());
                for (int i = 0; i < perStep && index < tasks.size(); i++) {
                    BuildTask task = tasks.get(index++);
                    if (task.location().getWorld() == null) {
                        continue;
                    }
                    builderNpc.faceLocation(task.location());
                    Entity entity = builderNpc.getEntity();
                    if (entity instanceof LivingEntity living) {
                        living.swingMainHand();
                    }
                    task.location().getBlock().setType(task.material(), false);
                }

                if (index >= tasks.size()) {
                    cleanup(true);
                    cancel();
                }
            }

            private void cleanup(boolean success) {
                restoreNpcs(movedNpcs);
                if (success) {
                    buildJobManager.completeCurrent(profile.identity().npcId());
                    profile.setState(BuilderState.COMPLETE);
                    bridgeHook.pushState(profile.identity(), BuilderState.COMPLETE, "Starter house built");
                    requester.sendMessage("§a" + profile.identity().displayName() + " finished building the starter house.");
                } else {
                    buildJobManager.failCurrent(profile.identity().npcId());
                    profile.setState(BuilderState.ERROR);
                    bridgeHook.pushState(profile.identity(), BuilderState.ERROR, "Build cancelled");
                    requester.sendMessage("§cThe build stopped before it completed.");
                }

                builderNpc.teleport(workSpot(plan), PlayerTeleportEvent.TeleportCause.PLUGIN);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        profile.setState(BuilderState.IDLE);
                        bridgeHook.pushState(profile.identity(), BuilderState.IDLE, "Ready");
                    }
                }.runTaskLater(plugin, 40L);
            }
        }.runTaskTimer(plugin, 0L, Math.max(2L, plugin.settings().buildIntervalTicks()));
    }

    private List<RelocatedNpc> relocateAffectedNpcs(NPC builderNpc, BuildBounds bounds, Location relocationSpot) {
        List<RelocatedNpc> moved = new ArrayList<>();
        if (!CitizensAPI.hasImplementation()) {
            return moved;
        }

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (npc == null || !npc.isSpawned()) {
                continue;
            }
            if (Objects.equals(npc.getUniqueId(), builderNpc.getUniqueId())) {
                continue;
            }
            Location current = npc.getStoredLocation();
            if (current.getWorld() == null || !current.getWorld().equals(bounds.world)) {
                continue;
            }
            if (bounds.contains(current)) {
                moved.add(new RelocatedNpc(npc, current.clone()));
                npc.teleport(relocationSpot, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
        return moved;
    }

    private void restoreNpcs(List<RelocatedNpc> movedNpcs) {
        for (RelocatedNpc moved : movedNpcs) {
            if (moved.npc() != null && moved.npc().isSpawned()) {
                moved.npc().teleport(moved.originalLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
    }

    private NPC findNpc(BuilderProfile profile) {
        if (!CitizensAPI.hasImplementation()) {
            return null;
        }
        long least = profile.identity().npcId().getLeastSignificantBits();
        int npcId = (int) least;
        return CitizensAPI.getNPCRegistry().getById(npcId);
    }

    private Location workSpot(BuildPlan plan) {
        Location base = plan.anchor().clone();
        int doorX = Math.max(7, plan.houseSpec().width()) / 2;
        return switch (plan.houseSpec().orientation()) {
            case SOUTH -> base.clone().add(doorX + 0.5, 0, -2.0);
            case NORTH -> base.clone().add(-doorX - 0.5, 0, 2.0);
            case EAST -> base.clone().add(-2.0, 0, -doorX - 0.5);
            case WEST -> base.clone().add(2.0, 0, doorX + 0.5);
        };
    }

    private Location safeRelocationSpot(BuildPlan plan, BuildBounds bounds) {
        Location base = plan.anchor().clone();
        return switch (plan.houseSpec().orientation()) {
            case SOUTH -> base.clone().add(bounds.width() + 3.5, 0, bounds.depth() + 3.5);
            case NORTH -> base.clone().add(-bounds.width() - 3.5, 0, -bounds.depth() - 3.5);
            case EAST -> base.clone().add(bounds.depth() + 3.5, 0, -bounds.width() - 3.5);
            case WEST -> base.clone().add(-bounds.depth() - 3.5, 0, bounds.width() + 3.5);
        };
    }

    private static final class BuildBounds {
        private final World world;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;

        private BuildBounds(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
            this.world = world;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        static BuildBounds fromTasks(List<BuildTask> tasks) {
            Location first = tasks.get(0).location();
            int minX = first.getBlockX();
            int maxX = minX;
            int minY = first.getBlockY();
            int maxY = minY;
            int minZ = first.getBlockZ();
            int maxZ = minZ;
            for (BuildTask task : tasks) {
                Location loc = task.location();
                minX = Math.min(minX, loc.getBlockX());
                maxX = Math.max(maxX, loc.getBlockX());
                minY = Math.min(minY, loc.getBlockY());
                maxY = Math.max(maxY, loc.getBlockY());
                minZ = Math.min(minZ, loc.getBlockZ());
                maxZ = Math.max(maxZ, loc.getBlockZ());
            }
            return new BuildBounds(first.getWorld(), minX, maxX, minY, maxY, minZ, maxZ);
        }

        boolean contains(Location location) {
            return location.getBlockX() >= minX && location.getBlockX() <= maxX
                    && location.getBlockY() >= minY && location.getBlockY() <= maxY + 1
                    && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
        }

        int width() {
            return maxX - minX + 1;
        }

        int depth() {
            return maxZ - minZ + 1;
        }
    }
}

package net.calickrosmp.builder.build;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.hook.CalickroNpcBridgeHook;
import net.calickrosmp.builder.job.BuildJob;
import net.calickrosmp.builder.job.BuildJobManager;
import net.calickrosmp.builder.npc.BuilderProfile;
import net.calickrosmp.builder.npc.BuilderState;
import net.calickrosmp.builder.plan.BuildPlan;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        if (builderNpc == null || !builderNpc.isSpawned() || builderNpc.getEntity() == null) {
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
        List<Location> workSpots = workSpots(plan, bounds);

        profile.setState(BuilderState.MOVING);
        profile.markBuildStarted();
        bridgeHook.pushState(profile.identity(), BuilderState.MOVING, "Walking to build site");
        buildJobManager.startCurrent(profile.identity().npcId());

        tryNavigateToAnyWorkSpot(
                profile.identity().npcId(),
                builderNpc,
                workSpots,
                0,
                () -> {
                    profile.setState(BuilderState.BUILDING);
                    bridgeHook.pushState(profile.identity(), BuilderState.BUILDING, plan.summary());
                    schedulePlacementStep(requester, profile, builderNpc, movedNpcs, tasks, plan, job, 0, workSpots.get(0));
                },
                () -> {
                    restoreNpcs(movedNpcs);
                    buildJobManager.failCurrent(profile.identity().npcId());
                    profile.clearBuildStarted();
                    profile.setState(BuilderState.ERROR);
                    bridgeHook.pushState(profile.identity(), BuilderState.ERROR, "Could not reach build site");
                    requester.sendMessage("§c" + profile.identity().displayName() + " could not walk to the build site.");
                }
        );
    }

    private void schedulePlacementStep(Player requester,
                                       BuilderProfile profile,
                                       NPC builderNpc,
                                       List<RelocatedNpc> movedNpcs,
                                       List<BuildTask> tasks,
                                       BuildPlan plan,
                                       BuildJob job,
                                       int startIndex,
                                       Location finalWorkSpot) {
        if (!builderNpc.isSpawned() || builderNpc.getEntity() == null) {
            cleanup(requester, profile, builderNpc, movedNpcs, plan, false, null);
            return;
        }

        int index = startIndex;
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
            if (task.blockData() != null) {
                task.location().getBlock().setBlockData(task.blockData(), false);
            } else {
                task.location().getBlock().setType(task.material(), false);
            }
        }

        if (index >= tasks.size()) {
            cleanup(requester, profile, builderNpc, movedNpcs, plan, true, finalWorkSpot);
            return;
        }

        final int nextIndex = index;
        long delay = profile.effectiveDelayTicks(plugin.settings(), tasks.size(), nextIndex);
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                schedulePlacementStep(requester, profile, builderNpc, movedNpcs, tasks, plan, job, nextIndex, finalWorkSpot), delay);
    }

    private void tryNavigateToAnyWorkSpot(UUID builderId,
                                         NPC builderNpc,
                                         List<Location> workSpots,
                                         int index,
                                         Runnable onArrive,
                                         Runnable onFail) {
        if (index >= workSpots.size()) {
            onFail.run();
            return;
        }

        Location target = workSpots.get(index);
        navigateBuilderToSite(builderId, builderNpc, target, onArrive, () ->
                tryNavigateToAnyWorkSpot(builderId, builderNpc, workSpots, index + 1, onArrive, onFail));
    }

    private void navigateBuilderToSite(UUID builderId, NPC builderNpc, Location workSpot, Runnable onArrive, Runnable onFail) {
        Entity entity = builderNpc.getEntity();
        if (entity == null) {
            onFail.run();
            return;
        }

        Location start = entity.getLocation().clone();
        Location centeredTarget = workSpot.clone();
        centeredTarget.setYaw(start.getYaw());
        centeredTarget.setPitch(start.getPitch());

        double startDistanceSquared = start.distanceSquared(centeredTarget);
        if (startDistanceSquared <= 2.25) {
            onArrive.run();
            return;
        }

        builderNpc.getNavigator().setTarget(centeredTarget);
        if (builderNpc.getNavigator().isNavigating()) {
            builderNpc.faceLocation(centeredTarget);
        }

        new BukkitRunnable() {
            int checks;
            boolean moved;

            @Override
            public void run() {
                if (buildJobManager.isCancelRequested(builderId)) {
                    builderNpc.getNavigator().cancelNavigation();
                    cancel();
                    onFail.run();
                    return;
                }

                checks++;

                Entity currentEntity = builderNpc.getEntity();
                if (currentEntity == null || !builderNpc.isSpawned()) {
                    cancel();
                    onFail.run();
                    return;
                }

                Location current = currentEntity.getLocation();
                if (current.distanceSquared(start) >= 1.0) {
                    moved = true;
                }

                if (current.distanceSquared(centeredTarget) <= 2.25) {
                    builderNpc.getNavigator().cancelNavigation();
                    cancel();
                    onArrive.run();
                    return;
                }

                if (checks >= 80) {
                    builderNpc.getNavigator().cancelNavigation();
                    cancel();
                    if (moved && current.distanceSquared(centeredTarget) <= 16.0) {
                        onArrive.run();
                    } else {
                        onFail.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void cleanup(Player requester,
                         BuilderProfile profile,
                         NPC builderNpc,
                         List<RelocatedNpc> movedNpcs,
                         BuildPlan plan,
                         boolean success,
                         Location finalWorkSpot) {
        restoreNpcs(movedNpcs);
        if (success) {
            buildJobManager.completeCurrent(profile.identity().npcId());
            profile.setState(BuilderState.COMPLETE);
            bridgeHook.pushState(profile.identity(), BuilderState.COMPLETE, "Starter house built");
            requester.sendMessage("§a" + profile.identity().displayName() + " finished building the starter house.");
            long started = profile.clearBuildStarted();
            if (started > 0L) {
                requester.sendMessage("§6[" + profile.identity().displayName() + "] §ePhew... that took me " + formatElapsed(System.currentTimeMillis() - started) + ".");
            }
        } else {
            buildJobManager.failCurrent(profile.identity().npcId());
            profile.clearBuildStarted();
            profile.setState(BuilderState.ERROR);
            bridgeHook.pushState(profile.identity(), BuilderState.ERROR, "Build cancelled");
            requester.sendMessage("§cThe build stopped before it completed.");
        }

        if (builderNpc != null && builderNpc.isSpawned()) {
            builderNpc.teleport(finalWorkSpot == null ? workSpots(plan, BuildBounds.fromTasks(BuildPatternFactory.createStarterHouse(plan))).get(0) : finalWorkSpot, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            profile.setState(BuilderState.IDLE);
            bridgeHook.pushState(profile.identity(), BuilderState.IDLE, "Ready");
        }, 40L);
    }

    private String formatElapsed(long elapsedMillis) {
        long totalSeconds = Math.max(0L, TimeUnit.MILLISECONDS.toSeconds(elapsedMillis));
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        List<String> parts = new ArrayList<>();
        if (hours > 0) {
            parts.add(hours + (hours == 1 ? " hour" : " hours"));
        }
        if (minutes > 0) {
            parts.add(minutes + (minutes == 1 ? " minute" : " minutes"));
        }
        if (seconds > 0 || parts.isEmpty()) {
            parts.add(seconds + (seconds == 1 ? " second" : " seconds"));
        }
        return String.join(", ", parts);
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
        int npcId = (int) profile.identity().npcId().getLeastSignificantBits();
        if (npcId <= 0) {
            return null;
        }
        return CitizensAPI.getNPCRegistry().getById(npcId);
    }

    private List<Location> workSpots(BuildPlan plan, BuildBounds bounds) {
        Location base = plan.anchor().clone();
        int halfWidth = Math.max(2, plan.houseSpec().width() / 2);
        int halfDepth = Math.max(2, plan.houseSpec().depth() / 2);
        List<Location> spots = new ArrayList<>();

        spots.add(switch (plan.houseSpec().orientation()) {
            case SOUTH -> base.clone().add(halfWidth + 0.5, 0, -2.0);
            case NORTH -> base.clone().add(halfWidth + 0.5, 0, plan.houseSpec().depth() + 1.5);
            case EAST -> base.clone().add(-2.0, 0, halfDepth + 0.5);
            case WEST -> base.clone().add(plan.houseSpec().width() + 1.5, 0, halfDepth + 0.5);
        });

        spots.add(base.clone().add(halfWidth + 0.5, 0, plan.houseSpec().depth() + 1.5));
        spots.add(base.clone().add(halfWidth + 0.5, 0, -2.0));
        spots.add(base.clone().add(-2.0, 0, halfDepth + 0.5));
        spots.add(base.clone().add(plan.houseSpec().width() + 1.5, 0, halfDepth + 0.5));
        spots.add(base.clone().add(-3.5, 0, -3.5));
        spots.add(base.clone().add(bounds.width() + 2.5, 0, bounds.depth() + 2.5));
        return spots;
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

        int width() { return maxX - minX + 1; }
        int depth() { return maxZ - minZ + 1; }
    }
}

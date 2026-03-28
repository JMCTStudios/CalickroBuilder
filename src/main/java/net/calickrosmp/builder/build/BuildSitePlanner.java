package net.calickrosmp.builder.build;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.hook.WorldGuardHook;
import net.calickrosmp.builder.plan.HouseSpec;
import net.calickrosmp.builder.plan.Orientation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BuildSitePlanner {
    private final CalickroBuilderPlugin plugin;
    private final WorldGuardHook worldGuardHook;

    public BuildSitePlanner(CalickroBuilderPlugin plugin, WorldGuardHook worldGuardHook) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
    }

    public SiteSelection selectStarterHouseSite(Player requester, Location origin, Orientation preferredOrientation, HouseSpec spec) {
        if (origin == null || origin.getWorld() == null) {
            return SiteSelection.failure("I don't know where to start building from yet.");
        }

        List<Orientation> orientations = orderedOrientations(origin, preferredOrientation);
        SiteSelection fallback = null;

        for (Orientation orientation : orientations) {
            SiteSelection candidate = selectStarterHouseAnchor(requester, origin, orientation, spec);
            if (!candidate.found()) {
                if (fallback == null) {
                    fallback = candidate;
                }
                continue;
            }

            int score = scoreOrientation(requester, candidate.anchor(), orientation, spec);
            candidate = candidate.withScore(score);
            if (score >= 1000) {
                return candidate;
            }
            if (fallback == null || !fallback.found() || candidate.score() > fallback.score()) {
                fallback = candidate;
            }
        }

        if (fallback != null) {
            return fallback;
        }
        return SiteSelection.failure("I couldn't find a safe build spot nearby. Move me away from roads or reduce preserve restrictions.");
    }

    public SiteSelection selectStarterHouseAnchor(Player requester, Location npcLocation, Orientation orientation, HouseSpec spec) {
        if (npcLocation == null || npcLocation.getWorld() == null) {
            return SiteSelection.failure("I don't know where to start building from yet.");
        }

        World world = npcLocation.getWorld();
        int width = spec.width();
        int depth = spec.depth();
        int doorX = width / 2;
        int minDistance = plugin.settings().siteSearchMinDistance();
        int maxDistance = plugin.settings().siteSearchMaxDistance();
        int lateralStep = Math.max(1, plugin.settings().siteLateralStep());
        int maxSideOffset = Math.max(0, plugin.settings().siteMaxSideOffset());

        for (int forward = minDistance; forward <= maxDistance; forward += 2) {
            for (int side = 0; side <= maxSideOffset; side += lateralStep) {
                int[] offsets = side == 0 ? new int[]{0} : new int[]{side, -side};
                for (int sideOffset : offsets) {
                    Location candidate = anchorFromOffsets(npcLocation, orientation, doorX, forward, sideOffset);
                    SiteCheckResult check = isValidAnchor(requester, world, candidate, orientation, width, depth);
                    if (check.allowed()) {
                        return SiteSelection.success(candidate, orientation, check.reason());
                    }
                }
            }
        }

        return SiteSelection.failure("I couldn't find a safe build spot nearby. Move me away from roads or reduce preserve restrictions.");
    }

    private List<Orientation> orderedOrientations(Location origin, Orientation preferredOrientation) {
        List<Orientation> order = new ArrayList<>();
        order.add(preferredOrientation);

        Orientation awayFromSpawn = orientationAwayFromSpawn(origin);
        if (!order.contains(awayFromSpawn)) {
            order.add(awayFromSpawn);
        }

        for (Orientation orientation : Orientation.values()) {
            if (!order.contains(orientation)) {
                order.add(orientation);
            }
        }
        return order;
    }

    private Orientation orientationAwayFromSpawn(Location origin) {
        Location spawn = origin.getWorld().getSpawnLocation();
        double dx = origin.getX() - spawn.getX();
        double dz = origin.getZ() - spawn.getZ();

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Orientation.EAST : Orientation.WEST;
        }
        return dz >= 0 ? Orientation.SOUTH : Orientation.NORTH;
    }

    private int scoreOrientation(Player requester, Location anchor, Orientation orientation, HouseSpec spec) {
        int score = 0;
        List<Material> avoid = plugin.settings().siteAvoidGroundMaterials();
        Location spawn = anchor.getWorld().getSpawnLocation();
        Location frontDoorOutside = frontOutside(anchor, orientation, spec.width());

        for (int i = 1; i <= 6; i++) {
            Location probe = frontDoorOutside.clone();
            switch (orientation) {
                case SOUTH -> probe.add(0, 0, -i);
                case NORTH -> probe.add(0, 0, i);
                case EAST -> probe.add(-i, 0, 0);
                case WEST -> probe.add(i, 0, 0);
            }
            Material below = probe.clone().add(0, -1, 0).getBlock().getType();
            Material at = probe.getBlock().getType();
            if (avoid.contains(at) || avoid.contains(below)) {
                score += 1000 - (i * 80);
                break;
            }
        }

        double dx = frontDoorOutside.getX() - spawn.getX();
        double dz = frontDoorOutside.getZ() - spawn.getZ();
        double distSq = (dx * dx) + (dz * dz);
        score += Math.min(250, (int) distSq / 4);

        if (!worldGuardHook.canBypassPreserve(requester) && wouldFaceTowardSpawn(anchor, orientation, spawn)) {
            score -= 600;
        }

        return score;
    }

    private boolean wouldFaceTowardSpawn(Location anchor, Orientation orientation, Location spawn) {
        Location front = frontOutside(anchor, orientation, 15);
        double frontDist = front.distanceSquared(spawn);
        double backDist = switch (orientation) {
            case SOUTH -> front.clone().add(0, 0, 4).distanceSquared(spawn);
            case NORTH -> front.clone().add(0, 0, -4).distanceSquared(spawn);
            case EAST -> front.clone().add(4, 0, 0).distanceSquared(spawn);
            case WEST -> front.clone().add(-4, 0, 0).distanceSquared(spawn);
        };
        return frontDist < backDist;
    }

    private Location frontOutside(Location anchor, Orientation orientation, int width) {
        int doorX = width / 2;
        return switch (orientation) {
            case SOUTH -> anchor.clone().add(doorX, 0, -1);
            case NORTH -> anchor.clone().add(-doorX, 0, 1);
            case EAST -> anchor.clone().add(-1, 0, -doorX);
            case WEST -> anchor.clone().add(1, 0, doorX);
        };
    }

    private Location anchorFromOffsets(Location npcLocation, Orientation orientation, int doorX, int forward, int sideOffset) {
        Location base = npcLocation.clone();
        return switch (orientation) {
            case SOUTH -> base.add(-(doorX + 0.5) + sideOffset, 0, forward);
            case NORTH -> base.add((doorX + 0.5) - sideOffset, 0, -forward);
            case EAST -> base.add(forward, 0, (doorX + 0.5) + sideOffset);
            case WEST -> base.add(-forward, 0, -(doorX + 0.5) - sideOffset);
        };
    }

    private SiteCheckResult isValidAnchor(Player requester, World world, Location anchor, Orientation orientation, int width, int depth) {
        Set<Material> avoid = new HashSet<>(plugin.settings().siteAvoidGroundMaterials());
        int padding = plugin.settings().collisionPadding();
        int preserveRadius = plugin.settings().preserveWorldSpawnRadius();
        boolean bypassPreserve = requester.hasPermission("calickrobuilder.bypass.preserve") || requester.hasPermission("calickrobuilder.bypass.all");

        Location spawn = world.getSpawnLocation();

        WorldGuardHook.ValidationScan wgScan = worldGuardHook.scanPlan(requester, anchor, orientation, width, depth, padding);
        if (!wgScan.allowed()) {
            return SiteCheckResult.blocked(wgScan.message());
        }

        for (int localX = -padding; localX < width + padding; localX++) {
            for (int localZ = -padding; localZ < depth + padding; localZ++) {
                Location sample = rotate(anchor, orientation, localX, 0, localZ);
                Block floor = sample.getBlock();
                Block below = sample.clone().add(0, -1, 0).getBlock();
                Block head = sample.clone().add(0, 1, 0).getBlock();
                Block aboveHead = sample.clone().add(0, 2, 0).getBlock();

                if (!bypassPreserve && preserveRadius > 0) {
                    double dx = sample.getX() - spawn.getX();
                    double dz = sample.getZ() - spawn.getZ();
                    if ((dx * dx) + (dz * dz) <= (preserveRadius * preserveRadius)) {
                        return SiteCheckResult.blocked("That spot would touch the preserved spawn center.");
                    }
                }

                if (avoid.contains(floor.getType()) || avoid.contains(below.getType())) {
                    return SiteCheckResult.blocked("That spot would build on a road or protected path.");
                }

                if (below.isLiquid() || floor.isLiquid()) {
                    return SiteCheckResult.blocked("That spot is too wet to build on.");
                }

                if (!below.getType().isSolid()) {
                    return SiteCheckResult.blocked("That spot doesn't have solid ground under it.");
                }

                if (!(head.isPassable() || head.getType().isAir())) {
                    return SiteCheckResult.blocked("That spot is blocked above ground.");
                }
                if (!(aboveHead.isPassable() || aboveHead.getType().isAir())) {
                    return SiteCheckResult.blocked("That spot doesn't have enough headroom.");
                }
            }
        }

        List<Location> porchChecks = switch (orientation) {
            case SOUTH -> List.of(anchor.clone().add(width / 2.0, 0, -1.0));
            case NORTH -> List.of(anchor.clone().add(-(width / 2.0), 0, 1.0));
            case EAST -> List.of(anchor.clone().add(-1.0, 0, -(width / 2.0)));
            case WEST -> List.of(anchor.clone().add(1.0, 0, (width / 2.0)));
        };
        for (Location porch : porchChecks) {
            if (avoid.contains(porch.getBlock().getType()) || avoid.contains(porch.clone().add(0, -1, 0).getBlock().getType())) {
                return SiteCheckResult.blocked("That front door would open directly onto a protected path.");
            }
        }

        return SiteCheckResult.allowed("Build site selected");
    }

    private Location rotate(Location base, Orientation orientation, int localX, int localY, int localZ) {
        return switch (orientation) {
            case SOUTH -> base.clone().add(localX, localY, localZ);
            case NORTH -> base.clone().add(-localX, localY, -localZ);
            case EAST -> base.clone().add(localZ, localY, -localX);
            case WEST -> base.clone().add(-localZ, localY, localX);
        };
    }

    public record SiteSelection(boolean found, Location anchor, Orientation orientation, String message, int score) {
        public static SiteSelection success(Location anchor, Orientation orientation, String message) {
            return new SiteSelection(true, anchor, orientation, message, 0);
        }

        public static SiteSelection failure(String message) {
            return new SiteSelection(false, null, null, message, Integer.MIN_VALUE);
        }

        public SiteSelection withScore(int newScore) {
            return new SiteSelection(found, anchor, orientation, message, newScore);
        }
    }

    private record SiteCheckResult(boolean allowed, String reason) {
        static SiteCheckResult allowed(String reason) {
            return new SiteCheckResult(true, reason);
        }

        static SiteCheckResult blocked(String reason) {
            return new SiteCheckResult(false, reason);
        }
    }
}

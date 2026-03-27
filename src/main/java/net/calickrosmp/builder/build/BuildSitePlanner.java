package net.calickrosmp.builder.build;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.plan.HouseSpec;
import net.calickrosmp.builder.plan.Orientation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BuildSitePlanner {
    private final CalickroBuilderPlugin plugin;

    public BuildSitePlanner(CalickroBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public SiteSelection selectStarterHouseAnchor(Player requester, Location npcLocation, Orientation orientation, HouseSpec spec) {
        World world = npcLocation.getWorld();
        if (world == null) {
            return SiteSelection.failure("The builder is not in a valid world.");
        }

        int width = Math.max(7, spec.width());
        int depth = Math.max(9, spec.depth());
        int doorX = width / 2;

        int minDistance = plugin.settings().siteSearchMinDistance();
        int maxDistance = plugin.settings().siteSearchMaxDistance();
        int lateralStep = Math.max(1, plugin.settings().siteLateralStep());
        int maxSideOffset = plugin.settings().siteMaxSideOffset();

        for (int forward = minDistance; forward <= maxDistance; forward++) {
            for (int side = 0; side <= maxSideOffset; side += lateralStep) {
                int[] offsets = side == 0 ? new int[]{0} : new int[]{side, -side};
                for (int sideOffset : offsets) {
                    Location candidate = anchorFromOffsets(npcLocation, orientation, doorX, forward, sideOffset);
                    SiteCheckResult check = isValidAnchor(requester, world, candidate, orientation, width, depth);
                    if (check.allowed()) {
                        return SiteSelection.success(candidate, check.reason());
                    }
                }
            }
        }

        return SiteSelection.failure("I couldn't find a safe build spot nearby. Move me away from roads or reduce preserve restrictions.");
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

        // Make sure the front porch itself does not land on a road either.
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

    public record SiteSelection(boolean found, Location anchor, String message) {
        public static SiteSelection success(Location anchor, String message) {
            return new SiteSelection(true, anchor, message);
        }

        public static SiteSelection failure(String message) {
            return new SiteSelection(false, null, message);
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

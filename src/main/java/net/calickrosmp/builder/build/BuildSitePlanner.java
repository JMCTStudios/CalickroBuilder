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
    private static final int MAX_CANDIDATES_PER_ORIGIN = 96;
    private final CalickroBuilderPlugin plugin;
    private final WorldGuardHook worldGuardHook;

    public BuildSitePlanner(CalickroBuilderPlugin plugin, WorldGuardHook worldGuardHook) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
    }

    public SiteSelection selectStarterHouseSite(Player requester, Location origin, Orientation preferredOrientation, HouseSpec spec) {
        GroundedOrigin grounded = groundOrigin(origin);
        if (!grounded.valid()) {
            return SiteSelection.failure("I don't know where to start building from yet.");
        }

        List<ScoredCandidate> bestCandidates = new ArrayList<>();
        for (Location searchOrigin : searchOrigins(grounded.location())) {
            for (Orientation orientation : orderedOrientations(searchOrigin, preferredOrientation)) {
                bestCandidates.addAll(collectCandidates(requester, searchOrigin, orientation, spec));
            }
        }

        ScoredCandidate best = null;
        for (ScoredCandidate candidate : bestCandidates) {
            WorldGuardHook.ValidationScan wg = worldGuardHook.scanPlan(requester, candidate.anchor(), candidate.orientation(), spec.width(), spec.depth(), plugin.settings().collisionPadding());
            if (!wg.allowed()) {
                continue;
            }
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }

        if (best != null) {
            return SiteSelection.success(best.anchor(), best.orientation(), best.reason()).withScore(best.score());
        }
        return SiteSelection.failure("I couldn't find a safe build spot nearby. Move me away from roads or spread the builds out more.");
    }

    public ScanReport scanArea(Player requester, Location origin, Orientation preferredOrientation, HouseSpec spec) {
        GroundedOrigin grounded = groundOrigin(origin);
        if (!grounded.valid()) {
            return new ScanReport(false, "No valid origin to scan from.", List.of());
        }

        List<String> details = new ArrayList<>();
        details.add("Requested origin: " + simple(origin));
        details.add("Grounded origin: " + simple(grounded.location()));
        details.add("Preferred orientation: " + preferredOrientation);
        details.add("Search distance: " + plugin.settings().siteSearchMinDistance() + "-" + plugin.settings().siteSearchMaxDistance());
        details.add("House gap: " + plugin.settings().siteMinHouseGap() + " | Road clearance: " + plugin.settings().siteRoadClearance());

        int checked = 0;
        int blockedRoad = 0;
        int blockedGround = 0;
        int blockedHeadroom = 0;
        int blockedStructure = 0;
        int blockedPreserve = 0;
        int blockedWorldGuard = 0;
        ScoredCandidate best = null;

        for (Location searchOrigin : searchOrigins(grounded.location())) {
            for (Orientation orientation : orderedOrientations(searchOrigin, preferredOrientation)) {
                for (CandidateCheck candidateCheck : collectCandidateChecks(requester, searchOrigin, orientation, spec, true)) {
                    if (!candidateCheck.allowed()) {
                        switch (candidateCheck.reasonCode()) {
                            case ROAD -> blockedRoad++;
                            case GROUND -> blockedGround++;
                            case HEADROOM -> blockedHeadroom++;
                            case STRUCTURE -> blockedStructure++;
                            case PRESERVE -> blockedPreserve++;
                            case WORLDGUARD -> blockedWorldGuard++;
                            default -> {}
                        }
                        continue;
                    }
                    checked++;
                    if (best == null || candidateCheck.score() > best.score()) {
                        best = new ScoredCandidate(candidateCheck.anchor(), candidateCheck.orientation(), candidateCheck.reason(), candidateCheck.score());
                    }
                }
            }
        }

        if (best != null) {
            details.add("Valid candidates: " + checked);
            details.add("Best anchor: " + simple(best.anchor()) + " facing " + best.orientation());
            details.add("Score: " + best.score());
            return new ScanReport(true, "Found a valid build plot.", details);
        }

        details.add("Valid candidates: 0");
        details.add("Rejected by road: " + blockedRoad);
        details.add("Rejected by ground: " + blockedGround);
        details.add("Rejected by headroom: " + blockedHeadroom);
        details.add("Rejected by structure spacing: " + blockedStructure);
        details.add("Rejected by preserve: " + blockedPreserve);
        details.add("Rejected by WorldGuard: " + blockedWorldGuard);
        details.add("Likely blockers: road clearance too strong, house-gap too large, or WorldGuard/preserve limits");
        return new ScanReport(false, "No valid build plot found from the current search area.", details);
    }

    private List<ScoredCandidate> collectCandidates(Player requester, Location origin, Orientation orientation, HouseSpec spec) {
        List<ScoredCandidate> candidates = new ArrayList<>();
        for (CandidateCheck check : collectCandidateChecks(requester, origin, orientation, spec, false)) {
            if (check.allowed()) {
                candidates.add(new ScoredCandidate(check.anchor(), check.orientation(), check.reason(), check.score()));
            }
        }
        return candidates;
    }

    private List<CandidateCheck> collectCandidateChecks(Player requester, Location origin, Orientation orientation, HouseSpec spec, boolean skipWorldGuard) {
        List<CandidateCheck> checks = new ArrayList<>();
        int width = spec.width();
        int doorX = width / 2;
        int minDistance = plugin.settings().siteSearchMinDistance();
        int maxDistance = plugin.settings().siteSearchMaxDistance();
        int lateralStep = Math.max(1, plugin.settings().siteLateralStep());
        int maxSideOffset = Math.max(0, plugin.settings().siteMaxSideOffset());
        int seen = 0;

        for (int forward = minDistance; forward <= maxDistance && seen < MAX_CANDIDATES_PER_ORIGIN; forward += 4) {
            for (int side = 0; side <= maxSideOffset && seen < MAX_CANDIDATES_PER_ORIGIN; side += lateralStep) {
                int[] offsets = side == 0 ? new int[]{0} : new int[]{side, -side};
                for (int sideOffset : offsets) {
                    Location candidate = anchorFromOffsets(origin, orientation, doorX, forward, sideOffset);
                    SiteCheckResult check = isValidAnchor(requester, origin.getWorld(), candidate, orientation, spec.width(), spec.depth(), skipWorldGuard);
                    if (!check.allowed()) {
                        checks.add(new CandidateCheck(candidate, orientation, check.reason(), 0, false, check.reasonCode()));
                    } else {
                        int score = scoreCandidate(origin, candidate, orientation, spec);
                        checks.add(new CandidateCheck(candidate, orientation, check.reason(), score, true, ReasonCode.NONE));
                    }
                    seen++;
                    if (seen >= MAX_CANDIDATES_PER_ORIGIN) {
                        break;
                    }
                }
            }
        }
        return checks;
    }

    private int scoreCandidate(Location origin, Location anchor, Orientation orientation, HouseSpec spec) {
        int score = 0;
        Location frontDoorOutside = frontOutside(anchor, orientation, spec.width());
        int roadDistance = nearestRoadDistance(frontDoorOutside, plugin.settings().siteRoadPreferenceDistance());
        if (roadDistance >= 0) {
            score += 1200 - (roadDistance * 80);
        } else {
            score -= 150;
        }

        Location spawn = anchor.getWorld().getSpawnLocation();
        if (wouldFaceTowardSpawn(anchor, orientation, spawn)) {
            score -= 300;
        }

        double originDistance = origin.distanceSquared(anchor);
        score -= Math.min(180, (int) originDistance / 6);

        int nearbyStructures = countNearbyStructureBlocks(anchor, orientation, spec.width(), spec.depth(), plugin.settings().siteMinHouseGap());
        score -= nearbyStructures * 25;
        score -= nearestWallPenalty(anchor, spec.width(), spec.depth());
        return score;
    }

    private List<Location> searchOrigins(Location origin) {
        List<Location> origins = new ArrayList<>();
        origins.add(origin.clone());
        int[] rings = new int[]{4, 8, 12, 16};
        for (int ring : rings) {
            origins.add(origin.clone().add(ring, 0, 0));
            origins.add(origin.clone().add(-ring, 0, 0));
            origins.add(origin.clone().add(0, 0, ring));
            origins.add(origin.clone().add(0, 0, -ring));
            origins.add(origin.clone().add(ring, 0, ring));
            origins.add(origin.clone().add(-ring, 0, ring));
            origins.add(origin.clone().add(ring, 0, -ring));
            origins.add(origin.clone().add(-ring, 0, -ring));
        }
        return origins;
    }

    private GroundedOrigin groundOrigin(Location origin) {
        if (origin == null || origin.getWorld() == null) {
            return new GroundedOrigin(false, null);
        }

        int x = origin.getBlockX();
        int z = origin.getBlockZ();
        World world = origin.getWorld();
        int y = Math.max(world.getHighestBlockYAt(x, z) + 1, world.getMinHeight() + 1);
        Location grounded = origin.clone();
        grounded.setX(x + 0.5);
        grounded.setY(y);
        grounded.setZ(z + 0.5);
        return new GroundedOrigin(true, grounded);
    }

    private List<Orientation> orderedOrientations(Location origin, Orientation preferredOrientation) {
        List<Orientation> ordered = new ArrayList<>();
        ordered.add(preferredOrientation);
        for (Orientation orientation : Orientation.values()) {
            if (orientation != preferredOrientation) {
                ordered.add(orientation);
            }
        }
        return ordered;
    }

    private boolean wouldFaceTowardSpawn(Location anchor, Orientation orientation, Location spawn) {
        Location front = frontOutside(anchor, orientation, 5);
        double frontDist = horizontalDistanceSquared(front, spawn);
        double backDist = horizontalDistanceSquared(frontOutside(anchor, opposite(orientation), 5), spawn);
        return frontDist < backDist;
    }

    private Orientation opposite(Orientation orientation) {
        return switch (orientation) {
            case SOUTH -> Orientation.NORTH;
            case NORTH -> Orientation.SOUTH;
            case EAST -> Orientation.WEST;
            case WEST -> Orientation.EAST;
        };
    }

    private double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return (dx * dx) + (dz * dz);
    }

    private int nearestRoadDistance(Location from, int maxDistance) {
        Set<Material> avoid = new HashSet<>(plugin.settings().siteAvoidGroundMaterials());
        for (int distance = 0; distance <= maxDistance; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) != distance) {
                        continue;
                    }
                    Material at = from.clone().add(dx, 0, dz).getBlock().getType();
                    Material below = from.clone().add(dx, -1, dz).getBlock().getType();
                    if (avoid.contains(at) || avoid.contains(below)) {
                        return distance;
                    }
                }
            }
        }
        return -1;
    }

    private int nearestWallPenalty(Location anchor, int width, int depth) {
        int testRadius = Math.max(width, depth) + 2;
        int penalty = 0;
        for (int dx = -testRadius; dx <= testRadius; dx += 2) {
            Material type = anchor.clone().add(dx, 0, 0).getBlock().getType();
            if (isLikelyWall(type)) {
                penalty += 3;
            }
        }
        for (int dz = -testRadius; dz <= testRadius; dz += 2) {
            Material type = anchor.clone().add(0, 0, dz).getBlock().getType();
            if (isLikelyWall(type)) {
                penalty += 3;
            }
        }
        return penalty;
    }

    private int countNearbyStructureBlocks(Location anchor, Orientation orientation, int width, int depth, int gap) {
        int count = 0;
        int padding = Math.min(2, plugin.settings().collisionPadding() + gap);
        for (int localX = -padding; localX < width + padding; localX += 2) {
            for (int localZ = -padding; localZ < depth + padding; localZ += 2) {
                Location sample = rotate(anchor, orientation, localX, 0, localZ);
                Material floor = sample.getBlock().getType();
                Material above = sample.clone().add(0, 1, 0).getBlock().getType();
                if (isLikelyStructure(floor) || isLikelyStructure(above)) {
                    count++;
                }
            }
        }
        return count;
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

    private Location anchorFromOffsets(Location origin, Orientation orientation, int doorX, int forward, int sideOffset) {
        Location base = origin.clone();
        return switch (orientation) {
            case SOUTH -> base.add(-(doorX + 0.5) + sideOffset, 0, forward);
            case NORTH -> base.add((doorX + 0.5) - sideOffset, 0, -forward);
            case EAST -> base.add(forward, 0, (doorX + 0.5) + sideOffset);
            case WEST -> base.add(-forward, 0, -(doorX + 0.5) - sideOffset);
        };
    }

    private SiteCheckResult isValidAnchor(Player requester, World world, Location anchor, Orientation orientation, int width, int depth, boolean skipWorldGuard) {
        Set<Material> avoid = new HashSet<>(plugin.settings().siteAvoidGroundMaterials());
        int padding = plugin.settings().collisionPadding();
        int preserveRadius = plugin.settings().preserveWorldSpawnRadius();
        int spacingPadding = Math.max(1, Math.min(2, padding + plugin.settings().siteMinHouseGap()));
        int roadClearance = Math.max(0, plugin.settings().siteRoadClearance());
        boolean bypassPreserve = requester.hasPermission("calickrobuilder.bypass.preserve") || requester.hasPermission("calickrobuilder.bypass.all");

        Location spawn = world.getSpawnLocation();

        if (!skipWorldGuard) {
            WorldGuardHook.ValidationScan wgScan = worldGuardHook.scanPlan(requester, anchor, orientation, width, depth, padding);
            if (!wgScan.allowed()) {
                return SiteCheckResult.blocked("WorldGuard rejected that plot.", ReasonCode.WORLDGUARD);
            }
        }

        for (int localX = -spacingPadding; localX < width + spacingPadding; localX++) {
            for (int localZ = -spacingPadding; localZ < depth + spacingPadding; localZ++) {
                Location sample = rotate(anchor, orientation, localX, 0, localZ);
                Block floor = sample.getBlock();
                Block below = sample.clone().add(0, -1, 0).getBlock();
                Block head = sample.clone().add(0, 1, 0).getBlock();
                Block aboveHead = sample.clone().add(0, 2, 0).getBlock();

                if (!bypassPreserve && preserveRadius > 0) {
                    double dx = sample.getX() - spawn.getX();
                    double dz = sample.getZ() - spawn.getZ();
                    if ((dx * dx) + (dz * dz) <= (preserveRadius * preserveRadius)) {
                        return SiteCheckResult.blocked("That spot would touch the preserved spawn center.", ReasonCode.PRESERVE);
                    }
                }

                boolean insideStructure = localX >= 0 && localX < width && localZ >= 0 && localZ < depth;
                boolean insideRoadClearance = localX >= -roadClearance && localX < width + roadClearance
                        && localZ >= -roadClearance && localZ < depth + roadClearance;

                if (insideRoadClearance && roadClearance > 0 && (avoid.contains(floor.getType()) || avoid.contains(below.getType()))) {
                    return SiteCheckResult.blocked("That spot would build on a road or protected path.", ReasonCode.ROAD);
                }

                if (insideStructure) {
                    if (below.isLiquid() || floor.isLiquid()) {
                        return SiteCheckResult.blocked("That spot is too wet to build on.", ReasonCode.GROUND);
                    }
                    if (!below.getType().isSolid()) {
                        return SiteCheckResult.blocked("That spot doesn't have solid ground under it.", ReasonCode.GROUND);
                    }
                    if (!(head.isPassable() || head.getType().isAir())) {
                        return SiteCheckResult.blocked("That spot is blocked above ground.", ReasonCode.HEADROOM);
                    }
                    if (!(aboveHead.isPassable() || aboveHead.getType().isAir())) {
                        return SiteCheckResult.blocked("That spot doesn't have enough headroom.", ReasonCode.HEADROOM);
                    }
                }

                if (!insideStructure && (isLikelyStructure(floor.getType()) || isLikelyStructure(head.getType()))) {
                    return SiteCheckResult.blocked("That plot is too close to an existing structure.", ReasonCode.STRUCTURE);
                }
            }
        }

        return SiteCheckResult.allowed("Build site selected");
    }

    private boolean isLikelyWall(Material material) {
        return switch (material) {
            case POLISHED_ANDESITE, STONE_BRICKS, ANDESITE, COBBLESTONE, DEEPSLATE_BRICKS, CRACKED_DEEPSLATE_BRICKS -> true;
            default -> false;
        };
    }

    private boolean isLikelyStructure(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        return switch (material) {
            case COBBLESTONE, MOSSY_COBBLESTONE, STONE_BRICKS, CRACKED_STONE_BRICKS,
                    OAK_PLANKS, SPRUCE_PLANKS, BIRCH_PLANKS, JUNGLE_PLANKS, ACACIA_PLANKS, DARK_OAK_PLANKS,
                    MANGROVE_PLANKS, CHERRY_PLANKS, BAMBOO_PLANKS,
                    OAK_SLAB, SPRUCE_SLAB, COBBLESTONE_SLAB, STONE_BRICK_SLAB,
                    OAK_STAIRS, SPRUCE_STAIRS, COBBLESTONE_STAIRS, STONE_BRICK_STAIRS,
                    GLASS, GLASS_PANE, OAK_DOOR, SPRUCE_DOOR, IRON_DOOR,
                    TORCH, LANTERN, CHEST, BARREL, CRAFTING_TABLE, FURNACE -> true;
            default -> false;
        };
    }

    private Location rotate(Location base, Orientation orientation, int localX, int localY, int localZ) {
        return switch (orientation) {
            case SOUTH -> base.clone().add(localX, localY, localZ);
            case NORTH -> base.clone().add(-localX, localY, -localZ);
            case EAST -> base.clone().add(localZ, localY, -localX);
            case WEST -> base.clone().add(-localZ, localY, localX);
        };
    }

    private String simple(Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private record ScoredCandidate(Location anchor, Orientation orientation, String reason, int score) {}
    private record CandidateCheck(Location anchor, Orientation orientation, String reason, int score, boolean allowed, ReasonCode reasonCode) {}
    private record GroundedOrigin(boolean valid, Location location) {}
    private enum ReasonCode { NONE, ROAD, GROUND, HEADROOM, STRUCTURE, PRESERVE, WORLDGUARD }

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

    public record ScanReport(boolean success, String summary, List<String> details) {}

    private record SiteCheckResult(boolean allowed, String reason, ReasonCode reasonCode) {
        static SiteCheckResult allowed(String reason) {
            return new SiteCheckResult(true, reason, ReasonCode.NONE);
        }

        static SiteCheckResult blocked(String reason, ReasonCode reasonCode) {
            return new SiteCheckResult(false, reason, reasonCode);
        }
    }
}

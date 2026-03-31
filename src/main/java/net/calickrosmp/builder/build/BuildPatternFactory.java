package net.calickrosmp.builder.build;

import net.calickrosmp.builder.plan.BuildPlan;
import net.calickrosmp.builder.plan.HouseSpec;
import net.calickrosmp.builder.plan.Orientation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;

import java.util.ArrayList;
import java.util.List;

public final class BuildPatternFactory {
    private BuildPatternFactory() {}

    public static List<BuildTask> createStarterHouse(BuildPlan plan) {
        HouseSpec spec = plan.houseSpec();
        Location base = plan.anchor().clone();
        Orientation orientation = spec.orientation();

        int width = Math.max(7, spec.width());
        int depth = Math.max(9, spec.depth());
        int wallHeight = 4;
        int roofY = wallHeight + 1;
        int doorX = width / 2;
        List<BuildTask> tasks = new ArrayList<>();

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                tasks.add(block(base, orientation, x, -1, z, Material.COBBLESTONE));
                tasks.add(block(base, orientation, x, 0, z, Material.OAK_PLANKS));
            }
        }

        for (int y = 1; y <= wallHeight; y++) {
            for (int x = 0; x < width; x++) {
                placeWall(tasks, base, orientation, x, y, 0, doorX);
                placeWall(tasks, base, orientation, x, y, depth - 1, -1);
            }
            for (int z = 1; z < depth - 1; z++) {
                tasks.add(block(base, orientation, 0, y, z, Material.SPRUCE_PLANKS));
                tasks.add(block(base, orientation, width - 1, y, z, Material.SPRUCE_PLANKS));
            }
        }

        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= depth; z++) {
                boolean border = x == -1 || x == width || z == -1 || z == depth;
                if (border) {
                    tasks.add(block(base, orientation, x, roofY, z, Material.DARK_OAK_SLAB));
                } else {
                    tasks.add(block(base, orientation, x, roofY, z, Material.SPRUCE_PLANKS));
                }
            }
        }

        tasks.add(block(base, orientation, doorX, 1, 0, Material.AIR));
        tasks.add(block(base, orientation, doorX, 2, 0, Material.AIR));

        tasks.add(door(base, orientation, doorX, 1, 0, Material.SPRUCE_DOOR, Bisected.Half.BOTTOM));
        tasks.add(door(base, orientation, doorX, 2, 0, Material.SPRUCE_DOOR, Bisected.Half.TOP));
        tasks.add(entranceStair(base, orientation, doorX, 0, -1, Material.SPRUCE_STAIRS));

        addWindow(tasks, base, orientation, 1, 2, 0);
        addWindow(tasks, base, orientation, width - 2, 2, 0);
        addWindow(tasks, base, orientation, 0, 2, depth / 2);
        addWindow(tasks, base, orientation, width - 1, 2, depth / 2);
        addWindow(tasks, base, orientation, 1, 2, depth - 1);
        addWindow(tasks, base, orientation, width - 2, 2, depth - 1);

        tasks.add(block(base, orientation, width / 2, 1, depth / 2, Material.TORCH));
        return tasks;
    }

    private static void placeWall(List<BuildTask> tasks, Location base, Orientation orientation,
                                  int x, int y, int z, int doorX) {
        boolean frontDoor = z == 0 && x == doorX && (y == 1 || y == 2);
        if (frontDoor) {
            tasks.add(block(base, orientation, x, y, z, Material.AIR));
            return;
        }
        tasks.add(block(base, orientation, x, y, z, Material.SPRUCE_PLANKS));
    }

    private static void addWindow(List<BuildTask> tasks, Location base, Orientation orientation,
                                  int x, int y, int z) {
        tasks.add(block(base, orientation, x, y, z, Material.GLASS));
        tasks.add(block(base, orientation, x, y + 1, z, Material.GLASS));
    }

    private static BuildTask block(Location base, Orientation orientation, int localX, int localY, int localZ, Material material) {
        Location placed = switch (orientation) {
            case SOUTH -> base.clone().add(localX, localY, localZ);
            case NORTH -> base.clone().add(-localX, localY, -localZ);
            case EAST -> base.clone().add(localZ, localY, -localX);
            case WEST -> base.clone().add(-localZ, localY, localX);
        };
        return new BuildTask(placed, material);
    }


    private static BuildTask entranceStair(Location base, Orientation orientation, int localX, int localY, int localZ, Material material) {
        Location placed = switch (orientation) {
            case SOUTH -> base.clone().add(localX, localY, localZ);
            case NORTH -> base.clone().add(-localX, localY, -localZ);
            case EAST -> base.clone().add(localZ, localY, -localX);
            case WEST -> base.clone().add(-localZ, localY, localX);
        };
        BlockData data = material.createBlockData();
        if (data instanceof org.bukkit.block.data.type.Stairs stairs) {
            stairs.setFacing(switch (orientation) {
                case SOUTH -> BlockFace.SOUTH;
                case NORTH -> BlockFace.NORTH;
                case EAST -> BlockFace.EAST;
                case WEST -> BlockFace.WEST;
            });
            stairs.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
            stairs.setShape(org.bukkit.block.data.type.Stairs.Shape.STRAIGHT);
            stairs.setWaterlogged(false);
        }
        return new BuildTask(placed, material, data);
    }

    private static BuildTask door(Location base, Orientation orientation, int localX, int localY, int localZ, Material material, Bisected.Half half) {
        Location placed = switch (orientation) {
            case SOUTH -> base.clone().add(localX, localY, localZ);
            case NORTH -> base.clone().add(-localX, localY, -localZ);
            case EAST -> base.clone().add(localZ, localY, -localX);
            case WEST -> base.clone().add(-localZ, localY, localX);
        };
        BlockData data = material.createBlockData();
        if (data instanceof Door door) {
            door.setHalf(half);
            door.setFacing(switch (orientation) {
                case SOUTH -> BlockFace.SOUTH;
                case NORTH -> BlockFace.NORTH;
                case EAST -> BlockFace.EAST;
                case WEST -> BlockFace.WEST;
            });
            door.setOpen(false);
            door.setHinge(org.bukkit.block.data.type.Door.Hinge.LEFT);
        }
        return new BuildTask(placed, material, data);
    }
}

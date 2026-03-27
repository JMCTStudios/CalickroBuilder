package net.calickrosmp.builder.build;

import net.calickrosmp.builder.plan.BuildPlan;
import net.calickrosmp.builder.plan.HouseSpec;
import net.calickrosmp.builder.plan.Orientation;
import org.bukkit.Location;
import org.bukkit.Material;

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

        // Foundation and floor.
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                tasks.add(block(base, orientation, x, -1, z, Material.COBBLESTONE));
                tasks.add(block(base, orientation, x, 0, z, Material.OAK_PLANKS));
            }
        }

        // Walls.
        for (int y = 1; y <= wallHeight; y++) {
            for (int x = 0; x < width; x++) {
                placeWall(tasks, base, orientation, x, y, 0, width, depth, doorX);
                placeWall(tasks, base, orientation, x, y, depth - 1, width, depth, doorX);
            }
            for (int z = 1; z < depth - 1; z++) {
                placeWall(tasks, base, orientation, 0, y, z, width, depth, doorX);
                placeWall(tasks, base, orientation, width - 1, y, z, width, depth, doorX);
            }
        }

        // Roof slab border then flat plank roof. Slightly more house-like than the box.
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

        // Door opening on the front side. Front is z=0 in local coordinates.
        tasks.add(block(base, orientation, doorX, 1, 0, Material.AIR));
        tasks.add(block(base, orientation, doorX, 2, 0, Material.AIR));

        // Front windows.
        addWindow(tasks, base, orientation, 1, 2, 0);
        addWindow(tasks, base, orientation, width - 2, 2, 0);
        // Side windows.
        addWindow(tasks, base, orientation, 0, 2, depth / 2);
        addWindow(tasks, base, orientation, width - 1, 2, depth / 2);
        // Back windows.
        addWindow(tasks, base, orientation, 1, 2, depth - 1);
        addWindow(tasks, base, orientation, width - 2, 2, depth - 1);

        // Interior light and simple front porch path block.
        tasks.add(block(base, orientation, width / 2, 1, depth / 2, Material.TORCH));
        tasks.add(block(base, orientation, doorX, 0, -1, Material.OAK_SLAB));

        return tasks;
    }

    private static void placeWall(List<BuildTask> tasks, Location base, Orientation orientation,
                                  int x, int y, int z, int width, int depth, int doorX) {
        boolean frontDoor = z == 0 && x == doorX && (y == 1 || y == 2);
        if (frontDoor) {
            tasks.add(block(base, orientation, x, y, z, Material.AIR));
            return;
        }
        tasks.add(block(base, orientation, x, y, z, Material.SPRUCE_PLANKS));
    }

    private static void addWindow(List<BuildTask> tasks, Location base, Orientation orientation,
                                  int x, int y, int z) {
        tasks.add(block(base, orientation, x, y, z, Material.GLASS_PANE));
        tasks.add(block(base, orientation, x, y + 1, z, Material.GLASS_PANE));
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
}

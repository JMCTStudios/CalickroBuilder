package net.calickrosmp.builder.build;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public final class BuildTask {
    private final Location location;
    private final Material material;
    private final BlockData blockData;

    public BuildTask(Location location, Material material) {
        this(location, material, null);
    }

    public BuildTask(Location location, Material material, BlockData blockData) {
        this.location = location;
        this.material = material;
        this.blockData = blockData;
    }

    public Location location() {
        return location;
    }

    public Material material() {
        return material;
    }

    public BlockData blockData() {
        return blockData;
    }
}

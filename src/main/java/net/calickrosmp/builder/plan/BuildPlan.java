package net.calickrosmp.builder.plan;

import org.bukkit.Location;

public record BuildPlan(
        StructureType structureType,
        String summary,
        Location anchor,
        Footprint footprint,
        HouseSpec houseSpec,
        String baselineKey
) {}

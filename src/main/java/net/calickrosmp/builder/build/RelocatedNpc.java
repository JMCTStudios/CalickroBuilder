package net.calickrosmp.builder.build;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;

public record RelocatedNpc(NPC npc, Location originalLocation) {}

package net.calickrosmp.builder.build;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.hook.CalickroNpcBridgeHook;
import net.calickrosmp.builder.npc.BuilderProfile;
import net.calickrosmp.builder.npc.BuilderState;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BuildExecutor {
    private final CalickroBuilderPlugin plugin;
    private final CalickroNpcBridgeHook bridgeHook;

    public BuildExecutor(CalickroBuilderPlugin plugin, CalickroNpcBridgeHook bridgeHook) {
        this.plugin = plugin;
        this.bridgeHook = bridgeHook;
    }

    public void execute(Player player, BuilderProfile profile, String detail, List<BuildTask> tasks, BoundingBox buildBox) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(tasks, "tasks");
        Objects.requireNonNull(buildBox, "buildBox");

        List<RelocatedNpc> relocatedNpcs = relocateBlockingNpcs(player.getWorld(), buildBox, player.getLocation().clone().add(8, 0, 8));

        profile.setState(BuilderState.BUILDING);
        bridgeHook.pushState(profile.identity(), BuilderState.BUILDING, detail);

        new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    restoreRelocated(relocatedNpcs);
                    profile.setState(BuilderState.ERROR);
                    bridgeHook.pushState(profile.identity(), BuilderState.ERROR, "Builder owner went offline during build");
                    cancel();
                    return;
                }

                int blocksThisTick = Math.max(1, plugin.settings().blocksPerTick());
                for (int placed = 0; placed < blocksThisTick && index < tasks.size(); placed++, index++) {
                    BuildTask task = tasks.get(index);
                    if (task.location().getWorld() == null) {
                        continue;
                    }
                    task.location().getBlock().setType(task.material(), false);
                }

                if (index >= tasks.size()) {
                    restoreRelocated(relocatedNpcs);
                    profile.setState(BuilderState.COMPLETE);
                    bridgeHook.pushState(profile.identity(), BuilderState.COMPLETE, detail);
                    player.sendMessage("§6[CalickroBuilder]§r §aBuild complete.");

                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        profile.setState(BuilderState.IDLE);
                        bridgeHook.pushState(profile.identity(), BuilderState.IDLE, "Awaiting next job");
                    }, 20L);

                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private List<RelocatedNpc> relocateBlockingNpcs(World world, BoundingBox buildBox, Location safeLocation) {
        List<RelocatedNpc> relocated = new ArrayList<>();

        if (CitizensAPI.getNPCRegistry() == null) {
            return relocated;
        }

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (!npc.isSpawned() || npc.getEntity() == null || npc.getEntity().getWorld() != world) {
                continue;
            }

            Location current = npc.getStoredLocation();
            if (current == null || current.getWorld() != world) {
                continue;
            }

            if (!buildBox.contains(current.getX(), current.getY(), current.getZ())) {
                continue;
            }

            Location original = current.clone();
            Location temporary = safeLocation.clone().add(relocated.size() * 2.0, 0.0, 0.0);
            npc.teleport(temporary, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            relocated.add(new RelocatedNpc(npc, original));
        }

        return relocated;
    }

    private void restoreRelocated(List<RelocatedNpc> relocatedNpcs) {
        for (RelocatedNpc relocatedNpc : relocatedNpcs) {
            if (relocatedNpc.npc().isSpawned()) {
                relocatedNpc.npc().teleport(relocatedNpc.originalLocation(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            } else {
                relocatedNpc.npc().spawn(relocatedNpc.originalLocation());
            }
        }
    }

    private record RelocatedNpc(NPC npc, Location originalLocation) {}
}

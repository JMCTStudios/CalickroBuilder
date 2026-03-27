package net.calickrosmp.builder.job;

import net.calickrosmp.builder.CalickroBuilderPlugin;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BuildJobManager {
    private final CalickroBuilderPlugin plugin;
    private final Map<UUID, Queue<BuildJob>> jobsByBuilder = new ConcurrentHashMap<>();

    public BuildJobManager(CalickroBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enqueue(BuildJob job) {
        jobsByBuilder.computeIfAbsent(job.builderId(), ignored -> new ArrayDeque<>()).add(job);
        plugin.log().info("Queued job " + job.jobId() + " for builder " + job.builderId());
    }

    public Optional<BuildJob> current(UUID builderId) {
        Queue<BuildJob> queue = jobsByBuilder.get(builderId);
        return queue == null ? Optional.empty() : Optional.ofNullable(queue.peek());
    }

    public void shutdown() {
        jobsByBuilder.clear();
    }
}

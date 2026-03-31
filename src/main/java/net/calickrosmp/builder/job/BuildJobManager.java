package net.calickrosmp.builder.job;

import net.calickrosmp.builder.CalickroBuilderPlugin;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BuildJobManager {

    private final CalickroBuilderPlugin plugin;
    private final Map<UUID, Queue<BuildJob>> jobsByBuilder = new ConcurrentHashMap<>();
    private final Set<UUID> cancelRequests = new HashSet<>();

    public BuildJobManager(CalickroBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public void enqueue(BuildJob job) {
        jobsByBuilder
            .computeIfAbsent(job.builderId(), ignored -> new ArrayDeque<>())
            .add(job);

        plugin.log().info("Queued job " + job.jobId() + " for builder " + job.builderId());
    }

    public Optional<BuildJob> current(UUID builderId) {
        Queue<BuildJob> queue = jobsByBuilder.get(builderId);
        return queue == null ? Optional.empty() : Optional.ofNullable(queue.peek());
    }

    public void startCurrent(UUID builderId) {
        current(builderId).ifPresent(job -> job.setStatus(JobStatus.RUNNING));
    }

    public void completeCurrent(UUID builderId) {
        clearCancelRequest(builderId);

        Queue<BuildJob> queue = jobsByBuilder.get(builderId);
        if (queue == null) return;

        BuildJob current = queue.poll();
        if (current != null) {
            current.setStatus(JobStatus.COMPLETE);
        }

        if (queue.isEmpty()) {
            jobsByBuilder.remove(builderId);
        }
    }

    public void failCurrent(UUID builderId) {
        clearCancelRequest(builderId);

        Queue<BuildJob> queue = jobsByBuilder.get(builderId);
        if (queue == null) return;

        BuildJob current = queue.poll();
        if (current != null) {
            current.setStatus(JobStatus.FAILED);
        }

        if (queue.isEmpty()) {
            jobsByBuilder.remove(builderId);
        }
    }

    // 🔥 REQUIRED FOR PHASE 4.4+
    public boolean isCancelRequested(UUID builderId) {
        return cancelRequests.contains(builderId);
    }

    public void requestCancel(UUID builderId) {
        cancelRequests.add(builderId);
    }

    public void clearCancelRequest(UUID builderId) {
        cancelRequests.remove(builderId);
    }

    public void shutdown() {
        cancelRequests.clear();
        jobsByBuilder.clear();
    }
}
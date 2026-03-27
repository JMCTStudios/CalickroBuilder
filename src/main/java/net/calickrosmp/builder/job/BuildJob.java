package net.calickrosmp.builder.job;

import net.calickrosmp.builder.plan.BuildPlan;

import java.time.Instant;
import java.util.UUID;

public final class BuildJob {
    private final UUID jobId = UUID.randomUUID();
    private final UUID builderId;
    private final UUID requesterId;
    private final JobType type;
    private final BuildPlan plan;
    private final Instant createdAt = Instant.now();
    private JobStatus status = JobStatus.QUEUED;

    public BuildJob(UUID builderId, UUID requesterId, JobType type, BuildPlan plan) {
        this.builderId = builderId;
        this.requesterId = requesterId;
        this.type = type;
        this.plan = plan;
    }

    public UUID jobId() { return jobId; }
    public UUID builderId() { return builderId; }
    public UUID requesterId() { return requesterId; }
    public JobType type() { return type; }
    public BuildPlan plan() { return plan; }
    public Instant createdAt() { return createdAt; }
    public JobStatus status() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
}

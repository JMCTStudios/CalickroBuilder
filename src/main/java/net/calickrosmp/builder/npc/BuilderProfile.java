package net.calickrosmp.builder.npc;

import net.calickrosmp.builder.config.BuilderSettings;

public final class BuilderProfile {
    private final BuilderIdentity identity;
    private BuilderMode mode;
    private BuilderState state;
    private String assignedRegion;
    private String lastBaselineName;
    private BuilderSpeedMode speedMode;
    private Long speedOverrideTicks;
    private long currentBuildStartedAt;

    public BuilderProfile(BuilderIdentity identity) {
        this.identity = identity;
        this.mode = BuilderMode.BUILD;
        this.state = BuilderState.IDLE;
        this.speedMode = BuilderSpeedMode.SMART;
    }

    public BuilderIdentity identity() { return identity; }
    public BuilderMode mode() { return mode; }
    public BuilderState state() { return state; }
    public String assignedRegion() { return assignedRegion; }
    public String lastBaselineName() { return lastBaselineName; }
    public BuilderSpeedMode speedMode() { return speedMode; }
    public Long speedOverrideTicks() { return speedOverrideTicks; }
    public long currentBuildStartedAt() { return currentBuildStartedAt; }

    public void setMode(BuilderMode mode) { this.mode = mode; }
    public void setState(BuilderState state) { this.state = state; }
    public void setAssignedRegion(String assignedRegion) { this.assignedRegion = assignedRegion; }
    public void setLastBaselineName(String lastBaselineName) { this.lastBaselineName = lastBaselineName; }
    public void setSpeedMode(BuilderSpeedMode speedMode) { this.speedMode = speedMode == null ? BuilderSpeedMode.SMART : speedMode; }
    public void setSpeedOverrideTicks(Long speedOverrideTicks) { this.speedOverrideTicks = speedOverrideTicks; }
    public void markBuildStarted() { this.currentBuildStartedAt = System.currentTimeMillis(); }
    public long clearBuildStarted() {
        long started = this.currentBuildStartedAt;
        this.currentBuildStartedAt = 0L;
        return started;
    }

    public long effectiveDelayTicks(BuilderSettings settings, int totalTasks, int currentIndex) {
        if (speedOverrideTicks != null) {
            return Math.max(1L, speedOverrideTicks);
        }
        return settings.resolveDelayTicks(speedMode, totalTasks, currentIndex);
    }
}

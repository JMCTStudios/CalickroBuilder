package net.calickrosmp.builder.npc;

public final class BuilderProfile {
    private final BuilderIdentity identity;
    private BuilderMode mode;
    private BuilderState state;
    private String assignedRegion;
    private String lastBaselineName;

    public BuilderProfile(BuilderIdentity identity) {
        this.identity = identity;
        this.mode = BuilderMode.BUILD;
        this.state = BuilderState.IDLE;
    }

    public BuilderIdentity identity() { return identity; }
    public BuilderMode mode() { return mode; }
    public BuilderState state() { return state; }
    public String assignedRegion() { return assignedRegion; }
    public String lastBaselineName() { return lastBaselineName; }

    public void setMode(BuilderMode mode) { this.mode = mode; }
    public void setState(BuilderState state) { this.state = state; }
    public void setAssignedRegion(String assignedRegion) { this.assignedRegion = assignedRegion; }
    public void setLastBaselineName(String lastBaselineName) { this.lastBaselineName = lastBaselineName; }
}

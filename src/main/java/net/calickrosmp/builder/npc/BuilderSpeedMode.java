package net.calickrosmp.builder.npc;

public enum BuilderSpeedMode {
    SMART,
    FIXED,
    CINEMATIC,
    FAST,
    CUSTOM;

    public static BuilderSpeedMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return SMART;
        }
        try {
            return BuilderSpeedMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SMART;
        }
    }
}

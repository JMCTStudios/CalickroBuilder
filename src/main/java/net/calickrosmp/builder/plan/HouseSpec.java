package net.calickrosmp.builder.plan;

public record HouseSpec(
        int floors,
        int width,
        int depth,
        int frontWindows,
        int sideWindowsEach,
        int backWindows,
        boolean ironDoor,
        Orientation orientation
) {
    public static HouseSpec starter(Orientation orientation) {
        return new HouseSpec(1, 15, 11, 2, 1, 2, true, orientation);
    }
}

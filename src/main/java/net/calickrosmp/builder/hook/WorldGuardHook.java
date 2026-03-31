package net.calickrosmp.builder.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.plan.BuildPlan;
import net.calickrosmp.builder.plan.Orientation;
import net.calickrosmp.builder.validation.ValidationIssue;
import net.calickrosmp.builder.validation.ValidationIssueType;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class WorldGuardHook {
    private final CalickroBuilderPlugin plugin;
    private boolean enabled;
    private boolean pluginPresent;

    public WorldGuardHook(CalickroBuilderPlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        this.enabled = plugin.getConfig().getBoolean("hooks.worldguard.enabled", true);
        this.pluginPresent = plugin.getServer().getPluginManager().isPluginEnabled("WorldGuard");
    }

    public boolean enabled() {
        return enabled && pluginPresent;
    }

    public boolean canBypass(Player player) {
        return player != null
                && (player.hasPermission("calickrobuilder.bypass.worldguard")
                || player.hasPermission("calickrobuilder.bypass.all"));
    }

    public boolean canBypassPreserve(Player player) {
        return player != null
                && (player.hasPermission("calickrobuilder.bypass.preserve")
                || player.hasPermission("calickrobuilder.bypass.all"));
    }

    public ValidationIssue validate(Player player, BuildPlan plan) {
        ValidationScan scan = scanPlan(
                player,
                plan.anchor(),
                plan.houseSpec().orientation(),
                plan.houseSpec().width(),
                plan.houseSpec().depth(),
                plan.footprint().padding()
        );
        if (scan.allowed()) {
            return null;
        }
        return new ValidationIssue(ValidationIssueType.WORLDGUARD, scan.message());
    }

    public boolean isProtected(Location location, Player player) {
        if (!enabled() || location == null || location.getWorld() == null) {
            return false;
        }

        List<String> allowedRegions = plugin.settings().worldGuardAllowedBuildRegions();
        List<String> preserveRegions = plugin.settings().worldGuardPreserveRegions();

        if (allowedRegions.isEmpty() && preserveRegions.isEmpty()) {
            return false;
        }

        boolean bypassWorldGuard = canBypass(player);
        boolean bypassPreserve = canBypassPreserve(player);
        Set<String> ids = getRegionIds(location);

        if (!preserveRegions.isEmpty() && intersectsAny(ids, preserveRegions) && !bypassPreserve) {
            return true;
        }

        if (!allowedRegions.isEmpty() && !bypassWorldGuard && !intersectsAny(ids, allowedRegions)) {
            return true;
        }

        return false;
    }

    public ValidationScan scanPlan(Player player, Location anchor, Orientation orientation, int width, int depth, int padding) {
        if (!enabled()) {
            return ValidationScan.allowed("WorldGuard disabled");
        }
        if (anchor == null || anchor.getWorld() == null) {
            return ValidationScan.blocked("No valid world found for WorldGuard validation.");
        }

        List<String> allowedRegions = plugin.settings().worldGuardAllowedBuildRegions();
        List<String> preserveRegions = plugin.settings().worldGuardPreserveRegions();
        if (allowedRegions.isEmpty() && preserveRegions.isEmpty()) {
            return ValidationScan.allowed("No WorldGuard region restrictions configured");
        }

        int minX = -padding;
        int maxX = width + padding - 1;
        int minZ = -padding;
        int maxZ = depth + padding - 1;

        for (int relX = minX; relX <= maxX; relX++) {
            for (int relZ = minZ; relZ <= maxZ; relZ++) {
                Location check = rotate(anchor, orientation, relX, relZ);
                if (isProtected(check, player)) {
                    Set<String> ids = getRegionIds(check);

                    if (!preserveRegions.isEmpty() && intersectsAny(ids, preserveRegions) && !canBypassPreserve(player)) {
                        return ValidationScan.blocked("That build would overlap a preserved WorldGuard region.");
                    }

                    if (!allowedRegions.isEmpty() && !canBypass(player) && !intersectsAny(ids, allowedRegions)) {
                        return ValidationScan.blocked("That build would extend outside the allowed WorldGuard build region.");
                    }

                    return ValidationScan.blocked("That build would overlap a protected WorldGuard area.");
                }
            }
        }

        return ValidationScan.allowed("WorldGuard check passed");
    }

    public Set<String> getRegionIds(Location location) {
        Set<String> ids = new HashSet<>();
        if (!enabled() || location == null || location.getWorld() == null) {
            return ids;
        }

        try {
            RegionManager manager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(location.getWorld()));
            if (manager == null) {
                return ids;
            }

            ApplicableRegionSet applicable = manager.getApplicableRegions(BukkitAdapter.asBlockVector(location));
            for (ProtectedRegion region : applicable) {
                ids.add(region.getId().toLowerCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {
            return ids;
        }

        return ids;
    }

    public ValidationIssue placeholderIssue(String message) {
        return new ValidationIssue(ValidationIssueType.WORLDGUARD, message);
    }

    private boolean intersectsAny(Set<String> current, List<String> expected) {
        for (String value : expected) {
            if (current.contains(value.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private Location rotate(Location anchor, Orientation orientation, int relX, int relZ) {
        return switch (orientation) {
            case SOUTH -> anchor.clone().add(relX, 0, relZ);
            case NORTH -> anchor.clone().add(-relX, 0, -relZ);
            case EAST -> anchor.clone().add(relZ, 0, -relX);
            case WEST -> anchor.clone().add(-relZ, 0, relX);
        };
    }

    public record ValidationScan(boolean allowed, String message) {
        public static ValidationScan allowed(String message) {
            return new ValidationScan(true, message);
        }

        public static ValidationScan blocked(String message) {
            return new ValidationScan(false, message);
        }
    }
}
package net.calickrosmp.builder.hook;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.plan.BuildPlan;
import net.calickrosmp.builder.plan.Orientation;
import net.calickrosmp.builder.validation.ValidationIssue;
import net.calickrosmp.builder.validation.ValidationIssueType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class WorldGuardHook {
    private final CalickroBuilderPlugin plugin;

    public WorldGuardHook(CalickroBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("hooks.worldguard.enabled", true)
                && Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    public boolean canBypass(Player player) {
        return player.hasPermission("calickrobuilder.bypass.worldguard") || player.hasPermission("calickrobuilder.bypass.all");
    }

    public boolean canBypassPreserve(Player player) {
        return player.hasPermission("calickrobuilder.bypass.preserve") || player.hasPermission("calickrobuilder.bypass.all");
    }

    public Optional<ValidationIssue> validate(Player player, BuildPlan plan) {
        if (!enabled() || plan.anchor() == null || plan.anchor().getWorld() == null) {
            return Optional.empty();
        }

        ValidationScan scan = scanPlan(player, plan.anchor(), plan.houseSpec() != null ? plan.houseSpec().orientation() : Orientation.SOUTH,
                plan.footprint().width(), plan.footprint().length(), plan.footprint().padding());
        if (!scan.allowed()) {
            return Optional.of(new ValidationIssue(ValidationIssueType.WORLDGUARD, scan.message()));
        }
        return Optional.empty();
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
        boolean bypassWorldGuard = canBypass(player);
        boolean bypassPreserve = canBypassPreserve(player);

        for (int localX = -padding; localX < width + padding; localX++) {
            for (int localZ = -padding; localZ < depth + padding; localZ++) {
                Location sample = rotate(anchor, orientation, localX, 0, localZ);
                Set<String> ids = getRegionIds(sample);

                if (!preserveRegions.isEmpty() && intersectsAny(ids, preserveRegions) && !bypassPreserve) {
                    return ValidationScan.blocked("That spot would overlap a preserved WorldGuard region.");
                }

                if (!allowedRegions.isEmpty() && !bypassWorldGuard && !intersectsAny(ids, allowedRegions)) {
                    return ValidationScan.blocked("That spot is outside the allowed WorldGuard build region.");
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
        } catch (NoClassDefFoundError | Exception ignored) {
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

    private Location rotate(Location base, Orientation orientation, int localX, int localY, int localZ) {
        return switch (orientation) {
            case SOUTH -> base.clone().add(localX, localY, localZ);
            case NORTH -> base.clone().add(-localX, localY, -localZ);
            case EAST -> base.clone().add(localZ, localY, -localX);
            case WEST -> base.clone().add(-localZ, localY, localX);
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

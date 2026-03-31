package net.calickrosmp.builder.hook;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.plan.BuildPlan;
import net.calickrosmp.builder.plan.Orientation;
import net.calickrosmp.builder.validation.ValidationIssue;
import net.calickrosmp.builder.validation.ValidationIssueType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;

public final class GriefPreventionHook {
    private final CalickroBuilderPlugin plugin;

    public GriefPreventionHook(CalickroBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("hooks.griefprevention.enabled", true)
                && Bukkit.getPluginManager().getPlugin("GriefPrevention") != null;
    }

    public Optional<ValidationIssue> validate(Player player, BuildPlan plan) {
        if (!enabled()) {
            return Optional.empty();
        }

        if (plan == null || plan.anchor() == null || plan.anchor().getWorld() == null) {
            return Optional.empty();
        }

        int width = plan.houseSpec() != null ? plan.houseSpec().width() : plan.footprint().width();
        int depth = plan.houseSpec() != null ? plan.houseSpec().depth() : plan.footprint().length();
        int padding = Math.max(0, plan.footprint().padding());
        Orientation orientation = plan.houseSpec() != null ? plan.houseSpec().orientation() : Orientation.SOUTH;

        int minX = -padding;
        int maxX = width + padding - 1;
        int minZ = -padding;
        int maxZ = depth + padding - 1;

        for (int relX = minX; relX <= maxX; relX++) {
            for (int relZ = minZ; relZ <= maxZ; relZ++) {
                Location check = rotate(plan.anchor(), orientation, relX, relZ);
                if (isClaimed(check, player)) {
                    return Optional.of(new ValidationIssue(
                            ValidationIssueType.GRIEF_PROTECTION,
                            "That build would overlap a protected GriefPrevention claim."
                    ));
                }
            }
        }

        return Optional.empty();
    }

    public boolean isClaimed(Location location, Player player) {
        if (!enabled() || location == null || location.getWorld() == null) {
            return false;
        }

        try {
            Plugin gp = Bukkit.getPluginManager().getPlugin("GriefPrevention");
            if (gp == null) {
                return false;
            }

            Class<?> gpClass = gp.getClass();
            Object gpInstance = gp;

            Object dataStore = null;
            try {
                var field = gpClass.getField("dataStore");
                dataStore = field.get(gpInstance);
            } catch (NoSuchFieldException ignored) {
                try {
                    Method getter = gpClass.getMethod("getDataStore");
                    dataStore = getter.invoke(gpInstance);
                } catch (Throwable ignoredAgain) {
                    return false;
                }
            }

            if (dataStore == null) {
                return false;
            }

            Method getClaimAt = null;
            for (Method method : dataStore.getClass().getMethods()) {
                if (!method.getName().equals("getClaimAt")) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 3 && Location.class.isAssignableFrom(params[0])) {
                    getClaimAt = method;
                    break;
                }
            }

            if (getClaimAt == null) {
                return false;
            }

            Object claim = getClaimAt.invoke(dataStore, location, true, null);
            if (claim == null) {
                return false;
            }

            if (player == null) {
                return true;
            }

            Method allowBuild = null;
            for (Method method : claim.getClass().getMethods()) {
                if (!method.getName().equals("allowBuild")) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2
                        && Player.class.isAssignableFrom(params[0])
                        && params[1].isEnum()) {
                    allowBuild = method;
                    break;
                }
            }

            if (allowBuild == null) {
                return true;
            }

            Object result = allowBuild.invoke(claim, player, location.getBlock().getType());
            return result != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public ValidationIssue placeholderIssue(String message) {
        return new ValidationIssue(ValidationIssueType.GRIEF_PROTECTION, message);
    }

    private Location rotate(Location anchor, Orientation orientation, int relX, int relZ) {
        return switch (orientation) {
            case SOUTH -> anchor.clone().add(relX, 0, relZ);
            case NORTH -> anchor.clone().add(-relX, 0, -relZ);
            case EAST -> anchor.clone().add(relZ, 0, -relX);
            case WEST -> anchor.clone().add(-relZ, 0, relX);
        };
    }
}
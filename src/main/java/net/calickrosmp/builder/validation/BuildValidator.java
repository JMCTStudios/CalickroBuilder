package net.calickrosmp.builder.validation;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.hook.GriefPreventionHook;
import net.calickrosmp.builder.hook.WorldGuardHook;
import net.calickrosmp.builder.plan.BuildPlan;
import net.calickrosmp.builder.plan.Orientation;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class BuildValidator {
    private final CalickroBuilderPlugin plugin;
    private final WorldGuardHook worldGuardHook;
    private final GriefPreventionHook griefPreventionHook;

    public BuildValidator(
            CalickroBuilderPlugin plugin,
            WorldGuardHook worldGuardHook,
            GriefPreventionHook griefPreventionHook
    ) {
        this.plugin = plugin;
        this.worldGuardHook = worldGuardHook;
        this.griefPreventionHook = griefPreventionHook;
    }

    public ValidationResult validate(Player player, BuildPlan plan) {
        ValidationResult result = ValidationResult.success();

        if (plan == null) {
            result.addIssue(new ValidationIssue(
                    ValidationIssueType.UNKNOWN,
                    "No build plan was provided."
            ));
            return result;
        }

        if (plan.anchor() == null || plan.anchor().getWorld() == null) {
            result.addIssue(new ValidationIssue(
                    ValidationIssueType.UNKNOWN,
                    "No valid world/anchor was provided for the build plan."
            ));
            return result;
        }

        if (plan.footprint() == null) {
            result.addIssue(new ValidationIssue(
                    ValidationIssueType.UNKNOWN,
                    "No valid footprint was provided for the build plan."
            ));
            return result;
        }

        if (plan.footprint().width() <= 0) {
            result.addIssue(new ValidationIssue(
                    ValidationIssueType.UNKNOWN,
                    "Build footprint width must be greater than zero."
            ));
        }

        if (plan.footprint().length() <= 0) {
            result.addIssue(new ValidationIssue(
                    ValidationIssueType.UNKNOWN,
                    "Build footprint length must be greater than zero."
            ));
        }

        if (!result.isAllowed()) {
            return result;
        }

        ValidationIssue footprintIssue = validateFootprint(player, plan);
        if (footprintIssue != null) {
            result.addIssue(footprintIssue);
            return result;
        }

        addIssueIfPresent(result, worldGuardHook.validate(player, plan));
        addIssueIfPresent(result, griefPreventionHook.validate(player, plan));

        if (plugin.settings().requireFullFootprintClear() && plan.footprint().width() <= 0) {
            result.addIssue(new ValidationIssue(
                    ValidationIssueType.UNKNOWN,
                    "Build footprint width must be greater than zero."
            ));
        }

        return result;
    }

    private ValidationIssue validateFootprint(Player player, BuildPlan plan) {
        Location anchor = plan.anchor();
        Orientation orientation = plan.houseSpec() != null
                ? plan.houseSpec().orientation()
                : Orientation.SOUTH;

        int width = plan.houseSpec() != null
                ? plan.houseSpec().width()
                : plan.footprint().width();

        int depth = plan.houseSpec() != null
                ? plan.houseSpec().depth()
                : plan.footprint().length();

        int padding = Math.max(0, plan.footprint().padding());

        int minX = -padding;
        int maxX = width + padding - 1;
        int minZ = -padding;
        int maxZ = depth + padding - 1;

        for (int relX = minX; relX <= maxX; relX++) {
            for (int relZ = minZ; relZ <= maxZ; relZ++) {
                Location check = rotate(anchor, orientation, relX, relZ);

                if (worldGuardHook != null && worldGuardHook.isProtected(check, player)) {
                    return new ValidationIssue(
                            ValidationIssueType.WORLDGUARD,
                            "Build overlaps a protected WorldGuard region."
                    );
                }

                if (griefPreventionHook != null && griefPreventionHook.isClaimed(check, player)) {
                    return new ValidationIssue(
                            ValidationIssueType.GRIEF_PROTECTION,
                            "Build overlaps a protected claim."
                    );
                }
            }
        }

        return null;
    }

    private Location rotate(Location anchor, Orientation orientation, int relX, int relZ) {
        return switch (orientation) {
            case SOUTH -> anchor.clone().add(relX, 0, relZ);
            case NORTH -> anchor.clone().add(-relX, 0, -relZ);
            case EAST -> anchor.clone().add(relZ, 0, -relX);
            case WEST -> anchor.clone().add(-relZ, 0, relX);
        };
    }

    @SuppressWarnings("unchecked")
    private void addIssueIfPresent(ValidationResult result, Object hookResult) {
        if (hookResult == null) {
            return;
        }

        if (hookResult instanceof ValidationIssue issue) {
            result.addIssue(issue);
            return;
        }

        if (hookResult instanceof Optional<?> optional) {
            Object value = optional.orElse(null);
            if (value instanceof ValidationIssue issue) {
                result.addIssue(issue);
            }
        }
    }
}
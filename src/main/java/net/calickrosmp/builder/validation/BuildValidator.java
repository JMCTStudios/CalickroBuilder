package net.calickrosmp.builder.validation;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.hook.GriefPreventionHook;
import net.calickrosmp.builder.hook.WorldGuardHook;
import net.calickrosmp.builder.plan.BuildPlan;
import org.bukkit.entity.Player;

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

        worldGuardHook.validate(player, plan).ifPresent(result::addIssue);
        griefPreventionHook.validate(player, plan).ifPresent(result::addIssue);

        if (plan.anchor() == null || plan.anchor().getWorld() == null) {
            result.addIssue(new ValidationIssue(
                    ValidationIssueType.UNKNOWN,
                    "No valid world/anchor was provided for the build plan."
            ));
        }

        if (plugin.settings().requireFullFootprintClear() && plan.footprint().width() <= 0) {
            result.addIssue(new ValidationIssue(
                    ValidationIssueType.UNKNOWN,
                    "Build footprint width must be greater than zero."
            ));
        }

        return result;
    }
}
package net.calickrosmp.builder.hook;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.plan.BuildPlan;
import net.calickrosmp.builder.validation.ValidationIssue;
import net.calickrosmp.builder.validation.ValidationIssueType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class WorldGuardHook {
    private final CalickroBuilderPlugin plugin;

    public WorldGuardHook(CalickroBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("hooks.worldguard.enabled", true)
                && Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    public Optional<ValidationIssue> validate(Player player, BuildPlan plan) {
        return Optional.empty();
    }

    public ValidationIssue placeholderIssue(String message) {
        return new ValidationIssue(ValidationIssueType.WORLDGUARD, message);
    }
}

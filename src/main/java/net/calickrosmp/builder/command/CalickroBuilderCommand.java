package net.calickrosmp.builder.command;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.npc.BuilderNpcRegistry;
import net.calickrosmp.builder.npc.BuilderProfile;
import net.calickrosmp.builder.npc.BuilderSpeedMode;
import net.calickrosmp.builder.provider.NpcProviderRegistry;
import net.calickrosmp.builder.service.BuildService;
import net.calickrosmp.builder.text.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CalickroBuilderCommand implements CommandExecutor, TabCompleter {
    private final CalickroBuilderPlugin plugin;
    private final BuildService buildService;
    private final BuilderNpcRegistry builderNpcRegistry;
    private final NpcProviderRegistry npcProviderRegistry;

    public CalickroBuilderCommand(
            CalickroBuilderPlugin plugin,
            BuildService buildService,
            BuilderNpcRegistry builderNpcRegistry,
            NpcProviderRegistry npcProviderRegistry
    ) {
        this.plugin = plugin;
        this.buildService = buildService;
        this.builderNpcRegistry = builderNpcRegistry;
        this.npcProviderRegistry = npcProviderRegistry;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("builder")) {
            Text.send(player, plugin.settings().messagePrefix(), "Usage: /cali builder <bind|provider|status|testhouse|scan|speed|speedmode|reload>");
            return true;
        }

        if (args.length == 1) {
            Text.send(player, plugin.settings().messagePrefix(), "Usage: /cali builder <bind|provider|status|testhouse|scan|speed|speedmode|reload>");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "bind" -> buildService.bindSelectedNpc(player);
            case "provider" -> Text.send(player, plugin.settings().messagePrefix(), "Active provider: &e" + npcProviderRegistry.getActiveProviderName());
            case "status" -> getBoundBuilderId(player).ifPresentOrElse(
                    id -> buildService.reportStatus(player, id),
                    () -> Text.send(player, plugin.settings().messagePrefix(), "Select a Citizens NPC with /npc select <name>, then bind it with /cali builder bind.")
            );
            case "testhouse" -> getBoundBuilderId(player).ifPresentOrElse(
                    id -> buildService.queueStarterHouse(player, id),
                    () -> Text.send(player, plugin.settings().messagePrefix(), "Bind a builder first with /cali builder bind after selecting an NPC.")
            );
            case "scan" -> getBoundBuilderId(player).ifPresentOrElse(
                    id -> buildService.scanCurrentArea(player, id),
                    () -> Text.send(player, plugin.settings().messagePrefix(), "Bind a builder first with /cali builder bind after selecting an NPC.")
            );
            case "speed" -> handleSpeed(player, args);
            case "speedmode" -> handleSpeedMode(player, args);
            case "reload" -> {
                plugin.reloadPlugin();
                Text.send(player, plugin.settings().messagePrefix(), "Config reloaded.");
            }
            default -> Text.send(player, plugin.settings().messagePrefix(), "Unknown subcommand.");
        }

        return true;
    }

    private void handleSpeed(Player player, String[] args) {
        if (!player.hasPermission("calickrobuilder.admin") && !player.hasPermission("calickrobuilder.speed")) {
            Text.send(player, plugin.settings().messagePrefix(), "&cYou do not have permission to change build speed.");
            return;
        }

        Optional<BuilderProfile> boundProfile = getBoundProfile(player);

        if (args.length < 3) {
            if (boundProfile.isPresent()) {
                BuilderProfile profile = boundProfile.get();
                String override = profile.speedOverrideTicks() == null ? "none" : profile.speedOverrideTicks().toString();
                Text.send(player, plugin.settings().messagePrefix(), "Builder &e" + profile.identity().displayName() + "&r mode=&b" + profile.speedMode().name().toLowerCase() + "&r override=&e" + override);
            } else {
                Text.send(player, plugin.settings().messagePrefix(), "Default speed mode: &e" + plugin.settings().defaultSpeedMode().name().toLowerCase() + "&r fixed ticks=&e" + plugin.settings().fixedTicksPerStep());
            }
            Text.send(player, plugin.settings().messagePrefix(), "Usage: /cali builder speed <ticks>");
            return;
        }

        long ticks;
        try {
            ticks = Long.parseLong(args[2]);
        } catch (NumberFormatException ex) {
            Text.send(player, plugin.settings().messagePrefix(), "&cBuild speed must be a whole number of ticks.");
            return;
        }

        if (ticks < 1L || ticks > 100L) {
            Text.send(player, plugin.settings().messagePrefix(), "&cPick a speed between 1 and 100 ticks.");
            return;
        }

        if (boundProfile.isPresent()) {
            BuilderProfile profile = boundProfile.get();
            profile.setSpeedOverrideTicks(ticks);
            plugin.builderPersistenceManager().saveBuilders();
            Text.send(player, plugin.settings().messagePrefix(), "Builder &e" + profile.identity().displayName() + "&r speed override updated to &e" + ticks + "&r ticks per step.");
            return;
        }

        plugin.settings().setFixedTicksPerStep(ticks);
        plugin.settings().setDefaultSpeedMode(BuilderSpeedMode.FIXED);
        Text.send(player, plugin.settings().messagePrefix(), "Default builder speed updated to &e" + ticks + "&r ticks per step.");
    }

    private void handleSpeedMode(Player player, String[] args) {
        if (!player.hasPermission("calickrobuilder.admin") && !player.hasPermission("calickrobuilder.speed")) {
            Text.send(player, plugin.settings().messagePrefix(), "&cYou do not have permission to change build speed modes.");
            return;
        }

        if (args.length < 3) {
            Text.send(player, plugin.settings().messagePrefix(), "Current default mode: &e" + plugin.settings().defaultSpeedMode().name().toLowerCase());
            Text.send(player, plugin.settings().messagePrefix(), "Usage: /cali builder speedmode <smart|fixed|cinematic|fast|custom>");
            return;
        }

        BuilderSpeedMode mode = BuilderSpeedMode.fromString(args[2]);
        Optional<BuilderProfile> boundProfile = getBoundProfile(player);
        if (boundProfile.isPresent()) {
            BuilderProfile profile = boundProfile.get();
            profile.setSpeedMode(mode);
            if (mode != BuilderSpeedMode.FIXED) {
                profile.setSpeedOverrideTicks(null);
            }
            plugin.builderPersistenceManager().saveBuilders();
            Text.send(player, plugin.settings().messagePrefix(), "Builder &e" + profile.identity().displayName() + "&r speed mode set to &b" + mode.name().toLowerCase() + "&r.");
            return;
        }

        plugin.settings().setDefaultSpeedMode(mode);
        Text.send(player, plugin.settings().messagePrefix(), "Default builder speed mode set to &b" + mode.name().toLowerCase() + "&r.");
    }

    private Optional<UUID> getBoundBuilderId(Player player) {
        return npcProviderRegistry.activeProvider().getSelectedNpcId(player)
                .filter(id -> builderNpcRegistry.find(id).isPresent());
    }

    private Optional<BuilderProfile> getBoundProfile(Player player) {
        return getBoundBuilderId(player).flatMap(builderNpcRegistry::find);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("builder");
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("builder")) {
            completions.add("bind");
            completions.add("provider");
            completions.add("status");
            completions.add("testhouse");
            completions.add("scan");
            completions.add("speed");
            completions.add("speedmode");
            completions.add("reload");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("builder") && args[1].equalsIgnoreCase("speed")) {
            completions.add("2");
            completions.add("4");
            completions.add("8");
            completions.add("12");
            completions.add("20");
            completions.add("40");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("builder") && args[1].equalsIgnoreCase("speedmode")) {
            completions.add("smart");
            completions.add("fixed");
            completions.add("cinematic");
            completions.add("fast");
            completions.add("custom");
        }

        return completions;
    }
}

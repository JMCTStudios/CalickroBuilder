package net.calickrosmp.builder.command;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.calickrosmp.builder.npc.BuilderNpcRegistry;
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
            Text.send(player, plugin.settings().messagePrefix(), "Usage: /cali builder <bind|provider|status|testhouse|scan|speed|reload>");
            return true;
        }

        if (args.length == 1) {
            Text.send(player, plugin.settings().messagePrefix(), "Usage: /cali builder <bind|provider|status|testhouse|scan|speed|reload>");
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

        if (args.length < 3) {
            Text.send(player, plugin.settings().messagePrefix(), "Current build interval: &e" + plugin.settings().buildIntervalTicks() + "&r ticks per step.");
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

        plugin.settings().setBuildIntervalTicks(ticks);
        Text.send(player, plugin.settings().messagePrefix(), "Builder speed updated to &e" + ticks + "&r ticks per step.");
    }

    private Optional<UUID> getBoundBuilderId(Player player) {
        return npcProviderRegistry.activeProvider().getSelectedNpcId(player)
                .filter(id -> builderNpcRegistry.find(id).isPresent());
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
            completions.add("reload");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("builder") && args[1].equalsIgnoreCase("speed")) {
            completions.add("2");
            completions.add("4");
            completions.add("8");
            completions.add("12");
            completions.add("20");
        }

        return completions;
    }
}

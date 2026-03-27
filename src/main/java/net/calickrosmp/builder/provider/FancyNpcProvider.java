package net.calickrosmp.builder.provider;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class FancyNpcProvider implements NpcProvider {
    @Override public NpcProviderType type() { return NpcProviderType.FANCY_NPCS; }
    @Override public String name() { return "FancyNpcs"; }
    @Override public boolean isAvailable() { return false; }
    @Override public Optional<UUID> getSelectedNpcId(Player player) { return Optional.empty(); }
    @Override public Optional<String> getSelectedNpcName(Player player) { return Optional.empty(); }
    @Override public boolean attachBuilderRole(Player player, UUID npcId) { return false; }
    @Override public boolean canControlNpc(CommandSender sender, UUID npcId) { return sender.hasPermission("calickrobuilder.admin"); }
}

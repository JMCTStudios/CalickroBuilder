package net.calickrosmp.builder.provider;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public interface NpcProvider {
    NpcProviderType type();
    String name();
    boolean isAvailable();
    Optional<UUID> getSelectedNpcId(Player player);
    Optional<String> getSelectedNpcName(Player player);
    boolean attachBuilderRole(Player player, UUID npcId);
    boolean canControlNpc(CommandSender sender, UUID npcId);
}

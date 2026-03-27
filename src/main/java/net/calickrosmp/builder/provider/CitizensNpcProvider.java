package net.calickrosmp.builder.provider;

import net.calickrosmp.builder.CalickroBuilderPlugin;
import net.citizensnpcs.api.event.NPCRemoveEvent;
import net.citizensnpcs.api.event.NPCSelectEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CitizensNpcProvider implements NpcProvider, Listener {
    private final CalickroBuilderPlugin plugin;
    private final Map<UUID, UUID> selectedNpcIdsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedNpcNamesByPlayer = new ConcurrentHashMap<>();
    private volatile boolean listenerRegistered;

    public CitizensNpcProvider(CalickroBuilderPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public NpcProviderType type() {
        return NpcProviderType.CITIZENS;
    }

    @Override
    public String name() {
        return "Citizens";
    }

    @Override
    public boolean isAvailable() {
        Plugin citizens = Bukkit.getPluginManager().getPlugin("Citizens");
        return citizens != null && citizens.isEnabled();
    }

    public void registerListenerIfAvailable() {
        if (!isAvailable() || listenerRegistered) {
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
        listenerRegistered = true;
        plugin.getLogger().info("Citizens provider hooked successfully.");
    }

    public void unregisterListenerIfRegistered() {
        if (!listenerRegistered) {
            return;
        }

        HandlerList.unregisterAll(this);
        listenerRegistered = false;
        selectedNpcIdsByPlayer.clear();
        selectedNpcNamesByPlayer.clear();
    }

    @Override
    public Optional<UUID> getSelectedNpcId(Player player) {
        return Optional.ofNullable(selectedNpcIdsByPlayer.get(player.getUniqueId()));
    }

    @Override
    public Optional<String> getSelectedNpcName(Player player) {
        return Optional.ofNullable(selectedNpcNamesByPlayer.get(player.getUniqueId()));
    }

    @Override
    public boolean attachBuilderRole(Player player, UUID npcId) {
        return isAvailable() && npcId.equals(selectedNpcIdsByPlayer.get(player.getUniqueId()));
    }

    @Override
    public boolean canControlNpc(CommandSender sender, UUID npcId) {
        return sender.hasPermission("calickrobuilder.admin");
    }

    @EventHandler
    public void onNpcSelect(NPCSelectEvent event) {
        CommandSender selector = event.getSelector();
        if (!(selector instanceof Player player)) {
            return;
        }

        NPC npc = event.getNPC();
        if (npc == null) {
            clearSelection(player.getUniqueId());
            return;
        }

        UUID playerId = player.getUniqueId();
        selectedNpcIdsByPlayer.put(playerId, uuidFromNpcId(npc.getId()));
        selectedNpcNamesByPlayer.put(playerId, sanitizeName(npc.getName()));
    }

    @EventHandler
    public void onNpcRemove(NPCRemoveEvent event) {
        UUID removed = uuidFromNpcId(event.getNPC().getId());

        selectedNpcIdsByPlayer.entrySet().removeIf(entry -> removed.equals(entry.getValue()));
        selectedNpcNamesByPlayer.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            UUID selected = selectedNpcIdsByPlayer.get(playerId);
            return removed.equals(selected);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearSelection(event.getPlayer().getUniqueId());
    }

    private void clearSelection(UUID playerId) {
        selectedNpcIdsByPlayer.remove(playerId);
        selectedNpcNamesByPlayer.remove(playerId);
    }

    private UUID uuidFromNpcId(int npcId) {
        return new UUID(0L, npcId & 0xFFFFFFFFL);
    }

    private String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "Builder";
        }
        return name;
    }
}
package net.calickrosmp.builder.npc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BuilderNpcRegistry {
    private final Map<UUID, BuilderProfile> builders = new ConcurrentHashMap<>();

    public BuilderProfile register(BuilderIdentity identity) {
        return builders.computeIfAbsent(identity.npcId(), key -> new BuilderProfile(identity));
    }

    public Optional<BuilderProfile> find(UUID npcId) {
        return Optional.ofNullable(builders.get(npcId));
    }
}

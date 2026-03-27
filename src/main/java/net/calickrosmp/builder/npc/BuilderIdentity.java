package net.calickrosmp.builder.npc;

import net.calickrosmp.builder.provider.NpcProviderType;

import java.util.UUID;

public record BuilderIdentity(UUID npcId, String displayName, NpcProviderType providerType) {}

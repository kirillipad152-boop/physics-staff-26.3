/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.network;

import dev.sableport.physicsstaff.PhysicsStaffMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server-to-client synchronization of locked sub-level UUIDs in a dimension.
 */
public record PhysicsStaffLocksPacket(ResourceKey<Level> dimension, Set<UUID> locks) implements CustomPacketPayload {
    public static final Type<PhysicsStaffLocksPacket> TYPE = new Type<>(PhysicsStaffMod.id("locks"));

    private static final StreamCodec<ByteBuf, Set<UUID>> UUID_SET_CODEC =
            ByteBufCodecs.collection(HashSet::new, UUIDUtil.STREAM_CODEC);

    public static final StreamCodec<ByteBuf, PhysicsStaffLocksPacket> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), PhysicsStaffLocksPacket::dimension,
            UUID_SET_CODEC, PhysicsStaffLocksPacket::locks,
            PhysicsStaffLocksPacket::new
    );

    @Override
    public Type<PhysicsStaffLocksPacket> type() {
        return TYPE;
    }
}

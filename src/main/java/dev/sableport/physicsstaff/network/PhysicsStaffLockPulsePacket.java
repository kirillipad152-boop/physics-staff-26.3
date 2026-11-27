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

import java.util.UUID;

/**
 * Server-to-client transient lock/unlock pulse feedback for nearby observers.
 */
public record PhysicsStaffLockPulsePacket(
        ResourceKey<Level> dimension,
        UUID subLevelUUID,
        boolean locked
) implements CustomPacketPayload {
    public static final Type<PhysicsStaffLockPulsePacket> TYPE = new Type<>(PhysicsStaffMod.id("lock_pulse"));

    public static final StreamCodec<ByteBuf, PhysicsStaffLockPulsePacket> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), PhysicsStaffLockPulsePacket::dimension,
            UUIDUtil.STREAM_CODEC, PhysicsStaffLockPulsePacket::subLevelUUID,
            ByteBufCodecs.BOOL, PhysicsStaffLockPulsePacket::locked,
            PhysicsStaffLockPulsePacket::new
    );

    @Override
    public Type<PhysicsStaffLockPulsePacket> type() {
        return TYPE;
    }
}

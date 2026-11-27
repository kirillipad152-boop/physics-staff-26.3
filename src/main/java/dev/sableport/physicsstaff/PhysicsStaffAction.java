/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

import java.util.function.IntFunction;

/**
 * Discrete server actions sent by a player wielding the Physics Staff.
 */
public enum PhysicsStaffAction {
    STOP_DRAG,
    LOCK,
    START_DRAG;

    private static final IntFunction<PhysicsStaffAction> BY_ID = ByIdMap.continuous(
            Enum::ordinal,
            values(),
            ByIdMap.OutOfBoundsStrategy.CLAMP
    );

    public static final StreamCodec<ByteBuf, PhysicsStaffAction> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);
}

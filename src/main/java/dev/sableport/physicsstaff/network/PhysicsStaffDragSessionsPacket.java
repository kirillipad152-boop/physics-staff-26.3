/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.network;

import dev.sableport.physicsstaff.PhysicsStaffMod;
import dev.ryanhcode.sable.util.SableBufferUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-client list of active Physics Staff drag anchors per dimension.
 */
public record PhysicsStaffDragSessionsPacket(ResourceKey<Level> dimension, List<DragEntry> sessions) implements CustomPacketPayload {
    public static final Type<PhysicsStaffDragSessionsPacket> TYPE = new Type<>(PhysicsStaffMod.id("drag_sessions"));

    public record DragEntry(UUID playerUUID, UUID subLevelUUID, Vector3dc plotAnchor) {
        public static final StreamCodec<ByteBuf, DragEntry> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public DragEntry decode(final ByteBuf buf) {
                final UUID playerUUID = UUIDUtil.STREAM_CODEC.decode(buf);
                final UUID subLevelUUID = UUIDUtil.STREAM_CODEC.decode(buf);
                final Vector3d anchor = SableBufferUtils.read(buf, new Vector3d());
                return new DragEntry(playerUUID, subLevelUUID, anchor);
            }

            @Override
            public void encode(final ByteBuf buf, final DragEntry entry) {
                UUIDUtil.STREAM_CODEC.encode(buf, entry.playerUUID);
                UUIDUtil.STREAM_CODEC.encode(buf, entry.subLevelUUID);
                SableBufferUtils.write(buf, entry.plotAnchor);
            }
        };
    }

    public static final StreamCodec<ByteBuf, PhysicsStaffDragSessionsPacket> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), PhysicsStaffDragSessionsPacket::dimension,
            ByteBufCodecs.collection(ArrayList::new, DragEntry.STREAM_CODEC), PhysicsStaffDragSessionsPacket::sessions,
            PhysicsStaffDragSessionsPacket::new
    );

    @Override
    public Type<PhysicsStaffDragSessionsPacket> type() {
        return TYPE;
    }
}

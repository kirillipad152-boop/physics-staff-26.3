/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.network;

import dev.sableport.physicsstaff.PhysicsStaffConfig;
import dev.sableport.physicsstaff.PhysicsStaffItem;
import dev.sableport.physicsstaff.PhysicsStaffMod;
import dev.sableport.physicsstaff.server.PhysicsStaffServerHandler;
import dev.ryanhcode.sable.util.SableBufferUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.UUID;

/**
 * Client-to-server continuous drag target update while dragging a sub-level.
 */
public record PhysicsStaffDragPacket(
        UUID subLevelUUID,
        Vector3dc playerRelativeGoal,
        Vector3dc localAnchor,
        Quaterniondc orientation
) implements CustomPacketPayload {
    public static final Type<PhysicsStaffDragPacket> TYPE = new Type<>(PhysicsStaffMod.id("drag"));

    public static final StreamCodec<ByteBuf, PhysicsStaffDragPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public PhysicsStaffDragPacket decode(final ByteBuf buf) {
            final UUID subLevelUUID = UUIDUtil.STREAM_CODEC.decode(buf);
            final Vector3d goal = SableBufferUtils.read(buf, new Vector3d());
            final Vector3d anchor = SableBufferUtils.read(buf, new Vector3d());
            final Quaterniond orientation = SableBufferUtils.read(buf, new Quaterniond());
            return new PhysicsStaffDragPacket(subLevelUUID, goal, anchor, orientation);
        }

        @Override
        public void encode(final ByteBuf buf, final PhysicsStaffDragPacket packet) {
            UUIDUtil.STREAM_CODEC.encode(buf, packet.subLevelUUID);
            SableBufferUtils.write(buf, packet.playerRelativeGoal);
            SableBufferUtils.write(buf, packet.localAnchor);
            SableBufferUtils.write(buf, packet.orientation);
        }
    };

    @Override
    public Type<PhysicsStaffDragPacket> type() {
        return TYPE;
    }

    public static void handle(final PhysicsStaffDragPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof final ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof final ServerLevel serverLevel)) {
                return;
            }
            if (player.isRemoved() || !PhysicsStaffItem.isHolding(player)) {
                return;
            }
            if (!isFinite(packet.playerRelativeGoal) || !isFinite(packet.localAnchor) || !isFiniteAndNonZero(packet.orientation)) {
                return;
            }

            final double maxRange = PhysicsStaffConfig.SYNCED.maxRange.get() + 4.0;
            final Vector3d clampedGoal = new Vector3d(packet.playerRelativeGoal);
            final double lenSq = clampedGoal.lengthSquared();
            if (lenSq > maxRange * maxRange) {
                clampedGoal.normalize(maxRange);
            }

            final Quaterniond normalizedOrientation = new Quaterniond(packet.orientation).normalize();
            PhysicsStaffServerHandler.get(serverLevel).drag(
                    player.getUUID(),
                    packet.subLevelUUID,
                    clampedGoal,
                    packet.localAnchor,
                    normalizedOrientation
            );
        });
    }

    private static boolean isFinite(final Vector3dc vec) {
        return Double.isFinite(vec.x()) && Double.isFinite(vec.y()) && Double.isFinite(vec.z());
    }

    private static boolean isFiniteAndNonZero(final Quaterniondc quat) {
        return Double.isFinite(quat.x())
                && Double.isFinite(quat.y())
                && Double.isFinite(quat.z())
                && Double.isFinite(quat.w())
                && quat.lengthSquared() > 1.0e-12;
    }
}

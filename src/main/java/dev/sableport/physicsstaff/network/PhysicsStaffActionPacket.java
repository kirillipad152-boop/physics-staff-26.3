/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.network;

import dev.sableport.physicsstaff.PhysicsStaffAction;
import dev.sableport.physicsstaff.PhysicsStaffConfig;
import dev.sableport.physicsstaff.PhysicsStaffItem;
import dev.sableport.physicsstaff.PhysicsStaffMod;
import dev.sableport.physicsstaff.server.PhysicsStaffServerHandler;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * Client-to-server discrete Physics Staff actions (start/stop drag, toggle lock).
 */
public record PhysicsStaffActionPacket(PhysicsStaffAction action, UUID subLevelUUID) implements CustomPacketPayload {
    public static final Type<PhysicsStaffActionPacket> TYPE = new Type<>(PhysicsStaffMod.id("action"));

    public static final StreamCodec<ByteBuf, PhysicsStaffActionPacket> STREAM_CODEC = StreamCodec.composite(
            PhysicsStaffAction.STREAM_CODEC, PhysicsStaffActionPacket::action,
            UUIDUtil.STREAM_CODEC, PhysicsStaffActionPacket::subLevelUUID,
            PhysicsStaffActionPacket::new
    );

    @Override
    public Type<PhysicsStaffActionPacket> type() {
        return TYPE;
    }

    public static void handle(final PhysicsStaffActionPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof final ServerPlayer player)) {
                return;
            }
            if (!(player.level() instanceof final ServerLevel serverLevel)) {
                return;
            }

            final PhysicsStaffServerHandler handler = PhysicsStaffServerHandler.get(serverLevel);

            if (packet.action == PhysicsStaffAction.STOP_DRAG) {
                handler.stopDragging(player.getUUID());
                return;
            }

            if (player.isRemoved() || !PhysicsStaffItem.isHolding(player)) {
                return;
            }

            final SubLevelContainer rawContainer = SubLevelContainer.getContainer(serverLevel);
            if (!(rawContainer instanceof final ServerSubLevelContainer container)) {
                return;
            }

            final SubLevel subLevel = container.getSubLevel(packet.subLevelUUID);
            if (subLevel == null || subLevel.isRemoved()) {
                return;
            }

            final double maxRange = PhysicsStaffConfig.SYNCED.maxRange.get() + 8.0;
            if (!isWithinInteractionRange(player.getEyePosition(), subLevel, maxRange)) {
                return;
            }

            if (packet.action == PhysicsStaffAction.LOCK) {
                handler.toggleLock(packet.subLevelUUID);
            } else if (packet.action == PhysicsStaffAction.START_DRAG) {
                // Drag pose updates arrive via PhysicsStaffDragPacket; nothing else required on start.
                handler.wakeUpSubLevel(packet.subLevelUUID);
            }
        });
    }

    private static boolean isWithinInteractionRange(final Vec3 eyePos, final SubLevel subLevel, final double maxRange) {
        final BoundingBox3dc bounds = subLevel.boundingBox();
        if (Double.isFinite(bounds.minX())
                && bounds.minX() <= bounds.maxX()
                && bounds.minY() <= bounds.maxY()
                && bounds.minZ() <= bounds.maxZ()) {
            final Vector3d closest = new Vector3d(
                    Math.clamp(eyePos.x, bounds.minX(), bounds.maxX()),
                    Math.clamp(eyePos.y, bounds.minY(), bounds.maxY()),
                    Math.clamp(eyePos.z, bounds.minZ(), bounds.maxZ())
            );
            return closest.distanceSquared(eyePos.x, eyePos.y, eyePos.z) <= maxRange * maxRange;
        }
        return subLevel.logicalPose().position().distanceSquared(eyePos.x, eyePos.y, eyePos.z) <= maxRange * maxRange;
    }
}

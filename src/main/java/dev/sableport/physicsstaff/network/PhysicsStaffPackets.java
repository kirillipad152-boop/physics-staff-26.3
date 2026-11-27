/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.network;

import dev.sableport.physicsstaff.PhysicsStaffMod;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers all common network payloads and provides server-to-client dispatch helpers.
 */
public final class PhysicsStaffPackets {
    public static final String PROTOCOL_VERSION = "1";

    private PhysicsStaffPackets() {}

    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(PhysicsStaffMod.MOD_ID)
                .versioned(PROTOCOL_VERSION);

        registrar.playToServer(
                PhysicsStaffActionPacket.TYPE,
                PhysicsStaffActionPacket.STREAM_CODEC,
                PhysicsStaffActionPacket::handle
        );
        registrar.playToServer(
                PhysicsStaffDragPacket.TYPE,
                PhysicsStaffDragPacket.STREAM_CODEC,
                PhysicsStaffDragPacket::handle
        );

        registrar.playToClient(
                PhysicsStaffLocksPacket.TYPE,
                PhysicsStaffLocksPacket.STREAM_CODEC
        );
        registrar.playToClient(
                PhysicsStaffDragSessionsPacket.TYPE,
                PhysicsStaffDragSessionsPacket.STREAM_CODEC
        );
        registrar.playToClient(
                PhysicsStaffLockPulsePacket.TYPE,
                PhysicsStaffLockPulsePacket.STREAM_CODEC
        );
    }

    public static void sendToPlayer(final ServerPlayer player, final CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToDimension(final ServerLevel level, final CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersInDimension(level, payload);
    }

    public static void sendToAllPlayers(final ServerLevel anyLevel, final CustomPacketPayload payload) {
        for (final ServerPlayer player : anyLevel.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}

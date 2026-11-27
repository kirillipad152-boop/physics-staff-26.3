/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.client;

import dev.sableport.physicsstaff.network.PhysicsStaffDragSessionsPacket;
import dev.sableport.physicsstaff.network.PhysicsStaffLockPulsePacket;
import dev.sableport.physicsstaff.network.PhysicsStaffLocksPacket;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-only payload handlers for Physics Staff state synchronization.
 */
public final class PhysicsStaffClientPackets {
    private PhysicsStaffClientPackets() {}

    public static void register(final RegisterClientPayloadHandlersEvent event) {
        event.register(PhysicsStaffLocksPacket.TYPE, PhysicsStaffClientPackets::handleLocks);
        event.register(PhysicsStaffDragSessionsPacket.TYPE, PhysicsStaffClientPackets::handleDragSessions);
        event.register(PhysicsStaffLockPulsePacket.TYPE, PhysicsStaffClientPackets::handleLockPulse);
    }

    private static void handleLocks(final PhysicsStaffLocksPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> PhysicsStaffClientHandler.get().updateLocks(packet.dimension(), packet.locks()));
    }

    private static void handleDragSessions(final PhysicsStaffDragSessionsPacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> PhysicsStaffClientHandler.get().updateRemoteSessions(packet.dimension(), packet.sessions()));
    }

    private static void handleLockPulse(final PhysicsStaffLockPulsePacket packet, final IPayloadContext context) {
        context.enqueueWork(() -> PhysicsStaffClientHandler.get().onLockPulse(
                packet.dimension(),
                packet.subLevelUUID(),
                packet.locked()
        ));
    }
}

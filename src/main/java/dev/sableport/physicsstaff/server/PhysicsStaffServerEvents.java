/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.server;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Connects Sable physics/container events and player lifecycle events to {@link PhysicsStaffServerHandler}.
 */
public final class PhysicsStaffServerEvents {
    private PhysicsStaffServerEvents() {}

    public static void register(final IEventBus neoBus) {
        neoBus.addListener(PhysicsStaffServerEvents::onSubLevelContainerReady);
        neoBus.addListener(PhysicsStaffServerEvents::onPrePhysicsTick);
        neoBus.addListener(PhysicsStaffServerEvents::onPlayerLoggedIn);
        neoBus.addListener(PhysicsStaffServerEvents::onPlayerChangedDimension);
        neoBus.addListener(PhysicsStaffServerEvents::onPlayerLoggedOut);
    }

    private static void onSubLevelContainerReady(final ForgeSableSubLevelContainerReadyEvent event) {
        if (!(event.getLevel() instanceof final ServerLevel serverLevel)) {
            return;
        }
        if (!(event.getContainer() instanceof final ServerSubLevelContainer container)) {
            return;
        }

        // ServerLevel.getChunkSource() is not yet initialized at this point in ServerLevel.<init>;
        // defer SavedData access until observer.tick() / onSubLevelAdded().
        container.addObserver(new PhysicsStaffSubLevelObserver(serverLevel));
    }

    private static void onPrePhysicsTick(final ForgeSablePrePhysicsTickEvent event) {
        final ServerLevel serverLevel = event.getPhysicsSystem().getLevel();
        PhysicsStaffServerHandler.get(serverLevel).physicsTick(event.getPhysicsSystem());
    }

    private static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            PhysicsStaffServerHandler.sendAllData(player);
        }
    }

    private static void onPlayerChangedDimension(final PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player) {
            PhysicsStaffServerHandler.sendAllData(player);
        }
    }

    private static void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof final ServerPlayer player
                && player.level() instanceof final ServerLevel serverLevel) {
            PhysicsStaffServerHandler.get(serverLevel).stopDragging(player.getUUID());
        }
    }
}

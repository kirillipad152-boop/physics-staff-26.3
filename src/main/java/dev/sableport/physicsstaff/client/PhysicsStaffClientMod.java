/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.client;

import dev.sableport.physicsstaff.PhysicsStaffMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterDebugRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only entrypoint registering Physics Staff keybinds, packets, debug renderer, and input handlers.
 */
@Mod(value = PhysicsStaffMod.MOD_ID, dist = Dist.CLIENT)
public final class PhysicsStaffClientMod {
    public PhysicsStaffClientMod(final IEventBus modBus) {
        modBus.addListener(PhysicsStaffKeys::register);
        modBus.addListener(PhysicsStaffClientPackets::register);
        modBus.addListener((final RegisterDebugRenderersEvent event) ->
                event.register(new PhysicsStaffRenderHandler()));

        PhysicsStaffClientEvents.register(NeoForge.EVENT_BUS);
    }
}

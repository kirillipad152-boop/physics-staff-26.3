/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sableport.physicsstaff.client.PhysicsStaffClientHandler;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Intercepts mouse camera turn to rotate a dragged sub-level while holding the Physics Staff rotate key.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @WrapOperation(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void physicsStaff$interceptTurn(
            final LocalPlayer player,
            final double yaw,
            final double pitch,
            final Operation<Void> original
    ) {
        if (PhysicsStaffClientHandler.get().onMouseMove(yaw, pitch)) {
            return;
        }
        original.call(player, yaw, pitch);
    }
}

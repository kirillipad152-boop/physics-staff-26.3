/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.sableport.physicsstaff.PhysicsStaffItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Handles client input interception, per-tick updates, hand animation polish, and logout cleanup.
 */
public final class PhysicsStaffClientEvents {
    private static boolean attackWasDown = false;
    private static boolean useWasDown = false;

    private PhysicsStaffClientEvents() {}

    public static void register(final IEventBus neoBus) {
        neoBus.addListener(PhysicsStaffClientEvents::onClientTickPre);
        neoBus.addListener(PhysicsStaffClientEvents::onInteractionTriggered);
        neoBus.addListener(PhysicsStaffClientEvents::onMouseScroll);
        neoBus.addListener(PhysicsStaffClientEvents::onRenderHand);
        neoBus.addListener(PhysicsStaffClientEvents::onLoggingOut);
    }

    private static void onClientTickPre(final ClientTickEvent.Pre event) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.keyAttack.isDown()) {
            attackWasDown = false;
        }
        if (!minecraft.options.keyUse.isDown()) {
            useWasDown = false;
        }
        PhysicsStaffClientHandler.get().tick();
    }

    private static void onInteractionTriggered(final InputEvent.InteractionKeyMappingTriggered event) {
        final Minecraft minecraft = Minecraft.getInstance();
        final LocalPlayer player = minecraft.player;
        if (player == null || !PhysicsStaffItem.isHolding(player)) {
            return;
        }

        // Prevent vanilla creative block breaking or off-hand item use while holding the Physics Staff.
        if (event.isAttack()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (!attackWasDown) {
                attackWasDown = true;
                PhysicsStaffClientHandler.get().onAttack();
            }
        } else if (event.isUseItem()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            if (!useWasDown) {
                useWasDown = true;
                PhysicsStaffClientHandler.get().onUse();
            }
        }
    }

    private static void onMouseScroll(final InputEvent.MouseScrollingEvent event) {
        if (PhysicsStaffClientHandler.get().onScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    private static void onRenderHand(final RenderHandEvent event) {
        if (!(event.getItemStack().getItem() instanceof PhysicsStaffItem)) {
            return;
        }
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        final PhysicsStaffClientHandler handler = PhysicsStaffClientHandler.get();
        final float ext = handler.getHandExtension(event.getPartialTick());
        final float lockFlash = handler.getLockFlashProgress();
        if (ext <= 1.0e-4f && lockFlash <= 1.0e-4f) {
            return;
        }

        final PoseStack poseStack = event.getPoseStack();
        final boolean isRightArm = (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND)
                ? player.getMainArm() == HumanoidArm.RIGHT
                : player.getMainArm() != HumanoidArm.RIGHT;
        final float side = isRightArm ? 1.0f : -1.0f;

        poseStack.translate(-side * 0.04f * ext, 0.04f * ext, -0.10f * ext);
        poseStack.rotateDegrees(Axis.XP, -14.0f * ext - 12.0f * lockFlash);
        poseStack.rotateDegrees(Axis.YP, side * 6.0f * ext);
    }

    private static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() != null) {
            attackWasDown = false;
            useWasDown = false;
            PhysicsStaffClientHandler.get().reset();
        }
    }
}

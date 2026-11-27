/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

/**
 * Creative Physics Staff used to grab, move, rotate, and freeze Sable sub-levels.
 */
public class PhysicsStaffItem extends Item {
    public static final float DEFAULT_RANGE = 128.0f;
    public static final double MIN_DRAG_DISTANCE = 2.0;

    public PhysicsStaffItem(final Properties properties) {
        super(properties);
    }

    @Override
    public boolean canDestroyBlock(
            final ItemStack itemStack,
            final BlockState state,
            final Level level,
            final BlockPos pos,
            final LivingEntity user
    ) {
        return false;
    }

    public static boolean isHolding(final Player player) {
        return player.getMainHandItem().getItem() instanceof PhysicsStaffItem
                || player.getOffhandItem().getItem() instanceof PhysicsStaffItem;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(
            final ItemStack stack,
            final TooltipContext context,
            final TooltipDisplay display,
            final Consumer<Component> builder,
            final TooltipFlag tooltipFlag
    ) {
        builder.accept(Component.translatable("item.physics_staff.creative_physics_staff.tooltip.drag")
                .withStyle(ChatFormatting.AQUA));
        builder.accept(Component.translatable("item.physics_staff.creative_physics_staff.tooltip.lock")
                .withStyle(ChatFormatting.GOLD));
        builder.accept(Component.translatable("item.physics_staff.creative_physics_staff.tooltip.rotate")
                .withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.physics_staff.creative_physics_staff.tooltip.scroll")
                .withStyle(ChatFormatting.GRAY));
    }
}

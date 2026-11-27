/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.sableport.physicsstaff.PhysicsStaffMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Keybindings for rotating and distance-adjusting sub-levels held by the Physics Staff.
 */
public final class PhysicsStaffKeys {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(PhysicsStaffMod.id("general"));

    public static final KeyMapping ROTATE_MODE = new KeyMapping(
            "key.physics_staff.rotate_mode",
            InputConstants.KEY_TAB,
            CATEGORY
    );

    public static final KeyMapping SCROLL_UP = new KeyMapping(
            "key.physics_staff.scroll_up",
            InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    public static final KeyMapping SCROLL_DOWN = new KeyMapping(
            "key.physics_staff.scroll_down",
            InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY
    );

    private PhysicsStaffKeys() {}

    public static void register(final RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(ROTATE_MODE);
        event.register(SCROLL_UP);
        event.register(SCROLL_DOWN);
    }
}

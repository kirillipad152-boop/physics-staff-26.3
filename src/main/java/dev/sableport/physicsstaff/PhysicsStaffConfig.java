/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Synced gameplay tuning and client input preferences for the Creative Physics Staff.
 */
public final class PhysicsStaffConfig {
    public static final Synced SYNCED;
    public static final ModConfigSpec SYNCED_SPEC;

    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        final var syncedPair = new ModConfigSpec.Builder().configure(Synced::new);
        SYNCED = syncedPair.getLeft();
        SYNCED_SPEC = syncedPair.getRight();

        final var clientPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();
    }

    private PhysicsStaffConfig() {}

    public static final class Synced {
        public final ModConfigSpec.DoubleValue linearStiffness;
        public final ModConfigSpec.DoubleValue linearDamping;
        public final ModConfigSpec.DoubleValue angularStiffness;
        public final ModConfigSpec.DoubleValue angularDamping;
        public final ModConfigSpec.DoubleValue maxRange;

        private Synced(final ModConfigSpec.Builder builder) {
            builder.push("physics_staff");
            this.linearStiffness = builder
                    .comment("Linear motor stiffness for sub-level dragging.")
                    .defineInRange("linearStiffness", 2650.0, 1.0, 100000.0);
            this.linearDamping = builder
                    .comment("Linear motor damping for sub-level dragging.")
                    .defineInRange("linearDamping", 125.0, 0.0, 10000.0);
            this.angularStiffness = builder
                    .comment("Angular motor stiffness for sub-level orientation holding/rotation.")
                    .defineInRange("angularStiffness", 10000.0, 1.0, 500000.0);
            this.angularDamping = builder
                    .comment("Angular motor damping for sub-level orientation holding/rotation.")
                    .defineInRange("angularDamping", 850.0, 0.0, 50000.0);
            this.maxRange = builder
                    .comment("Maximum raycast & drag distance in blocks.")
                    .defineInRange("maxRange", 128.0, 8.0, 512.0);
            builder.pop();
        }
    }

    public static final class Client {
        public final ModConfigSpec.DoubleValue scrollSensitivity;
        public final ModConfigSpec.DoubleValue rotateSensitivity;
        public final ModConfigSpec.BooleanValue renderLockIndicators;

        private Client(final ModConfigSpec.Builder builder) {
            builder.push("physics_staff");
            this.scrollSensitivity = builder
                    .comment("Multiplier for adjusting drag distance with the scroll wheel.")
                    .defineInRange("scrollSensitivity", 1.0, 0.1, 10.0);
            this.rotateSensitivity = builder
                    .comment("Multiplier for rotating held sub-levels while holding the Rotate key.")
                    .defineInRange("rotateSensitivity", 1.0, 0.1, 10.0);
            this.renderLockIndicators = builder
                    .comment("Render lock markers on frozen sub-levels while holding the Physics Staff.")
                    .define("renderLockIndicators", true);
            builder.pop();
        }
    }
}

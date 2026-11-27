/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.server;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;

/**
 * Observes Sable sub-level lifecycle events on the server to re-apply or clean up Physics Staff locks and drag sessions.
 */
public class PhysicsStaffSubLevelObserver implements SubLevelObserver {
    private final ServerLevel level;
    private boolean initializedLocks = false;

    public PhysicsStaffSubLevelObserver(final ServerLevel level) {
        this.level = level;
    }

    @Override
    public void tick(final SubLevelContainer container) {
        if (this.level.getChunkSource() == null) {
            return;
        }
        final PhysicsStaffServerHandler handler = PhysicsStaffServerHandler.get(this.level);
        if (!this.initializedLocks) {
            this.initializedLocks = true;
            for (final SubLevel subLevel : container.getAllSubLevels()) {
                handler.applyLockIfNeeded(subLevel);
            }
        }
        handler.tick();
    }

    @Override
    public void onSubLevelAdded(final SubLevel subLevel) {
        if (this.level.getChunkSource() != null) {
            PhysicsStaffServerHandler.get(this.level).applyLockIfNeeded(subLevel);
        }
    }

    @Override
    public void onSubLevelRemoved(final SubLevel subLevel, final SubLevelRemovalReason reason) {
        if (this.level.getChunkSource() != null) {
            PhysicsStaffServerHandler.get(this.level).onSubLevelRemoved(subLevel, reason == SubLevelRemovalReason.REMOVED);
        }
    }
}

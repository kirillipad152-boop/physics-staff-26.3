/*
 * Copyright (c) The Simulated Team / The Creators of Aeronautics
 * Licensed under the MIT License. See LICENSE.md in the project root.
 */

package dev.sableport.physicsstaff.server;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.sableport.physicsstaff.PhysicsStaffConfig;
import dev.sableport.physicsstaff.PhysicsStaffItem;
import dev.sableport.physicsstaff.PhysicsStaffMod;
import dev.sableport.physicsstaff.network.PhysicsStaffDragSessionsPacket;
import dev.sableport.physicsstaff.network.PhysicsStaffLockPulsePacket;
import dev.sableport.physicsstaff.network.PhysicsStaffLocksPacket;
import dev.sableport.physicsstaff.network.PhysicsStaffPackets;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-level persistent lock storage and active server-side drag constraints for the Physics Staff.
 */
public class PhysicsStaffServerHandler extends SavedData {
    private static final Codec<PhysicsStaffServerHandler> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUIDUtil.CODEC.listOf()
                            .optionalFieldOf("locks", List.of())
                            .forGetter(PhysicsStaffServerHandler::getLockedUuids)
            ).apply(instance, PhysicsStaffServerHandler::fromLockedUuids)
    );

    public static final SavedDataType<PhysicsStaffServerHandler> TYPE = new SavedDataType<>(
            PhysicsStaffMod.id("physics_staff_locks"),
            () -> new PhysicsStaffServerHandler(),
            CODEC
    );

    private final Map<UUID, Lock> locks = new HashMap<>();
    private final Map<UUID, DragSession> draggingSessions = new HashMap<>();
    private @Nullable ServerLevel level;
    private boolean draggingSessionsDirty = false;

    public PhysicsStaffServerHandler() {}

    private static PhysicsStaffServerHandler fromLockedUuids(final List<UUID> lockedUuids) {
        final PhysicsStaffServerHandler handler = new PhysicsStaffServerHandler();
        for (final UUID uuid : lockedUuids) {
            handler.locks.put(uuid, new Lock(uuid, null));
        }
        return handler;
    }

    private List<UUID> getLockedUuids() {
        return List.copyOf(this.locks.keySet());
    }

    public static PhysicsStaffServerHandler get(final ServerLevel level) {
        final PhysicsStaffServerHandler data = level.getDataStorage().computeIfAbsent(TYPE);
        data.level = level;
        return data;
    }

    public static void sendAllData(final ServerPlayer player) {
        if (!(player.level() instanceof final ServerLevel currentLevel)) {
            return;
        }
        for (final ServerLevel level : currentLevel.getServer().getAllLevels()) {
            final PhysicsStaffServerHandler handler = get(level);
            PhysicsStaffPackets.sendToPlayer(player, new PhysicsStaffLocksPacket(
                    level.dimension(),
                    new HashSet<>(handler.locks.keySet())
            ));
            PhysicsStaffPackets.sendToPlayer(player, makeSessionsPacket(level, handler));
        }
    }

    private static PhysicsStaffDragSessionsPacket makeSessionsPacket(
            final ServerLevel level,
            final PhysicsStaffServerHandler handler
    ) {
        final List<PhysicsStaffDragSessionsPacket.DragEntry> sessions =
                new ArrayList<>(handler.draggingSessions.size());
        for (final Map.Entry<UUID, DragSession> entry : handler.draggingSessions.entrySet()) {
            final DragSession session = entry.getValue();
            sessions.add(new PhysicsStaffDragSessionsPacket.DragEntry(
                    entry.getKey(),
                    session.subLevel.getUniqueId(),
                    new Vector3d(session.plotAnchor)
            ));
        }
        return new PhysicsStaffDragSessionsPacket(level.dimension(), sessions);
    }

    private static @Nullable FixedConstraintHandle createLockConstraint(
            final ServerSubLevelContainer container,
            final ServerSubLevel subLevel
    ) {
        final SubLevelPhysicsSystem physicsSystem = container.physicsSystem();
        if (physicsSystem == null) {
            return null;
        }
        final PhysicsPipeline pipeline = physicsSystem.getPipeline();
        return pipeline.addConstraint(null, subLevel, new FixedConstraintConfiguration(
                subLevel.logicalPose().position(),
                subLevel.logicalPose().rotationPoint(),
                subLevel.logicalPose().orientation()
        ));
    }

    public void tick() {
        if (this.level == null) {
            return;
        }

        final Iterator<Map.Entry<UUID, DragSession>> iter = this.draggingSessions.entrySet().iterator();
        while (iter.hasNext()) {
            final Map.Entry<UUID, DragSession> entry = iter.next();
            final DragSession session = entry.getValue();
            final Player player = this.level.getPlayerByUUID(entry.getKey());

            if (player == null || player.isRemoved() || !PhysicsStaffItem.isHolding(player)) {
                session.onRemoved();
                iter.remove();
                this.markDraggingSessionsDirty();
                continue;
            }

            session.tick();
            if (session.isMarkedForRemoval()) {
                session.onRemoved();
                iter.remove();
                this.markDraggingSessionsDirty();
            }
        }

        if (this.draggingSessionsDirty) {
            this.sendDragSessionsToClients();
            this.draggingSessionsDirty = false;
        }
    }

    public void physicsTick(final SubLevelPhysicsSystem physicsSystem) {
        for (final DragSession session : this.draggingSessions.values()) {
            session.physicsTick(physicsSystem);
        }
    }

    public void toggleLock(final UUID subLevelUUID) {
        if (this.level == null) {
            return;
        }
        final SubLevelContainer rawContainer = SubLevelContainer.getContainer(this.level);
        if (!(rawContainer instanceof final ServerSubLevelContainer container)) {
            return;
        }
        final SubLevel rawSubLevel = container.getSubLevel(subLevelUUID);
        if (!(rawSubLevel instanceof final ServerSubLevel subLevel) || subLevel.isRemoved()) {
            return;
        }

        final Lock existingLock = this.locks.get(subLevelUUID);
        if (existingLock != null) {
            this.locks.remove(subLevelUUID);
            existingLock.remove();
            if (container.physicsSystem() != null) {
                container.physicsSystem().getPipeline().wakeUp(subLevel);
            }
            this.setLocksDirty();
            PhysicsStaffPackets.sendToDimension(this.level, new PhysicsStaffLockPulsePacket(
                    this.level.dimension(),
                    subLevelUUID,
                    false
            ));
            return;
        }

        // Stop any active drag on this sub-level before freezing it in place.
        this.stopDraggingSubLevel(subLevelUUID);

        final FixedConstraintHandle handle = createLockConstraint(container, subLevel);
        this.locks.put(subLevelUUID, new Lock(subLevelUUID, handle));
        this.setLocksDirty();
        PhysicsStaffPackets.sendToDimension(this.level, new PhysicsStaffLockPulsePacket(
                this.level.dimension(),
                subLevelUUID,
                true
        ));
    }

    public void wakeUpSubLevel(final UUID subLevelUUID) {
        if (this.level == null) {
            return;
        }
        final SubLevelContainer rawContainer = SubLevelContainer.getContainer(this.level);
        if (!(rawContainer instanceof final ServerSubLevelContainer container) || container.physicsSystem() == null) {
            return;
        }
        final SubLevel rawSubLevel = container.getSubLevel(subLevelUUID);
        if (rawSubLevel instanceof final ServerSubLevel subLevel && !subLevel.isRemoved()) {
            container.physicsSystem().getPipeline().wakeUp(subLevel);
        }
    }

    public boolean isLocked(final SubLevel subLevel) {
        final Lock lock = this.locks.get(subLevel.getUniqueId());
        return lock != null && lock.handle != null && lock.handle.isValid();
    }

    public void applyLockIfNeeded(final SubLevel subLevel) {
        if (this.level == null || !(subLevel instanceof final ServerSubLevel serverSubLevel) || subLevel.isRemoved()) {
            return;
        }
        final Lock lock = this.locks.get(subLevel.getUniqueId());
        if (lock != null && (lock.handle == null || !lock.handle.isValid())) {
            final SubLevelContainer rawContainer = SubLevelContainer.getContainer(this.level);
            if (!(rawContainer instanceof final ServerSubLevelContainer container)) {
                return;
            }
            final FixedConstraintHandle handle = createLockConstraint(container, serverSubLevel);
            this.locks.put(lock.subLevel(), new Lock(lock.subLevel(), handle));
        }
    }

    public void removeLock(final SubLevel subLevel) {
        final Lock removedLock = this.locks.remove(subLevel.getUniqueId());
        if (removedLock != null) {
            removedLock.remove();
            this.setLocksDirty();
        }
    }

    public void onSubLevelRemoved(final SubLevel subLevel, final boolean permanentRemoval) {
        this.stopDraggingSubLevel(subLevel.getUniqueId());
        if (permanentRemoval) {
            this.removeLock(subLevel);
        } else {
            // Release runtime constraint while unloaded, preserving UUID in SavedData.
            final Lock lock = this.locks.get(subLevel.getUniqueId());
            if (lock != null && lock.handle != null) {
                lock.remove();
                this.locks.put(lock.subLevel(), new Lock(lock.subLevel(), null));
            }
        }
    }

    public void drag(
            final UUID playerUUID,
            final UUID subLevelUUID,
            final Vector3dc globalGoal,
            final Vector3dc localAnchor,
            final Quaterniondc orientation
    ) {
        if (this.level == null) {
            return;
        }
        final SubLevelContainer rawContainer = SubLevelContainer.getContainer(this.level);
        if (!(rawContainer instanceof final ServerSubLevelContainer container)) {
            return;
        }
        final SubLevel rawSubLevel = container.getSubLevel(subLevelUUID);
        if (!(rawSubLevel instanceof final ServerSubLevel subLevel) || subLevel.isRemoved()) {
            return;
        }

        this.removeLock(subLevel);

        DragSession session = this.draggingSessions.get(playerUUID);
        if (session != null && session.subLevel != subLevel) {
            session.onRemoved();
            session = null;
        }
        if (session == null) {
            session = new DragSession(playerUUID, subLevel);
            this.draggingSessions.put(playerUUID, session);
            this.markDraggingSessionsDirty();
        }

        session.playerRelativeGoal.set(globalGoal);
        session.plotAnchor.set(localAnchor);
        session.orientation.set(orientation);
    }

    public void stopDragging(final UUID playerUUID) {
        final DragSession session = this.draggingSessions.remove(playerUUID);
        if (session != null) {
            this.markDraggingSessionsDirty();
            session.onRemoved();
        }
    }

    private void stopDraggingSubLevel(final UUID subLevelUUID) {
        boolean removedAny = false;
        final Iterator<Map.Entry<UUID, DragSession>> iter = this.draggingSessions.entrySet().iterator();
        while (iter.hasNext()) {
            final DragSession session = iter.next().getValue();
            if (session.subLevel.getUniqueId().equals(subLevelUUID)) {
                session.onRemoved();
                iter.remove();
                removedAny = true;
            }
        }
        if (removedAny) {
            this.markDraggingSessionsDirty();
        }
    }

    private void setLocksDirty() {
        this.setDirty(true);
        if (this.level != null) {
            PhysicsStaffPackets.sendToAllPlayers(this.level, new PhysicsStaffLocksPacket(
                    this.level.dimension(),
                    new HashSet<>(this.locks.keySet())
            ));
        }
    }

    private void markDraggingSessionsDirty() {
        this.draggingSessionsDirty = true;
    }

    private void sendDragSessionsToClients() {
        if (this.level != null) {
            PhysicsStaffPackets.sendToAllPlayers(this.level, makeSessionsPacket(this.level, this));
        }
    }

    private record Lock(UUID subLevel, @Nullable PhysicsConstraintHandle handle) {
        private void remove() {
            if (this.handle != null) {
                this.handle.remove();
            }
        }
    }

    private static final class DragSession {
        private final UUID playerUUID;
        private final ServerSubLevel subLevel;
        private final Vector3d plotAnchor = new Vector3d();
        private final Vector3d playerRelativeGoal = new Vector3d();
        private final Vector3d localGoal = new Vector3d();
        private final Quaterniond orientation = new Quaterniond();
        private boolean markedForRemoval = false;
        private @Nullable PhysicsConstraintHandle constraint = null;

        private DragSession(final UUID playerUUID, final ServerSubLevel subLevel) {
            this.playerUUID = playerUUID;
            this.subLevel = subLevel;
        }

        private void tick() {
            if (this.subLevel.isRemoved()) {
                this.markedForRemoval = true;
            }
        }

        private void physicsTick(final SubLevelPhysicsSystem physicsSystem) {
            if (this.subLevel.isRemoved()) {
                return;
            }

            if (this.constraint != null) {
                this.constraint.remove();
                this.constraint = null;
            }

            final PhysicsPipeline pipeline = physicsSystem.getPipeline();
            pipeline.wakeUp(this.subLevel);
            this.constraint = pipeline.addConstraint(
                    null,
                    this.subLevel,
                    new FreeConstraintConfiguration(JOMLConversion.ZERO, this.plotAnchor, this.orientation)
            );

            final Player player = this.subLevel.getLevel().getPlayerByUUID(this.playerUUID);
            if (player == null || this.constraint == null) {
                return;
            }

            final double angularStiffness = PhysicsStaffConfig.SYNCED.angularStiffness.get();
            final double angularDamping = PhysicsStaffConfig.SYNCED.angularDamping.get();
            final double linearStiffness = PhysicsStaffConfig.SYNCED.linearStiffness.get();
            final double linearDamping = PhysicsStaffConfig.SYNCED.linearDamping.get();

            for (final ConstraintJointAxis angularAxis : ConstraintJointAxis.ANGULAR) {
                this.constraint.setMotor(angularAxis, 0.0, angularStiffness, angularDamping, false, 0.0);
            }

            final double partialTick = physicsSystem.getPartialPhysicsTick();
            final double eyePosX = Mth.lerp(partialTick, player.xOld, player.getX());
            final double eyePosY = Mth.lerp(partialTick, player.yOld, player.getY()) + player.getEyeHeight();
            final double eyePosZ = Mth.lerp(partialTick, player.zOld, player.getZ());

            this.localGoal.set(this.playerRelativeGoal).add(eyePosX, eyePosY, eyePosZ);
            this.orientation.transformInverse(this.localGoal);

            this.constraint.setMotor(ConstraintJointAxis.LINEAR_X, this.localGoal.x(), linearStiffness, linearDamping, false, 0.0);
            this.constraint.setMotor(ConstraintJointAxis.LINEAR_Y, this.localGoal.y(), linearStiffness, linearDamping, false, 0.0);
            this.constraint.setMotor(ConstraintJointAxis.LINEAR_Z, this.localGoal.z(), linearStiffness, linearDamping, false, 0.0);
        }

        private boolean isMarkedForRemoval() {
            return this.markedForRemoval;
        }

        private void onRemoved() {
            if (this.constraint != null) {
                this.constraint.remove();
                this.constraint = null;
            }
        }
    }
}
